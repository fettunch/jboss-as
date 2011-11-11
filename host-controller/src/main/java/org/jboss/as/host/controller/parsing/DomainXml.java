/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.ControllerMessages.MESSAGES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_SUBSYSTEM_ENDPOINT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.CommonXml;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.resource.SocketBindingGroupResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly  {@code domain.xml}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainXml extends CommonXml {

    public DomainXml(final ModuleLoader loader, ExecutorService executorService) {
        super(loader, executorService);
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> nodes) throws XMLStreamException {
        if (Element.forName(reader.getLocalName()) != Element.DOMAIN) {
            throw unexpectedElement(reader);
        }
        Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
        switch (readerNS) {
            case DOMAIN_1_0:
            case DOMAIN_1_1: {
                readDomainElement(reader, new ModelNode(), readerNS, nodes);
                break;
            }
            default: {
                throw unexpectedElement(reader);
            }
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context) throws XMLStreamException {
        ModelNode modelNode = context.getModelNode();

        writer.writeStartDocument();
        writer.writeStartElement(Element.DOMAIN.getLocalName());

        writer.writeDefaultNamespace(Namespace.CURRENT.getUriString());
        writeNamespaces(writer, modelNode);
        writeSchemaLocation(writer, modelNode);

        if (modelNode.hasDefined(EXTENSION)) {
            writeExtensions(writer, modelNode.get(EXTENSION));
        }
        if(modelNode.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, modelNode.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, false);
        }
        if(modelNode.hasDefined(PATH)) {
            writePaths(writer, modelNode.get(PATH));
        }
        if(modelNode.hasDefined(PROFILE)) {
            writer.writeStartElement(Element.PROFILES.getLocalName());
            for(final Property profile : modelNode.get(PROFILE).asPropertyList()) {
                writeProfile(writer, profile.getName(), profile.getValue(), context);
            }
            writer.writeEndElement();
        }
        if(modelNode.hasDefined(INTERFACE)) {
            writeInterfaces(writer, modelNode.get(INTERFACE));
        }
        if(modelNode.hasDefined(SOCKET_BINDING_GROUP)) {
            writer.writeStartElement(Element.SOCKET_BINDING_GROUPS.getLocalName());
            for(final Property property : modelNode.get(SOCKET_BINDING_GROUP).asPropertyList()) {
                writeSocketBindingGroup(writer, property.getValue(), false);
            }
            writer.writeEndElement();
        }
        if(modelNode.hasDefined(DEPLOYMENT)) {
            writeDomainDeployments(writer, modelNode.get(DEPLOYMENT));
        }
        if(modelNode.hasDefined(SERVER_GROUP)) {
            writer.writeStartElement(Element.SERVER_GROUPS.getLocalName());
            for(final Property property : modelNode.get(SERVER_GROUP).asPropertyList()) {
                writeServerGroup(writer, property.getName(), property.getValue());
            }
            writer.writeEndElement();
        }

        writer.writeEndElement();
        writer.writeEndDocument();
    }

    void readDomainElement(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            switch (Namespace.forUri(reader.getAttributeNamespace(i))) {
                case XML_SCHEMA_INSTANCE: {
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case SCHEMA_LOCATION: {
                            parseSchemaLocations(reader, address, list, i);
                            break;
                        }
                        case NO_NAMESPACE_SCHEMA_LOCATION: {
                            // todo, jeez
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        // Content
        // Handle elements: sequence

        Element element = nextElement(reader, expectedNs);
        if (element == Element.EXTENSIONS) {
            parseExtensions(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, expectedNs, list, interfaceNames);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }

    }

//        for (;;) {
//            switch (reader.nextTag()) {
//                case START_ELEMENT: {
//                    readHeadComment(reader, address, list);
//                    if (Namespace.forUri(reader.getNamespaceURI()) != Namespace.DOMAIN_1_0) {
//                        throw unexpectedElement(reader);
//                    }
//                    switch (Element.forName(reader.getLocalName())) {
//                        default: throw unexpectedElement(reader);
//                    }
//                }
//                case END_ELEMENT: {
//                    readTailComment(reader, address, list);
//                    return;
//                }
//                default: throw new IllegalStateException();
//            }
//        }

    void parseDomainSocketBindingGroups(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs,
                                        final List<ModelNode> list, final Set<String> interfaces) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET_BINDING_GROUP: {
                    switch (expectedNs) {
                        case DOMAIN_1_0:
                            // parse 1.0 socket binding group
                            this.parseSocketBindingGroup_1_0(reader, interfaces, address, expectedNs, list);
                            break;
                        case DOMAIN_1_1:
                            // parse 1.1 socket binding group
                            this.parseSocketBindingGroup_1_1(reader, interfaces, address, expectedNs, list);
                            break;
                        default:
                            unexpectedElement(reader);

                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    void parseSocketBindingGroup_1_0(final XMLExtendedStreamReader reader, final Set<String> interfaces, final ModelNode address,
                                 final Namespace expectedNs, final List<ModelNode> updates) throws XMLStreamException {
        final Set<String> includedGroups = new HashSet<String>();
        // unique socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        // Handle attributes
        final String[] attrValues = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.DEFAULT_INTERFACE.getLocalName());
        final String socketBindingGroupName = attrValues[0];
        final String defaultInterface = attrValues[1];

        final ModelNode groupAddress = new ModelNode().set(address);
        groupAddress.add(SOCKET_BINDING_GROUP, socketBindingGroupName);

        final ModelNode bindingGroupUpdate = new ModelNode();
        bindingGroupUpdate.get(OP_ADDR).set(groupAddress);
        bindingGroupUpdate.get(OP).set(ADD);

        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, bindingGroupUpdate, reader.getLocation());

        final ModelNode includes = bindingGroupUpdate.get(INCLUDES);
        includes.setEmptyList();
        updates.add(bindingGroupUpdate);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INCLUDE: {
                    final String includedGroup = readStringAttributeElement(reader, Attribute.SOCKET_BINDING_GROUP.getLocalName());
                    if (!includedGroups.add(includedGroup)) {
                        throw MESSAGES.alreadyDeclared(Attribute.SOCKET_BINDING_GROUP.getLocalName(), includedGroup, reader.getLocation());
                    }
                    SocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(includedGroup, bindingGroupUpdate, reader.getLocation());
                    break;
                }
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw MESSAGES.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), bindingName,
                                Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    void parseSocketBindingGroup_1_1(final XMLExtendedStreamReader reader, final Set<String> interfaces, final ModelNode address,
                                 final Namespace expectedNs, final List<ModelNode> updates) throws XMLStreamException {
        final Set<String> includedGroups = new HashSet<String>();
        // both outbound-socket-bindings and socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        // Handle attributes
        final String[] attrValues = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.DEFAULT_INTERFACE.getLocalName());
        final String socketBindingGroupName = attrValues[0];
        final String defaultInterface = attrValues[1];

        final ModelNode groupAddress = new ModelNode().set(address);
        groupAddress.add(SOCKET_BINDING_GROUP, socketBindingGroupName);

        final ModelNode bindingGroupUpdate = new ModelNode();
        bindingGroupUpdate.get(OP_ADDR).set(groupAddress);
        bindingGroupUpdate.get(OP).set(ADD);

        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, bindingGroupUpdate, reader.getLocation());

        final ModelNode includes = bindingGroupUpdate.get(INCLUDES);
        includes.setEmptyList();
        updates.add(bindingGroupUpdate);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INCLUDE: {
                    final String includedGroup = readStringAttributeElement(reader, Attribute.SOCKET_BINDING_GROUP.getLocalName());
                    if (!includedGroups.add(includedGroup)) {
                        throw MESSAGES.alreadyDeclared(Attribute.SOCKET_BINDING_GROUP.getLocalName(), includedGroup, reader.getLocation());
                    }
                    SocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(includedGroup, bindingGroupUpdate, reader.getLocation());
                    break;
                }
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw MESSAGES.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(),
                                bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                case OUTBOUND_SOCKET_BINDING: {
                    final String bindingName = parseOutboundSocketBinding(reader, interfaces, socketBindingGroupName, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw MESSAGES.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(),
                                bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    void parseServerGroups(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element serverGroup = Element.forName(reader.getLocalName());
            if (Element.SERVER_GROUP != serverGroup) {
                throw unexpectedElement(reader);
            }

            String name = null;
            String profile = null;
            Boolean managementSubsystemEndpoint = null;

            // Handle attributes
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {

                final String value = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                } else {
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    switch (attribute) {
                        case NAME: {
                            if (name != null) {
                                throw ParseUtils.duplicateAttribute(reader, attribute.getLocalName());
                            }
                            if (!names.add(value)) {
                                throw ParseUtils.duplicateNamedElement(reader, value);
                            }
                            name = value;
                            break;
                        }
                        case PROFILE: {
                            if (profile != null) {
                                throw ParseUtils.duplicateAttribute(reader, attribute.getLocalName());
                            }
                            profile = value;
                            break;
                        } case MANAGEMENT_SUBSYSTEM_ENDPOINT: {
                            if (managementSubsystemEndpoint != null) {
                                throw ParseUtils.duplicateAttribute(reader, attribute.getLocalName());
                            }
                            managementSubsystemEndpoint = Boolean.valueOf(value);
                            break;
                        }
                        default:
                            throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            if (name == null) {
                throw missingRequired(reader, Collections.singleton(Attribute.NAME));
            }
            if (profile == null) {
                throw missingRequired(reader, Collections.singleton(Attribute.PROFILE));
            }

            final ModelNode groupAddress = new ModelNode().set(address);
            groupAddress.add(ModelDescriptionConstants.SERVER_GROUP, name);

            final ModelNode group = new ModelNode();
            group.get(OP).set(ADD);
            group.get(OP_ADDR).set(groupAddress);
            group.get(PROFILE).set(profile);
            if (managementSubsystemEndpoint != null) {
                group.get(MANAGEMENT_SUBSYSTEM_ENDPOINT).set(managementSubsystemEndpoint);
            }
            list.add(group);

            // Handle elements

            boolean sawDeployments = false;
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, expectedNs);
                final Element element = Element.forName(reader.getLocalName());
                switch (element) {
                    case JVM: {
                        parseJvm(reader, groupAddress, expectedNs, list, new HashSet<String>());
                        break;
                    }
                    case SOCKET_BINDING_GROUP: {
                        parseSocketBindingGroupRef(reader, groupAddress, list);
                        break;
                    }
                    case DEPLOYMENTS: {
                        if (sawDeployments) {
                            throw MESSAGES.alreadyDefined(element.getLocalName(), reader.getLocation());
                        }
                        sawDeployments = true;
                        parseDeployments(reader, groupAddress, expectedNs, list, true);
                        break;
                    }
                    case SYSTEM_PROPERTIES: {
                        parseSystemProperties(reader, groupAddress, expectedNs, list, false);
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedElement(reader);
                }
            }

        }
    }

    void parseProfiles(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element element = Element.forName(reader.getLocalName());
            if (Element.PROFILE != element) {
                throw unexpectedElement(reader);
            }

            // Attributes
            requireSingleAttribute(reader, Attribute.NAME.getLocalName());
            final String name = reader.getAttributeValue(0);
            if (! names.add(name)) {
                throw MESSAGES.duplicateDeclaration("profile", name, reader.getLocation());
            }

            final List<ModelNode> subsystems = new ArrayList<ModelNode>();
            final Set<String> includes = new HashSet<String>();
            final ModelNode profileIncludes = new ModelNode();

            // Content
            // Sequence
            final Set<String> configuredSubsystemTypes = new HashSet<String>();
            while (reader.nextTag() != END_ELEMENT) {
                switch (Namespace.forUri(reader.getNamespaceURI())) {
                    case UNKNOWN: {
                        if (Element.forName(reader.getLocalName()) != Element.SUBSYSTEM) {
                            throw unexpectedElement(reader);
                        }
                        if (!configuredSubsystemTypes.add(reader.getNamespaceURI())) {
                            throw MESSAGES.duplicateDeclaration("subsystem", name, reader.getLocation());
                        }
                        // parse content
                        reader.handleAny(subsystems);

                        break;
                    }
                    case DOMAIN_1_0:
                    case DOMAIN_1_1: {
                        requireNamespace(reader, expectedNs);
                        // include should come first
                        if (configuredSubsystemTypes.size() > 0) {
                            throw unexpectedElement(reader);
                        }
                        if (Element.forName(reader.getLocalName()) != Element.INCLUDE) {
                            throw unexpectedElement(reader);
                        }
                        final String includedName = readStringAttributeElement(reader, Attribute.PROFILE.getLocalName());
                        if (! names.contains(includedName)) {
                            throw MESSAGES.profileNotFound(reader.getLocation());
                        }
                        if (! includes.add(includedName)) {
                            throw MESSAGES.duplicateProfile(reader.getLocation());
                        }
                        profileIncludes.add(includedName);
                        break;
                    }
                    default: {
                        throw unexpectedElement(reader);
                    }
                }
            }
            final ModelNode profile = new ModelNode();
            profile.get(OP).set(ADD);
            profile.get(OP_ADDR).set(address).add(ModelDescriptionConstants.PROFILE, name);
            profile.get(INCLUDES).set(profileIncludes);
            list.add(profile);

            // Process subsystems
            for(final ModelNode update : subsystems) {
                // Process relative subsystem path address
                final ModelNode subsystemAddress = address.clone().set(address).add(ModelDescriptionConstants.PROFILE, name);
                for(final Property path : update.get(OP_ADDR).asPropertyList()) {
                    subsystemAddress.add(path.getName(), path.getValue().asString());
                }
                update.get(OP_ADDR).set(subsystemAddress);
                list.add(update);
            }

            if (configuredSubsystemTypes.size() == 0) {
                throw MESSAGES.profileHasNoSubsystems(reader.getLocation());
            }
        }
    }

    void parseSubsystemManagementEndpoint(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);
        requireNoContent(reader);

    }
    private void writeProfile(final XMLExtendedStreamWriter writer, final String profileName, final ModelNode profileNode, final ModelMarshallingContext context) throws XMLStreamException {

        writer.writeStartElement(Element.PROFILE.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), profileName);

        if(profileNode.hasDefined(INCLUDES)) {
            for(final ModelNode include : profileNode.get(INCLUDES).asList()) {
                writer.writeEmptyElement(INCLUDE);
                writer.writeAttribute(PROFILE, include.asString());
            }
        }
        if(profileNode.hasDefined(SUBSYSTEM)) {
            final Set<String> subsystemNames = profileNode.get(SUBSYSTEM).keys();
            if (subsystemNames.size() > 0) {
                String defaultNamespace = writer.getNamespaceContext().getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX);
                for (String subsystemName : subsystemNames) {
                    try {
                        ModelNode subsystem = profileNode.get(SUBSYSTEM, subsystemName);
                        XMLElementWriter<SubsystemMarshallingContext> subsystemWriter = context.getSubsystemWriter(subsystemName);
                        if (subsystemWriter != null) { // FIXME -- remove when extensions are doing the registration
                            subsystemWriter.writeContent(writer, new SubsystemMarshallingContext(subsystem, writer));
                        }
                    }
                    finally {
                        writer.setDefaultNamespace(defaultNamespace);
                    }
                }
            }
        }
        writer.writeEndElement();
    }

    private void writeDomainDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        final Set<String> deploymentNames = modelNode.keys();
        if (deploymentNames.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
            for (String uniqueName : deploymentNames) {
                final ModelNode deployment = modelNode.get(uniqueName);
                final String runtimeName = deployment.get(RUNTIME_NAME).asString();
                writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                writeAttribute(writer, Attribute.NAME, uniqueName);
                writeAttribute(writer, Attribute.RUNTIME_NAME, runtimeName);
                final List<ModelNode> contentItems = deployment.require(CONTENT).asList();
                for (ModelNode contentItem : contentItems) {
                    writeContentItem(writer, contentItem);
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void writeServerGroupDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        final Set<String> deploymentNames = modelNode.keys();
        if (deploymentNames.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
            for (String uniqueName : deploymentNames) {
                final ModelNode deployment = modelNode.get(uniqueName);
                final String runtimeName = deployment.get(RUNTIME_NAME).asString();
                final boolean enabled = !deployment.hasDefined(ENABLED) || deployment.get(ENABLED).asBoolean();
                writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                writeAttribute(writer, Attribute.NAME, uniqueName);
                writeAttribute(writer, Attribute.RUNTIME_NAME, runtimeName);
                if (!enabled) {
                    writeAttribute(writer, Attribute.ENABLED, Boolean.FALSE.toString());
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void writeServerGroup(final XMLExtendedStreamWriter writer, final String groupName, final ModelNode group) throws XMLStreamException {
        writer.writeStartElement(Element.SERVER_GROUP.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), groupName);
        writer.writeAttribute(Attribute.PROFILE.getLocalName(), group.get(PROFILE).asString());

        // JVM
        if(group.hasDefined(JVM)) {
            for(final Property jvm : group.get(JVM).asPropertyList()) {
                writeJVMElement(writer, jvm.getName(), jvm.getValue());
                break; // TODO just write the first !?
            }
        }

        // Socket binding ref
        String bindingGroupRef = group.hasDefined(SOCKET_BINDING_GROUP) ? group.get(SOCKET_BINDING_GROUP).asString() : null;
        String portOffset = group.hasDefined(SOCKET_BINDING_PORT_OFFSET) ? group.get(SOCKET_BINDING_PORT_OFFSET).asString() : null;
        Boolean managementSubsystemEndpoint = group.hasDefined(MANAGEMENT_SUBSYSTEM_ENDPOINT) ? group.get(MANAGEMENT_SUBSYSTEM_ENDPOINT).asBoolean() : null;
        if (bindingGroupRef != null || portOffset != null) {
            writer.writeStartElement(Element.SOCKET_BINDING_GROUP.getLocalName());
            if (bindingGroupRef != null) {
                writeAttribute(writer, Attribute.REF, bindingGroupRef);
            }
            if (portOffset != null) {
                writeAttribute(writer, Attribute.PORT_OFFSET, portOffset);
            }
            if (managementSubsystemEndpoint != null) {
                writeAttribute(writer, Attribute.MANAGEMENT_SUBSYSTEM_ENDPOINT, managementSubsystemEndpoint.toString());
            }
            writer.writeEndElement();
        }

        if(group.hasDefined(DEPLOYMENT)) {
            writeServerGroupDeployments(writer, group.get(DEPLOYMENT));
        }
        // System properties
        if(group.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, group.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, false);
        }

        writer.writeEndElement();
    }
}