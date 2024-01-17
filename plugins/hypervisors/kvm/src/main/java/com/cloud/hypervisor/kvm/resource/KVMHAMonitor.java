// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.kvm.resource;

import com.cloud.agent.properties.AgentProperties;
import com.cloud.agent.properties.AgentPropertiesFileHandler;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.utils.script.Script;
import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.libvirt.StoragePool;
import org.libvirt.StoragePoolInfo.StoragePoolState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KVMHAMonitor extends KVMHABase implements Runnable {

    private static final Logger s_logger = Logger.getLogger(KVMHAMonitor.class);
    private final Map<String, HAStoragePool> storagePool = new ConcurrentHashMap<>();
    private final Map<String, HAStoragePool> storageRbdPool = new ConcurrentHashMap<>();
    private final Map<String, HAStoragePool> storageClvmPool = new ConcurrentHashMap<>();
    private final boolean rebootHostAndAlertManagementOnHeartbeatTimeout;

    private final String hostPrivateIp;

    public KVMHAMonitor(HAStoragePool pool, String host, String scriptPath, String scriptPathRbd, String scriptPathClvm) {
        if (pool != null) {
            storagePool.put(pool.getPoolUUID(), pool);
        }
        hostPrivateIp = host;
        configureHeartBeatPath(scriptPath, scriptPathRbd, scriptPathClvm);
        rebootHostAndAlertManagementOnHeartbeatTimeout = AgentPropertiesFileHandler.getPropertyValue(AgentProperties.REBOOT_HOST_AND_ALERT_MANAGEMENT_ON_HEARTBEAT_TIMEOUT);
    }

    private static synchronized void configureHeartBeatPath(String scriptPath, String scriptPathRbd, String scriptPathClvm) {
        KVMHABase.s_heartBeatPath = scriptPath;
        KVMHABase.s_heartBeatPathRbd = scriptPathRbd;
        KVMHABase.s_heartBeatPathClvm = scriptPathClvm;
    }
    public void addStoragePool(HAStoragePool pool) {
        storagePool.put(pool.getPoolUUID(), pool);
    }

    public void addRbdStoragePool(HAStoragePool pool) {
        storageRbdPool.put(pool.getPoolUUID(), pool);
    }

    public void addClvmStoragePool(HAStoragePool pool) {
        storageClvmPool.put(pool.getPoolUUID(), pool);
    }

    public void removeStoragePool(String uuid) {
        HAStoragePool pool = storagePool.get(uuid);
        if (pool != null) {
            Script.runSimpleBashScript("umount " + pool.getMountDestPath());
            storagePool.remove(uuid);
        }

    }

    public void removeRbdStoragePool(String uuid) {
        HAStoragePool pool = storageRbdPool.get(uuid);
        if (pool != null) {
            Script.runSimpleBashScript("umount " + pool.getMountDestPath());
            storageRbdPool.remove(uuid);
        }
    }

    public void removeClvmStoragePool(String uuid) {
        HAStoragePool pool = storageClvmPool.get(uuid);
        if (pool != null) {
            storageClvmPool.remove(uuid);
        }
    }

    public List<HAStoragePool> getStoragePools() {
        return new ArrayList<>(storagePool.values());
    }

    public List<HAStoragePool> getRbdStoragePools() {
        return new ArrayList<>(storageRbdPool.values());
    }

    public List<HAStoragePool> getClvmStoragePools() {
        return new ArrayList<>(storageClvmPool.values());
    }

    public HAStoragePool getStoragePool(String uuid) {
        return storagePool.get(uuid);
    }

    public HAStoragePool getRbdStoragePool(String uuid) {
        return storageRbdPool.get(uuid);
    }

    public HAStoragePool getClvmStoragePool(String uuid) {
        return storageClvmPool.get(uuid);
    }

    protected void runHeartBeat(Map<String, HAStoragePool> storagePool) {
        Set<String> removedPools = new HashSet<>();
        for (String uuid : storagePool.keySet()) {
            HAStoragePool primaryStoragePool = storagePool.get(uuid);
            if (primaryStoragePool.getPool().getType() == StoragePoolType.NetworkFilesystem || primaryStoragePool.getPool().getType() == StoragePoolType.RBD || primaryStoragePool.getPool().getType() == StoragePoolType.CLVM) {
                checkForNotExistingPools(removedPools, uuid);
                if (removedPools.contains(uuid)) {
                    continue;
                }
            }
            String result = null;
            result = executePoolHeartBeatCommand(uuid, primaryStoragePool, result);
            if (result != null && rebootHostAndAlertManagementOnHeartbeatTimeout) {
                s_logger.warn(String.format("Write heartbeat for pool [%s] failed: %s; stopping cloudstack-agent.", uuid, result));
                if (primaryStoragePool.getPool().isPoolSupportHA()) {
                    primaryStoragePool.getPool().createHeartBeatCommand(primaryStoragePool, null, false);
                }
            }
        }
        if (!removedPools.isEmpty()) {
            for (String uuid : removedPools) {
                removeStoragePool(uuid);
            }
        }
    }

    private String executePoolHeartBeatCommand(String uuid, HAStoragePool primaryStoragePool, String result) {
        for (int i = 1; i <= _heartBeatUpdateMaxTries; i++) {
            if (primaryStoragePool.getPool().isPoolSupportHA()) {
                result = primaryStoragePool.getPool().createHeartBeatCommand(primaryStoragePool, hostPrivateIp, true);
            }
            if (result != null) {
                s_logger.warn(String.format("Write heartbeat for pool [%s] failed: %s; try: %s of %s.", uuid, result, i, _heartBeatUpdateMaxTries));
                try {
                    Thread.sleep(_heartBeatUpdateRetrySleep);
                } catch (InterruptedException e) {
                    s_logger.debug("[IGNORED] Interrupted between heartbeat retries.", e);
                }
            } else {
                break;
            }
        }
        return result;
    }

    private void checkForNotExistingPools(Set<String> removedPools, String uuid) {
        try {
            Connect conn = LibvirtConnection.getConnection();
            StoragePool storage = conn.storagePoolLookupByUUIDString(uuid);
            if (storage == null || storage.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                if (storage == null) {
                    s_logger.debug(String.format("Libvirt storage pool [%s] not found, removing from HA list.", uuid));
                } else {
                    s_logger.debug(String.format("Libvirt storage pool [%s] found, but not running, removing from HA list.", uuid));
                }

                removedPools.add(uuid);
            }

            s_logger.debug(String.format("Found storage pool [%s] in libvirt, continuing.", uuid));

        } catch (LibvirtException e) {
            s_logger.debug(String.format("Failed to lookup libvirt storage pool [%s].", uuid), e);

            if (e.toString().contains("pool not found")) {
                s_logger.debug(String.format("Removing pool [%s] from HA monitor since it was deleted.", uuid));
                removedPools.add(uuid);
            }
        }
    }

    @Override
    public void run() {
        while (true) {

            runHeartBeat(storagePool);
            runHeartBeat(storageRbdPool);
            runHeartBeat(storageClvmPool);

            try {
                Thread.sleep(_heartBeatUpdateFreq);
            } catch (InterruptedException e) {
                s_logger.debug("[IGNORED] Interrupted between heartbeats.", e);
            }
        }
    }

}
