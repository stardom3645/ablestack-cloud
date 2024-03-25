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

package com.cloud.dr;


import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.admin.GetDisasterRecoveryClusterListCmd;
import org.apache.cloudstack.api.command.admin.RunDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.UpdateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.response.GetDisasterRecoveryClusterListResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;


public interface DisasterRecoveryClusterService extends PluggableService, Configurable {

    static final ConfigKey<Boolean> DisasterRecoveryFeatureEnabled = new ConfigKey<Boolean>("Advanced", Boolean.class,
            "disaster.recovery.features.enabled",
            "false",
            "Indicates whether Disaster Recovery Feature is enabled or not. Management server restart needed on change",
            false);

    DisasterRecoveryCluster findById(final Long id);


    ListResponse<GetDisasterRecoveryClusterListResponse> listDisasterRecoveryClusterResponse(GetDisasterRecoveryClusterListCmd cmd);

    GetDisasterRecoveryClusterListResponse setDisasterRecoveryClusterListResultResponse(long id);

    GetDisasterRecoveryClusterListResponse updateDisasterRecoveryCluster(UpdateDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException;

    boolean runDisasterRecoveryCommand(RunDisasterRecoveryClusterCmd runDisasterRecoveryClusterCmd);
}
