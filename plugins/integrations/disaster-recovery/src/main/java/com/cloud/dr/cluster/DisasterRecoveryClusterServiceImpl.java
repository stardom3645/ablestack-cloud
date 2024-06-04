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

import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;

import javax.inject.Inject;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.query.dao.UserVmJoinDao;
import com.cloud.api.query.vo.UserVmJoinVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.dr.cluster.dao.DisasterRecoveryClusterDao;
import com.cloud.dr.cluster.dao.DisasterRecoveryClusterDetailsDao;
import com.cloud.dr.cluster.dao.DisasterRecoveryClusterVmMapDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.UserAccount;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.PropertiesUtil;
import com.cloud.utils.script.Script;
import com.cloud.utils.server.ServerProperties;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.command.admin.dr.GetDisasterRecoveryClusterListCmd;
import org.apache.cloudstack.api.command.admin.dr.UpdateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.ScvmIpAddressResponse;
import org.apache.cloudstack.api.command.admin.dr.ConnectivityTestsDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.CreateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.DeleteDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.glue.ListScvmIpAddressCmd;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.dr.cluster.GetDisasterRecoveryClusterListResponse;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DisasterRecoveryClusterServiceImpl extends ManagerBase implements DisasterRecoveryClusterService {

    @Inject
    private ManagementServerHostDao msHostDao;
    @Inject
    private DisasterRecoveryClusterDao disasterRecoveryClusterDao;
    @Inject
    private DisasterRecoveryClusterVmMapDao disasterRecoveryClusterVmMapDao;
    @Inject
    protected UserVmJoinDao userVmJoinDao;
    @Inject
    private DisasterRecoveryClusterDetailsDao disasterRecoveryClusterDetailsDao;
    @Inject
    protected AccountService accountService;
    protected static Logger LOGGER = LogManager.getLogger(DisasterRecoveryClusterServiceImpl.class);

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_TEST_CONNECT, eventDescription = "disaster recovery cluster connection testing", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean connectivityTestsDisasterRecovery(final ConnectivityTestsDisasterRecoveryClusterCmd cmd) {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String url = cmd.getDrClusterUrl();
        String apiKey = cmd.getDrClusterApiKey();
        String secretKey = cmd.getDrClusterSecretKey();
        String moldUrl = url + "/client/api/";
        String moldCommand = "listScvmIpAddress";
        String moldMethod = "GET";

        String response = DisasterRecoveryClusterUtil.moldListScvmIpAddressAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        if (response != null) {
            String[] array = response.split(",");
            for(int i=0; i < array.length; i++) {
                String glueIp = array[i];
                String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueCommand = "/glue";
                String glueMethod = "GET";
                String glueStatus = DisasterRecoveryClusterUtil.glueStatusAPI(glueUrl, glueCommand, glueMethod);
                LOGGER.info(glueStatus);
                if (glueStatus != null) {
                    // glue 상태에 따라 오픈 여부 설정 필요
                    if (glueStatus.contains("HEALTH_OK") || glueStatus.contains("HEALTH_WARN") ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public ListResponse<ScvmIpAddressResponse> listScvmIpAddressResponse(ListScvmIpAddressCmd cmd) {
        List<ScvmIpAddressResponse> responses = new ArrayList<>();
        ScvmIpAddressResponse response = new ScvmIpAddressResponse();
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
        }
        response.setObjectName("scvmipaddress");
        response.setIpAddress(ipList);
        responses.add(response);
        ListResponse<ScvmIpAddressResponse> listResponse = new ListResponse<>();
        listResponse.setResponses(responses);
        return listResponse;
    }

    // DR 클러스터 리스트 관련 코드
    @Override
    public ListResponse<GetDisasterRecoveryClusterListResponse> listDisasterRecoveryClusterResponse(GetDisasterRecoveryClusterListCmd cmd) {
        Long id = cmd.getId();
        String name = cmd.getName();
        String drClusterType = cmd.getDrClusterType();
        List<GetDisasterRecoveryClusterListResponse> responsesList = new ArrayList<>();
        Filter searchFilter = new Filter(DisasterRecoveryClusterVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DisasterRecoveryClusterVO> sb = this.disasterRecoveryClusterDao.createSearchBuilder();

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.EQ);
        sb.and("drClusterType", sb.entity().getDrClusterType(), SearchCriteria.Op.EQ);
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
        if (drClusterType != null) {
            sc.setParameters("drClusterType", drClusterType);
        }
        if(keyword != null){
            sc.addOr("id", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.addOr("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.addOr("uuid", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List <DisasterRecoveryClusterVO> results = disasterRecoveryClusterDao.search(sc, searchFilter);
        for (DisasterRecoveryClusterVO result : results) {
            GetDisasterRecoveryClusterListResponse disasterRecoveryClusterResponse = setDisasterRecoveryClusterListResultResponse(result.getId());
            responsesList.add(disasterRecoveryClusterResponse);
        }
        ListResponse<GetDisasterRecoveryClusterListResponse> response = new ListResponse<>();
        response.setResponses(responsesList);
        return response;
    }

    public GetDisasterRecoveryClusterListResponse setDisasterRecoveryClusterListResultResponse(long clusterId) {
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(clusterId);
        GetDisasterRecoveryClusterListResponse response = new GetDisasterRecoveryClusterListResponse();
        response.setObjectName("disasterrecoverycluster");
        response.setId(drcluster.getUuid());
        response.setName(drcluster.getName());
        response.setDescription(drcluster.getDescription());
        response.setDrClusterUrl(drcluster.getDrClusterUrl());
        response.setDrClusterType(drcluster.getDrClusterType());
        response.setDrClusterStatus(drcluster.getDrClusterStatus());
        response.setDrClusterApiKey(drcluster.getDrClusterApiKey());
        response.setDrClusterSecretKey(drcluster.getDrClusterSecretKey());
        response.setCreated(drcluster.getCreated());
        LOGGER.info("setDisasterRecoveryClusterListResultResponse");
        String moldUrl = drcluster.getDrClusterUrl() + "/client/api/";
        String moldCommand = "listScvmIpAddress";
        String moldMethod = "GET";
        String ScvmResponse = DisasterRecoveryClusterUtil.moldListScvmIpAddressAPI(moldUrl, moldCommand, moldMethod, drcluster.getDrClusterApiKey(), drcluster.getDrClusterSecretKey());
        if (ScvmResponse != null) {
            String[] array = ScvmResponse.split(",");
            for(int i=0; i < array.length; i++) {
                String glueIp = array[i];
                String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueCommand = "/mirror";
                String glueMethod = "GET";
                String daemonHealth = DisasterRecoveryClusterUtil.glueMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                if (daemonHealth != null) {
                    if (daemonHealth.contains("OK")) {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        break;
                    } else if (daemonHealth.contains("WARNING")){
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Disabled.toString());
                        break;
                    } else {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                        break;
                    }
                } else {
                    drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                    break;
                }
            }
        } else {
            drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
        }
        disasterRecoveryClusterDao.update(drcluster.getId(), drcluster);
        response.setMirroringAgentStatus(drcluster.getMirroringAgentStatus());
        List<UserVmResponse> disasterRecoveryClusterVmResponses = new ArrayList<UserVmResponse>();
        List<DisasterRecoveryClusterVmMapVO> drClusterVmList = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drcluster.getId());
        ResponseObject.ResponseView respView = ResponseObject.ResponseView.Restricted;
        Account caller = CallContext.current().getCallingAccount();
        if (accountService.isRootAdmin(caller.getId())) {
            respView = ResponseObject.ResponseView.Full;
        }
        String responseName = "drclustervmlist";
        if (drClusterVmList != null && !drClusterVmList.isEmpty()) {
            for (DisasterRecoveryClusterVmMapVO vmMapVO : drClusterVmList) {
                UserVmJoinVO userVM = userVmJoinDao.findById(vmMapVO.getVmId());
                if (userVM != null) {
                    UserVmResponse cvmResponse = ApiDBUtils.newUserVmResponse(respView, responseName, userVM, EnumSet.of(ApiConstants.VMDetails.nics), caller);
                    disasterRecoveryClusterVmResponses.add(cvmResponse);
                }
            }
        }
        Map<String, String> details = disasterRecoveryClusterDetailsDao.listDetailsKeyPairs(clusterId);
        if (details != null && !details.isEmpty()) {
            response.setDetails(details);
        }
        response.setDisasterRecoveryClusterVms(disasterRecoveryClusterVmResponses);
        // Second dr cluster의 서비스 오퍼링 정보를 가져오는 코드
        String moldCommandListServiceOfferings = "listServiceOfferings";
        List<ServiceOfferingResponse> secDrClusterServiceOfferingListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommandListServiceOfferings, moldMethod, drcluster.getDrClusterApiKey(), drcluster.getDrClusterSecretKey());
        response.setSecDisasterRecoveryClusterServiceOfferingList(secDrClusterServiceOfferingListResponse);
        // Second dr cluster의 네트워크 리스트 정보를 가져오는 코드
        String moldCommandListNetworks = "listNetworks";
        List<NetworkResponse> secDrClusterNetworksListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommandListNetworks, moldMethod, drcluster.getDrClusterApiKey(), drcluster.getDrClusterSecretKey());
        response.setSecDisasterRecoveryClusterNetworkList(secDrClusterNetworksListResponse);
        return response;
    }

    public GetDisasterRecoveryClusterListResponse setDisasterRecoveryClusterResponse(long clusterId) {
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(clusterId);
        GetDisasterRecoveryClusterListResponse response = new GetDisasterRecoveryClusterListResponse();
        response.setObjectName("disasterrecoverycluster");
        response.setId(drcluster.getUuid());
        response.setName(drcluster.getName());
        response.setDescription(drcluster.getDescription());
        response.setDrClusterUrl(drcluster.getDrClusterUrl());
        response.setDrClusterType(drcluster.getDrClusterType());
        response.setDrClusterStatus(drcluster.getDrClusterStatus());
        response.setDrClusterApiKey(drcluster.getDrClusterApiKey());
        response.setDrClusterSecretKey(drcluster.getDrClusterSecretKey());
        response.setCreated(drcluster.getCreated());
        LOGGER.info("setDisasterRecoveryClusterListResultResponse");
        String moldUrl = drcluster.getDrClusterUrl() + "/client/api/";
        String moldCommand = "listScvmIpAddress";
        String moldMethod = "GET";
        String ScvmResponse = DisasterRecoveryClusterUtil.moldListScvmIpAddressAPI(moldUrl, moldCommand, moldMethod, drcluster.getDrClusterApiKey(), drcluster.getDrClusterSecretKey());
        if (ScvmResponse != null) {
            String[] array = ScvmResponse.split(",");
            for(int i=0; i < array.length; i++) {
                String glueIp = array[i];
                String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueCommand = "/mirror";
                String glueMethod = "GET";
                String daemonHealth = DisasterRecoveryClusterUtil.glueMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                if (daemonHealth != null) {
                    if (daemonHealth.contains("OK")) {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        break;
                    } else if (daemonHealth.contains("WARNING")){
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Disabled.toString());
                        break;
                    } else {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                        break;
                    }
                } else {
                    drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                    break;
                }
            }
        } else {
            drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
        }
        disasterRecoveryClusterDao.update(drcluster.getId(), drcluster);
        return response;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_DISASTER_RECOVERY_CLUSTER, eventDescription = "updating dr cluster", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public GetDisasterRecoveryClusterListResponse updateDisasterRecoveryCluster(UpdateDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryClusterService.DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery plugin is disabled");
        }
        DisasterRecoveryClusterVO drcluster = null;
        final Long drClusterId = cmd.getId();
        final String drClusterName = cmd.getName();
        if (drClusterId == null) {
            drcluster = disasterRecoveryClusterDao.findByName(drClusterName);
        } else {
            drcluster = disasterRecoveryClusterDao.findById(drClusterId);
            if (drcluster == null) {
                throw new InvalidParameterValueException("Invalid Disaster Recovery id specified");
            }
        }
        LOGGER.info(drcluster.getName());
        drcluster = disasterRecoveryClusterDao.createForUpdate(drcluster.getId());
        if (cmd.getDrClusterStatus() != null && cmd.getMirroringAgentStatus() != null) {
            LOGGER.info(":::::::::::::::::::1");
            final String drClusterStatus = cmd.getDrClusterStatus();
            final String mirroringAgentStatus = cmd.getMirroringAgentStatus();
            LOGGER.info(drClusterStatus);
            LOGGER.info(mirroringAgentStatus);
            drcluster.setDrClusterStatus(drClusterStatus);
            drcluster.setMirroringAgentStatus(mirroringAgentStatus);
            if (!disasterRecoveryClusterDao.update(drcluster.getId(), drcluster)) {
                throw new CloudRuntimeException(String.format("Failed to update Disaster Recovery ID: %s", drcluster.getUuid()));
            }
            drcluster = disasterRecoveryClusterDao.findByName(drClusterName);
        } else {
            LOGGER.info(":::::::::::::::::::2");
            if (cmd.getName() != null) {
                drcluster.setName(drClusterName);
            }
            if (cmd.getDescription() != null) {
                String drClusterDescription = cmd.getDescription();
                drcluster.setDescription(drClusterDescription);
            }
            if (cmd.getDrClusterUrl() != null) {
                String drClusterUrl = cmd.getDrClusterUrl();
                drcluster.setDrClusterUrl(drClusterUrl);
            }
            if (cmd.getDetails() != null) {
                Map<String,String> details = cmd.getDetails();
                drcluster.setDetails(details);
                disasterRecoveryClusterDao.saveDetails(drcluster);
            }
            if (!disasterRecoveryClusterDao.update(drcluster.getId(), drcluster)) {
                throw new CloudRuntimeException(String.format("Failed to update Disaster Recovery ID: %s", drcluster.getUuid()));
            }
            drcluster = disasterRecoveryClusterDao.findById(drClusterId);
        }
        return setDisasterRecoveryClusterResponse(drcluster.getId());
    }

    @Override
    public DisasterRecoveryCluster createDisasterRecoveryCluster(CreateDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        validateDisasterRecoveryClusterCreateParameters(cmd);
        ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
        DisasterRecoveryClusterVO cluster = Transaction.execute(new TransactionCallback<DisasterRecoveryClusterVO>() {
            @Override
            public DisasterRecoveryClusterVO doInTransaction(TransactionStatus status) {
                DisasterRecoveryClusterVO newCluster = new DisasterRecoveryClusterVO(msHost.getId(), cmd.getName(), cmd.getDescription(),
                        cmd.getDrClusterApiKey(), cmd.getDrClusterSecretKey(), cmd.getDrClusterPrivateKey(), cmd.getDrClusterGlueIpAddress(), cmd.getDrClusterUrl(), cmd.getDrClusterType(), DisasterRecoveryCluster.DrClusterStatus.Created.toString(), DisasterRecoveryCluster.MirroringAgentStatus.Created.toString());
                disasterRecoveryClusterDao.persist(newCluster);
                return newCluster;
            }
        });
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Disaster recovery cluster name: %s and ID: %s has been created", cluster.getName(), cluster.getUuid()));
        }
        return cluster;
    }

    private void validateDisasterRecoveryClusterCreateParameters(final CreateDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        final String name = cmd.getName();
        final String type = cmd.getDrClusterType();
        final String url = cmd.getDrClusterUrl();
        final String apiKey = cmd.getDrClusterApiKey();
        final String secretKey = cmd.getDrClusterSecretKey();
        final String privateKey = cmd.getDrClusterPrivateKey();
        final String glueIp = cmd.getDrClusterGlueIpAddress();

        if (name == null || name.isEmpty()) {
            throw new InvalidParameterValueException("Invalid name for the disaster recovery cluster name:" + name);
        }
        if (type.equalsIgnoreCase("secondary") && (privateKey == null || privateKey.isEmpty())) {
            throw new InvalidParameterValueException("Invalid private key for the disaster recovery cluster private key:" + privateKey);
        }
        if (type.equalsIgnoreCase("secondary") && (glueIp == null || glueIp.isEmpty())) {
            throw new InvalidParameterValueException("Invalid glue ip for the disaster recovery cluster glue ip:" + glueIp);
        }
        if (url == null || url.isEmpty()) {
            throw new InvalidParameterValueException("Invalid url for the disaster recovery cluster url:" + url);
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new InvalidParameterValueException("Invalid api key for the disaster recovery cluster api key:" + apiKey);
        }
        if (secretKey == null || secretKey.isEmpty()) {
            throw new InvalidParameterValueException("Invalid secret key for the disaster recovery cluster secret key:" + secretKey);
        }
    }

    private String[] getServerProperties() {
        String[] serverInfo = null;
        final String HTTP_PORT = "http.port";
        final String HTTPS_ENABLE = "https.enable";
        final String HTTPS_PORT = "https.port";
        final File confFile = PropertiesUtil.findConfigFile("server.properties");
        try {
            InputStream is = new FileInputStream(confFile);
            String port = null;
            String protocol = null;
            final Properties properties = ServerProperties.getServerProperties(is);
            if (properties.getProperty(HTTPS_ENABLE).equals("true")){
                port = properties.getProperty(HTTPS_PORT);
                protocol = "https://";
            } else {
                port = properties.getProperty(HTTP_PORT);
                protocol = "http://";
            }
            serverInfo = new String[]{port, protocol};
        } catch (final IOException e) {
            LOGGER.debug("Failed to read configuration from server.properties file", e);
        }
        return serverInfo;
    }

    @Override
    public boolean setupDisasterRecoveryCluster(long clusterId) throws CloudRuntimeException {
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(clusterId);
        String drName = drCluster.getName();
        String drDescription = drCluster.getDescription();
        // secondary cluster 정보
        String secUrl = drCluster.getDrClusterUrl();
        String secClusterType = drCluster.getDrClusterType();
        String secApiKey = drCluster.getDrClusterApiKey();
        String secSecretKey = drCluster.getDrClusterSecretKey();
        String secPrivateKey = drCluster.getDrClusterPrivateKey();
        String secGlueIpAddress = drCluster.getDrClusterGlueIpAddress();
        try {
            FileOutputStream fos = new FileOutputStream("glue.key");
            fos.write(secPrivateKey.getBytes());
            fos.close();
        } catch (IOException e) {
            throw new CloudRuntimeException("Converting the secondary cluster's private key to a file failed.", e);
        }
        File permKey = new File("glue.key");
        // primary cluster 정보
        String[] properties = getServerProperties();
        ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
        // String priUrl = properties[1] + "://" + msHost.getServiceIP() + ":" + properties[0];
        String priUrl = "http://10.10.254.39:5050";
        String priClusterType = "primary";
        UserAccount user = accountService.getActiveUserAccount("admin", 1L);
        String priApiKey = user.getApiKey();
        String priSecretKey = user.getSecretKey();
        // secondary cluster에 CreateDisasterRecoveryClusterCmd 호출
        Map<String, String> secParams = new HashMap<>();
        secParams.put("name", drName);
        secParams.put("description", drDescription);
        secParams.put("drclustertype", priClusterType);
        secParams.put("drclusterurl", priUrl);
        secParams.put("drclusterapikey", priApiKey);
        secParams.put("drclustersecretkey", priSecretKey);
        String secCommand = "createDisasterRecoveryCluster";
        String secMethod = "POST";
        String secResponse = DisasterRecoveryClusterUtil.moldCreateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, secParams);
        if (secResponse == null || secResponse.isEmpty()) {
            // secondary cluster의 db에 dr 정보가 정상적으로 업데이트 되지 않은 경우
            // primary cluster db 업데이트
            drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
            drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
            disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
            // secondary cluster에 UpdateDisasterRecoveryClusterCmd 호출
            secCommand = "updateDisasterRecoveryCluster";
            secMethod = "GET";
            Map<String, String> errParams = new HashMap<>();
            errParams.put("name", drName);
            errParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
            errParams.put("mirroringagentstatus", DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
            DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, errParams);
            return false;
        } else {
            // secondary cluster의 db에 dr 정보가 정상적으로 업데이트 된 경우
            // primary cluster에 scvmList 조회하여 api 요청
            String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
            if (ipList != null || !ipList.isEmpty()) {
                ipList = ipList.replaceAll(",$", "");
                LOGGER.info(ipList);
                // primary cluster에 glueMirrorSetupAPI 호출
                String[] array = ipList.split(",");
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueCommand = "/mirror";
                    String glueMethod = "POST";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("localClusterName", "local");
                    glueParams.put("remoteClusterName", "remote");
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("host", secGlueIpAddress);
                    boolean result = DisasterRecoveryClusterUtil.glueMirrorSetupAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                    // mirror setup 성공
                    if (result) {
                        // primary cluster db 업데이트
                        drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                        // secondary cluster db 업데이트
                        secCommand = "updateDisasterRecoveryCluster";
                        secMethod = "GET";
                        Map<String, String> sucParams = new HashMap<>();
                        sucParams.put("name", drName);
                        sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                        return true;
                    } else {
                        // primary cluster db 업데이트
                        drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                        drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                        disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                        // secondary cluster db 업데이트
                        secCommand = "updateDisasterRecoveryCluster";
                        secMethod = "GET";
                        Map<String, String> sucParams = new HashMap<>();
                        sucParams.put("name", drName);
                        sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                        sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                        DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                    }
                }
            } else {
                // primary cluster의 scvm ip 리스트를 가져오지 못한 경우
                // primary cluster db 업데이트
                drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                // secondary cluster에 UpdateDisasterRecoveryClusterCmd 호출
                secCommand = "updateDisasterRecoveryCluster";
                secMethod = "GET";
                Map<String, String> errParams = new HashMap<>();
                errParams.put("name", drName);
                errParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                errParams.put("mirroringagentstatus", DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, errParams);
            }
        }
        return false;
    }

    @Override
    public GetDisasterRecoveryClusterListResponse createDisasterRecoveryClusterResponse(long clusterId) {
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(clusterId);
        GetDisasterRecoveryClusterListResponse response = new GetDisasterRecoveryClusterListResponse();
        response.setObjectName("disasterrecoverycluster");
        response.setId(drcluster.getUuid());
        response.setName(drcluster.getName());
        response.setDescription(drcluster.getDescription());
        response.setDrClusterUrl(drcluster.getDrClusterUrl());
        response.setDrClusterType(drcluster.getDrClusterType());
        response.setDrClusterStatus(drcluster.getDrClusterStatus());
        response.setMirroringAgentStatus(drcluster.getMirroringAgentStatus());
        response.setDrClusterApiKey(drcluster.getDrClusterApiKey());
        response.setDrClusterSecretKey(drcluster.getDrClusterSecretKey());
        response.setCreated(drcluster.getCreated());
        return response;
    }

    @Override
    public boolean deleteDisasterRecoveryCluster(DeleteDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster name specified");
        }
        if (drCluster.getDrClusterType().equalsIgnoreCase("primary")) {
            return disasterRecoveryClusterDao.remove(drCluster.getId());
        } else {
            String drName = drCluster.getName();
            String secUrl = drCluster.getDrClusterUrl();
            String secClusterType = drCluster.getDrClusterType();
            String secApiKey = drCluster.getDrClusterApiKey();
            String secSecretKey = drCluster.getDrClusterSecretKey();
            String secPrivateKey = drCluster.getDrClusterPrivateKey();
            String secGlueIpAddress = drCluster.getDrClusterGlueIpAddress();
            try {
                FileOutputStream fos = new FileOutputStream("glue.key");
                fos.write(secPrivateKey.getBytes());
                fos.close();
            } catch (IOException e) {
                throw new CloudRuntimeException("Converting the secondary cluster's private key to a file failed.", e);
            }
            File permKey = new File("glue.key");
            // primary cluster 정보
            String[] properties = getServerProperties();
            ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
            // String priUrl = properties[1] + "://" + msHost.getServiceIP() + ":" + properties[0];
            String priUrl = "http://10.10.254.39:5050";
            String priClusterType = "primary";
            UserAccount user = accountService.getActiveUserAccount("admin", 1L);
            String priApiKey = user.getApiKey();
            String priSecretKey = user.getSecretKey();
            // primary cluster에 scvmList 조회하여 api 요청
            String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
            if (ipList != null || !ipList.isEmpty()) {
                ipList = ipList.replaceAll(",$", "");
                // primary cluster에 glueMirrorDeleteAPI 호출
                String[] array = ipList.split(",");
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueCommand = "/mirror";
                    String glueMethod = "DELETE";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("host", secGlueIpAddress);
                    boolean result = DisasterRecoveryClusterUtil.glueMirrorDeleteAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                    // mirror delete 성공
                    if (result) {
                        // primary cluster db 업데이트
                        disasterRecoveryClusterDao.remove(drCluster.getId());
                        // secondary cluster db 조회
                        String secCommand = "getDisasterRecoveryClusterList";
                        String secMethod = "GET";
                        Map<String, Long> sucParams = new HashMap<>();
                        List<GetDisasterRecoveryClusterListResponse> drListResponse = DisasterRecoveryClusterUtil.moldGetDisasterRecoveryClusterListAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey);
                        if (drListResponse != null || !drListResponse.isEmpty()) {
                            for (GetDisasterRecoveryClusterListResponse dr : drListResponse) {
                                if (dr.getName() == drCluster.getName()) {
                                    String primaryDrId = dr.getId();
                                    // secondary cluster db 업데이트
                                    secCommand = "deleteDisasterRecoveryCluster";
                                    secMethod = "GET";
                                    sucParams = new HashMap<>();
                                    sucParams.put("id", primaryDrId);
                                    DisasterRecoveryClusterUtil.moldDeleteDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                                    return true;
                                }
                            }
                        } else {
                            return false;
                        }
                    } else {
                        // mirror 삭제 명령 후 실패한 경우 어떻게 처리할것인지??
                        // primary cluster db 업데이트
                        drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                        drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                        disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                        // secondary cluster db 업데이트
                        String secCommand = "updateDisasterRecoveryCluster";
                        String secMethod = "GET";
                        Map<String, String> sucParams = new HashMap<>();
                        sucParams.put("name", drName);
                        sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                        sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                        DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                    }
                }
            } else {
                // primary cluster의 scvm ip 리스트를 가져오지 못한 경우
                throw new CloudRuntimeException("primary cluster scvm list lookup fails.");
            }
            return false;
        }
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DisasterRecoveryServiceEnabled.value()) {
            return cmdList;
        }
        cmdList.add(ListScvmIpAddressCmd.class);
        cmdList.add(ConnectivityTestsDisasterRecoveryClusterCmd.class);
        cmdList.add(GetDisasterRecoveryClusterListCmd.class);
        cmdList.add(UpdateDisasterRecoveryClusterCmd.class);
        cmdList.add(CreateDisasterRecoveryClusterCmd.class);
        cmdList.add(DeleteDisasterRecoveryClusterCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return DisasterRecoveryClusterService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                DisasterRecoveryServiceEnabled
        };
    }
}
