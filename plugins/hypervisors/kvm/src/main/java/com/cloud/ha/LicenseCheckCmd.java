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

package com.cloud.ha;

import com.cloud.exception.ResourceUnavailableException;
import com.cloud.response.LicenseCheckerResponse;
import com.cloud.hypervisor.kvm.resource.LicenseCheck;
import com.cloud.hypervisor.kvm.resource.LicenseCheckService;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;
import java.util.Date;
import javax.inject.Inject;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.HostResponse;



@APICommand(name = "licenseCheck",
        description = "integrity verification results",
        responseObject = LicenseCheckerResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {LicenseCheck.class},
        authorized = {RoleType.Admin})
public class LicenseCheckCmd extends BaseCmd {
    public static final String APINAME = "licenseCheck";

    @Inject
    private LicenseCheckService licenseService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

   @Parameter(name = ApiConstants.HOST_ID, type = CommandType.UUID, entityType = HostResponse.class,
            description = "ID of the host", required = true, validations = {ApiArgValidator.PositiveNumber})
    private Long hostId;

    @Parameter(name = ApiConstants.EXPIRY_DATE,
              type = CommandType.DATE,
              description = "license expiry date")
    private Date expiryDate;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getHostId() {
        return hostId;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() throws ResourceUnavailableException, ServerApiException {
        try {
            LicenseCheckerResponse response = licenseService.checkLicense(this);
            setResponseObject(response);
        } catch (CloudRuntimeException ex){
            ex.printStackTrace();
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to get license information due to: " + ex.getLocalizedMessage());
        }
    }
}
