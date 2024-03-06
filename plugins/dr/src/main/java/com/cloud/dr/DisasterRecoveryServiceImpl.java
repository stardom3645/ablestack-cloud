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

package com.cloud.dr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.command.admin.GetDisasterRecoveryCmd;
import org.apache.cloudstack.api.command.admin.RunDisasterRecoveryCmd;
import org.apache.cloudstack.api.response.GetDisasterRecoveryResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.management.ManagementServerHost;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.commons.lang3.StringUtils;

import com.cloud.alert.AlertManager;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventTypes;
import com.cloud.event.EventVO;
import com.cloud.security.dao.DisasterRecoveryDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;

public class DisasterRecoveryServiceImpl extends ManagerBase implements PluggableService, DisasterRecoveryService, Configurable {

    private static final ConfigKey<Integer> DisasterRecoveryInterval = new ConfigKey<>("Advanced", Integer.class,
            "security.check.interval", "1",
            "The interval disaster recovery background tasks in days", false);
    private static String runMode = "";

    @Inject
    private DisasterRecoveryDao securityCheckDao;
    @Inject
    private ManagementServerHostDao msHostDao;
    @Inject
    private AlertManager alertManager;
    ScheduledExecutorService executor;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("DisasterRecoveryer"));
        return true;
    }

    @Override
    public boolean start() {
        runMode = "first";
        if(DisasterRecoveryInterval.value() != 0) {
            executor.scheduleAtFixedRate(new DisasterRecoveryTask(), 0, DisasterRecoveryInterval.value(), TimeUnit.DAYS);
        }
        return true;
    }

    @Override
    public boolean stop() {
        runMode = "";
        return true;
    }

    protected class DisasterRecoveryTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                securityCheck();
            } catch (Exception e) {
                logger.error("Exception in disaster recovery schedule : "+ e);
            }
        }

        private void securityCheck() {
            if (runMode == "first") {
                ActionEventUtils.onStartedActionEvent(CallContext.current().getCallingUserId(), CallContext.current().getCallingAccountId(), EventTypes.EVENT_SECURITY_CHECK,
                    "disaster recovery perform on the management server when running the product", new Long(0), null, true, 0);
            } else {
                ActionEventUtils.onStartedActionEvent(CallContext.current().getCallingUserId(), CallContext.current().getCallingAccountId(), EventTypes.EVENT_SECURITY_CHECK,
                    "disaster recovery schedule perform on the management server when operating the product", new Long(0), null, true, 0);
            }
            ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
            String path = Script.findScript("scripts/security/", "securitycheck.sh");
            if (path == null) {
                logger.error("Unable to find the securitycheck script");
            }
            ProcessBuilder processBuilder = new ProcessBuilder("sh", path);
            Process process = null;
            try {
                process = processBuilder.start();
                BufferedReader bfr = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = bfr.readLine()) != null) {
                    String[] temp = line.split(",");
                    String checkName = temp[0];
                    String checkResult = temp[1];
                    String checkMessage;
                    if ("false".equals(checkResult)) {
                        checkMessage = "process does not operate normally at last check";
                        if (runMode == "first") {
                            alertManager.sendAlert(AlertManager.AlertType.ALERT_TYPE_MANAGEMENT_NODE, 0, new Long(0), "Management server node " + msHost.getServiceIP() + " disaster recovery when running the product failed : "+ checkName + " " + checkMessage, "");
                        } else {
                            alertManager.sendAlert(AlertManager.AlertType.ALERT_TYPE_MANAGEMENT_NODE, 0, new Long(0), "Management server node " + msHost.getServiceIP() + " disaster recovery schedule failed : "+ checkName + " " + checkMessage, "");
                        }
                    } else {
                        checkMessage = "process operates normally";
                    }
                    updateDisasterRecoveryResult(msHost.getId(), checkName, Boolean.parseBoolean(checkResult), checkMessage);
                }
                if (runMode == "first") {
                    ActionEventUtils.onCompletedActionEvent(CallContext.current().getCallingUserId(), CallContext.current().getCallingAccountId(), EventVO.LEVEL_INFO,
                        EventTypes.EVENT_SECURITY_CHECK, "Successfully completed disaster recovery perform on the management server when running the product", new Long(0), null, 0);
                } else {
                    ActionEventUtils.onCompletedActionEvent(CallContext.current().getCallingUserId(), CallContext.current().getCallingAccountId(), EventVO.LEVEL_INFO,
                        EventTypes.EVENT_SECURITY_CHECK, "Successfully completed disaster recovery schedule perform on the management server when operating the product", new Long(0), null, 0);
                }
                runMode = "";
            } catch (IOException e) {
                runMode = "";
                logger.error("Failed to execute disaster recovery schedule for management server: "+e);
            }
        }
    }

    @Override
    public List<GetDisasterRecoveryResponse> listDisasterRecoverys(GetDisasterRecoveryCmd cmd) {
        long mshostId = cmd.getMsHostId();
        List<DisasterRecovery> result = new ArrayList<>(securityCheckDao.getDisasterRecoverys(mshostId));
        List<GetDisasterRecoveryResponse> responses = new ArrayList<>(result.size());
        for (DisasterRecovery scResult : result) {
            GetDisasterRecoveryResponse securityCheckResponse = new GetDisasterRecoveryResponse();
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
    @ActionEvent(eventType = EventTypes.EVENT_SECURITY_CHECK, eventDescription = "disaster recovery perform on the management server when operating the product", async = true)
    public boolean runDisasterRecoveryCommand(final RunDisasterRecoveryCmd cmd) {
        Long mshostId = cmd.getMsHostId();
        ManagementServerHost mshost = msHostDao.findById(mshostId);
        String path = Script.findScript("scripts/security/", "securitycheck.sh");
        if (path == null) {
            throw new CloudRuntimeException(String.format("Unable to find the securitycheck script"));
        }
        ProcessBuilder processBuilder = new ProcessBuilder("sh", path);
        Process process = null;
        try {
            process = processBuilder.start();
            StringBuffer output = new StringBuffer();
            BufferedReader bfr = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bfr.readLine()) != null) {
                String[] temp = line.split(",");
                String checkName = temp[0];
                String checkResult = temp[1];
                String checkMessage;
                if ("false".equals(checkResult)) {
                    checkMessage = "process does not operate normally at last check";
                    alertManager.sendAlert(AlertManager.AlertType.ALERT_TYPE_MANAGEMENT_NODE, 0, new Long(0), "Management server node " + mshost.getServiceIP() + " disaster recovery when operating the product failed : "+ checkName + " " + checkMessage, "");
                } else {
                    checkMessage = "process operates normally";
                }
                updateDisasterRecoveryResult(mshost.getId(), checkName, Boolean.parseBoolean(checkResult), checkMessage);
                output.append(line).append('\n');
            }
            if (output.toString().contains("false")) {
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            throw new CloudRuntimeException("Failed to execute disaster recovery command for management server: "+mshost.getId() +e);
        }
    }

    private void updateDisasterRecoveryResult(final long msHostId, String checkName, boolean checkResult, String checkMessage) {
        boolean newDisasterRecoveryEntry = false;
        DisasterRecoveryVO connectivityVO = securityCheckDao.getDisasterRecoveryResult(msHostId, checkName);
        if (connectivityVO == null) {
            connectivityVO = new DisasterRecoveryVO(msHostId, checkName);
            newDisasterRecoveryEntry = true;
        }
        connectivityVO.setCheckResult(checkResult);
        connectivityVO.setLastUpdateTime(new Date());
        if (StringUtils.isNotEmpty(checkMessage)) {
            connectivityVO.setCheckDetails(checkMessage.getBytes(com.cloud.utils.StringUtils.getPreferredCharset()));
        }
        if (newDisasterRecoveryEntry) {
            securityCheckDao.persist(connectivityVO);
        } else {
            securityCheckDao.update(connectivityVO.getId(), connectivityVO);
        }
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(RunDisasterRecoveryCmd.class);
        cmdList.add(GetDisasterRecoveryCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return DisasterRecoveryServiceImpl.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{
                DisasterRecoveryInterval
        };
    }
}