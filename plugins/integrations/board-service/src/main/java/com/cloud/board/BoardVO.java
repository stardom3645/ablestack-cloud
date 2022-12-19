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

package com.cloud.board;

import java.sql.Blob;
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
@Table(name = "board")
public class BoardVO implements Board {
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

    @Column(name = "type")
    private String type;

    @Column(name = "content")
    private String content;

    @Column(name = "hit")
    private long hit;

    @Column(name = "file", length = 65535)
    private String file;

    @Column(name = GenericDao.CREATED_COLUMN)
    Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    Date removed;

    public BoardVO() {
        this.uuid = UUID.randomUUID().toString();
    }

    public BoardVO(String title, long accountId, long domainId, String type, String content, String file) {
        this.uuid = UUID.randomUUID().toString();
        this.title = title;
        this.accountId = accountId;
        this.domainId = domainId;
        this.type = type;
        this.content = content;
        this.file = file;
        this.hit = 0;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getHit() {
        return hit;
    }

    public void setHit(long hit) {
        this.hit = hit;
    }

    public Blob getFile() {
        return file;
    }

    public void setFile(Blob file) {
        this.file = file;
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

}
