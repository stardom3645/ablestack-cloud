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

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.desktop.version.DesktopSupportedVersion;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
@EntityReference(value = {DesktopSupportedVersion.class})
public class DesktopSupportedVersionResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the Desktop supported version")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "Name of the Desktop supported version")
    private String name;

    @SerializedName(ApiConstants.VERSION)
    @Param(description = "Desktop version")
    private String version;

    @SerializedName(ApiConstants.TEMPLATE_ID)
    @Param(description = "the id of the template for Desktop supported version")
    private String templateId;

    @SerializedName(ApiConstants.TEMPLATE_NAME)
    @Param(description = "the name of the template for Desktop supported version")
    private String templateName;

    @SerializedName(ApiConstants.TEMPLATE_STATE)
    @Param(description = "the state of the template for Desktop supported version")
    private String templateState;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the id of the zone in which Desktop supported version is available")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the zone in which Desktop supported version is available")
    private String zoneName;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the enabled or disabled state of the Desktop supported version")
    private String state;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateState() {
        return templateState;
    }

    public void setTemplateState(String templateState) {
        this.templateState = templateState;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
