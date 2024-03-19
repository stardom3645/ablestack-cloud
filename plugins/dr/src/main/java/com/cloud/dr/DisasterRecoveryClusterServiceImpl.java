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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.command.admin.GetDisasterRecoveryClusterListCmd;
import org.apache.cloudstack.api.command.admin.RunDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.UpdateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.response.GetDisasterRecoveryClusterListResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.management.ManagementServerHost;
import org.apache.cloudstack.utils.identity.ManagementServerNode;

import com.cloud.alert.AlertManager;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventTypes;
import com.cloud.event.EventVO;
import com.cloud.dr.dao.DisasterRecoveryClusterDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;

public class DisasterRecoveryClusterServiceImpl extends ManagerBase implements DisasterRecoveryClusterService {

    private static String runMode = "";

    @Inject
    private DisasterRecoveryClusterDao disasterRecoveryClusterDao;
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
//                    updateDisasterRecoveryResult(msHost.getId(), checkName, Boolean.parseBoolean(checkResult), checkMessage);
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
    public ListResponse<GetDisasterRecoveryClusterListResponse> listDisasterRecoveryClusterResponse(GetDisasterRecoveryClusterListCmd cmd) {
        Long id = cmd.getId();
        String name = cmd.getName();
        List<GetDisasterRecoveryClusterListResponse> responsesList = new ArrayList<>();
        Filter searchFilter = new Filter(DisasterRecoveryClusterVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DisasterRecoveryClusterVO> sb = this.disasterRecoveryClusterDao.createSearchBuilder();

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        SearchCriteria<DisasterRecoveryClusterVO> sc = sb.create();
        String keyword = cmd.getKeyword();
        if (keyword != null){
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        if (id != null) {
            sc.setParameters("id", id);
        }
        if (name != null) {
            sc.setParameters("name", name);
        }
        if(keyword != null){
            sc.addOr("uuid", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List <DisasterRecoveryClusterVO> results = disasterRecoveryClusterDao.search(sc, searchFilter);
        for (DisasterRecoveryClusterVO result : results) {
            GetDisasterRecoveryClusterListResponse automationControllerResponse = setDisasterRecoveryListResultResponse(result.getId());
            responsesList.add(automationControllerResponse);
        }
        ListResponse<GetDisasterRecoveryClusterListResponse> response = new ListResponse<>();
        response.setResponses(responsesList);
        return response;
    }

    public GetDisasterRecoveryClusterListResponse setDisasterRecoveryListResultResponse(long clusterId) {
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(clusterId);
        GetDisasterRecoveryClusterListResponse response = new GetDisasterRecoveryClusterListResponse();
        response.setObjectName("disasterrecoverycluster");
        response.setId(drcluster.getUuid());
        response.setName(drcluster.getName());
        response.setDrClusterUuid(drcluster.getDrClusterUuid());
        response.setDrClusterIp(drcluster.getDrClusterIp());
        response.setDrClusterPort(drcluster.getDrClusterPort());
        response.setDrClusterType(drcluster.getDrClusterType());
        response.setDrClusterStatus(drcluster.getDrClusterStatus());
        response.setMirroringAgentStatus(drcluster.getMirroringAgentStatus());
        response.setApiKey(drcluster.getApiKey());
        response.setSecretKey(drcluster.getSecretKey());
        response.setCreated(drcluster.getCreated());
        return response;
    }

    @Override
    public GetDisasterRecoveryClusterListResponse updateDisasterRecoveryCluster(UpdateDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryClusterService.DisasterRecoveryFeatureEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery plugin is disabled");
        }
        final Long drClusterId = cmd.getId();
        DisasterRecoveryCluster.DrClusterStatus drClusterStatus = null;
        DisasterRecoveryCluster.MirroringAgentStatus mirroringAgentStatus = null;
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(drClusterId);
        if (drcluster == null) {
            throw new InvalidParameterValueException("Invalid Disaster Recovery id specified");
        }
        try {
            drClusterStatus = DisasterRecoveryCluster.DrClusterStatus.valueOf(cmd.getDrClusterStatus());
            mirroringAgentStatus = DisasterRecoveryCluster.MirroringAgentStatus.valueOf(cmd.getMirroringAgentStatus());
        } catch (IllegalArgumentException iae) {
            throw new InvalidParameterValueException(String.format("Invalid value for %s parameter", ApiConstants.STATE));
        }
        if (!drClusterStatus.equals(drcluster.getDrClusterStatus()) && !mirroringAgentStatus.equals(drcluster.getMirroringAgentStatus())) {
            drcluster = disasterRecoveryClusterDao.createForUpdate(drcluster.getId());
            drcluster.setDrClusterStatus(String.valueOf(drcluster));
            if (!disasterRecoveryClusterDao.update(drcluster.getId(), drcluster)) {
                throw new CloudRuntimeException(String.format("Failed to update Disaster Recovery ID: %s", drcluster.getUuid()));
            }
            drcluster = disasterRecoveryClusterDao.findById(drClusterId);
        }
        return setDisasterRecoveryListResultResponse(drcluster.getId());
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_SECURITY_CHECK, eventDescription = "disaster recovery perform on the management server when operating the product", async = true)
    public boolean runDisasterRecoveryCommand(final RunDisasterRecoveryClusterCmd cmd) {
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
//                updateDisasterRecoveryResult(mshost.getId(), checkName, Boolean.parseBoolean(checkResult), checkMessage);
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

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
//        cmdList.add(RunDisasterRecoveryCmd.class);
        cmdList.add(GetDisasterRecoveryClusterListCmd.class);
        cmdList.add(UpdateDisasterRecoveryClusterCmd.class);
        return cmdList;
    }

    @Override
    public DisasterRecoveryCluster findById(final Long id) {
        return disasterRecoveryClusterDao.findById(id);
    }

    @Override
    public String getConfigComponentName() {
        return DisasterRecoveryClusterService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                DisasterRecoveryFeatureEnabled
        };
    }

}