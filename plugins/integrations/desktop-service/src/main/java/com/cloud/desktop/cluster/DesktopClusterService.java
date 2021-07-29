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
package com.cloud.desktop.cluster;

import org.apache.cloudstack.api.command.user.desktop.cluster.AddDesktopClusterIpRangeCmd;
import org.apache.cloudstack.api.command.user.desktop.cluster.ListDesktopClusterCmd;
import org.apache.cloudstack.api.command.user.desktop.cluster.ListDesktopClusterIpRangeCmd;
import org.apache.cloudstack.api.response.DesktopClusterResponse;
import org.apache.cloudstack.api.response.DesktopClusterIpRangeResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import com.cloud.utils.component.PluggableService;

public interface DesktopClusterService extends PluggableService, Configurable {

    static final ConfigKey<Boolean> DesktopServiceEnabled = new ConfigKey<Boolean>("Advanced", Boolean.class,
            "cloud.desktop.service.enabled",
            "false",
            "Indicates whether Desktop Service plugin is enabled or not. Management server restart needed on change",
            false);

    DesktopCluster findById(final Long id);

    ListResponse<DesktopClusterResponse> listDesktopCluster(ListDesktopClusterCmd cmd);
    ListResponse<DesktopClusterIpRangeResponse> listDesktopClusterIpRanges(ListDesktopClusterIpRangeCmd cmd);
    DesktopClusterIpRange addDesktopClusterIpRange(AddDesktopClusterIpRangeCmd cmd);

    DesktopClusterResponse createDesktopClusterResponse(long desktopClusterId);
    DesktopClusterIpRangeResponse createDesktopClusterIpRangeResponse(long ipRangeId);
}
