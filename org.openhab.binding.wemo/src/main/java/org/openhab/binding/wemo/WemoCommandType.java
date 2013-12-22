/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wemo;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.wemo.internal.Direction;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Type;
import org.openhab.model.item.binding.BindingConfigParseException;

/**
 * Represents all valid commands which could be processed by this binding
 * 
 * @author Karel Goderis
 * @since 1.1.0
 */

public enum WemoCommandType {


	SETSTATE {
		{
			command = "SetState";
			service = "basicevent";
			action = "SetBinaryState";
			variable = "BinaryState";
			typeClass = OnOffType.class;
			direction = Direction.OUT;
		}
	},
	
	GETSTATE {
		{
			command = "GetState";
			service = "basicevent";
			action = "GetBinaryState";
			variable = "BinaryState";
			typeClass = OnOffType.class;
			direction = Direction.IN;
			polling = true;
		}
	}

	;		


	/** Represents the Wemo command as it will be used in *.items configuration */
	// openhab command associated with this upnp cpmmand, e.g. used in items
	String command;
	// name of upnp service template 
	String service;
	// name of upnp action/command, must be defined in service template. put null for complex commands that go beyond simple assynchronous execution
	// WARNING: NOT USED ANYMORE BUT I KEPT IT IN HERE TO MAKE THE CODE / UPNP MORE READABLE
	String action;
	// if action == null, then variable indicates the name of the GENA variable we need to process/use
	String variable;
	// type of the item supported by this command
	Class<? extends Type> typeClass;
	// direction of the openhab command, eg IN, OUT or BIDIRECTIONAL. that we will accept in conjunction with this command
	Direction direction;
	// true if a variable need to be polled pro-actively, e.g. values are not returned as part of a GENA subscription
	boolean polling = false;
	
	
	
	public String getWemoCommand() {
		return command;
	}

	public String getService() {
		return service;
	}

	public String getAction() {
		return action;
	}

	public String getVariable() {
		return variable;
	}
	
	public Direction getDirection() {
		return direction;
	}
	
	/**
	 * @return the polling
	 */
	public boolean isPolling() {
		return polling;
	}

	public Class<? extends Type> getTypeClass() {
		return typeClass;
	}

	/**
	 * 
	 * @param WemoCommand command string e.g. message, volume, channel
	 * @param action class to validate
	 * @return true if item class can bound to WemoCommand
	 */

	public static boolean validateBinding(WemoCommandType type, Item item) {
		if(type !=null && item != null && item.getAcceptedDataTypes().contains(type.getTypeClass())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @param wemoCommand command string e.g. message, volume, channel
	 * @return simple name of all valid item classes
	 */
	
	public static String getValidItemTypes(String wemoCommand) {
		String ret = "";
		for (WemoCommandType c : WemoCommandType.values()) {
			if (wemoCommand.equals(c.getWemoCommand()) && c.getWemoCommand() != null) {
				if (StringUtils.isEmpty(ret)) {
					ret = c.getTypeClass().getSimpleName();
				} else {
					if (!ret.contains(c.getTypeClass().getSimpleName())) {
						ret = ret + ", " + c.getTypeClass().getSimpleName();
					}
				}
			}
		}
		return ret;
	}


	public static List<WemoCommandType> getSubscriptions(){
		List<WemoCommandType> result = new ArrayList<WemoCommandType>();
		for(WemoCommandType c: WemoCommandType.values()){
			if(c.getVariable() != null && c.getWemoCommand() != null && c.isPolling() == false){
				result.add(c);
			}
		}
		return result;
	}
	
	public static List<WemoCommandType> getPolling(){
		List<WemoCommandType> result = new ArrayList<WemoCommandType>();
		for(WemoCommandType c: WemoCommandType.values()){
			if(c.isPolling()) {
//			if(c.getVariable() != null && c.getWemoCommand() != null && c.isPolling()){
				result.add(c);
			}
		}
		return result;
	}
	
	public static WemoCommandType getCommandType(String wemoCommand, Direction direction) throws WemoIllegalCommandTypeException {

		if ("".equals(wemoCommand)) {
			return null;
		}

		for (WemoCommandType c : WemoCommandType.values()) {

			if (wemoCommand.equals(c.getWemoCommand()) && c.getDirection().equals(direction)) {
				return c;
			}
		}

		throw new WemoIllegalCommandTypeException("Cannot find wemoCommandType for '"
				+ wemoCommand + "' with direction '"+direction.toString()+"'");
	}

	public static List<WemoCommandType> getCommandByVariable(
			String stateVariable) {
		List<WemoCommandType> result = new ArrayList<WemoCommandType>();
		for(WemoCommandType c: WemoCommandType.values()){
			if(c.getVariable() != null && c.getVariable().equals(stateVariable)){
				result.add(c);
			}
		}
		return result;
	}
	
}