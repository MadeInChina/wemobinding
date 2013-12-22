/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.openhab.binding.wemo.internal;


import org.teleal.cling.binding.staging.MutableAction;
import org.teleal.cling.binding.staging.MutableActionArgument;
import org.teleal.cling.binding.staging.MutableAllowedValueRange;
import org.teleal.cling.binding.staging.MutableService;
import org.teleal.cling.binding.staging.MutableStateVariable;
import org.teleal.cling.binding.xml.DescriptorBindingException;
import org.teleal.cling.binding.xml.ServiceDescriptorBinder;
import org.teleal.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.meta.ActionArgument;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.meta.StateVariableEventDetails;
import org.teleal.cling.model.types.CustomDatatype;
import org.teleal.cling.model.types.Datatype;
//import org.seamless.xml.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.teleal.cling.binding.xml.Descriptor.Service.ATTRIBUTE;
import static org.teleal.cling.binding.xml.Descriptor.Service.ELEMENT;
import org.teleal.cling.binding.annotations.*;
import org.teleal.common.xml.SAXParser;

/**
 * Implementation based on JAXP SAX.
 *
 * @author Christian Bauer
 */

public class myUDA10ServiceDescriptorBinderSAXImpl extends UDA10ServiceDescriptorBinderImpl {

    private static Logger log = Logger.getLogger(ServiceDescriptorBinder.class.getName());

    @Override
    public <S extends Service> S describe(S undescribedService, String descriptorXml) throws DescriptorBindingException, ValidationException {

        if (descriptorXml == null || descriptorXml.length() == 0 || (!undescribedService.getDevice().getDetails().getManufacturerDetails().getManufacturer().toString().toUpperCase().contains("BELKIN"))) {
            throw new DescriptorBindingException("Null or empty descriptor");
        }

        try {
        	
        	/*
        	 * The Belkin UPNP stack is not 100% compatible with the cling library
        	 * I had to cobble together this service descriptor based on the one that came with cling.
        	 * Then I had to repair all of the problems with the xml files provided by the Wemo switch 
        	 */
            log.fine("Reading service from XML descriptor");

            descriptorXml = descriptorXml.trim().replaceFirst("^([\\W]+)<","<");
            char myChar = descriptorXml.charAt(0);
            for (int i = 0; i < descriptorXml.length(); i++){
            	myChar = descriptorXml.charAt(i);
                if ((int)myChar == 65279){
                	log.warning("AARON, we found a BOM");
                }
            }
            
            String newXml;
            Matcher junkMatcher = (Pattern.compile("^([\\W]+)<"))
            		.matcher( descriptorXml.trim() );
            
            newXml = junkMatcher.replaceFirst("<");
            newXml = newXml.replaceAll("\0", " ");
            newXml = descriptorXml.replaceAll("<retval/>", " ");
            newXml = newXml.replaceAll("<retval />", " "); /*	The SAX parser is seeing this as a null retval, also 
            												*	in some cases multiple return values where only 1 is allowed
            												*/
            
            newXml = newXml.replaceAll("\"smartprivateKey\"", "smartprivateKey"); 	// can't contain quotes
            newXml = newXml.replaceAll("\"pluginprivateKey\"", "pluginprivateKey"); // can't contain quotes
            
            log.fine("AARON: service, " + newXml);
            
            SAXParser parser = new SAXParser();

            MutableService descriptor = new MutableService();

            hydrateBasic(descriptor, undescribedService);

            new RootHandler(descriptor, parser);

            parser.parse(
                    new InputSource(
                            // TODO: UPNP VIOLATION: Virgin Media Superhub sends trailing spaces/newlines after last XML element, need to trim()
                            new StringReader(newXml.trim())
                    )
            );

            // Build the immutable descriptor graph
            return (S)descriptor.build(undescribedService.getDevice());

        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DescriptorBindingException("Could not parse service descriptor: " + ex.toString(), ex);
        }
    }

    protected static class RootHandler extends ServiceDescriptorHandler<MutableService> {

        public RootHandler(MutableService instance, SAXParser parser) {
            super(instance, parser);
        }

