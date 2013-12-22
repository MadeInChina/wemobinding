/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wemo;

import java.util.List;

import org.openhab.binding.wemo.internal.Direction;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.types.Command;


/**
 * @author Karel Goderis
 * @since 1.1.0
 *
 */
public interface WemoBindingProvider extends BindingProvider {

	/**
	 * Returns a <code>List</code> of matching Wemo ids (associated to <code>itemName</code>
	 * 
	 * @param itemName
	 *            the item for which to find a Wemo id
	 * 
	 * @return a List of matching Wemo ids or <code>null</code> if no matching Wemo id
	 *         could be found.
	 */
	public List<String> getWemoID(String itemName);
	
	/**
	 * Returns the matching Wemo id (associated to <code>itemName</code> and aCommand)
	 * 
	 * @param itemName
	 *            the item for which to find a Wemo id
	 * 
	 * @return a List of matching Wemo ids or <code>null</code> if no matching Wemo id
	 *         could be found.
	 */
	public String getWemoID(String itemName, Command aCommand);

	/**
	 * Returns the matching Wemo command (associated to <code>itemName</code> and aCommand)
	 * 
	 * @param itemName
	 *            the item for which to find a Wemo id
	 * 
	 * @return a List of matching Wemo ids or <code>null</code> if no matching Wemo id
	 *         could be found.
	 */
	public WemoCommandType getWemoCommandType(String itemName, Command aCommand, Direction direction);
	
	/**
	 * Returns the matching direction  (associated to <code>itemName</code> and aCommand)
	 * 
	 * @param itemName
	 *            the item for which to find a Wemo id
	 * 
	 * @return a List of matching Wemo ids or <code>null</code> if no matching Wemo id
	 *         could be found.
	 */
	public Direction getDirection(String itemName, Command aCommand);	

	/**
	 * Returns the list of items  (associated to <code>wemoID</code> and a Wemo Command Type)
	 * 
	 */
	public List<String> getItemNames(String wemoID, WemoCommandType wemoCommandType);
	
	/**
	 * Returns the list of Commands  (associated to an <code>Item</code> and a Wemo Command Type)
	 * 
	 */
	public List<Command> getCommands(String anItem,WemoCommandType wemoCommandType);

	/**
	 * Returns the list of Commands that are linked to variables/updates (associated to an <code>Item</code>)
	 * 
	 */
	public List<Command> getVariableCommands(String itemName);
}
