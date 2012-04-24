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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.wso2.carbon.registry.core.*;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.handlers.Handler;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.extensions.handlers.utils.SchemaValidator;
import org.wso2.carbon.registry.extensions.utils.CommonUtil;
import org.wso2.carbon.registry.extensions.utils.WSDLValidationInfo;
import org.wso2.carbon.registry.handlers.uri.utils.SchemaProcessor;
import org.wso2.carbon.registry.handlers.uri.utils.UriConstants;

import java.io.*;
import java.util.*;

public class SchemaUriHandler extends Handler {
    private static final Log log = LogFactory.getLog(SchemaUriHandler.class);
    private boolean disableSchemaValidation = false;

    public void importResource(RequestContext requestContext, String sourceURL) throws RegistryException {
        if (!CommonUtil.isUpdateLockAvailable()) {
            return;
        }
        CommonUtil.acquireUpdateLock();
        try {
            String resourcePath = requestContext.getResourcePath().getCompletePath();

            WSDLValidationInfo validationInfo = null;
            try {
                if (!disableSchemaValidation) {
                    validationInfo =
                            SchemaValidator.validate(new XMLInputSource(null, sourceURL, null));
                }
            } catch (Exception e) {
                throw new RegistryException("Exception occured while validating the schema", e);
            }

            String savedName = processSchemaImport(requestContext, resourcePath, validationInfo, sourceURL);

            onPutCompleted(resourcePath,
                    Collections.singletonMap(sourceURL, savedName),
                    Collections.<String>emptyList(), requestContext);

            requestContext.setActualPath(savedName);
            requestContext.setProcessingComplete(true);
        } finally {
            CommonUtil.releaseUpdateLock();
        }
    }

    /**
     * creates the parent directory structure for a given resource at a temp location in the file system.
     *
     * @param file
     * @throws IOException
     */
    private void makeDirs(File file) throws IOException {
        if (file != null && !file.exists() && !file.mkdirs()) {
            log.warn("Failed to create directories at path: " + file.getAbsolutePath());
        }
    }

    /**
     * Method to customize the Schema Processor.
     *
     * @param requestContext the request context for the import/put operation.
     * @param validationInfo the WSDL validation information.
     * @return the Schema Processor instance.
     */
    @SuppressWarnings("unused")
    protected SchemaProcessor buildSchemaProcessor(RequestContext requestContext,
                                                   WSDLValidationInfo validationInfo) {
        return new SchemaProcessor(requestContext, validationInfo);
    }

    /**
     * Method that runs the schema import procedure.
     *
     * @param requestContext the request context for the import operation
     * @param resourcePath   the path of the resource
     * @param validationInfo the validation information
     * @return the path at which the schema was uploaded to
     * @throws RegistryException if the operation failed.
     */
    protected String processSchemaImport(RequestContext requestContext, String resourcePath,
                                         WSDLValidationInfo validationInfo, String sourceURL) throws RegistryException {
        SchemaProcessor schemaProcessor =
                buildSchemaProcessor(requestContext, validationInfo);

        return schemaProcessor
                .importSchemaToRegistry(requestContext, resourcePath,
                        getChrootedLocation(requestContext.getRegistryContext()), true, sourceURL);
    }

    /**
     * Method that will executed after the put operation has been done.
     *
     * @param path           the path of the resource.
     * @param addedResources the resources that have been added to the registry.
     * @param otherResources the resources that have not been added to the registry.
     * @param requestContext the request context for the put operation.
     * @throws RegistryException if the operation failed.
     */
    @SuppressWarnings("unused")
    protected void onPutCompleted(String path, Map<String, String> addedResources,
                                  List<String> otherResources, RequestContext requestContext)
            throws RegistryException {
    }

    private String getChrootedLocation(RegistryContext registryContext) {
        return RegistryUtils.getAbsolutePath(registryContext,
                RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH + UriConstants.XSD_LOCATION);
    }

    public void setDisableSchemaValidation(String disableSchemaValidation) {
        this.disableSchemaValidation = Boolean.toString(true).equals(disableSchemaValidation);
    }

}