        @Override
        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {

            /*
            if (element.equals(SpecVersionHandler.EL)) {
                MutableUDAVersion udaVersion = new MutableUDAVersion();
                getInstance().udaVersion = udaVersion;
                new SpecVersionHandler(udaVersion, this);
            }
            */

            if (element.equals(ActionListHandler.EL)) {
                List<MutableAction> actions = new ArrayList();
                getInstance().actions = actions;
                new ActionListHandler(actions, this);
            }

            if (element.equals(StateVariableListHandler.EL)) {
                List<MutableStateVariable> stateVariables = new ArrayList();
                getInstance().stateVariables = stateVariables;
                new StateVariableListHandler(stateVariables, this);

                
                Datatype.Builtin builtin = Datatype.Builtin.getByDescriptorName("string");
                
                /*
                 * The Wemo xml files have actions/arguments with relatedStateVariables
                 * The following stateVariables do not actually exist in the xml returned by Wemo
                 * They could not be mapped using the cling library.
                 * The Wemo devices and services could not be hydrated
                 * Added stateVariables manually. I should create a function for this
                 * or map them to something usefull.
                 */
                
                MutableStateVariable stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "ssid";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);
            	
            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "DstSupported";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "auth";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "password";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "encrypt";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "channel";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "PictureSize";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "PictureWidth";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "PictureColorDeep";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "LOGURL";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "SmartDevURL";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "MacAddr";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);


            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Mac";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Serial";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Udn";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "RestoreState";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "PluginKey";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Level";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Option";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "NewFirmwareVersion";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "ReleaseDate";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "URL";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Signature";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "DownloadStartTime";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "action";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "Tues";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "DeviceId";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "dst";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "HomeId";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);

