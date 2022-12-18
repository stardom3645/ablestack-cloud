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

package com.cloud.request;

import org.apache.cloudstack.api.command.admin.request.AddResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.DeleteResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.ListResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.UpdateResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.StateUpdateResourceRequestCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ResourceRequestResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;

public interface ResourceRequestService extends PluggableService, Configurable {

    static final ConfigKey<Boolean> ResourceRequestEnabled = new ConfigKey<Boolean>("Advanced", Boolean.class,
    "cloud.resource.request.enabled",
    "false",
    "Indicates whether Resource Request plugin is enabled or not. Management server restart needed on change",
    true);

    ListResponse<ResourceRequestResponse> listResourceRequest(ListResourceRequestCmd cmd);
    ResourceRequestResponse addResourceRequest(AddResourceRequestCmd cmd);
    boolean deleteResourceRequest(DeleteResourceRequestCmd cmd) throws CloudRuntimeException;
    ResourceRequestResponse updateResourceRequest(UpdateResourceRequestCmd cmd) throws CloudRuntimeException;
    ResourceRequestResponse stateUpdateResourceRequest(StateUpdateResourceRequestCmd cmd) throws CloudRuntimeException;
}