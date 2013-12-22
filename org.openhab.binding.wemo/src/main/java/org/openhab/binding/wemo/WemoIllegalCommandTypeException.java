/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wemo;

public class WemoIllegalCommandTypeException extends Exception{

	private static final long serialVersionUID = 1107925527851348965L;

	public WemoIllegalCommandTypeException(String msg) {
            super(msg);
    }

    public WemoIllegalCommandTypeException(Throwable cause) {
            super(cause);
    }

    public WemoIllegalCommandTypeException(String msg, Throwable cause) {
            super(msg, cause);
    }
	
}
