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
package com.cloud.dr.cluster;

import java.util.List;
import java.util.ArrayList;

import javax.inject.Inject;

import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ScvmIpAddressResponse;
import org.apache.cloudstack.api.command.admin.dr.ConnectivityTestsDisasterRecoveryCmd;
import org.apache.cloudstack.api.command.admin.glue.ListScvmIpAddressCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.event.ActionEvent;
import com.cloud.utils.script.Script;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;

public class DisasterRecoveryServiceImpl extends ManagerBase implements DisasterRecoveryService {

    @Inject
    private ManagementServerHostDao msHostDao;

    protected static Logger LOGGER = LogManager.getLogger(DisasterRecoveryServiceImpl.class);

    @Override
    @ActionEvent(eventType = DisasterRecoveryEventTypes.EVENT_DR_TEST_CONNECT, eventDescription = "disaster recovery cluster connection testing")
    public boolean connectivityTestsDisasterRecovery(final ConnectivityTestsDisasterRecoveryCmd cmd) {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String moldProtocol = cmd.getProtocol();
        String moldIp = cmd.getIpAddress();
        String moldPort = cmd.getPort();
        String apiKey = cmd.getApiKey();
        String secretKey = cmd.getSecretKey();

        String moldUrl = moldProtocol + "://" + moldIp + ":" + moldPort + "/client/api/";
        String moldCommand = "listScvmIpAddress";
        String moldMethod = "GET";

        String response = DisasterRecoveryUtil.moldListScvmIpAddressAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        if (response != null || response != "") {
            String[] array = response.split(",");
            for(int i=0; i < array.length; i++) {
                String glueIp = array[i];
                String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueCommand = "/glue";
                String glueMethod = "GET";
                String glueStatus = DisasterRecoveryUtil.glueStatusAPI(glueUrl, glueCommand, glueMethod);
                if (glueStatus != null) {
                    if (glueStatus.contains("HEALTH_OK")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public ListResponse<ScvmIpAddressResponse> listScvmIpAddress(ListScvmIpAddressCmd cmd) {
        List<ScvmIpAddressResponse> responses = new ArrayList<>();
        ScvmIpAddressResponse response = new ScvmIpAddressResponse();
        ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        ipList = ipList.replaceAll(",$", "");
        response.setObjectName("scvmipaddress");
        response.setIpAddress(ipList);
        responses.add(response);
        ListResponse<ScvmIpAddressResponse> listResponse = new ListResponse<>();
        listResponse.setResponses(responses);
        return listResponse;

    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DisasterRecoveryServiceEnabled.value()) {
            return cmdList;
        }
        cmdList.add(ListScvmIpAddressCmd.class);
        cmdList.add(ConnectivityTestsDisasterRecoveryCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return DisasterRecoveryService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                DisasterRecoveryServiceEnabled
        };
    }
}
