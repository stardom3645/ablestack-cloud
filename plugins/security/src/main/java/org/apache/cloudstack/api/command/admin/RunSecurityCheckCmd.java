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

package org.apache.cloudstack.api.command.admin;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ManagementServerResponse;
import org.apache.cloudstack.api.response.RunSecurityCheckResponse;
import org.apache.log4j.Logger;

import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.utils.Pair;
import com.cloud.security.SecurityCheck;
import com.cloud.security.SecurityCheckService;

@APICommand(name = RunSecurityCheckCmd.APINAME,
        description = "Execute security check command on management server",
        responseObject = RunSecurityCheckResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {SecurityCheck.class},
        authorized = {RoleType.Admin})
public class RunSecurityCheckCmd extends BaseCmd {
    public static final Logger LOG = Logger.getLogger(RunSecurityCheckCmd.class);
    public static final String APINAME = "runSecurityCheck";

    @Inject
    private SecurityCheckService securityService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.MANAGEMENT_SERVER_ID, type = CommandType.UUID, entityType = ManagementServerResponse.class,
            required = true, description = "the ID of the mshost")
    private Long msHostId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getMsHostId() {
        return msHostId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() throws ResourceUnavailableException, ServerApiException {
        try {
            Pair<Boolean, String> result = securityService.runSecurityCheckCommand(this);
            RunSecurityCheckResponse response = new RunSecurityCheckResponse();
            Boolean success = result.first();
            String details = result.second();
            response.setSuccess(success);
            response.setDetails(details);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } catch (final ServerApiException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}
