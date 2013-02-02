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
package com.cloud.ucs.manager;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.BaseCmd.CommandType;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.server.ManagementService;
import com.cloud.user.Account;

@APICommand(description="Adds a Ucs manager", responseObject=AddUcsManagerResponse.class)
public class AddUcsManagerCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(AddUcsManagerCmd.class);
    
    @Inject
    private UcsManager mgr;
    
    @Parameter(name=ApiConstants.ZONE_ID, type=CommandType.LONG, description="the Zone id for the ucs manager", required=true)
    private Long zoneId;
    
    @Parameter(name=ApiConstants.NAME, type=CommandType.STRING, description="the name of UCS manager")
    private String name;
    
    @Parameter(name=ApiConstants.URL, type=CommandType.STRING, description="the name of UCS url")
    private String url;
    
    @Parameter(name=ApiConstants.USERNAME, type=CommandType.STRING, description="the username of UCS")
    private String username;
    
    @Parameter(name=ApiConstants.PASSWORD, type=CommandType.STRING, description="the password of UCS")
    private String password;
    
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
            ResourceAllocationException, NetworkRuleConflictException {
        try {
            AddUcsManagerResponse rsp = mgr.addUcsManager(this);
            rsp.setObjectName("ucsmanager");
            rsp.setResponseName(getCommandName());
            this.setResponseObject(rsp);
        } catch (Exception e) {
            s_logger.warn("Exception: ", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage()); 
        }
    }

    @Override
    public String getCommandName() {
        return "addUcsManagerResponse";
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
}
