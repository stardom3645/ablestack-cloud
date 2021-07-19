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

import java.util.List;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.desktop.vm.Desktop;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
@EntityReference(value = {Desktop.class})
public class DesktopResponse extends BaseResponse implements ControlledEntityResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the Desktop")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the name of the Desktop")
    private String name;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the name of the zone of the Desktop")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the zone of the Desktop")
    private String zoneName;

    @SerializedName(ApiConstants.SERVICE_OFFERING_ID)
    @Param(description = "the ID of the service offering of the Desktop")
    private String serviceOfferingId;

    @SerializedName("serviceofferingname")
    @Param(description = "the name of the service offering of the Desktop")
    private String serviceOfferingName;

    @SerializedName(ApiConstants.AD_DOMAIN_NAME)
    @Param(description = "the ID of the template of the Desktop")
    private String adDomainName;

    @SerializedName(ApiConstants.NETWORK_ID)
    @Param(description = "the ID of the network of the Desktop")
    private String networkId;

    @SerializedName(ApiConstants.ASSOCIATED_NETWORK_NAME)
    @Param(description = "the name of the network of the Desktop")
    private String associatedNetworkName;

    @SerializedName(ApiConstants.DESKTOP_VERSION_ID)
    @Param(description = "the ID of the Desktop version for the Desktop")
    private String desktopVersionId;

    @SerializedName(ApiConstants.DESKTOP_VERSION_NAME)
    @Param(description = "the name of the Desktop version for the Desktop")
    private String desktopVersionName;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account associated with the Desktop")
    private String accountName;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the project id of the Desktop")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the project name of the Desktop")
    private String projectName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain in which the Desktop exists")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the name of the domain in which the Desktop exists")
    private String domainName;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the Desktop")
    private String state;

    @SerializedName(ApiConstants.VIRTUAL_MACHINES)
    @Param(description = "the list of virtualmachine associated with this Desktop")
    private List<UserVmResponse> virtualMachines;

    @SerializedName(ApiConstants.IP_ADDRESS)
    @Param(description = "Public IP Address of the cluster")
    private String ipAddress;

    @SerializedName(ApiConstants.IP_ADDRESS_ID)
    @Param(description = "Public IP Address ID of the cluster")
    private String ipAddressId;

    public DesktopResponse() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getServiceOfferingId() {
        return serviceOfferingId;
    }

    public void setServiceOfferingId(String serviceOfferingId) {
        this.serviceOfferingId = serviceOfferingId;
    }

    public String getAdDomainName() {
        return adDomainName;
    }

    public void setAdDomainName(String adDomainName) {
        this.adDomainName = adDomainName;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public String getAssociatedNetworkName() {
        return associatedNetworkName;
    }

    public void setAssociatedNetworkName(String associatedNetworkName) {
        this.associatedNetworkName = associatedNetworkName;
    }

    public String getDesktopVersionId() {
        return desktopVersionId;
    }

    public void setDesktopVersionId(String desktopVersionId) {
        this.desktopVersionId = desktopVersionId;
    }

    public String getDesktopVersionName() {
        return desktopVersionName;
    }

    public void setDesktopVersionName(String desktopVersionName) {
        this.desktopVersionName = desktopVersionName;
    }

    public String getProjectId() {
        return projectId;
    }

    @Override
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServiceOfferingName() {
        return serviceOfferingName;
    }

    public void setServiceOfferingName(String serviceOfferingName) {
        this.serviceOfferingName = serviceOfferingName;
    }

    public void setVirtualMachines(List<UserVmResponse> virtualMachines) {
        this.virtualMachines = virtualMachines;
    }

    public List<UserVmResponse> getVirtualMachines() {
        return virtualMachines;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setIpAddressId(String ipAddressId) {
        this.ipAddressId = ipAddressId;
    }
}
