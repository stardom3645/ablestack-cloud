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

import com.cloud.board.Board;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
@EntityReference(value = { Board.class })
public class BoardResponse extends BaseResponse {
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

    @SerializedName(ApiConstants.TYPE)
    @Param(description = "Resource Request TYPE")
    private String type;

    @SerializedName(ApiConstants.CONTENT)
    @Param(description = "Resource Request ITEM")
    private String content;

    @SerializedName(ApiConstants.HIT)
    @Param(description = "Resource Request ITEM")
    private long hit;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContents() {
        return content;
    }

    public void setContents(String content) {
        this.content = content;
    }

    public long getHit() {
        return hit;
    }

    public void setHit(long hit) {
        this.hit = hit;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }


}
