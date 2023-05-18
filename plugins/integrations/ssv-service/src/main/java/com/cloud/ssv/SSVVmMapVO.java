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
package com.cloud.ssv;

import javax.persistence.Column;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;

@Entity
@Table(name = "ssv_vm_map")
public class SSVVmMapVO implements SSVVmMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    long id;

    @Column(name = "ssv_service_id")
    long ssvServiceId;

    @Column(name = "vm_id")
    private long vmId;


    public SSVVmMapVO() {
    }

    public SSVVmMapVO(long ssvServiceId, long vmId) {
        this.ssvServiceId = ssvServiceId;
        this.vmId = vmId;
    }

    @Override
    public long getId() {
        return id;
    }

    public long getSsvServiceId() {
        return ssvServiceId;
    }

    public void setSsvServiceId(long ssvServiceId) {
        this.ssvServiceId = ssvServiceId;
    }

    @Override
    public long getVmId() {
        return vmId;
    }

    public void setVmId(long vmId) {
        this.vmId = vmId;
    }

}