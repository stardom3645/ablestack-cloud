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

package org.apache.cloudstack.api.response.dr.cluster;

import com.cloud.dr.cluster.DisasterRecoveryClusterVmMap;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

@SuppressWarnings("unused")
@EntityReference(value = {DisasterRecoveryClusterVmMap.class})
public class GetDisasterRecoveryClusterVmListResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the disaster recovery cluster vm map")
    private String id;

    @SerializedName(ApiConstants.DR_CLUSTER_ID)
    @Param(description = "the id of the disaster recovery cluster")
    private String drClusterId;

    @SerializedName(ApiConstants.DR_CLUSTER_VM_ID)
    @Param(description = "the id of the disaster recovery cluster vm")
    private String drClusterVmId;

    @SerializedName(ApiConstants.DR_CLUSTER_MIRROR_VM_ID)
    @Param(description = "the id of the disaster recovery cluster mirror vm")
    private String mirroredVmId;

    @SerializedName(ApiConstants.DR_CLUSTER_MIRROR_VM_NAME)
    @Param(description = "the name of the disaster recovery cluster mirror vm")
    private String mirroredVmName;

    @SerializedName(ApiConstants.DR_CLUSTER_MIRROR_VM_STATUS)
    @Param(description = "the status of the disaster recovery cluster mirror vm")
    private String mirroredVmStatus;

    @SerializedName(ApiConstants.DR_CLUSTER_MIRROR_VM_VOL_STATUS)
    @Param(description = "the status of the disaster recovery cluster mirror vm volume")
    private String mirroredVmVolumeStatus;

    public String getId() {
        return id;
    }

    public String getDrClusterId() {
        return drClusterId;
    }

    public String getDrClusterVmId() {
        return drClusterVmId;
    }

    public String getMirroredVmId() {
        return mirroredVmId;
    }

    public String getMirroredVmName() {
        return mirroredVmName;
    }

    public String getMirroredVmStatus() {
        return mirroredVmStatus;
    }

    public String getMirroredVmVolumeStatus() {
        return mirroredVmVolumeStatus;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDrClusterId(String drClusterId) {
        this.drClusterId = drClusterId;
    }

    public void setDrClusterVmId(String drClusterVmId) {
        this.drClusterVmId = drClusterVmId;
    }

    public void setMirroredVmId(String mirroredVmId) {
        this.mirroredVmId = mirroredVmId;
    }

    public void setMirroredVmName(String mirroredVmName) {
        this.mirroredVmName = mirroredVmName;
    }

    public void setMirroredVmStatus(String mirroredVmStatus) {
        this.mirroredVmStatus = mirroredVmStatus;
    }

    public void setMirroredVmVolumeStatus(String mirroredVmVolumeStatus) {
        this.mirroredVmVolumeStatus = mirroredVmVolumeStatus;
    }

}