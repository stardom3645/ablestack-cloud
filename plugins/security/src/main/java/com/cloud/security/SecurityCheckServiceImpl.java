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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.command.admin.GetSecurityCheckCmd;
import org.apache.cloudstack.api.command.admin.RunSecurityCheckCmd;
import org.apache.cloudstack.api.response.GetSecurityCheckResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.management.ManagementServerHost;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.security.dao.SecurityCheckDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;

public class SecurityCheckServiceImpl extends ManagerBase implements PluggableService, SecurityCheckService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(SecurityCheckServiceImpl.class);

    private static final ConfigKey<Integer> SecurityCheckInterval = new ConfigKey<>("Advanced", Integer.class,
            "security.check.interval", "0",
            "The interval security check background tasks in seconds", false);

    @Inject
    private SecurityCheckDao securityCheckDao;
    @Inject
    private ManagementServerHostDao msHostDao;

    @Override
    public List<GetSecurityCheckResponse> listSecurityChecks(GetSecurityCheckCmd cmd) {
        long mshostId = cmd.getMsHostId();
        List<SecurityCheck> result = new ArrayList<>(securityCheckDao.getSecurityChecks(mshostId));
        List<GetSecurityCheckResponse> responses = new ArrayList<>(result.size());
        for (SecurityCheck scResult : result) {
            GetSecurityCheckResponse securityCheckResponse = new GetSecurityCheckResponse();
            securityCheckResponse.setObjectName("securitychecks");
            securityCheckResponse.setCheckName(scResult.getCheckName());
            securityCheckResponse.setResult(scResult.getCheckResult());
            securityCheckResponse.setLastUpdated(scResult.getLastUpdateTime());
            securityCheckResponse.setDetails(scResult.getParsedCheckDetails());
            responses.add(securityCheckResponse);
        }
        return responses;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_SECURITY_CHECK, eventDescription = "running security check on management server", async = true)
    public boolean runSecurityCheckCommand(final RunSecurityCheckCmd cmd) {
        final Long mshostId = cmd.getMsHostId();
        ManagementServerHost mshost = msHostDao.findById(mshostId);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("plugins/security/scripts/securitycheck.sh");
        Process process = null;
        try {
            process = processBuilder.start();
            StringBuffer output = new StringBuffer();
            BufferedReader bfr = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bfr.readLine()) != null) {
                String[] temp = line.split(",");
                for (int i=0; i<temp.length; i++) {
                    String checkName = temp[0];
                    String checkResult = temp[1];
                    String checkMessage;
                    if ("false".equals(checkResult)) {
                        checkMessage = "service down at last check";
                    } else {
                        checkMessage = "service is running";
                    }
                    updateSecurityCheckResult(mshost.getId(), checkName, Boolean.parseBoolean(checkResult), checkMessage);
                }
                output.append(line).append('\n');
            }
            if (output.toString().contains("false")) {
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            throw new CloudRuntimeException("Failed to execute security check command for management server: "+mshost.getId() +e);
        }
    }

    private void updateSecurityCheckResult(final long msHostId, String checkName, boolean checkResult, String checkMessage) {
        boolean newSecurityCheckEntry = false;
        SecurityCheckVO connectivityVO = securityCheckDao.getSecurityCheckResult(msHostId, checkName);
        if (connectivityVO == null) {
            connectivityVO = new SecurityCheckVO(msHostId, checkName);
            newSecurityCheckEntry = true;
        }
        connectivityVO.setCheckResult(checkResult);
        connectivityVO.setLastUpdateTime(new Date());
        if (StringUtils.isNotEmpty(checkMessage)) {
            connectivityVO.setCheckDetails(checkMessage.getBytes(com.cloud.utils.StringUtils.getPreferredCharset()));
        }
        if (newSecurityCheckEntry) {
            securityCheckDao.persist(connectivityVO);
        } else {
            securityCheckDao.update(connectivityVO.getId(), connectivityVO);
        }
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(RunSecurityCheckCmd.class);
        cmdList.add(GetSecurityCheckCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return SecurityCheckServiceImpl.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{
                SecurityCheckInterval
        };
    }
}
