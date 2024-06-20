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

package org.apache.cloudstack.api.command.admin.dr;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.command.admin.AdminCmd;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.context.CallContext;

import com.cloud.dr.cluster.DisasterRecoveryClusterService;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = CreateDisasterRecoveryClusterVmCmd.APINAME,
        description = "Create Disaster Recovery Cluster Virtual Machine",
        responseObject = SuccessResponse.class,
        authorized = {RoleType.Admin})
public class CreateDisasterRecoveryClusterVmCmd extends BaseCmd implements AdminCmd {
    public static final String APINAME = "createDisasterRecoveryClusterVm";

    @Inject
    private DisasterRecoveryClusterService disasterRecoveryClusterService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.DR_CLUSTER_ID,
                type = CommandType.UUID,
                required = true,
                description = "disaster recovery cluster id")
    private Long drClusterId;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID,
                type = CommandType.UUID,
                required = true,
                description = "the virtual machine ID used by disaster recovery cluster")
    private Long vmId;

    @Parameter(name = ApiConstants.SERVICE_OFFERING_ID,
                type = CommandType.UUID,
                required = true,
                description = "the service offering ID used by disaster recovery cluster")
    private Long serviceOfferingId;

    @Parameter(name = ApiConstants.NETWORK_ID,
                type = CommandType.UUID,
                required = true,
                description = "the network ID used by disaster recovery cluster")
    private Long networkId;


    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public Long getDrClusterId() {
        return drClusterId;
    }

    public Long getVmId() {
        return vmId;
    }

    public Long getServiceOfferingId() {
        return serviceOfferingId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws CloudRuntimeException {
        try {
            if (!disasterRecoveryClusterService.setupDisasterRecoveryClusterVm(this)) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to create disaster recovery cluster vm ID: %d", getVmId()));
            }
            SuccessResponse response = new SuccessResponse(getCommandName());
            setResponseObject(response);
        } catch (CloudRuntimeException ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }
}