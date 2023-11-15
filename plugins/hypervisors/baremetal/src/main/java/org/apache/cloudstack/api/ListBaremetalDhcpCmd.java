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
//
// Automatically generated by addcopyright.py at 01/29/2013
package org.apache.cloudstack.api;

import com.cloud.baremetal.networkservice.BaremetalDhcpManager;
import com.cloud.baremetal.networkservice.BaremetalDhcpResponse;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;

import javax.inject.Inject;
import java.util.List;

@APICommand(name = "listBaremetalDhcp", description = "list baremetal dhcp servers", responseObject = BaremetalDhcpResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListBaremetalDhcpCmd extends BaseListCmd {
    @Inject
    BaremetalDhcpManager _dhcpMgr;

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.LONG, description = "DHCP server device ID")
    private Long id;

    @Parameter(name = ApiConstants.DHCP_SERVER_TYPE, type = CommandType.STRING, description = "Type of DHCP device")
    private String deviceType;

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID,
            type = CommandType.UUID,
            entityType = PhysicalNetworkResponse.class,
            required = true,
            description = "the Physical Network ID")
    private Long physicalNetworkId;

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public void setPhysicalNetworkId(Long physicalNetworkId) {
        this.physicalNetworkId = physicalNetworkId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
        ResourceAllocationException, NetworkRuleConflictException {
        try {
            ListResponse<BaremetalDhcpResponse> response = new ListResponse<BaremetalDhcpResponse>();
            List<BaremetalDhcpResponse> dhcpResponses = _dhcpMgr.listBaremetalDhcps(this);
            response.setResponses(dhcpResponses);
            response.setResponseName(getCommandName());
            response.setObjectName("baremetaldhcps");
            this.setResponseObject(response);
        } catch (Exception e) {
            logger.debug("Exception happend while executing ListBaremetalDhcpCmd");
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }

    }

    }
