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

package com.cloud.hypervisor.kvm.resource;

import com.cloud.agent.api.to.NicTO;
import com.cloud.exception.InternalErrorException;
import com.cloud.network.Networks;
import org.apache.log4j.Logger;
import org.libvirt.LibvirtException;

import javax.naming.ConfigurationException;
import java.util.Map;

public class DirectVifDriver extends VifDriverBase {

    private static final Logger s_logger = Logger.getLogger(DirectVifDriver.class);

    /**
     * Experimental driver to configure direct networking in libvirt. This should only
     * be used on an LXC cluster that does not run any system VMs.
     *
     * @param nic
     * @param guestOsType
     * @return
     * @throws InternalErrorException
     * @throws LibvirtException
     */
    public LibvirtVMDef.InterfaceDef plug(NicTO nic, String guestOsType) throws InternalErrorException,
            LibvirtException {
        LibvirtVMDef.InterfaceDef intf = new LibvirtVMDef.InterfaceDef();

        if (nic.getType() == Networks.TrafficType.Guest) {
            intf.defDirectNet(_libvirtComputingResource.getNetworkDirectDevice(), null, nic.getMac(), getGuestNicModel(guestOsType),
                    _libvirtComputingResource.getNetworkDirectSourceMode());

        } else if (nic.getType() == Networks.TrafficType.Public) {
            intf.defDirectNet(_libvirtComputingResource.getNetworkDirectDevice(), null, nic.getMac(), getGuestNicModel(guestOsType),
                    _libvirtComputingResource.getNetworkDirectSourceMode());
        }

        return intf;
    }

    public void unplug(LibvirtVMDef.InterfaceDef iface) {
        // not needed, libvirt will cleanup
    }

}