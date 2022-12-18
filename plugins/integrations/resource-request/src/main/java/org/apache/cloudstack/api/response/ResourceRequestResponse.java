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

package org.apache.cloudstack.api.response;

import java.util.Date;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.request.ResourceRequest;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
@EntityReference(value = { ResourceRequest.class })
public class ResourceRequestResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the Resource Request")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "Name of the Resource Request")
    private String title;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account of the Resource Request")
    private String accountName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain in which the Resource Request")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the name of the domain in which the Resource Request")
    private String domainName;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the name of the zone of the Resource Request")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the zone of the Resource Request")
    private String zoneName;

    @SerializedName(ApiConstants.PURPOSE)
    @Param(description = "Resource Request PURPOSE")
    private String purpose;

    @SerializedName(ApiConstants.ITEM)
    @Param(description = "Resource Request ITEM")
    private String item;

    @SerializedName(ApiConstants.QUANTITY)
    @Param(description = "Resource Request QUANTITY")
    private String quantity;

    @SerializedName(ApiConstants.DOMAIN_APPROVER)
    @Param(description = "Resource Request DOMAIN_APPROVER")
    private String domainApprover;

    @SerializedName(ApiConstants.ADMIN_APPROVER)
    @Param(description = "Resource Request ADMIN_APPROVER")
    private String adminApprover;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "Resource Request STATE")
    private String state;

    @SerializedName(ApiConstants.CPU_NUMBER)
    @Param(description = "Resource Request CPU_NUMBER")
    private String cpu;

    @SerializedName(ApiConstants.MEMORY)
    @Param(description = "Resource Request MEMORY")
    private String memory;

    @SerializedName(ApiConstants.NETWORK)
    @Param(description = "Resource Request NETWORK")
    private String network;

    @SerializedName(ApiConstants.NETWORK_IDS)
    @Param(description = "Resource Request Delete NETWORK")
    private String networkIds;

    @SerializedName(ApiConstants.ROOT_DISK_SIZE)
    @Param(description = "Resource Request ROOT_DISK_SIZE")
    private String volume;

    @SerializedName(ApiConstants.VOLUME_IDS)
    @Param(description = "Resource Request delete volume id")
    private String volumeIds;

    @SerializedName(ApiConstants.COMMENT)
    @Param(description = "Resource Request COMMENT")
    private String comment;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "Resource Request CREATED")
    private Date created;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getDomainApprover() {
        return domainApprover;
    }

    public void setDomainApprover(String domainApprover) {
        this.domainApprover = domainApprover;
    }

    public String getAdminApprover() {
        return adminApprover;
    }

    public void setAdminApprover(String adminApprover) {
        this.adminApprover = adminApprover;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCpu() {
        return cpu;
    }

    public void setCpu(String cpu) {
        this.cpu = cpu;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getNetworkIds() {
        return networkIds;
    }

    public void setNetworkIds(String networkIds) {
        this.networkIds = networkIds;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getVolumeIds() {
        return volumeIds;
    }

    public void setVolumeIds(String volumeIds) {
        this.volumeIds = volumeIds;
    }

}
