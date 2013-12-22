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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.binding.wemo.WemoCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.UpnpService;
import org.teleal.cling.controlpoint.ActionCallback;
import org.teleal.cling.controlpoint.SubscriptionCallback;
import org.teleal.cling.model.action.ActionArgumentValue;
import org.teleal.cling.model.action.ActionException;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.gena.CancelReason;
import org.teleal.cling.model.gena.GENASubscription;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.Action;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.meta.StateVariable;
import org.teleal.cling.model.meta.StateVariableTypeDetails;
import org.teleal.cling.model.state.StateVariableValue;
import org.teleal.cling.model.types.InvalidValueException;
import org.teleal.cling.model.types.ServiceType;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.model.types.UDN;

/**
 * Internal data structure which carries the connection details of one Wemo
 * player (there could be several)
 * 
 * @author Karel Goderis
 * @since 1.1.0
 * 
 */
class WemoDevice {

	private static Logger logger = LoggerFactory.getLogger(WemoBinding.class);

	protected final int interval = 600;
	private boolean isConfigured = false;

	private RemoteDevice device;
	private UDN udn;
	static protected UpnpService upnpService;
	protected WemoBinding wemoBinding;

	private Map<String, StateVariableValue> stateMap = Collections
			.synchronizedMap(new HashMap<String, StateVariableValue>());

	/**
	 * @return the stateMap
	 */
	public Map<String, StateVariableValue> getStateMap() {
		return stateMap;
	}

	public boolean isConfigured() {
		return isConfigured;
	}

	WemoDevice(WemoBinding binding) {

		if (binding != null) {
			wemoBinding = binding;
		}
	}

	private void enableGENASubscriptions() {

		if (device != null && isConfigured()) {

			// Create a GENA subscription of each service for this device, if
			// supported by the device
			List<WemoCommandType> subscriptionCommands = WemoCommandType
					.getSubscriptions();
			List<String> addedSubscriptions = new ArrayList<String>();

			for (WemoCommandType c : subscriptionCommands) {
				Service service = device.findService(new UDAServiceId(c
						.getService()));
				if (service != null
						&& !addedSubscriptions.contains(c.getService())) {
					WemoPlayerSubscriptionCallback callback = new WemoPlayerSubscriptionCallback(
							service, interval);
					// logger.debug("Added a GENA Subscription for service {} on device {}",service,device);
					addedSubscriptions.add(c.getService());
					upnpService.getControlPoint().execute(callback);
				}
			}
		}
	}

