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

package com.cloud.security;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.cloud.utils.StringUtils;

@Entity
@Table(name = "security_check")
public class SecurityCheckVO implements SecurityCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private long id;

    @Column(name = "mshost_id", updatable = false, nullable = false)
    private long msHostId;

    @Column(name = "check_name", updatable = false, nullable = false)
    private String checkName;

    @Column(name = "check_result")
    private boolean checkResult;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_update", updatable = true, nullable = true)
    private Date lastUpdateTime;

    @Column(name = "check_details", updatable = true, nullable = true)
    private byte[] checkDetails;

    public long getId() {
        return id;
    }

    @Override
    public long getMsHostId() {
        return msHostId;
    }

    @Override
    public String getCheckName() {
        return checkName;
    }

    @Override
    public boolean getCheckResult() {
        return checkResult;
    }

    @Override
    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public String getParsedCheckDetails() {
        return checkDetails != null ? new String(checkDetails, StringUtils.getPreferredCharset()) : "";
    }

    public byte[] getCheckDetails() {
        return checkDetails;
    }

    public void setCheckResult(boolean checkResult) {
        this.checkResult = checkResult;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setCheckDetails(byte[] checkDetails) {
        this.checkDetails = checkDetails;
    }

    protected SecurityCheckVO() {
    }

    public SecurityCheckVO(long mshostId) {
        this.msHostId = mshostId;
    }

    @Override
    public String toString() {
        return super.toString() +
                "- check name: " + checkName +
                ", check result: " + checkResult +
                ", check last update: " + lastUpdateTime +
                ", details: " + getParsedCheckDetails();
    }
}
