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
package com.cloud.network.dao;

import com.cloud.network.MonitoringService;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "monitoring_services")
public class MonitoringServiceVO implements MonitoringService {

    public MonitoringServiceVO(String service, String processname, String serviceName, String servicePath,
                            String pidFile) {
        this.service = service;
        this.processname = processname;
        this.servicename = serviceName;
        this.servicePath = servicePath;
        this.servicePidFile= pidFile;

    }

    protected MonitoringServiceVO() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    long id;

    @Column(name = "service")
    String service;

    @Column(name="process_name", updatable=false)
    String processname;

    @Column(name="service_name", updatable=false)
    String servicename;

    @Column(name="service_path", updatable=false)
    private String servicePath;

    @Column(name="pidFile", updatable=false)
    private String servicePidFile;

    @Column(name="isDefault")
    private boolean isDefault;


    @Column(name = "uuid")
    String uuid = UUID.randomUUID().toString();

    public long getId() {
        return id;
    }

    @Override
    public String getService() {
        return this.service;
    }

    @Override
    public String getServiceName() {
        return this.servicename;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getPidFile() {
        return this.servicePidFile;

    }

    @Override
    public String getServicePath() {
        return this.servicePidFile;
    }

    @Override
    public String getUuid() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public long getAccountId() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public long getDomainId() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean getDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        isDefault = isDefault;
    }

    public String getProcessname() {
        return processname;
    }
}
