/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wemo.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.IllegalClassException;
import org.apache.commons.lang.StringUtils;
import org.teleal.cling.model.Namespace;
import org.openhab.binding.wemo.WemoBindingProvider;
import org.openhab.binding.wemo.WemoCommandType;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.TypeParser;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.DefaultUpnpServiceConfiguration;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.binding.xml.DeviceDescriptorBinder;
import org.teleal.cling.binding.xml.ServiceDescriptorBinder;
import org.teleal.cling.binding.xml.UDA10DeviceDescriptorBinderSAXImpl;
import org.teleal.cling.controlpoint.SubscriptionCallback;
import org.teleal.cling.model.gena.CancelReason;
import org.teleal.cling.model.gena.GENASubscription;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.message.header.DeviceTypeHeader;
import org.teleal.cling.model.message.header.STAllHeader;
import org.teleal.cling.model.message.header.ServiceTypeHeader;
import org.teleal.cling.model.message.header.UDAServiceTypeHeader;
import org.teleal.cling.model.message.header.UDNHeader;
import org.teleal.cling.model.meta.LocalDevice;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.state.StateVariableValue;
import org.teleal.cling.model.types.DeviceType;
import org.teleal.cling.model.types.ServiceType;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.model.types.UDAServiceType;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import org.teleal.cling.transport.impl.apache.StreamClientConfigurationImpl;
import org.teleal.cling.transport.impl.apache.StreamClientImpl;
import org.teleal.cling.transport.impl.apache.StreamServerConfigurationImpl;
import org.teleal.cling.transport.impl.apache.StreamServerImpl;
import org.teleal.cling.transport.spi.NetworkAddressFactory;
import org.teleal.cling.transport.spi.StreamClient;
import org.teleal.cling.transport.spi.StreamServer;
import org.xml.sax.SAXException;

/**
 * @author Karel Goderis
 * @author Pauli Anttila
 * @since 1.1.0
 * 
 */
