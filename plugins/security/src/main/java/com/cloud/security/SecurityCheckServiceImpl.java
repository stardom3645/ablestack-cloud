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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.command.admin.GetSecurityCheckCmd;
import org.apache.cloudstack.api.command.admin.RunSecurityCheckCmd;
import org.apache.cloudstack.api.response.GetSecurityCheckResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;

import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.security.dao.SecurityCheckDao;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;

public class SecurityCheckServiceImpl extends ManagerBase implements PluggableService, SecurityCheckService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(SecurityCheckServiceImpl.class);

    private static final ConfigKey<Integer> SecurityCheckInterval = new ConfigKey<>("Advanced", Integer.class,
            "security.check.interval", "0",
            "The interval security check background tasks in seconds", false);

    @Inject
    private SecurityCheckDao securityCheckDao;

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
    public Pair<Boolean, String> runSecurityCheckCommand(final RunSecurityCheckCmd cmd) {
        final Long mshostId = cmd.getMsHostId();
        LOGGER.info("Running security check results for management server " + mshostId);
        // ManagementServerHost mshost = msHostDao.findById(mshostId);
        // String resultDetails = "";
        // boolean success = true;
        // String scriptsDir = "scripts/security";
        // String securityCekScr = Script.findScript(scriptsDir, "securitycheck.sh");
        // final int timeout = 30000;
        // Script scr = new Script(securityCekScr, timeout, LOGGER);
        // OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
        // String result;
        // result = scr.execute(parser);
        // String parsedLine = parser.getLine();
        // LOGGER.info(parsedLine);
        // if (scr.getExitValue() != 0) {
        //     LOGGER.error("Error while executing script " + scr.toString());
        //     throw new CloudRuntimeException("Error while executing script " + scr.toString());
        // }
        return null;
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
