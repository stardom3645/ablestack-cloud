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
package org.apache.cloudstack.api.command.admin.outofbandmanagement;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;

import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;

import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ListHostDevicesResponse;
import org.apache.cloudstack.context.CallContext;

@APICommand(name = "listHostDevices", description = "list Host Devices'.", since = "4.20.0.0", responseObject = ListHostDevicesResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, authorized = {
        RoleType.Admin })
public class ListHostDevicesCmd extends BaseListCmd {

    private static final String LISTHOSTDEVICES = "listhostdevices";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = BaseCmd.CommandType.UUID, entityType = ListHostDevicesResponse.class, description = "host ID", required = true, validations = {
            ApiArgValidator.PositiveNumber })
    private Long id;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public static String getResultObjectName() {
        return "listhostdevices";
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public void execute() {
        ListResponse<ListHostDevicesResponse> response = _mgr.listHostDevices(this);
        response.setResponseName(getCommandName());
        response.setObjectName(getCommandName());
        this.setResponseObject(response);
    }
}
