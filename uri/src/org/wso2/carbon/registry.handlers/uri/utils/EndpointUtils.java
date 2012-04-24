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
package org.wso2.carbon.registry.handlers.uri.utils;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.registry.core.Association;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.extensions.utils.CommonConstants;
import org.wso2.carbon.registry.extensions.utils.CommonUtil;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.StringReader;
import java.util.*;

public class EndpointUtils {
    private static final Log log = LogFactory.getLog(EndpointUtils.class);

    private static final String SOAP11_ENDPOINT_EXPR = "/wsdl:definitions/wsdl:service/wsdl:port/soap:address";
    private static final String SOAP12_ENDPOINT_EXPR = "/wsdl:definitions/wsdl:service/wsdl:port/soap12:address";
    private static final String HTTP_ENDPOINT_EXPR = "/wsdl:definitions/wsdl:service/wsdl:port/http:address";
    private static final String LOCATION_ATTR = "location";

    private static final String ENDPOINT_DEFAULT_LOCATION = "/uris/endpoints/";
    private static String endpointLocation = ENDPOINT_DEFAULT_LOCATION;
    private static String endpointMediaType = CommonConstants.ENDPOINT_MEDIA_TYPE;

    public static void setEndpointLocation(String location) {
        endpointLocation = location;
    }

    public static String getEndpointLocation() {
        return endpointLocation;
    }

    public static void setEndpointMediaType(String mediaType) {
        endpointMediaType = mediaType;
    }

    public static String getEndpointMediaType() {
        return endpointMediaType;
    }

    public static void saveEndpointsFromWSDL(String wsdlPath, Resource wsdlResource,
                                      Registry registry, Registry systemRegistry,String environment
            ,List<String> dependencies,String version) throws RegistryException {
        // building the wsdl element.
        byte[] wsdlContentBytes = (byte[])wsdlResource.getContent();
        if (wsdlContentBytes == null) {
            return;
        }
        OMElement wsdlElement;
        try {
            wsdlElement = buildOMElement(new String(wsdlContentBytes));
        } catch (Exception e) {
            String msg = "Error in building the wsdl element for path: " + wsdlPath + ".";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }
        // saving soap11 endpoints
        List<OMElement> soap11Elements;
        try {
            soap11Elements =  evaluateXPathToElements(SOAP11_ENDPOINT_EXPR, wsdlElement);
        } catch (Exception e) {
            String msg = "Error in evaluating xpath expressions to extract endpoints, wsdl path: " + wsdlPath + ".";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }
        for (OMElement soap11Element: soap11Elements) {
            String locationUrl = soap11Element.getAttributeValue(new QName(LOCATION_ATTR));
            Map<String, String> properties = new HashMap<String, String>();
            properties.put(CommonConstants.SOAP11_ENDPOINT_ATTRIBUTE, "true");
            saveEndpoint(registry, locationUrl, wsdlPath, properties, systemRegistry,environment,dependencies,version);
        }

        // saving soap12 endpoints
        List<OMElement> soap12Elements;
        try {
            soap12Elements =  evaluateXPathToElements(SOAP12_ENDPOINT_EXPR, wsdlElement);
        } catch (Exception e) {
            String msg = "Error in evaluating xpath expressions to extract endpoints, wsdl path: " + wsdlPath + ".";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }
        for (OMElement soap12Element: soap12Elements) {
            String locationUrl = soap12Element.getAttributeValue(new QName(LOCATION_ATTR));
            Map<String, String> properties = new HashMap<String, String>();
            properties.put(CommonConstants.SOAP12_ENDPOINT_ATTRIBUTE, "true");
            saveEndpoint(registry, locationUrl, wsdlPath, properties, systemRegistry,environment,dependencies,version);
        }

        // saving http endpoints
        List<OMElement> httpElements;
        try {
            httpElements =  evaluateXPathToElements(HTTP_ENDPOINT_EXPR, wsdlElement);
        } catch (Exception e) {
            String msg = "Error in evaluating xpath expressions to extract endpoints, wsdl path: " + wsdlPath + ".";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }
        for (OMElement httpElement: httpElements) {
            String locationUrl = httpElement.getAttributeValue(new QName(LOCATION_ATTR));
            Map<String, String> properties = new HashMap<String, String>();
            properties.put(CommonConstants.HTTP_ENDPOINT_ATTRIBUTE, "true");
            saveEndpoint(registry, locationUrl, wsdlPath, properties, systemRegistry,environment,dependencies,version);
        }
    }