            	stateVariable = new MutableStateVariable();
            	stateVariable.eventDetails = new StateVariableEventDetails(false);
            	stateVariable.name = "DeviceName";
                stateVariable.dataType = builtin.getDatatype();
            	stateVariables.add(stateVariable);
        }
    }
    }

    protected static class ActionListHandler extends ServiceDescriptorHandler<List<MutableAction>> {

        public static final ELEMENT EL = ELEMENT.actionList;

        public ActionListHandler(List<MutableAction> instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {
            if (element.equals(ActionHandler.EL)) {
                MutableAction action = new MutableAction();
                getInstance().add(action);
                new ActionHandler(action, this);
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class ActionHandler extends ServiceDescriptorHandler<MutableAction> {

        public static final ELEMENT EL = ELEMENT.action;

        public ActionHandler(MutableAction instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {
            if (element.equals(ActionArgumentListHandler.EL)) {
                List<MutableActionArgument> arguments = new ArrayList();
                getInstance().arguments = arguments;
                new ActionArgumentListHandler(arguments, this);
            }
        }

        @Override
        public void endElement(ELEMENT element) throws SAXException {
            switch (element) {
                case name:
                    getInstance().name = getCharacters();
                    break;
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class ActionArgumentListHandler extends ServiceDescriptorHandler<List<MutableActionArgument>> {

        public static final ELEMENT EL = ELEMENT.argumentList;

        public ActionArgumentListHandler(List<MutableActionArgument> instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {
            if (element.equals(ActionArgumentHandler.EL)) {
                MutableActionArgument argument = new MutableActionArgument();
                getInstance().add(argument);
                new ActionArgumentHandler(argument, this);
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class ActionArgumentHandler extends ServiceDescriptorHandler<MutableActionArgument> {

        public static final ELEMENT EL = ELEMENT.argument;

        public ActionArgumentHandler(MutableActionArgument instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void endElement(ELEMENT element) throws SAXException {
            switch (element) {
                case name:
                    getInstance().name = getCharacters();
                    break;
                case direction:
                    getInstance().direction = ActionArgument.Direction.valueOf(getCharacters().toUpperCase(Locale.ENGLISH));
                    break;
                case relatedStateVariable:
                    getInstance().relatedStateVariable = getCharacters();
                    break;
                case retval:
                    getInstance().retval = getInstance().direction.toString().contains("OUT") ? true : false;
                    break;
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class StateVariableListHandler extends ServiceDescriptorHandler<List<MutableStateVariable>> {

        public static final ELEMENT EL = ELEMENT.serviceStateTable;

        public StateVariableListHandler(List<MutableStateVariable> instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {
            if (element.equals(StateVariableHandler.EL)) {
                MutableStateVariable stateVariable = new MutableStateVariable();

                String sendEventsAttributeValue = attributes.getValue(ATTRIBUTE.sendEvents.toString());
                stateVariable.eventDetails = new StateVariableEventDetails(
                        sendEventsAttributeValue != null && sendEventsAttributeValue.toUpperCase(Locale.ENGLISH).equals("YES")
                );

                getInstance().add(stateVariable);
                new StateVariableHandler(stateVariable, this);
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class StateVariableHandler extends ServiceDescriptorHandler<MutableStateVariable> {

        public static final ELEMENT EL = ELEMENT.stateVariable;

        public StateVariableHandler(MutableStateVariable instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {
            if (element.equals(AllowedValueListHandler.EL)) {
                List<String> allowedValues = new ArrayList();
                getInstance().allowedValues = allowedValues;
                new AllowedValueListHandler(allowedValues, this);
            }

            if (element.equals(AllowedValueRangeHandler.EL)) {
                MutableAllowedValueRange allowedValueRange = new MutableAllowedValueRange();
                getInstance().allowedValueRange = allowedValueRange;
                new AllowedValueRangeHandler(allowedValueRange, this);
            }
        }

        @Override
        public void endElement(ELEMENT element) throws SAXException {
            switch (element) {
                case name:
                    getInstance().name = getCharacters();
                    break;
                case dataType:
                    String dtName = getCharacters();
                    Datatype.Builtin builtin = Datatype.Builtin.getByDescriptorName(dtName);
                    getInstance().dataType = builtin != null ? builtin.getDatatype() : new CustomDatatype(dtName);
                    break;
                case defaultValue:
                    getInstance().defaultValue = getCharacters();
                    break;
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class AllowedValueListHandler extends ServiceDescriptorHandler<List<String>> {

        public static final ELEMENT EL = ELEMENT.allowedValueList;

        public AllowedValueListHandler(List<String> instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void endElement(ELEMENT element) throws SAXException {
            switch (element) {
                case allowedValue:
                    getInstance().add(getCharacters());
                    break;
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class AllowedValueRangeHandler extends ServiceDescriptorHandler<MutableAllowedValueRange> {

        public static final ELEMENT EL = ELEMENT.allowedValueRange;

        public AllowedValueRangeHandler(MutableAllowedValueRange instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        @Override
        public void endElement(ELEMENT element) throws SAXException {
            try {
                switch (element) {
                    case minimum:
                        getInstance().minimum = Long.valueOf(getCharacters());
                        break;
                    case maximum:
                        getInstance().maximum = Long.valueOf(getCharacters());
                        break;
                    case step:
                        getInstance().step = Long.valueOf(getCharacters());
                        break;
                }
            } catch (Exception ex) {
                // Ignore
            }
        }

        @Override
        public boolean isLastElement(ELEMENT element) {
            return element.equals(EL);
        }
    }

    protected static class ServiceDescriptorHandler<I> extends SAXParser.Handler<I> {

        public ServiceDescriptorHandler(I instance) {
            super(instance);
        }

        public ServiceDescriptorHandler(I instance, SAXParser parser) {
            super(instance, parser);
        }

        public ServiceDescriptorHandler(I instance, ServiceDescriptorHandler parent) {
            super(instance, parent);
        }

        public ServiceDescriptorHandler(I instance, SAXParser parser, ServiceDescriptorHandler parent) {
            super(instance, parser, parent);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            ELEMENT el = ELEMENT.valueOrNullOf(localName);
            if (el == null) return;
            startElement(el, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            ELEMENT el = ELEMENT.valueOrNullOf(localName);
            if (el == null) return;
            endElement(el);
        }

        @Override
        protected boolean isLastElement(String uri, String localName, String qName) {
            ELEMENT el = ELEMENT.valueOrNullOf(localName);
            return el != null && isLastElement(el);
        }

        public void startElement(ELEMENT element, Attributes attributes) throws SAXException {

        }

        public void endElement(ELEMENT element) throws SAXException {

        }

        public boolean isLastElement(ELEMENT element) {
            return false;
        }
    }

}
