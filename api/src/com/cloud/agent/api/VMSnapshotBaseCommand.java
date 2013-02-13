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

package com.cloud.agent.api;

import java.util.List;

import com.cloud.agent.api.to.VolumeTO;

public class VMSnapshotBaseCommand extends Command{
    protected List<VolumeTO> volumeTOs;
    protected VMSnapshotTO target;
    protected String vmName;
    protected String guestOSType;
    
    
    public VMSnapshotBaseCommand(String vmName, VMSnapshotTO snapshot, List<VolumeTO> volumeTOs, String guestOSType) {
        this.vmName = vmName;
        this.target = snapshot;
        this.volumeTOs = volumeTOs;
        this.guestOSType = guestOSType;
    }
    
    public List<VolumeTO> getVolumeTOs() {
        return volumeTOs;
    }

    public void setVolumeTOs(List<VolumeTO> volumeTOs) {
        this.volumeTOs = volumeTOs;
    }

    public VMSnapshotTO getTarget() {
        return target;
    }

    public void setTarget(VMSnapshotTO target) {
        this.target = target;
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getGuestOSType() {
        return guestOSType;
    }

    public void setGuestOSType(String guestOSType) {
        this.guestOSType = guestOSType;
    }
}
