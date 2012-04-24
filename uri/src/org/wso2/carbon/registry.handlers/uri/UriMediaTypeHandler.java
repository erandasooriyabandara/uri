/*
 * Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.registry.handlers.uri;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.jaxen.SimpleNamespaceContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.jdbc.handlers.Handler;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;
import org.wso2.carbon.registry.handlers.uri.utils.UriConstants;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.List;

public class UriMediaTypeHandler extends Handler{

    @Override
    public void put(RequestContext requestContext) throws RegistryException{
        Registry registry = requestContext.getRegistry();
        Resource resource = requestContext.getResource();
        String resourceId = resource.getId();
        String fileUri = null;
        String type = null;

        byte[] content =  ((String)resource.getContent()).getBytes();
        ByteArrayInputStream in = new ByteArrayInputStream(content);
        OMElement docElement;
        try {
            StAXOMBuilder builder = new StAXOMBuilder(in);
            docElement = builder.getDocumentElement();
            SimpleNamespaceContext simpleNamespaceContext = new SimpleNamespaceContext();
            simpleNamespaceContext.addNamespace("pre",docElement.getNamespace().getNamespaceURI());
            AXIOMXPath expression = new AXIOMXPath("//pre:overview");
            expression.setNamespaceContext(simpleNamespaceContext);
            List<OMElement> overview = expression.selectNodes(docElement);
            Iterator<OMElement> elements = overview.get(0).getChildElements();

            while (elements.hasNext()){
                OMElement element = elements.next();
                String localName = element.getLocalName();
                if("uri".equals(localName)){
                    fileUri = element.getText();
                } else if ("type".equals(localName)){
                    type = element.getText();
                }
            }


        } catch (Exception e) {
            throw new RegistryException(
                    "Failed to parse content of URI ");
        }

        if(type.equals(UriConstants.WSDL)){
            WsdlUriHandler wsdlUriHandler = new WsdlUriHandler();
            wsdlUriHandler.importResource(requestContext, fileUri);
        } else if(type.equals(UriConstants.XSD)){
            SchemaUriHandler schemaUriHandler = new SchemaUriHandler();
            schemaUriHandler.importResource(requestContext, fileUri);
        } else if(type.equals(UriConstants.POLICY)){
            PolicyUriHandler policyUriHandler = new PolicyUriHandler();
            policyUriHandler.importResource(requestContext, fileUri);
        }

    }
}