    private static String[] wsdlPrefixes = {
            "wsdl", "http://schemas.xmlsoap.org/wsdl/",
            "wsdl2", "http://www.w3.org/ns/wsdl",
            "xsd", "http://www.w3.org/2001/XMLSchema",
            "soap", "http://schemas.xmlsoap.org/wsdl/soap/",
            "soap12", "http://schemas.xmlsoap.org/wsdl/soap12/",
            "http", "http://schemas.xmlsoap.org/wsdl/http/",
            "s", CommonConstants.SERVICE_ELEMENT_NAMESPACE,
    };

    private static List<OMElement> evaluateXPathToElements(String expression,
                                                           OMElement root) throws Exception {
        String[] nsPrefixes = wsdlPrefixes;
        AXIOMXPath xpathExpression = new AXIOMXPath(expression);

        for (int j = 0; j < nsPrefixes.length; j ++) {
            xpathExpression.addNamespace(nsPrefixes[j++], nsPrefixes[j]);
        }
        return (List<OMElement>)xpathExpression.selectNodes(root);
    }

    private static OMElement buildOMElement(String content) throws Exception {
        XMLStreamReader parser;
        try {
            parser = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(content));
        } catch (XMLStreamException e) {
            String msg = "Error in initializing the parser to build the OMElement.";
            throw new Exception(msg, e);
        }

        //create the builder
        StAXOMBuilder builder = new StAXOMBuilder(parser);
        //get the root element (in this case the envelope)

