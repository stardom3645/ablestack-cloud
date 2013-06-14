/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.datastore.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.response.StorageProviderResponse;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProvider;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProvider.DataStoreProviderType;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProviderManager;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreDriver;
import org.apache.cloudstack.storage.datastore.PrimaryDataStoreProviderManager;
import org.apache.cloudstack.storage.image.ImageStoreDriver;
import org.apache.cloudstack.storage.image.datastore.ImageStoreProviderManager;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;

@Component
public class DataStoreProviderManagerImpl extends ManagerBase implements DataStoreProviderManager {
    private static final Logger s_logger = Logger.getLogger(DataStoreProviderManagerImpl.class);
    @Inject
    List<DataStoreProvider> providers;
    protected Map<String, DataStoreProvider> providerMap = new HashMap<String, DataStoreProvider>();
    @Inject
    PrimaryDataStoreProviderManager primaryDataStoreProviderMgr;
    @Inject
    ImageStoreProviderManager imageDataStoreProviderMgr;

    @Override
    public DataStoreProvider getDataStoreProvider(String name) {
        return providerMap.get(name);
    }

    public List<StorageProviderResponse> getPrimaryDataStoreProviders() {
        List<StorageProviderResponse> providers = new ArrayList<StorageProviderResponse>();
        for (DataStoreProvider provider : providerMap.values()) {
            if (provider.getTypes().contains(DataStoreProviderType.PRIMARY)) {
                StorageProviderResponse response = new StorageProviderResponse();
                response.setName(provider.getName());
                response.setType(DataStoreProvider.DataStoreProviderType.PRIMARY.toString());
                providers.add(response);
            }
        }
        return providers;
    }

    public List<StorageProviderResponse> getImageDataStoreProviders() {
        List<StorageProviderResponse> providers = new ArrayList<StorageProviderResponse>();
        for (DataStoreProvider provider : providerMap.values()) {
            if (provider.getTypes().contains(DataStoreProviderType.IMAGE)) {
                StorageProviderResponse response = new StorageProviderResponse();
                response.setName(provider.getName());
                response.setType(DataStoreProvider.DataStoreProviderType.IMAGE.toString());
                providers.add(response);
            }
        }
        return providers;
    }

    public List<StorageProviderResponse> getCacheDataStoreProviders() {
        List<StorageProviderResponse> providers = new ArrayList<StorageProviderResponse>();
        for (DataStoreProvider provider : providerMap.values()) {
            if (provider.getTypes().contains(DataStoreProviderType.ImageCache)) {
                StorageProviderResponse response = new StorageProviderResponse();
                response.setName(provider.getName());
                response.setType(DataStoreProviderType.ImageCache.toString());
                providers.add(response);
            }
        }
        return providers;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        Map<String, Object> copyParams = new HashMap<String, Object>(params);

        for (DataStoreProvider provider : providers) {
            String providerName = provider.getName();
            if (providerMap.get(providerName) != null) {
                s_logger.debug("Failed to register data store provider, provider name: " + providerName
                        + " is not unique");
                return false;
            }

            s_logger.debug("registering data store provider:" + provider.getName());

            providerMap.put(providerName, provider);
            try {
                boolean registrationResult = provider.configure(copyParams);
                if (!registrationResult) {
                    providerMap.remove(providerName);
                    s_logger.debug("Failed to register data store provider: " + providerName);
                    return false;
                }

                Set<DataStoreProviderType> types = provider.getTypes();
                if (types.contains(DataStoreProviderType.PRIMARY)) {
                    primaryDataStoreProviderMgr.registerDriver(provider.getName(),
                            (PrimaryDataStoreDriver) provider.getDataStoreDriver());
                    primaryDataStoreProviderMgr.registerHostListener(provider.getName(), provider.getHostListener());
                } else if (types.contains(DataStoreProviderType.IMAGE)) {
                    imageDataStoreProviderMgr.registerDriver(provider.getName(),
                            (ImageStoreDriver) provider.getDataStoreDriver());
                }
            } catch (Exception e) {
                s_logger.debug("configure provider failed", e);
                providerMap.remove(providerName);
                return false;
            }
        }

        return true;
    }

    @Override
    public DataStoreProvider getDefaultPrimaryDataStoreProvider() {
        return this.getDataStoreProvider(DataStoreProvider.DEFAULT_PRIMARY);
    }

    @Override
    public DataStoreProvider getDefaultImageDataStoreProvider() {
        return this.getDataStoreProvider(DataStoreProvider.NFS_IMAGE);
    }

    @Override
    public DataStoreProvider getDefaultCacheDataStoreProvider() {
        return this.getDataStoreProvider(DataStoreProvider.NFS_IMAGE);
    }

    @Override
    public List<StorageProviderResponse> getDataStoreProviders(String type) {
        if (type == null) {
            throw new InvalidParameterValueException("Invalid parameter, need to specify type: either primary or image");
        }
        if (type.equalsIgnoreCase(DataStoreProvider.DataStoreProviderType.PRIMARY.toString())) {
            return this.getPrimaryDataStoreProviders();
        } else if (type.equalsIgnoreCase(DataStoreProvider.DataStoreProviderType.IMAGE.toString())) {
            return this.getImageDataStoreProviders();
        } else if (type.equalsIgnoreCase(DataStoreProvider.DataStoreProviderType.ImageCache.toString())) {
            return this.getCacheDataStoreProviders();
        } else {
            throw new InvalidParameterValueException("Invalid parameter: " + type);
        }
    }
}
