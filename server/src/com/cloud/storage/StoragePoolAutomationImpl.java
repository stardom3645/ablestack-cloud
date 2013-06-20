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
package com.cloud.storage;

import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreLifeCycle;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProvider;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProviderManager;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.alert.AlertManager;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.resource.ResourceManager;
import com.cloud.server.ManagementServer;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.storage.dao.StoragePoolWorkDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.user.User;
import com.cloud.user.UserContext;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.ExecutionException;
import com.cloud.vm.ConsoleProxyVO;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.SecondaryStorageVmVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.ConsoleProxyDao;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

@Component
public class StoragePoolAutomationImpl implements StoragePoolAutomation {
    private static final Logger s_logger = Logger
            .getLogger(StoragePoolAutomationImpl.class);
    @Inject
    protected VirtualMachineManager vmMgr;
    @Inject
    protected SecondaryStorageVmDao _secStrgDao;
    @Inject
    UserVmDao userVmDao;
    @Inject
    protected UserDao _userDao;
    @Inject
    protected DomainRouterDao _domrDao;
    @Inject
    protected StoragePoolHostDao _storagePoolHostDao;
    @Inject
    protected AlertManager _alertMgr;
    @Inject
    protected ConsoleProxyDao _consoleProxyDao;

    @Inject
    protected StoragePoolWorkDao _storagePoolWorkDao;
    @Inject
    PrimaryDataStoreDao primaryDataStoreDao;
    @Inject
    DataStoreManager dataStoreMgr;
    @Inject
    protected ResourceManager _resourceMgr;
    @Inject
    AgentManager agentMgr;
    @Inject
    VolumeDao volumeDao;
    @Inject
    VMInstanceDao vmDao;
    @Inject
    ManagementServer server;
    @Inject DataStoreProviderManager providerMgr;
    