        return builder.getDocumentElement();
    }

    private static void saveEndpoint(Registry registry, String url,
                                   String associatedPath, Map<String, String> properties,
                                   Registry systemRegistry,String environment,List<String> dependencies,String version) throws RegistryException {
        String urlToPath = deriveEndpointFromUrl(url);

        String endpointAbsoluteBasePath = RegistryUtils.getAbsolutePath(registry.getRegistryContext(),
                environment);
        if (!systemRegistry.resourceExists(endpointAbsoluteBasePath)) {
            systemRegistry.put(endpointAbsoluteBasePath, systemRegistry.newCollection());
        }

        String prefix = urlToPath.substring(0,urlToPath.lastIndexOf(RegistryConstants.PATH_SEPARATOR) +1 );
        String name = urlToPath.replace(prefix,"");

        String regex = endpointAbsoluteBasePath + prefix + "[\\d].[\\d].[\\d]" + RegistryConstants.PATH_SEPARATOR + name;

        for (String dependency : dependencies) {
            if(dependency.matches(regex)){
                String newRelativePath =  RegistryUtils.getRelativePathToOriginal(dependency,
                        org.wso2.carbon.registry.core.RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH );
                saveEndpointValues(registry, url, associatedPath, properties, systemRegistry, newRelativePath, dependency);
                return;
            }
        }
        String endpointAbsolutePath = environment + prefix + version + RegistryConstants.PATH_SEPARATOR + name;
        String relativePath = environment.substring(0,RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH.length())
                + prefix + version + RegistryConstants.PATH_SEPARATOR + name;

        saveEndpointValues(registry, url, associatedPath, properties, systemRegistry, relativePath, endpointAbsolutePath);
    }
    private static void saveEndpoint(Registry registry, String url,
                                   String associatedPath, Map<String, String> properties,
                                   Registry systemRegistry) throws RegistryException {
        String urlToPath = deriveEndpointFromUrl(url);

        String endpointAbsoluteBasePath = RegistryUtils.getAbsolutePath(registry.getRegistryContext(),
                org.wso2.carbon.registry.core.RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH +
                endpointLocation);
        if (!systemRegistry.resourceExists(endpointAbsoluteBasePath)) {
            systemRegistry.put(endpointAbsoluteBasePath, systemRegistry.newCollection());
        }
        if(endpointLocation.endsWith(RegistryConstants.PATH_SEPARATOR)){
            if(urlToPath.startsWith(RegistryConstants.PATH_SEPARATOR)){
                urlToPath = urlToPath.replaceFirst(RegistryConstants.PATH_SEPARATOR,"");
            }
        }else{
            if(!urlToPath.startsWith(RegistryConstants.PATH_SEPARATOR)){
                urlToPath = RegistryConstants.PATH_SEPARATOR + urlToPath;
            }
        }
        String relativePath = endpointLocation + urlToPath;
        String endpointAbsolutePath = endpointAbsoluteBasePath + urlToPath;

        saveEndpointValues(registry, url, associatedPath, properties, systemRegistry, relativePath, endpointAbsolutePath);
    }

    private static void saveEndpointValues(Registry registry, String url, String associatedPath
            , Map<String, String> properties, Registry systemRegistry, String relativePath
            , String endpointAbsolutePath) throws RegistryException {
        Resource resource;
        String endpointId = null;
        if (registry.resourceExists(endpointAbsolutePath)) {
            resource = registry.get(endpointAbsolutePath);
            endpointId = resource.getProperty(CommonConstants.ARTIFACT_ID_PROP_KEY);
        } else {
            resource = registry.newResource();
            resource.setContent(url.getBytes());
        }
        boolean endpointIdCreated = false;
        if (endpointId == null) {
            endpointIdCreated = true;
            endpointId = UUID.randomUUID().toString();
            resource.setProperty(CommonConstants.ARTIFACT_ID_PROP_KEY, endpointId);
        }

        CommonUtil.addGovernanceArtifactEntryWithRelativeValues(
                systemRegistry, endpointId, relativePath);

        boolean propertiesChanged = false;
        if (properties != null) {
            for (Map.Entry<String, String> e : properties.entrySet()) {
                propertiesChanged = true;
                resource.setProperty(e.getKey(), e.getValue());
            }
        }

        if (endpointIdCreated || propertiesChanged) {
            // this will be definitely false for a brand new resource
            resource.setMediaType(endpointMediaType);
            registry.put(endpointAbsolutePath, resource);
            // we need to create a version here.
        }

        registry.addAssociation(associatedPath, endpointAbsolutePath, CommonConstants.DEPENDS);
        registry.addAssociation(endpointAbsolutePath, associatedPath, CommonConstants.USED_BY);
    }

    /**
     * Returns an endpoint path for the url without the starting '/'
     * @param url the endpoint url
     * @return the path
     */
    public static String deriveEndpointFromUrl(String url) {
        final String ENDPOINT_RESOURCE_PREFIX = "ep-";
        String name = url.split("/")[url.split("/").length - 1].replace(".","-").replace("=", "-").
                replace("@", "-").replace("#", "-").replace("~", "-");
        String[] temp = url.split("[?]")[0].split("/");
        StringBuffer sb = new StringBuffer();
        for(int i=0; i<temp.length-1; i++){
            sb.append(temp[i]).append("/");
        }
        String urlToPath = CommonUtil.derivePathFragmentFromNamespace(sb.toString());
        // excluding extra slashes.
        urlToPath = urlToPath.substring(1, urlToPath.length() - 1);
        urlToPath += "/" + ENDPOINT_RESOURCE_PREFIX +  name;
        return urlToPath;
    }

}
