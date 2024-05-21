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
import java.io.IOException;
import java.io.File;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

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
import com.google.common.base.Preconditions;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.command.admin.dr.GetDisasterRecoveryClusterListCmd;
import org.apache.cloudstack.api.command.admin.dr.GetSecDisasterRecoveryClusterInfoListCmd;
import org.apache.cloudstack.api.command.admin.dr.UpdateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ScvmIpAddressResponse;
import org.apache.cloudstack.api.command.admin.dr.ConnectivityTestsDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.CreateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.glue.ListScvmIpAddressCmd;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.dr.cluster.GetDisasterRecoveryClusterListResponse;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

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
        String apiKey = cmd.getApiKey();
        String secretKey = cmd.getSecretKey();
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
    public ListResponse<ScvmIpAddressResponse> listScvmIpAddressResponse(ListScvmIpAddressCmd cmd) {
        List<ScvmIpAddressResponse> responses = new ArrayList<>();
        ScvmIpAddressResponse response = new ScvmIpAddressResponse();
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        ipList = ipList.replaceAll(",$", "");
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
            GetDisasterRecoveryClusterListResponse automationControllerResponse = setDisasterRecoveryClusterListResultResponse(result.getId());
            responsesList.add(automationControllerResponse);
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
        response.setApiKey(drcluster.getApiKey());
        response.setSecretKey(drcluster.getSecretKey());
        response.setCreated(drcluster.getCreated());

        String moldUrl = drcluster.getDrClusterUrl() + "/client/api/";
        String moldCommand = "listScvmIpAddress";
        String moldMethod = "GET";
        String ScvmResponse = DisasterRecoveryClusterUtil.moldListScvmIpAddressAPI(moldUrl, moldCommand, moldMethod, drcluster.getApiKey(), drcluster.getSecretKey());
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
        return response;
    }

    @Override
    public ListResponse<ServiceOfferingResponse> listSecDisasterRecoveryClusterInfoResponse(GetSecDisasterRecoveryClusterInfoListCmd cmd) {
        final Long drClusterId = cmd.getId();
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(drClusterId);
        ListResponse response = new ListResponse();
        response.setObjectName("secdrclusterofferinglist");

        String moldUrl = drcluster.getDrClusterUrl() + "/client/api/";
        String moldCommand = "listServiceOfferings";
        String moldMethod = "GET";
        List<JSONObject> secDrClusterInfoListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommand, moldMethod, drcluster.getApiKey(), drcluster.getSecretKey());
        System.out.println(secDrClusterInfoListResponse);
        response.setResponses(secDrClusterInfoListResponse);
