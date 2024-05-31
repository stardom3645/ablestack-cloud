// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
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

package com.cloud.dr.cluster;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import java.util.Date;

public interface DisasterRecoveryCluster extends InternalIdentity, Identity {

    public enum DrClusterStatus {
        Disabled, Enabled, Created, Error
    }

    public enum MirroringAgentStatus {
        Disabled, Enabled, Created, Error
    }

    long getId();
    String getUuid();
    long getMsHostId();
    String getName();
    String getDescription();
    String getDrClusterUrl();
    String getDrClusterType();
    String getDrClusterStatus();
    String getMirroringAgentStatus();
    String getDrClusterApiKey();
    String getDrClusterSecretKey();
    Date getCreated();
    Date getRemoved();

}
