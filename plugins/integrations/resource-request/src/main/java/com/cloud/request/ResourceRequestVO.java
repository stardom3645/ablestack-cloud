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

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "resource_request_status")
public class ResourceRequestVO implements ResourceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "title")
    private String title;

    @Column(name = "account_id")
    private long accountId;

    @Column(name = "domain_id")
    private long domainId;

    @Column(name = "zone_id")
    private long zoneId;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "item")
    private String item;

    @Column(name = "quantity")
    private String quantity;

    @Column(name = "domain_approver")
    private long domainApprover;

    @Column(name = "admin_approver")
    private long adminApprover;

    @Column(name = "state")
    private String state;

    @Column(name = "cpu")
    private String cpu;

    @Column(name = "memory")
    private String memory;

    @Column(name = "network")
    private String network;

    @Column(name = "volume")
    private String volume;

    @Column(name = "comment")
    private String comment;

    @Column(name = GenericDao.CREATED_COLUMN)
    Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    Date removed;

    public ResourceRequestVO() {
        this.uuid = UUID.randomUUID().toString();
    }

    public ResourceRequestVO(String title, long accountId, long domainId, String purpose, String item, String quantity, String state, String cpu, String memory, String network, String volume) {
        this.uuid = UUID.randomUUID().toString();
        this.title = title;
        this.accountId = accountId;
        this.domainId = domainId;
        this.purpose = purpose;
        this.item = item;
        this.quantity = quantity;
        this.state = state;
        this.cpu = cpu;
        this.memory = memory;
        this.network = network;
        this.volume = volume;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public long getDomainId() {
        return domainId;
    }

    public void setDomainId(long domainId) {
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

    public long getDomainApprover() {
        return domainApprover;
    }

    public void setDomainApprover(long domainApprover) {
        this.domainApprover = domainApprover;
    }

    public long getAdminApprover() {
        return adminApprover;
    }

    public void setAdminApprover(long adminApprover) {
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

    public Date getRemoved() {
        return removed;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }

    public long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

}
