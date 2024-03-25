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

import com.cloud.dr.cluster.DisasterRecoveryCluster;
import com.cloud.dr.cluster.DisasterRecoveryClusterService;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.AdminCmd;
import org.apache.cloudstack.api.response.dr.cluster.GetDisasterRecoveryClusterListResponse;

import javax.inject.Inject;

@APICommand(name = UpdateDisasterRecoveryClusterCmd.APINAME,
        description = "Update a disaster recovery",
        responseObject = GetDisasterRecoveryClusterListResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {DisasterRecoveryCluster.class},
        authorized = {RoleType.Admin})
public class UpdateDisasterRecoveryClusterCmd extends BaseCmd implements AdminCmd {
    public static final String APINAME = "updateDisasterRecoveryCluster";

    @Inject
    private DisasterRecoveryClusterService disasterRecoveryClusterService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID,
            entityType = GetDisasterRecoveryClusterListResponse.class,
            description = "the ID of the disaster recovery",
            required = true)
    private Long id;

    @Parameter(name = ApiConstants.DR_CLUSTER_STATUS, type = CommandType.STRING,
            description = "the enabled or disabled dr cluster state of the disaster recovery",
            required = true)
    private String drClusterStatus;

    @Parameter(name = ApiConstants.MIRRORING_AGENT_STATUS, type = CommandType.STRING,
            description = "the enabled or disabled mirroring agent state of the disaster recovery",
            required = true)
    private String mirroringAgentStatus;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public Long getId() {
        return id;
    }

    public String getDrClusterStatus() {
        return drClusterStatus;
    }

    public String getMirroringAgentStatus() {
        return mirroringAgentStatus;
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public void execute() throws ServerApiException, ConcurrentOperationException {
        try {
            GetDisasterRecoveryClusterListResponse response = disasterRecoveryClusterService.updateDisasterRecoveryCluster(this);
            if (response == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update disaster recovery");
            }
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (CloudRuntimeException ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }
}