//        disasterRecoveryClusterDao.update(drcluster.getId(), drcluster);
//        response.setMirroringAgentStatus(drcluster.getMirroringAgentStatus());
//        List<UserVmResponse> disasterRecoveryClusterVmResponses = new ArrayList<UserVmResponse>();
//        List<DisasterRecoveryClusterVmMapVO> drClusterVmList = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drcluster.getId());
//        ResponseObject.ResponseView respView = ResponseObject.ResponseView.Restricted;
//        Account caller = CallContext.current().getCallingAccount();
//        if (accountService.isRootAdmin(caller.getId())) {
//            respView = ResponseObject.ResponseView.Full;
//        }
//        String responseName = "drclustervmlist";
//        if (drClusterVmList != null && !drClusterVmList.isEmpty()) {
//            for (DisasterRecoveryClusterVmMapVO vmMapVO : drClusterVmList) {
//                UserVmJoinVO userVM = userVmJoinDao.findById(vmMapVO.getVmId());
//                if (userVM != null) {
//                    UserVmResponse cvmResponse = ApiDBUtils.newUserVmResponse(respView, responseName, userVM, EnumSet.of(ApiConstants.VMDetails.nics), caller);
//                    disasterRecoveryClusterVmResponses.add(cvmResponse);
//                }
//            }
//        }
//        Map<String, String> details = disasterRecoveryClusterDetailsDao.listDetailsKeyPairs(clusterId);
//        if (details != null && !details.isEmpty()) {
//            response.setDetails(details);
//        }
//        response.setDisasterRecoveryClusterVms(disasterRecoveryClusterVmResponses);
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
        if (cmd.getDrClusterStatus() != null && cmd.getMirroringAgentStatus() != null) {
            return setDisasterRecoveryClusterListResultResponse(drcluster.getId());
        } else {
            drcluster = disasterRecoveryClusterDao.createForUpdate(drcluster.getId());
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
        return setDisasterRecoveryClusterListResultResponse(drcluster.getId());
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
                DisasterRecoveryClusterVO newCluster = new DisasterRecoveryClusterVO(msHost.getId(), cmd.getName(), cmd.getDescription(), cmd.getDrClusterType(), cmd.getDrClusterUrl(),
                        cmd.getApiKey(), cmd.getSecretKey(), DisasterRecoveryCluster.DrClusterStatus.Created.toString(), DisasterRecoveryCluster.MirroringAgentStatus.Created.toString());
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
        final String apiKey = cmd.getApiKey();
        final String secretKey = cmd.getSecretKey();
        final String privateKey = cmd.getPrivateKey();

        if (name == null || name.isEmpty()) {
            throw new InvalidParameterValueException("Invalid name for the disaster recovery cluster name:" + name);
        }
        // if (type.equalsIgnoreCase("secondary") && (privateKey == null || privateKey.isEmpty())) {
        //     throw new InvalidParameterValueException("Invalid private key for the disaster recovery cluster private key:" + privateKey);
        // }
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

    private PrivateKey parsePrivateKey(final String key) throws IOException {
        Preconditions.checkArgument(StringUtils.isNotEmpty(key));
        try (final PemReader pemReader = new PemReader(new StringReader(key));) {
            final PemObject pemObject = pemReader.readPemObject();
            final byte[] content = pemObject.getContent();
            final PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
            final KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
            return factory.generatePrivate(privKeySpec);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IOException("No encryption provider available.", e);
        } catch (final InvalidKeySpecException e) {
            throw new IOException("Invalid Key format.", e);
        }
    }

    public boolean setupDisasterRecoveryCluster(long clusterId) {
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(clusterId);
        String drName = drCluster.getName();
        String drDescription = drCluster.getDescription();
        // secondary cluster 정보
        String secUrl = drCluster.getDrClusterUrl();
        String secClusterType = drCluster.getDrClusterType();
        String secApiKey = drCluster.getApiKey();
        String secSecretKey = drCluster.getSecretKey();
        // String secPrivateKey = drCluster.getPrivateKey();
        final File permKey = new File("/root/.ssh/dr.key");
        // primary cluster 정보
        String[] properties = getServerProperties();
        ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
        String priUrl = properties[1] + "://" + msHost.getServiceIP() + ":" + properties[0];
        String priClusterType = "primary";
        UserAccount user = accountService.getActiveUserAccount("admin", 1L);
        String priApiKey = user.getApiKey();
        String priSecretKey = user.getSecretKey();
        // secondary cluster에 CreateDisasterRecoveryClusterCmd 호출
        Map<String, String> secParams = new HashMap<>();
        secParams.put("name", drName);
        secParams.put("description", drDescription);
        secParams.put("drClusterType", priClusterType);
        secParams.put("drClusterUrl", priUrl);
        secParams.put("apiKey", priApiKey);
        secParams.put("secretKey", priSecretKey);
        String secCommand = "createDisasterRecoveryCluster";
        String secMethod = "POST";
        String secResponse = DisasterRecoveryClusterUtil.moldCreateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, secParams);
        LOGGER.info("secResponse::::::::::::::::::::::::::::::createDisasterRecoveryCluster");
        LOGGER.info(secResponse);
        if (secResponse != null) {
            // secondary cluster의 db에 dr 정보가 정상적으로 업데이트 되지 않은 경우
            // secondary cluster에 UpdateDisasterRecoveryClusterCmd 호출
            Map<String, String> errParams = new HashMap<>();
            errParams.put("name", drName);
            errParams.put("drClusterStatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
            errParams.put("mirroringAgentStatus", DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
            DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, errParams);
            return false;
        } else {
            // secondary cluster의 db에 dr 정보가 정상적으로 업데이트 된 경우
            // secondary cluster에 ListScvmIpAddressCmd 호출
            secUrl = secUrl + "/client/api/";
            secCommand = "listScvmIpAddress";
            secMethod = "GET";
            secResponse = DisasterRecoveryClusterUtil.moldListScvmIpAddressAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey);
            LOGGER.info("secResponse::::::::::::::::::::::::::::::listScvmIpAddress");
            LOGGER.info(secResponse);
            if (secResponse == null) {
                // secondary cluster의 scvm ip 리스트를 가져오지 못한 경우
                // secondary cluster에 UpdateDisasterRecoveryClusterCmd 호출
                Map<String, String> errParams = new HashMap<>();
                errParams.put("name", drName);
                errParams.put("drClusterStatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                errParams.put("mirroringAgentStatus", DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, errParams);
                return false;
            } else {
                // secondary cluster에 glueMirrorSetupAPI 호출
                String[] array = secResponse.split(",");
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    String glueUrl = "https://" + glueIp + ":8080/api/v1"; // glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueCommand = "/mirror";
                    String glueMethod = "POST";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("localClusterName", "local");
                    glueParams.put("remoteClusterName", "remote");
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("host", glueIp);
                    //final PrivateKey key = parsePrivateKey(secPrivateKey);
                    //boolean result = DisasterRecoveryClusterUtil.glueMirrorSetupAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                    boolean result = true;
                    LOGGER.info("result::::::::::::::::::::::::::::::");
                    LOGGER.info(result);
                    // mirror setup 성공
                    if (result) {
                        // primary cluster db 업데이트
                        drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                        // secondary cluster db 업데이트
                        Map<String, String> errParams = new HashMap<>();
                        errParams.put("name", drName);
                        errParams.put("drClusterStatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        errParams.put("mirroringAgentStatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl, secCommand, secMethod, secApiKey, secSecretKey, errParams);
                        return true;
                    }
                }
            }
        }
        // secondary cluster에 UpdateDisasterRecoveryClusterCmd 호출
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
        response.setApiKey(drcluster.getApiKey());
        response.setSecretKey(drcluster.getSecretKey());
        response.setCreated(drcluster.getCreated());
        return response;
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
        cmdList.add(GetSecDisasterRecoveryClusterInfoListCmd.class);
        cmdList.add(CreateDisasterRecoveryClusterCmd.class);
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
