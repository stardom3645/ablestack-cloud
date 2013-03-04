package org.apache.cloudstack.api.command.user.vm;

import com.cloud.exception.*;
import com.cloud.user.Account;
import com.cloud.user.UserContext;
import com.cloud.uservm.UserVm;
import org.apache.cloudstack.api.*;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.log4j.Logger;

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
@APICommand(name = "scaleVirtualMachine", description="Scales the virtual machine to a new service offering.", responseObject=UserVmResponse.class)
public class ScaleVMCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ScaleVMCmd.class.getName());
    private static final String s_name = "scalevirtualmachineresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @ACL
    @Parameter(name=ApiConstants.ID, type=CommandType.UUID, entityType=UserVmResponse.class,
            required=true, description="The ID of the virtual machine")
    private Long id;

    @ACL
    @Parameter(name=ApiConstants.SERVICE_OFFERING_ID, type=CommandType.UUID, entityType=ServiceOfferingResponse.class,
            required=true, description="the ID of the service offering for the virtual machine")
    private Long serviceOfferingId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public Long getServiceOfferingId() {
        return serviceOfferingId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    public static String getResultObjectName() {
        return "virtualmachine";
    }

    @Override
    public long getEntityOwnerId() {
        UserVm userVm = _entityMgr.findById(UserVm.class, getId());
        if (userVm != null) {
            return userVm.getAccountId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }

    @Override
    public void execute(){
        UserContext.current().setEventDetails("Vm Id: "+getId());
        UserVm result = null;
        try {
            result = _userVmService.upgradeVirtualMachine(this);
        } catch (ResourceUnavailableException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        } catch (ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        } catch (ManagementServerException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        } catch (VirtualMachineMigrationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
        if (result != null){
            UserVmResponse response = _responseGenerator.createUserVmResponse("virtualmachine", result).get(0);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to upgrade vm");
        }
    }
}