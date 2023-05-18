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
package org.apache.cloudstack.api.command.user.ssv;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
// import org.apache.cloudstack.acl.SecurityChecker.AccessType;
// import org.apache.cloudstack.api.ACL;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DiskOfferingResponse;
// import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.SSVResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
// import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
// import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.ssv.SSV;
import com.cloud.ssv.SSVEventTypes;
import com.cloud.ssv.SSVService;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = CreateSSVCmd.APINAME,
        description = "Creates a Shared Storage VM",
        responseObject = SSVResponse.class,
        responseView = ResponseView.Full,
        entityType = {SSV.class},
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class CreateSSVCmd extends BaseAsyncCreateCmd {
    public static final Logger LOGGER = Logger.getLogger(CreateSSVCmd.class.getName());
    public static final String APINAME = "createSSV";

    @Inject
    public SSVService SSVService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "name for the Shared Storage VM ")
    private String name;

    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING, required = true, description = "description for the Shared Storage VM ")
    private String description;

    // @ACL(accessType = AccessType.UseEntry)
    // @Parameter(name = ApiConstants.SERVICE_OFFERING_ID, type = CommandType.UUID, entityType = ServiceOfferingResponse.class, description = "the ID of the service offering for the virtual machines in the cluster.")
    // private Long serviceOfferingId;

    // @ACL(accessType = AccessType.UseEntry)
    // @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an optional account for the virtual machine. Must be used with domainId.")
    // private String accountName;

    // @ACL(accessType = AccessType.UseEntry)
    // @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class,
    //         description = "an optional domainId for the virtual machine. If the account parameter is used, domainId must also be used.")
    // private Long domainId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "zone id for the Automation Controller ")
    private Long zoneId;

    // @Parameter(name = ApiConstants.TEMPLATE_ID, type = CommandType.UUID, entityType = TemplateResponse.class, description = "the ID of the template for the virtual machine")
    // private Long templateId;

    @Parameter(name = ApiConstants.DISK_OFFERING_ID, type = CommandType.UUID, entityType = DiskOfferingResponse.class, description = "list volumes by disk offering", since = "4.4")
    private Long diskOfferingId;

    @Parameter(name = ApiConstants.SIZE, type = CommandType.LONG, description = "the arbitrary size for the DATADISK volume. Mutually exclusive with diskOfferingId")
    private Long size;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, required = true, description = "Network in which Shared Storage VM  is to be launched")
    private Long networkId;

    @Parameter(name = ApiConstants.SHARED_STORAGE_VM_TYPE, type = CommandType.STRING, required = true, description = "access type for Shared Storage VM ")
    private String ssvType;

    @Parameter(name = ApiConstants.GATEWAY, type = CommandType.STRING, description = "Gateway for L2 Network of Shared Storage VM ")
    private String gateway;

    @Parameter(name = ApiConstants.NETMASK, type = CommandType.STRING, description = "Netmask for L2 Network of Shared Storage VM ")
    private String netmask;

    @Parameter(name = ApiConstants.SHARED_STORAGE_VM_IP, type = CommandType.STRING, description = "DC IP for the desktop controller")
    private String ssvIp;


    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    // public String getAccountName() {
    //     if (accountName == null) {
    //         return CallContext.current().getCallingAccount().getAccountName();
    //     }
    //     return accountName;
    // }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    // public Long getDomainId() {
    //     if (domainId == null) {
    //         return CallContext.current().getCallingAccount().getDomainId();
    //     }
    //     return domainId;
    // }

    public Long getZoneId() {
        return zoneId;
    }

    // public Long getTemplateId() {
    //     return templateId;
    // }

    public Long getDiskOfferingId() {
        return diskOfferingId;
    }

    public Long getSize() {
        return size;
    }

    // public Long getServiceOfferingId() {
    //     return serviceOfferingId;
    // }

    public Long getNetworkId() {
        return networkId;
    }

    public String getSsvType() {
        return ssvType;
    }

    public String getGateway() {
        return gateway;
    }

    public String getNetmask() {
        return netmask;
    }

    public String getSsvIp() {
        return ssvIp;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }

    public static String getResultObjectName() {
        return "ssv";
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public String getEventType() {
        return SSVEventTypes.EVENT_SSV_CREATE;
    }

    @Override
    public String getCreateEventType() {
        return SSVEventTypes.EVENT_SSV_CREATE;
    }

    @Override
    public String getCreateEventDescription() {
        return "creating Shared Storage VM";
    }

    @Override
    public String getEventDescription() {
        return "Creating Shared Storage VM. Id: " + getEntityId();
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.VirtualMachine;
    }

    @Override
    public void execute() {
        try {
            if (!SSVService.startSSV(this, getEntityId(), true)) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to start Shared Storage VM");
            }
            SSVResponse response = SSVService.createSSVResponse(getEntityId());
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (CloudRuntimeException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void create() throws CloudRuntimeException {
        try {
            SSV app = SSVService.createSSV(this);
            if (app == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create Shared Storage VM");
            }
            setEntityId(app.getId());
            setEntityUuid(app.getUuid());
        } catch (CloudRuntimeException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}