	protected boolean isUpdatedValue(String valueName,
			StateVariableValue newValue) {
		if (newValue != null && valueName != null) {
			StateVariableValue oldValue = stateMap.get(valueName);

			if (newValue.getValue() == null) {
				// we will *not* store an empty value, thank you.
				return false;
			} else {
				if (oldValue == null) {
					// there was nothing stored before
					return true;
				} else {
					if (oldValue.getValue() == null) {
						// something was defined, but no value present
						return true;
					} else {
						if (newValue.getValue().equals(oldValue.getValue())) {
							return false;
						} else {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	protected void processStateVariableValue(String valueName,
			StateVariableValue newValue) {
		logger.error("AARON processStateVaraibleValue {}", newValue);
		if (newValue != null && isUpdatedValue(valueName, newValue)) {
			Map<String, StateVariableValue> mapToProcess = new HashMap<String, StateVariableValue>();
			mapToProcess.put(valueName, newValue);
			stateMap.putAll(mapToProcess);
			wemoBinding.processVariableMap(device, mapToProcess);
		}
	}

	/**
	 * @return the device
	 */
	public RemoteDevice getDevice() {
		return device;
	}

	/**
	 * @param device
	 *            the device to set
	 */
	public void setDevice(RemoteDevice device) {
		this.device = device;
	}

	public class WemoPlayerSubscriptionCallback extends SubscriptionCallback {

		public WemoPlayerSubscriptionCallback(Service service) {
			super(service);
			// TODO Auto-generated constructor stub
		}

		public WemoPlayerSubscriptionCallback(Service service,
				int requestedDurationSeconds) {
			super(service, requestedDurationSeconds);
		}

		@Override
		public void established(GENASubscription sub) {
			// logger.debug("Established: " + sub.getSubscriptionId());
		}

		@Override
		protected void failed(GENASubscription subscription,
				UpnpResponse responseStatus, Exception exception,
				String defaultMsg) {
			logger.error(defaultMsg);
		}

		public void eventReceived(GENASubscription sub) {

			// get the device linked to this service linked to this subscription

			logger.debug("Received GENA Event on {}",sub.getService());

			Map<String, StateVariableValue> values = sub.getCurrentValues();
			Map<String, StateVariableValue> mapToProcess = new HashMap<String, StateVariableValue>();
			Map<String, StateVariableValue> parsedValues = null;

			// now, lets deal with the specials - some UPNP responses require
			// some XML parsing
			// or we need to update our internal data structure
			// or are things we want to store for further reference

			if (isConfigured) {
				stateMap.putAll(mapToProcess);
				// logger.debug("to process {}",mapToProcess.toString());
				// logger.debug("statemap {}",stateMap.toString());
				wemoBinding.processVariableMap(device, mapToProcess);
			}
		}

		public void eventsMissed(GENASubscription sub, int numberOfMissedEvents) {
			logger.warn("Missed events: " + numberOfMissedEvents);
		}

		@Override
		protected void ended(GENASubscription subscription,
				CancelReason reason, UpnpResponse responseStatus) {
			// TODO Auto-generated method stub

		}
	}

	public void setService(UpnpService service) {
		if (upnpService == null) {
			upnpService = service;
			// = new UpnpServiceImpl(new WemoUpnpServiceConfiguration());
			// logger.debug("Creating a new UPNP Service handler on {}",device.getDisplayString());
		}
		if (upnpService != null) {
			isConfigured = true;
			// logger.debug("{} is fully configured",device.getDisplayString());
			enableGENASubscriptions();
		}

	}

	/**
	 * @return the udn
	 */
	public UDN getUdn() {
		return udn;
	}

	/**
	 * @param udn
	 *            the udn to set
	 */
	public void setUdn(UDN udn) {
		this.udn = udn;
	}

	@Override
	public String toString() {
		return "Wemo [udn=" + udn + ", device=" + device + "]";
	}

	protected void executeActionInvocation(ActionInvocation invocation) {
		if (invocation != null) {
			// new WemoActionCallback(invocation,
			// upnpService.getControlPoint()).run();
			new ActionCallback.Default(invocation,
					upnpService.getControlPoint()).run();

			ActionException anException = invocation.getFailure();
			if (anException != null && anException.getMessage() != null) {
				logger.warn(anException.getMessage());
			}

			Map<String, ActionArgumentValue> result = invocation.getOutputMap();
			Map<String, StateVariableValue> mapToProcess = new HashMap<String, StateVariableValue>();
			if (result != null) {

				// only process the variables that have changed value
				for (String variable : result.keySet()) {
					ActionArgumentValue newArgument = result.get(variable);

					StateVariable newVariable = new StateVariable(variable,
							new StateVariableTypeDetails(
									newArgument.getDatatype()));
					StateVariableValue newValue = new StateVariableValue(
							newVariable, newArgument.getValue());

					// StateVariableValue oldValue = stateMap.get(variable);

					if (isUpdatedValue(variable, newValue)) {
						// logger.debug("Adding to Map: {} {}",variable.toString(),newValue.getValue());
						mapToProcess.put(variable, newValue);
					}
				}

				stateMap.putAll(mapToProcess);
				wemoBinding.processVariableMap(device, mapToProcess);
			}
		}
	}
	public boolean setState(String string) {

		if (string != null && isConfigured()) {

			ServiceType serviceType = new ServiceType("Belkin", "basicevent", 1);
			Service service = device.findService(serviceType);

			Action action = service.getAction("SetBinaryState");
			ActionInvocation invocation = new ActionInvocation(action);
			logger.debug("AARON setState was called!");
			try {
				if (string.equals("ON") || string.equals("OPEN")
						|| string.equals("UP")) {
					invocation.setInput("BinaryState", "1");
				} else

				if (string.equals("OFF") || string.equals("CLOSED")
						|| string.equals("DOWN")) {
					invocation.setInput("BinaryState", "0");
				} else {
					return false;
				}
			} catch (InvalidValueException ex) {
				logger.error("Action Invalid Value Exception {}",
						ex.getMessage());
			} catch (NumberFormatException ex) {
				logger.error("Action Invalid Value Format Exception {}",
						ex.getMessage());
			}
			executeActionInvocation(invocation);

			return true;

		} else {
			return false;
		}
	}

	public boolean updateState() {
		if (isConfigured()) {
			logger.debug("AARON, updateState");

			ServiceType serviceType = new ServiceType("Belkin", "basicevent", 1);
			Service service = device.findService(serviceType);
			logger.debug("SERVICE ACTIONS: " + service.getServiceId().toString());
			Action action = service.getAction("GetBinaryState");
			logger.debug("AARON, updateState Action {}", action.toString());
			
			ActionInvocation invocation = new ActionInvocation(action);
			executeActionInvocation(invocation);

			logger.debug("AARON service is, {}", service.getServiceType().toString());
			return true;
		} else {
			return false;
		}
	}

	public boolean getState() {

		if (isConfigured()) {

			logger.debug("AARON, getState");

			updateState();
			StateVariableValue variable = stateMap.get("BinaryState");
			logger.debug("AARON, getState stateVariable {}", variable);
			if (stateMap != null) {
				variable = stateMap.get("BinaryState");
				if (variable != null) {
					return variable.getValue().equals("1") ? true : false;
				}
			}
		}

		return false;
	}
}