public class WemoBinding extends AbstractBinding<WemoBindingProvider>
		implements ManagedService {

	private static Logger logger = LoggerFactory.getLogger(WemoBinding.class);

	private static final Pattern EXTRACT_WEMO_CONFIG_PATTERN = Pattern
			.compile("^(.*?)\\.(udn)$");

	private Map<String, WemoDevice> wemoDeviceCache = Collections
			.synchronizedMap(new HashMap<String, WemoDevice>());

	static protected UpnpService upnpService;
	static protected WemoBinding self;

	static protected Integer interval = 600;
	static protected boolean bindingStarted = false;

	private List<String> wemoDevicesFromCfg = null;

	private int pollingPeriod = 1000;

	public class WemoUpnpServiceConfiguration extends
			DefaultUpnpServiceConfiguration {

		@Override
		public Namespace createNamespace() {
			return new Namespace("Belkin");
		}
		
		@Override
		public DeviceDescriptorBinder getDeviceDescriptorBinderUDA10(){
			return new myUDA10DeviceDescriptorBinderSAXImpl();
		}
		
		@Override
		public ServiceDescriptorBinder getServiceDescriptorBinderUDA10(){
			return new myUDA10ServiceDescriptorBinderSAXImpl();
		}
		
		@SuppressWarnings("rawtypes")
		@Override
		public StreamClient createStreamClient() {
			return new StreamClientImpl(new StreamClientConfigurationImpl());
		}

		@SuppressWarnings("rawtypes")
		@Override
		public StreamServer createStreamServer(
				NetworkAddressFactory networkAddressFactory) {
			return new StreamServerImpl(new StreamServerConfigurationImpl(
					networkAddressFactory.getStreamListenPort()));
		}

	}

	RegistryListener listener = new RegistryListener() {

		public void remoteDeviceDiscoveryStarted(Registry registry,
				RemoteDevice device) {
			logger.debug("Discovery started: " + device.getDisplayString());
		}

		public void remoteDeviceDiscoveryFailed(Registry registry,
				RemoteDevice device, Exception ex) {
			logger.debug("Discovery failed: " + device.getDisplayString()
					+ " => " + ex);
		}

		@SuppressWarnings("rawtypes")
		public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
			logger.debug("Remote device available: "
					+ device.getDisplayString());
			// add only WeMo devices
			logger.debug("Manufacturer: " + device.getDetails().getManufacturerDetails().getManufacturer());
			if (device.getDetails().getManufacturerDetails().getManufacturer()
					.toUpperCase().contains("BELKIN")) {

				// ignore Zone Bridges
				logger.debug("Model Number: " + device.getDetails().getModelDetails().getModelNumber());
				if (device.getDetails().getModelDetails().getModelNumber()
						.toUpperCase().contains("1.0")) {
					UDN udn = device.getIdentity().getUdn();
					boolean existingDevice = false;
					// Check if we already received a configuration for this
					// device through the .cfg
					for (String item : wemoDeviceCache.keySet()) {
						WemoDevice wemoConfig = wemoDeviceCache
								.get(item);
						if (wemoConfig.getUdn().equals(udn)) {
							// We already have an (empty) config, populate it
							logger.debug(
									"Found UPNP device {} matching a pre-defined config {}",
									device, wemoConfig);
							wemoConfig.setDevice(device);
							wemoConfig.setService(upnpService);

							existingDevice = true;
						}
					}

					if (!existingDevice) {
						// Add device to the cached Configs
						WemoDevice newConfig = new WemoDevice(self);
						newConfig.setUdn(udn);
						newConfig.setDevice(device);
						newConfig.setService(upnpService);

						String wemoID = StringUtils.substringAfter(newConfig
								.getUdn().toString(), ":");

						wemoDeviceCache.put(wemoID, newConfig);
						logger.debug(
								"Added a new Wemo with ID {} as configuration for device {}",
								wemoID, newConfig);

					}

					// add GENA service to capture zonegroup information
					ServiceType serviceType = new ServiceType("Belkin", "basicevent", 1);
					Service service = device.findService(serviceType);
					
					WemoSubscriptionCallback callback = new WemoSubscriptionCallback(
							service, interval);
					upnpService.getControlPoint().execute(callback);
					logger.debug("Added a GENA Subscription in the Wemo Binding for service {} on device {}",service,device);

				} else {
					logger.debug("Ignore Wemo device");
				}
			} else {
				logger.debug("Ignore non WeMo devices");
			}
		}

		public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
			logger.trace("Remote device updated: " + device.getDisplayString());
		}

		public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
			logger.trace("Remote device removed: " + device.getDisplayString());
		}

		public void localDeviceAdded(Registry registry, LocalDevice device) {
			logger.trace("Local device added: " + device.getDisplayString());
		}

		public void localDeviceRemoved(Registry registry, LocalDevice device) {
			logger.trace("Local device removed: " + device.getDisplayString());
		}

		public void beforeShutdown(Registry registry) {
			logger.debug("Before shutdown, the registry has devices: "
					+ registry.getDevices().size());
		}

		public void afterShutdown() {
			logger.debug("Shutdown of registry complete!");

		}
	};

	public WemoBinding() {
		self = this;
	}

	public void activate() {
		start();
	}

	/**
	 * Find the first matching {@link ChannelBindingProvider} according to
	 * <code>itemName</code>
	 * 
	 * @param itemName
	 * 
	 * @return the matching binding provider or <code>null</code> if no binding
	 *         provider could be found
	 */
	protected WemoBindingProvider findFirstMatchingBindingProvider(
			String itemName) {
		logger.debug("findFirstMatchingBindingProvider {}", itemName);
		WemoBindingProvider firstMatchingProvider = null;
		for (WemoBindingProvider provider : providers) {
			List<String> wemoIDs = provider.getWemoID(itemName);
			if (wemoIDs != null && wemoIDs.size() > 0) {
				firstMatchingProvider = provider;
				break;
			}
		}
		return firstMatchingProvider;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		start();
	}

	@Override
	protected void internalReceiveCommand(String itemName, Command command) {

		logger.debug("internalReceiveCommand is: " + command.toString() + " itemName is " + itemName.toString());
		WemoBindingProvider provider = findFirstMatchingBindingProvider(itemName);
		String commandAsString = command.toString();
		if (command != null) {

			List<Command> commands = new ArrayList<Command>();

			if (command instanceof StringType || command instanceof DecimalType) {
				commands = provider.getVariableCommands(itemName);
			} else {
				commands.add(command);
			}

			for (Command someCommand : commands) {

				String wemoID = provider.getWemoID(itemName, someCommand);
				Direction direction = provider.getDirection(itemName,
						someCommand);
				WemoCommandType wemoCommandType = provider
						.getWemoCommandType(itemName, someCommand, direction);

				if (wemoID != null && direction != null) {
					if (wemoCommandType != null) {
						if (direction.equals(Direction.OUT)
								| direction.equals(Direction.BIDIRECTIONAL)) {
							executeCommand(itemName, someCommand, wemoID,
									wemoCommandType, commandAsString);
						} else {
							logger.error(
									"wrong command direction for binding [Item={}, command={}]",
									itemName, commandAsString);
						}
					} else {
						logger.error(
								"wrong command type for binding [Item={}, command={}]",
								itemName, commandAsString);
					}
				} else {
					logger.error("{} is an unrecognised command for Item {}",
							commandAsString, itemName);
				}
			}

		}

	}

	@SuppressWarnings("unchecked")
	private Type createStateForType(WemoCommandType ctype, String value)
			throws BindingConfigParseException {

		logger.debug("createStateForType was called");
		if (ctype != null && value != null) {

			Class<? extends Type> typeClass = ctype.getTypeClass();
			List<Class<? extends State>> stateTypeList = new ArrayList<Class<? extends State>>();

			stateTypeList.add((Class<? extends State>) typeClass);

			String finalValue = value;

			// Note to Kai or Thomas: wemo devices return some "true" "false"
			// values for specific variables. We convert those
			// into ON OFF if the commandTypes allow so. This is a little hack,
			// but IMHO OnOffType should
			// be enhanced, or a TrueFalseType should be developed
			if (typeClass.equals(OnOffType.class)) {
				finalValue = StringUtils.upperCase(value);
				if (finalValue.equals("TRUE")) {
					finalValue = "ON";
				} else if (finalValue.equals("FALSE")) {
					finalValue = "OFF";
				}
			}

			State state = TypeParser.parseState(stateTypeList, finalValue);

			return state;
		} else {
			return null;
		}
	}

	private String createStringFromCommand(Command command,
			String commandAsString) {

		String value = null;

		if (command instanceof StringType || command instanceof DecimalType) {
			value = commandAsString;
		} else {
			value = command.toString();
		}

		return value;

	}

	@SuppressWarnings("rawtypes")
	public void processVariableMap(RemoteDevice device,
			Map<String, StateVariableValue> values) {

		logger.debug("processVariableMap for {}", device.getDisplayString().toString());
		if (device != null && values != null) {

			// get the device linked to this service linked to this subscription
			String wemoID = getWemoIDforDevice(device);

			for (String stateVariable : values.keySet()) {

				// find all the CommandTypes that are defined for each
				// StateVariable
				List<WemoCommandType> supportedCommands = WemoCommandType
						.getCommandByVariable(stateVariable);

				StateVariableValue status = values.get(stateVariable);

				for (WemoCommandType wemoCommandType : supportedCommands) {

					// create a new State based on the type of Wemo Command and
					// the status value in the map
					Type newState = null;
					try {
						newState = createStateForType(wemoCommandType, status
								.getValue().toString());
					} catch (BindingConfigParseException e) {
						logger.error(
								"Error parsing a value {} to a state variable of type {}",
								status.toString(), wemoCommandType
										.getTypeClass().toString());
					}

					for (WemoBindingProvider provider : providers) {
						List<String> qualifiedItems = provider.getItemNames(
								wemoID, wemoCommandType);
						for (String anItem : qualifiedItems) {
							// get the openHAB commands attached to each Item at
							// this given Provider
							List<Command> commands = provider.getCommands(
									anItem, wemoCommandType);
							for (Command aCommand : commands) {
								Direction theDirection = provider.getDirection(
										anItem, aCommand);
								Direction otherDirection = wemoCommandType
										.getDirection();
								if ((theDirection == Direction.IN || theDirection == Direction.BIDIRECTIONAL)
										&& (otherDirection != Direction.OUT)) {

									if (newState != null) {
										if (newState.equals((State) aCommand)
												|| newState instanceof StringType
												|| newState instanceof DecimalType) {
											eventPublisher.postUpdate(anItem,
													(State) newState);
										}
									} else {
										throw new IllegalClassException(
												"Cannot process update for the command of type "
														+ wemoCommandType
																.toString());
									}

								}
							}
						}
					}
				}
			}
		}
	}

	protected class WemoSubscriptionCallback extends SubscriptionCallback {

		@SuppressWarnings("rawtypes")
		public WemoSubscriptionCallback(Service service, Integer interval) {
			super(service, interval);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void established(GENASubscription sub) {
			logger.debug("Aaron Established: " + sub.getSubscriptionId());
		}

		@SuppressWarnings("rawtypes")
		@Override
		protected void failed(GENASubscription subscription,
				UpnpResponse responseStatus, Exception exception,
				String defaultMsg) {
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public void eventReceived(GENASubscription sub) {

			// get the device linked to this service linked to this subscription

			Map<String, StateVariableValue> values = sub.getCurrentValues();
			// now, lets deal with the specials - some UPNP responses require
			// some XML parsing
			// or we need to update our internal data structure
			// or are things we want to store for further reference
			for (String stateVariable : values.keySet()) {

			}
		}

		@SuppressWarnings("rawtypes")
		public void eventsMissed(GENASubscription sub, int numberOfMissedEvents) {
			logger.warn("Missed events: " + numberOfMissedEvents);
		}

		@SuppressWarnings("rawtypes")
		@Override
		protected void ended(GENASubscription subscription,
				CancelReason reason, UpnpResponse responseStatus) {
			// TODO Auto-generated method stub

		}
	}

	private void executeCommand(String itemName, Command command,
			String wemoID, WemoCommandType wemoCommandType,
			String commandAsString) {

		boolean result = false;
		logger.debug("Getting Wemo ID: {} for command {} of type {} ", wemoID, command.toString(), wemoCommandType.name().toString());
		if (wemoID != null) {
			WemoDevice device = wemoDeviceCache.get(wemoID);
			if (device != null) {
				switch (wemoCommandType) {
				case SETSTATE:
					result = device.setState(createStringFromCommand(command,
							commandAsString));
					break;
				default:
					logger.debug("executeCommand: COMMAND NOT FOUND???");
					break;

				}
				;

			} else {
				logger.error(
						"UPNP device is not defined for Wemo Player with ID {}",
						wemoID);
				return;
			}
		}

		if (result) {

			// create a new State based on the type of Wemo Command and the
			// status value in the map
			Type newState = null;
			try {
				newState = createStateForType(wemoCommandType, commandAsString);
			} catch (BindingConfigParseException e) {
				logger.error(
						"Error parsing a value {} to a state variable of type {}",
						commandAsString, wemoCommandType.getTypeClass()
								.toString());
			}

			if (newState != null) {
				if (newState.equals((State) command)
						|| newState instanceof StringType
						|| newState instanceof DecimalType) {
					eventPublisher.postUpdate(itemName, (State) newState);
				} else {
					eventPublisher.postUpdate(itemName, (State) command);
				}
			} else {
				throw new IllegalClassException(
						"Cannot process update for the command of type "
								+ wemoCommandType.toString());
			}

		}

	}

	Thread pollingThread = new Thread("Wemo Polling Thread") {

		boolean shutdown = false;

		@Override
		public void run() {

			logger.debug(getName()
					+ " has been started with a polling frequency of {} ms",
					pollingPeriod);

			while (!shutdown && pollingPeriod > 0) {

				try {
					if (upnpService != null) {
						// get all the CommandTypes that require polling
						List<WemoCommandType> supportedCommands = WemoCommandType
								.getPolling();

						for (WemoCommandType wemoCommandType : supportedCommands) {
							// loop through all the device and poll for each of
							// the supportedCommands
							for (String wemoID : wemoDeviceCache.keySet()) {
								WemoDevice device = wemoDeviceCache
										.get(wemoID);

								// logger.debug("poll command '{}' from device '{}'",
								// wemoCommandType, wemoID);

								try {
									if (device != null && device.isConfigured()) {
										switch (wemoCommandType) {
										case GETSTATE:
											device.updateState();
											break;
										default:
											break;
										}
										;
									}
								} catch (Exception e) {
									logger.debug(
											"Error occured when poll command '{}' from device '{}' ",
											wemoCommandType, wemoID);
								}
							}
						}

						try {
							Thread.sleep(pollingPeriod);
						} catch (InterruptedException e) {
							logger.debug("pausing thread " + getName()
									+ " interrupted");

						}
					}
				} catch (Exception e) {

					logger.debug("Error occured during polling", e);

					try {
						Thread.sleep(100);
					} catch (InterruptedException e1) {
						logger.debug("pausing thread " + getName()
								+ " interrupted");
					}
				}
			}
		}
	};

	@SuppressWarnings("rawtypes")
	public void updated(Dictionary config) throws ConfigurationException {
		if (config != null) {
			Enumeration keys = config.keys();
			while (keys.hasMoreElements()) {

				String key = (String) keys.nextElement();

				// the config-key enumeration contains additional keys that we
				// don't want to process here ...
				if ("service.pid".equals(key)) {
					continue;
				}

				if ("pollingPeriod".equals(key)) {
					pollingPeriod = Integer.parseInt((String) config.get(key));
					logger.debug("Setting polling period to {} ms", pollingPeriod);
					continue;
				}

				Matcher matcher = EXTRACT_WEMO_CONFIG_PATTERN.matcher(key);
				if (!matcher.matches()) {
					logger.debug("given wemo-config-key '"
						+ key + "' does not follow the expected pattern '<wemoId>.<udn>'");
					continue;
				}

				matcher.reset();
				matcher.find();

				String wemoID = matcher.group(1);

				WemoDevice wemoConfig = wemoDeviceCache.get(wemoID);
				if (wemoConfig == null) {
					wemoConfig = new WemoDevice(self);
					wemoDeviceCache.put(wemoID, wemoConfig);
				}

				String configKey = matcher.group(2);
				String value = (String) config.get(key);

				if ("udn".equals(configKey)) {

					if (wemoDevicesFromCfg == null) {
						wemoDevicesFromCfg = new ArrayList<String>();
					}

					wemoDevicesFromCfg.add(value);

					wemoConfig.setUdn(new UDN(value));

					logger.debug("Add predefined Wemo device with UDN {}", wemoConfig.getUdn());

				} else {
					throw new ConfigurationException(configKey,
						"the given configKey '" + configKey + "' is unknown");
				}
			}
			start();
		}
	}

	public void start() {
		if (bindingStarted) {
			logger.trace("Tried to start Wemo polling although it is already started!");
			return;
		}

		// This will create necessary network resources for UPnP right away
		upnpService = new UpnpServiceImpl(new WemoUpnpServiceConfiguration(), listener);

		try {
			if (wemoDevicesFromCfg != null) {
				// Search predefined devices from configuration
				for (String udn : wemoDevicesFromCfg) {
					logger.debug("Querying network for predefined Wemo device with UDN '{}'", udn);

					// Query the network for this UDN
					upnpService.getControlPoint().search(new UDNHeader(new UDN(udn)));
				}
			}

			logger.debug("Querying network for Wemo devices");

			// Send a search message to all devices and services, they should
			// respond soon
			upnpService.getControlPoint().search(new STAllHeader());

			// UDADeviceType udaType = new UDADeviceType("Device");
			//upnpService.getControlPoint().search(
			// new UDADeviceTypeHeader(udaType));

			// Search only dedicated devices
			//final UDAServiceType udaType = new UDAServiceType("AVTransport");

			//upnpService.getControlPoint().search( new UDAServiceTypeHeader(udaType));
			//DeviceType deviceType = new DeviceType("Belkin", "controllee", 1);
			//upnpService.getControlPoint().search(new DeviceTypeHeader(deviceType));
			//ServiceType serviceType = new ServiceType("Belkin", "basicevent", 1);
			//upnpService.getControlPoint().search(new ServiceTypeHeader(serviceType));
		} catch (Exception e) {
			logger.warn("Error occured when searching UPNP devices", e);
		}

		// start the thread that will poll some devices
		pollingThread.setDaemon(true);
		pollingThread.start();
		
		bindingStarted = true;
		logger.debug("Wemo Binding Discovery has been started.");
	}

	protected String getWemoIDforDevice(RemoteDevice device) {
		for (String id : wemoDeviceCache.keySet()) {
			WemoDevice config = wemoDeviceCache.get(id);
			if (config.getDevice() == device) {
				return id;
			}
		}
		return null;
	}

	protected WemoDevice getPlayerForID(String name) {

		String wemoID = null;

		for (String deviceName : wemoDeviceCache.keySet()) {
			if (deviceName.equals(name)) {
				wemoID = deviceName;
				break;
			}
		}

		if (wemoID == null) {

			for (String deviceName : wemoDeviceCache.keySet()) {
				WemoDevice device = wemoDeviceCache.get(deviceName);
				if (device.getUdn().getIdentifierString().equals(name)) {
					wemoID = deviceName;
					break;
				}
			}

		}

		return wemoDeviceCache.get(wemoID);

	}

}
