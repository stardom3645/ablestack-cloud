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

import com.cloud.dr.cluster.DisasterRecoveryClusterService;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.user.Account;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.api.response.dr.cluster.GetDisasterRecoveryClusterListResponse;

import javax.inject.Inject;

@APICommand(name = GetSecDisasterRecoveryClusterInfoListCmd.APINAME,
        description = "list disastser recovery cluster",
        responseObject = ListResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        authorized = {RoleType.Admin})
public class GetSecDisasterRecoveryClusterInfoListCmd extends BaseListCmd {
    public static final String APINAME = "getSecDisasterRecoveryClusterInfoList";

    @Inject
    private DisasterRecoveryClusterService disasterRecoveryClusterService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID,
            entityType = GetDisasterRecoveryClusterListResponse.class,
            description = "the ID of the disaster recovery cluster")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "name of disaster recovery cluster")
    private String name;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseListCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////


    @Override
    public void execute() throws ConcurrentOperationException, ServerApiException {
        ListResponse<ServiceOfferingResponse> response = disasterRecoveryClusterService.listSecDisasterRecoveryClusterInfoResponse(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}