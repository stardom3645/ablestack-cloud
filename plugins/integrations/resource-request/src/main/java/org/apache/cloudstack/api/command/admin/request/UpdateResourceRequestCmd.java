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

package org.apache.cloudstack.api.command.admin.request;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.AdminCmd;
import org.apache.cloudstack.api.response.ResourceRequestResponse;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.request.ResourceRequest;
import com.cloud.request.ResourceRequestService;

@APICommand(name = UpdateResourceRequestCmd.APINAME,
        description = "Update a Resource Request",
        responseObject = ResourceRequestResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {ResourceRequest.class},
        authorized = {RoleType.Admin, RoleType.DomainAdmin, RoleType.User})
public class UpdateResourceRequestCmd extends BaseCmd implements AdminCmd {
    public static final Logger LOGGER = Logger.getLogger(UpdateResourceRequestCmd.class.getName());
    public static final String APINAME = "updateResourceRequest";

    @Inject
    private ResourceRequestService resourceRequestService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = BaseCmd.CommandType.UUID,
            entityType = ResourceRequestResponse.class,
            description = "the ID of the Resource Request",
            required = true)
    private Long id;

    @Parameter(name = ApiConstants.TITLE, type = CommandType.STRING,
            description = "the format for the template. Possible values include QCOW2")
    private String title;

    @Parameter(name = ApiConstants.QUANTITY, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String quantity;

    @Parameter(name = ApiConstants.PURPOSE, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String purpose;

    @Parameter(name = ApiConstants.ITEM, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String item;

    @Parameter(name = ApiConstants.CPU_NUMBER, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String cpu;

    @Parameter(name = ApiConstants.MEMORY, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String memory;

    @Parameter(name = ApiConstants.NETWORK, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String network;

    @Parameter(name = ApiConstants.DISK_SIZE, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String volume;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getQuantity() {
        return quantity;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getItem() {
        return item;
    }

    public String getCpu() {
        return cpu;
    }

    public String getMemory() {
        return memory;
    }

    public String getNetwork() {
        return network;
    }

    public String getVolume() {
        return volume;
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
            ResourceRequestResponse response = resourceRequestService.updateResourceRequest(this);
            if (response == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update desktop conroller version");
            }
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (CloudRuntimeException ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }
}