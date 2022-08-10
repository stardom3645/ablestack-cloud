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
package org.apache.cloudstack.storage.datastore.lifecycle;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.StoragePoolInfo;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.StorageConflictException;


import com.cloud.utils.UriUtils;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;

import com.cloud.storage.dao.StoragePoolWorkDao;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.engine.subsystem.api.storage.ClusterScope;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.HostScope;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreLifeCycle;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreParameters;
import org.apache.cloudstack.engine.subsystem.api.storage.ZoneScope;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.volume.datastore.PrimaryDataStoreHelper;
import org.apache.log4j.Logger;


import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KRBDPrimaryDataStoreLifeCycleImpl implements PrimaryDataStoreLifeCycle {
    private static final Logger s_logger = Logger.getLogger(KRBDPrimaryDataStoreLifeCycleImpl.class);
    @Inject
    protected ResourceManager _resourceMgr;
    @Inject
    PrimaryDataStoreDao primaryDataStoreDao;
    @Inject
    DataStoreManager dataStoreMgr;
    @Inject
    AgentManager agentMgr;
    @Inject
    StorageManager storageMgr;

    @Inject
    protected StoragePoolWorkDao _storagePoolWorkDao;
    @Inject
    PrimaryDataStoreHelper dataStoreHelper;

    public KRBDPrimaryDataStoreLifeCycleImpl() {
    }

    @Override
    public DataStore initialize(Map<String, Object> dsInfos) {
        Long clusterId = (Long) dsInfos.get("clusterId");
        Long podId = (Long) dsInfos.get("podId");
        Long zoneId = (Long) dsInfos.get("zoneId");
        String url = (String) dsInfos.get("url");
        String providerName = (String) dsInfos.get("providerName");
        HypervisorType hypervisorType = (HypervisorType) dsInfos.get("hypervisorType");
        if (clusterId != null && podId == null) {
            throw new InvalidParameterValueException("Cluster id requires pod id");
        }

        PrimaryDataStoreParameters parameters = new PrimaryDataStoreParameters();

        URI uri = null;
        boolean multi = false;
        try {
            String urlType = url.substring(0, 3);
            if (urlType.equals("krbd") && url.contains(",")) {
                multi = true;
                url = url.replaceAll(",", "/");
            }
            uri = new URI(UriUtils.encodeURIComponent(url));
            if (uri.getScheme().equalsIgnoreCase("krbd")) {
                String uriHost = uri.getHost();
                String uriPath = uri.getPath();
                if (uriPath == null) {
                    throw new InvalidParameterValueException("host or path is null, should be rbd://hostname/pool");
                }
                if (multi) {
                    String multiHost = uriHost + (uriPath.substring(0, uriPath.lastIndexOf("/")).replaceAll("/", ","));
                    String[] hostArr = multiHost.split(",");
                    if (hostArr.length > 5) {
                        throw new InvalidParameterValueException("RADOS monitor can support up to 5 hosts.");
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new InvalidParameterValueException(url + " is not a valid uri");
        }

        String tags = (String) dsInfos.get("tags");
        Map<String, String> details = (Map<String, String>) dsInfos.get("details");

        parameters.setTags(tags);
        parameters.setDetails(details);

        String scheme = uri.getScheme();
        String storageHost = uri.getHost();
        String hostPath = null;
        try {
            hostPath = URLDecoder.decode(uri.getPath(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            s_logger.error("[ignored] we are on a platform not supporting \"UTF-8\"!?!", e);
        }
        if (hostPath == null) { // if decoding fails, use getPath() anyway
            hostPath = uri.getPath();
        }

        String userInfo = uri.getUserInfo();
        int port = uri.getPort();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("createPool Params @ scheme - " + scheme + " storageHost - " + storageHost + " hostPath - "
                    + hostPath + " port - " + port);
        }

        if (scheme.equalsIgnoreCase("rbd")) {
            if (port == -1) {
                port = 0;
            }
            if (multi) {
                storageHost = storageHost + (hostPath.substring(0, hostPath.lastIndexOf("/")).replaceAll("/", ","));
                hostPath = hostPath.substring(hostPath.lastIndexOf("/") + 1);
            }
            parameters.setType(Storage.StoragePoolType.RBD);
            parameters.setHost(storageHost);
            parameters.setPort(port);
            parameters.setPath(hostPath.replaceFirst("/", ""));
            parameters.setUserInfo(userInfo);
        }

        String uuid = UUID.nameUUIDFromBytes((storageHost + hostPath).getBytes()).toString();
        List<StoragePoolVO> spHandles = primaryDataStoreDao.findIfDuplicatePoolsExistByUUID(uuid);
        if ((spHandles != null) && (spHandles.size() > 0)) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Another active pool with the same uuid already exists");
            }
            throw new CloudRuntimeException("Another active pool with the same uuid already exists");
        }

        parameters.setUuid(uuid);
        parameters.setZoneId(zoneId);
        parameters.setPodId(podId);
        parameters.setName((String) dsInfos.get("name"));
        parameters.setClusterId(clusterId);
        parameters.setProviderName(providerName);
        parameters.setHypervisorType(hypervisorType);

        return dataStoreHelper.createPrimaryDataStore(parameters);
    }

    protected boolean createStoragePool(long hostId, StoragePool pool) {
        s_logger.debug("creating pool " + pool.getName() + " on  host " + hostId);

        if (pool.getPoolType() != Storage.StoragePoolType.RBD) {
            s_logger.warn(" Doesn't support storage pool type " + pool.getPoolType());
            return false;
        }
        CreateStoragePoolCommand cmd = new CreateStoragePoolCommand(true, pool);
        final Answer answer = agentMgr.easySend(hostId, cmd);
        if (answer != null && answer.getResult()) {
            return true;
        } else {
            primaryDataStoreDao.expunge(pool.getId());
            String msg = answer != null
                    ? "Can not create storage pool through host " + hostId + " due to " + answer.getDetails()
                    : "Can not create storage pool through host " + hostId + " due to CreateStoragePoolCommand returns null";
            s_logger.warn(msg);
            throw new CloudRuntimeException(msg);
        }
    }

    @Override
    public boolean attachCluster(DataStore store, ClusterScope scope) {
        PrimaryDataStoreInfo primarystore = (PrimaryDataStoreInfo)store;
        // Check if there is host up in this cluster
        List<HostVO> allHosts =
                _resourceMgr.listAllUpHosts(Host.Type.Routing, primarystore.getClusterId(), primarystore.getPodId(), primarystore.getDataCenterId());
        if (allHosts.isEmpty()) {
            primaryDataStoreDao.expunge(primarystore.getId());
            throw new CloudRuntimeException("No host up to associate a storage pool with in cluster " + primarystore.getClusterId());
        }

        boolean success = false;
        for (HostVO h : allHosts) {
            success = createStoragePool(h.getId(), primarystore);
            if (success) {
                break;
            }
        }

        s_logger.debug("In createPool Adding the pool to each of the hosts");
        List<HostVO> poolHosts = new ArrayList<HostVO>();
        for (HostVO h : allHosts) {
            try {
                storageMgr.connectHostToSharedPool(h.getId(), primarystore.getId());
                poolHosts.add(h);
            } catch (StorageConflictException se) {
                primaryDataStoreDao.expunge(primarystore.getId());
                throw new CloudRuntimeException("Storage has already been added as local storage");
            } catch (Exception e) {
                s_logger.warn("Unable to establish a connection between " + h + " and " + primarystore, e);
            }
        }

        if (poolHosts.isEmpty()) {
            s_logger.warn("No host can access storage pool " + primarystore + " on cluster " + primarystore.getClusterId());
            primaryDataStoreDao.expunge(primarystore.getId());
            throw new CloudRuntimeException("Failed to access storage pool");
        }

        dataStoreHelper.attachCluster(store);
        return true;
    }

    @Override
    public boolean attachZone(DataStore dataStore, ZoneScope scope, HypervisorType hypervisorType) {
        List<HostVO> hosts = _resourceMgr.listAllUpAndEnabledHostsInOneZoneByHypervisor(hypervisorType, scope.getScopeId());
        s_logger.debug("In createPool. Attaching the pool to each of the hosts.");
        List<HostVO> poolHosts = new ArrayList<HostVO>();
        for (HostVO host : hosts) {
            try {
                storageMgr.connectHostToSharedPool(host.getId(), dataStore.getId());
                poolHosts.add(host);
            } catch (StorageConflictException se) {
                    primaryDataStoreDao.expunge(dataStore.getId());
                    throw new CloudRuntimeException("Storage has already been added as local storage to host: " + host.getName());
            } catch (Exception e) {
                s_logger.warn("Unable to establish a connection between " + host + " and " + dataStore, e);
            }
        }
        if (poolHosts.isEmpty()) {
            s_logger.warn("No host can access storage pool " + dataStore + " in this zone.");
            primaryDataStoreDao.expunge(dataStore.getId());
            throw new CloudRuntimeException("Failed to create storage pool as it is not accessible to hosts.");
        }
        dataStoreHelper.attachZone(dataStore, hypervisorType);
        return true;
    }

    @Override
    public boolean attachHost(DataStore store, HostScope scope, StoragePoolInfo existingInfo) {
        return false;
    }

    @Override
    public boolean maintain(DataStore store) {
        return false;
    }

    @Override
    public boolean cancelMaintain(DataStore store) {
        return false;
    }

    @Override
    public boolean deleteDataStore(DataStore store) {
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.cloudstack.engine.subsystem.api.storage.DataStoreLifeCycle#
     * migrateToObjectStore(org.apache.cloudstack.engine.subsystem.api.storage.
     * DataStore)
     */
    @Override
    public boolean migrateToObjectStore(DataStore store) {
        return false;
    }

    @Override
    public void updateStoragePool(StoragePool storagePool, Map<String, String> details) {
    }

    @Override
    public void enableStoragePool(DataStore store) {
    }

    @Override
    public void disableStoragePool(DataStore store) {
    }
}
