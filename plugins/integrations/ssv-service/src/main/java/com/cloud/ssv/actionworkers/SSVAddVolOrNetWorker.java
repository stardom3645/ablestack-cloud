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

package com.cloud.ssv.actionworkers;

import org.apache.cloudstack.api.command.user.ssv.AddVolSSVCmd;
import org.apache.cloudstack.api.command.user.vm.RebootVMCmd;
import org.apache.cloudstack.api.command.user.volume.CreateVolumeCmd;
import org.apache.logging.log4j.Level;
import java.lang.reflect.Field;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.ssv.SSV;
import com.cloud.ssv.SSVManagerImpl;
import com.cloud.ssv.SSVVmMapVO;
import com.cloud.storage.Volume;
import com.cloud.uservm.UserVm;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;

public class SSVAddVolOrNetWorker extends SSVActionWorker {
    public SSVAddVolOrNetWorker(final SSV ssv, final SSVManagerImpl appManager) {
        super(ssv, appManager);
    }

    public boolean addVol(AddVolSSVCmd cmd) throws CloudRuntimeException {
        // init();
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Add Volmue to Shared Storage VM  : %s", ssv.getName()));
        }
        // stateTransitTo(ssv.getId(), SSV.Event.AddVolumeRequested);
        SSVVmMapVO vo = ssvVmMapDao.listVmBySSVServiceId(ssv.getId());
        UserVm vm = userVmDao.findById(vo.getVmId());
        if (vm == null) {
            logTransitStateAndThrow(Level.ERROR, String.format("Failed to Add Volmue VMs in Shared Storage VM : %s", ssv.getName()), ssv.getId(), SSV.Event.OperationFailed);
        } else {
            try {
                logger.debug("Create Volume 11111111........................");
                CreateVolumeCmd createVolume = new CreateVolumeCmd();
                createVolume = ComponentContext.inject(createVolume);
                Field diskOfferingId = createVolume.getClass().getDeclaredField("diskOfferingId");
                diskOfferingId.setAccessible(true);
                diskOfferingId.set(createVolume, cmd.getDiskOfferingId());

                logger.debug("cmd.getSize() :::::::::" + cmd.getSize());
                if (cmd.getSize() != null && cmd.getSize() > 0 ) {
                    Field size = createVolume.getClass().getDeclaredField("size");
                    size.setAccessible(true);
                    size.set(createVolume, cmd.getSize());
                }

                Field zoneId = createVolume.getClass().getDeclaredField("zoneId");
                zoneId.setAccessible(true);
                zoneId.set(createVolume, cmd.getZoneId());


                logger.debug("Create Volume 111111112222222........................");

                Volume volume = volumeApiService.allocVolume(createVolume);

                logger.debug("Create Volume ........................");
                logger.debug("volume id :: " + volume.getId());

                volumeApiService.attachVolumeToVM(vm.getId(), volume.getId(), null);
                logger.debug("Attach VolumeTo VM ........................");
                RebootVMCmd rebootVm = new RebootVMCmd();
                rebootVm = ComponentContext.inject(rebootVm);
                Field id = rebootVm.getClass().getDeclaredField("id");
                id.setAccessible(true);
                id.set(rebootVm, vm.getId());

                logger.debug("Reboot VM ........................");
                userVmService.rebootVirtualMachine(rebootVm);
                logger.debug("Done ........................");
            } catch (InsufficientCapacityException | ResourceUnavailableException | IllegalAccessException | NoSuchFieldException | ConcurrentOperationException | ResourceAllocationException ex) {
                logger.warn(String.format("Failed to Add Volmue VM : %s in Shared Storage VM  : %s due to ", vm.getDisplayName(), ssv.getName()) + ex);
                // dont bail out here. proceed further to stop the reset of the VM's
            }
            if (!vm.getState().equals(VirtualMachine.State.Running)) {
                logTransitStateAndThrow(Level.ERROR, String.format("Failed to Stop Control VMs in Shared Storage VM : %s", ssv.getName()), ssv.getId(), SSV.Event.OperationFailed);
            }
        }
        // stateTransitTo(ssv.getId(), SSV.Event.OperationSucceeded);
        return true;
    }
}