    @Override
    public boolean maintain(DataStore store) {
        Long userId = UserContext.current().getCallerUserId();
        User user = _userDao.findById(userId);
        Account account = UserContext.current().getCaller();
        StoragePoolVO pool = this.primaryDataStoreDao.findById(store.getId());
        try {
            StoragePool storagePool = (StoragePool) store;
            List<HostVO> hosts = _resourceMgr.listHostsInClusterByStatus(
                    pool.getClusterId(), Status.Up);
            if (hosts == null || hosts.size() == 0) {
                pool.setStatus(StoragePoolStatus.Maintenance);
                primaryDataStoreDao.update(pool.getId(), pool);
                return true;
            } else {
                // set the pool state to prepare for maintenance
                pool.setStatus(StoragePoolStatus.PrepareForMaintenance);
                primaryDataStoreDao.update(pool.getId(), pool);
            }
            // remove heartbeat
            for (HostVO host : hosts) {
                ModifyStoragePoolCommand cmd = new ModifyStoragePoolCommand(
                        false, storagePool);
                final Answer answer = agentMgr.easySend(host.getId(), cmd);
                if (answer == null || !answer.getResult()) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("ModifyStoragePool false failed due to "
                                + ((answer == null) ? "answer null" : answer
                                        .getDetails()));
                    }
                } else {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("ModifyStoragePool false secceeded");
                    }
                }
            }
            // check to see if other ps exist
            // if they do, then we can migrate over the system vms to them
            // if they dont, then just stop all vms on this one
            List<StoragePoolVO> upPools = primaryDataStoreDao
                    .listByStatusInZone(pool.getDataCenterId(),
                            StoragePoolStatus.Up);
            boolean restart = true;
            if (upPools == null || upPools.size() == 0) {
                restart = false;
            }

            // 2. Get a list of all the ROOT volumes within this storage pool
            List<VolumeVO> allVolumes = this.volumeDao.findByPoolId(pool
                    .getId());

            // 3. Enqueue to the work queue
            for (VolumeVO volume : allVolumes) {
                VMInstanceVO vmInstance = vmDao
                        .findById(volume.getInstanceId());

                if (vmInstance == null) {
                    continue;
                }

                // enqueue sp work
                if (vmInstance.getState().equals(State.Running)
                        || vmInstance.getState().equals(State.Starting)
                        || vmInstance.getState().equals(State.Stopping)) {

                    try {
                        StoragePoolWorkVO work = new StoragePoolWorkVO(
                                vmInstance.getId(), pool.getId(), false, false,
                                server.getId());
                        _storagePoolWorkDao.persist(work);
                    } catch (Exception e) {
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("Work record already exists, re-using by re-setting values");
                        }
                        StoragePoolWorkVO work = _storagePoolWorkDao
                                .findByPoolIdAndVmId(pool.getId(),
                                        vmInstance.getId());
                        work.setStartedAfterMaintenance(false);
                        work.setStoppedForMaintenance(false);
                        work.setManagementServerId(server.getId());
                        _storagePoolWorkDao.update(work.getId(), work);
                    }
                }
            }

            // 4. Process the queue
            List<StoragePoolWorkVO> pendingWork = _storagePoolWorkDao
                    .listPendingWorkForPrepareForMaintenanceByPoolId(pool
                            .getId());

            for (StoragePoolWorkVO work : pendingWork) {
                // shut down the running vms
                VMInstanceVO vmInstance = vmDao.findById(work.getVmId());

                if (vmInstance == null) {
                    continue;
                }

                // if the instance is of type consoleproxy, call the console
                // proxy
                if (vmInstance.getType().equals(
                        VirtualMachine.Type.ConsoleProxy)) {
                    // call the consoleproxymanager
                    ConsoleProxyVO consoleProxy = _consoleProxyDao
                            .findById(vmInstance.getId());
                    if (!vmMgr.advanceStop(consoleProxy, true, user, account)) {
                        String errorMsg = "There was an error stopping the console proxy id: "
                                + vmInstance.getId()
                                + " ,cannot enable storage maintenance";
                        s_logger.warn(errorMsg);
                        throw new CloudRuntimeException(errorMsg);
                    } else {
                        // update work status
                        work.setStoppedForMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }

                    if (restart) {

                        if (this.vmMgr.advanceStart(consoleProxy, null, user,
                                account) == null) {
                            String errorMsg = "There was an error starting the console proxy id: "
                                    + vmInstance.getId()
                                    + " on another storage pool, cannot enable primary storage maintenance";
                            s_logger.warn(errorMsg);
                        } else {
                            // update work status
                            work.setStartedAfterMaintenance(true);
                            _storagePoolWorkDao.update(work.getId(), work);
                        }
                    }
                }

                // if the instance is of type uservm, call the user vm manager
                if (vmInstance.getType().equals(VirtualMachine.Type.User)) {
                    UserVmVO userVm = userVmDao.findById(vmInstance.getId());
                    if (!vmMgr.advanceStop(userVm, true, user, account)) {
                        String errorMsg = "There was an error stopping the user vm id: "
                                + vmInstance.getId()
                                + " ,cannot enable storage maintenance";
                        s_logger.warn(errorMsg);
                        throw new CloudRuntimeException(errorMsg);
                    } else {
                        // update work status
                        work.setStoppedForMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }
                }

                // if the instance is of type secondary storage vm, call the
                // secondary storage vm manager
                if (vmInstance.getType().equals(
                        VirtualMachine.Type.SecondaryStorageVm)) {
                    SecondaryStorageVmVO secStrgVm = _secStrgDao
                            .findById(vmInstance.getId());
                    if (!vmMgr.advanceStop(secStrgVm, true, user, account)) {
                        String errorMsg = "There was an error stopping the ssvm id: "
                                + vmInstance.getId()
                                + " ,cannot enable storage maintenance";
                        s_logger.warn(errorMsg);
                        throw new CloudRuntimeException(errorMsg);
                    } else {
                        // update work status
                        work.setStoppedForMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }

                    if (restart) {
                        if (vmMgr.advanceStart(secStrgVm, null, user, account) == null) {
                            String errorMsg = "There was an error starting the ssvm id: "
                                    + vmInstance.getId()
                                    + " on another storage pool, cannot enable primary storage maintenance";
                            s_logger.warn(errorMsg);
                        } else {
                            // update work status
                            work.setStartedAfterMaintenance(true);
                            _storagePoolWorkDao.update(work.getId(), work);
                        }
                    }
                }

                // if the instance is of type domain router vm, call the network
                // manager
                if (vmInstance.getType().equals(
                        VirtualMachine.Type.DomainRouter)) {
                    DomainRouterVO domR = _domrDao.findById(vmInstance.getId());
                    if (!vmMgr.advanceStop(domR, true, user, account)) {
                        String errorMsg = "There was an error stopping the domain router id: "
                                + vmInstance.getId()
                                + " ,cannot enable primary storage maintenance";
                        s_logger.warn(errorMsg);
                        throw new CloudRuntimeException(errorMsg);
                    } else {
                        // update work status
                        work.setStoppedForMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }

                    if (restart) {
                        if (vmMgr.advanceStart(domR, null, user, account) == null) {
                            String errorMsg = "There was an error starting the domain router id: "
                                    + vmInstance.getId()
                                    + " on another storage pool, cannot enable primary storage maintenance";
                            s_logger.warn(errorMsg);
                        } else {
                            // update work status
                            work.setStartedAfterMaintenance(true);
                            _storagePoolWorkDao.update(work.getId(), work);
                        }
                    }
                }
            }
            
        } catch(Exception e) {
            s_logger.error(
                    "Exception in enabling primary storage maintenance:", e);
            pool.setStatus(StoragePoolStatus.ErrorInMaintenance);
            this.primaryDataStoreDao.update(pool.getId(), pool);
            throw new CloudRuntimeException(e.getMessage());
        }
        return true;
    }

    @Override
    public boolean cancelMaintain(DataStore store) {
        // Change the storage state back to up
        Long userId = UserContext.current().getCallerUserId();
        User user = _userDao.findById(userId);
        Account account = UserContext.current().getCaller();
        StoragePoolVO poolVO = this.primaryDataStoreDao
                .findById(store.getId());
        StoragePool pool = (StoragePool)store;
       
        List<HostVO> hosts = _resourceMgr.listHostsInClusterByStatus(
                pool.getClusterId(), Status.Up);
        if (hosts == null || hosts.size() == 0) {
            return true;
        }
        // add heartbeat
        for (HostVO host : hosts) {
            ModifyStoragePoolCommand msPoolCmd = new ModifyStoragePoolCommand(
                    true, pool);
            final Answer answer = agentMgr.easySend(host.getId(), msPoolCmd);
            if (answer == null || !answer.getResult()) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("ModifyStoragePool add failed due to "
                            + ((answer == null) ? "answer null" : answer
                                    .getDetails()));
                }
            } else {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("ModifyStoragePool add secceeded");
                }
            }
        }

        // 2. Get a list of pending work for this queue
        List<StoragePoolWorkVO> pendingWork = _storagePoolWorkDao
                .listPendingWorkForCancelMaintenanceByPoolId(poolVO.getId());

        // 3. work through the queue
        for (StoragePoolWorkVO work : pendingWork) {
            try {
                VMInstanceVO vmInstance = vmDao.findById(work.getVmId());

                if (vmInstance == null) {
                    continue;
                }

                // if the instance is of type consoleproxy, call the console
                // proxy
                if (vmInstance.getType().equals(
                        VirtualMachine.Type.ConsoleProxy)) {

                    ConsoleProxyVO consoleProxy = _consoleProxyDao
                            .findById(vmInstance.getId());
                    if (vmMgr.advanceStart(consoleProxy, null, user, account) == null) {
                        String msg = "There was an error starting the console proxy id: "
                                + vmInstance.getId()
                                + " on storage pool, cannot complete primary storage maintenance";
                        s_logger.warn(msg);
                        throw new ExecutionException(msg);
                    } else {
                        // update work queue
                        work.setStartedAfterMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }
                }

                // if the instance is of type ssvm, call the ssvm manager
                if (vmInstance.getType().equals(
                        VirtualMachine.Type.SecondaryStorageVm)) {
                    SecondaryStorageVmVO ssVm = _secStrgDao.findById(vmInstance
                            .getId());
                    if (vmMgr.advanceStart(ssVm, null, user, account) == null) {
                        String msg = "There was an error starting the ssvm id: "
                                + vmInstance.getId()
                                + " on storage pool, cannot complete primary storage maintenance";
                        s_logger.warn(msg);
                        throw new ExecutionException(msg);
                    } else {
                        // update work queue
                        work.setStartedAfterMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }
                }

                // if the instance is of type ssvm, call the ssvm manager
                if (vmInstance.getType().equals(
                        VirtualMachine.Type.DomainRouter)) {
                    DomainRouterVO domR = _domrDao.findById(vmInstance.getId());
                    if (vmMgr.advanceStart(domR, null, user, account) == null) {
                        String msg = "There was an error starting the domR id: "
                                + vmInstance.getId()
                                + " on storage pool, cannot complete primary storage maintenance";
                        s_logger.warn(msg);
                        throw new ExecutionException(msg);
                    } else {
                        // update work queue
                        work.setStartedAfterMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }
                }

                // if the instance is of type user vm, call the user vm manager
                if (vmInstance.getType().equals(VirtualMachine.Type.User)) {
                    UserVmVO userVm = userVmDao.findById(vmInstance.getId());

                    if (vmMgr.advanceStart(userVm, null, user, account) == null) {

                        String msg = "There was an error starting the user vm id: "
                                + vmInstance.getId()
                                + " on storage pool, cannot complete primary storage maintenance";
                        s_logger.warn(msg);
                        throw new ExecutionException(msg);
                    } else {
                        // update work queue
                        work.setStartedAfterMaintenance(true);
                        _storagePoolWorkDao.update(work.getId(), work);
                    }
                }
                return true;
            } catch (Exception e) {
                s_logger.debug("Failed start vm", e);
                throw new CloudRuntimeException(e.toString());
            }
        }
        return false;
    }

}
