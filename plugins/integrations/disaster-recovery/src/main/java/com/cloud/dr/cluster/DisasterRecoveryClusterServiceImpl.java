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
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;

import javax.inject.Inject;

import com.cloud.api.query.dao.UserVmJoinDao;
import com.cloud.api.query.vo.UserVmJoinVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.dr.cluster.dao.DisasterRecoveryClusterDao;
import com.cloud.dr.cluster.dao.DisasterRecoveryClusterDetailsDao;
import com.cloud.dr.cluster.dao.DisasterRecoveryClusterVmMapDao;
import com.cloud.event.ActionEvent;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeApiService;
import com.cloud.offering.DiskOffering;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.UserAccount;
import com.cloud.user.dao.AccountDao;
import com.cloud.uservm.UserVm;
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
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmService;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.command.admin.dr.GetDisasterRecoveryClusterListCmd;
import org.apache.cloudstack.api.command.admin.dr.UpdateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.UpdateDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.ScvmIpAddressResponse;
import org.apache.cloudstack.api.command.admin.dr.ClearDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.ConnectivityTestsDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.CreateDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.CreateDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.command.admin.dr.DeleteDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.DeleteDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.command.admin.dr.DisableDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.EnableDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.PromoteDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.PromoteDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.command.admin.dr.ResyncDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.DemoteDisasterRecoveryClusterCmd;
import org.apache.cloudstack.api.command.admin.dr.DemoteDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.command.admin.dr.StartDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.command.admin.dr.StopDisasterRecoveryClusterVmCmd;
import org.apache.cloudstack.api.command.admin.glue.ListScvmIpAddressCmd;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.api.response.dr.cluster.GetDisasterRecoveryClusterListResponse;
import org.apache.cloudstack.api.response.dr.cluster.GetDisasterRecoveryClusterVmListResponse;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

public class DisasterRecoveryClusterServiceImpl extends ManagerBase implements DisasterRecoveryClusterService {

    @Inject
    private ManagementServerHostDao msHostDao;
    @Inject
    private VolumeDao volsDao;
    @Inject
    private VMInstanceDao vmDao;
    @Inject
    private UserVmDao userVmDao;
    @Inject
    private AccountDao accountDao;
    @Inject
    private DiskOfferingDao diskOfferingDao;
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
    @Inject
    protected UserVmService userVmService;
    @Inject
    protected UserVmManager userVmManager;
    @Inject
    protected VolumeApiService volumeService;
    protected static Logger LOGGER = LogManager.getLogger(DisasterRecoveryClusterServiceImpl.class);

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_TEST_CONNECT, eventDescription = "disaster recovery cluster connection testing", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean connectivityTestsDisasterRecovery(final ConnectivityTestsDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
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
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                String glueCommand = "/glue";
                String glueMethod = "GET";
                String glueStatus = DisasterRecoveryClusterUtil.glueStatusAPI(glueUrl, glueCommand, glueMethod);
                if (glueStatus != null) {
                    ///////////////////// glue 상태에 따라 오픈 여부 설정 필요
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
        response.setDrClusterGlueIpAddress(drcluster.getDrClusterGlueIpAddress());
        response.setCreated(drcluster.getCreated());

        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(clusterId);
        String secApiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secSecretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        if (details != null && !details.isEmpty()) {
            response.setDetails(details);
        }

        // 미러링 데몬 상태 업데이트
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                String glueCommand = "/mirror";
                String glueMethod = "GET";
                String daemonHealth = DisasterRecoveryClusterUtil.glueMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                if (daemonHealth != null) {
                    if (daemonHealth.contains("OK")) {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        break;
                    } else if (daemonHealth.contains("WARNING")){
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Warning.toString());
                        break;
                    } else if (daemonHealth.contains("DISABLED")){
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Disabled.toString());
                        break;
                    } else {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                        break;
                    }
                } else {
                    drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Unknown.toString());
                }
            }
        } else {
            drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Unknown.toString());
        }
        disasterRecoveryClusterDao.update(drcluster.getId(), drcluster);
        response.setMirroringAgentStatus(drcluster.getMirroringAgentStatus());

        List<GetDisasterRecoveryClusterVmListResponse> disasterRecoveryClusterVmListResponse = setDisasterRecoveryClusterVmListResponse(drcluster.getId());
        response.setDisasterRecoveryClusterVmMap(disasterRecoveryClusterVmListResponse);

        String moldUrl = drcluster.getDrClusterUrl() + "/client/api/";
        String moldMethod = "GET";
        String moldCommandListServiceOfferings = "listServiceOfferings";
        List<ServiceOfferingResponse> secDrClusterServiceOfferingListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommandListServiceOfferings, moldMethod, secApiKey, secSecretKey);
        response.setSecDisasterRecoveryClusterServiceOfferingList(secDrClusterServiceOfferingListResponse);
        String moldCommandListNetworks = "listNetworks";
        List<NetworkResponse> secDrClusterNetworksListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommandListNetworks, moldMethod, secApiKey, secSecretKey);
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
        response.setDrClusterGlueIpAddress(drcluster.getDrClusterGlueIpAddress());
        response.setCreated(drcluster.getCreated());

        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drcluster.getId());
        String secApiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secSecretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        if (details != null && !details.isEmpty()) {
            response.setDetails(details);
        }

        // 미러링 데몬 상태 업데이트
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            String daemonHealth = null;
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                String glueCommand = "/mirror";
                String glueMethod = "GET";
                daemonHealth = DisasterRecoveryClusterUtil.glueMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                if (daemonHealth != null) {
                    if (daemonHealth.contains("OK")) {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        break;
                    } else if (daemonHealth.contains("WARNING")){
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Warning.toString());
                        break;
                    } else if (daemonHealth.contains("DISABLED")){
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Disabled.toString());
                        break;
                    } else {
                        drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                        break;
                    }
                } else {
                    drcluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Unknown.toString());
                }
            }
        }

        disasterRecoveryClusterDao.update(drcluster.getId(), drcluster);
        return response;
    }

    public GetDisasterRecoveryClusterVmListResponse setDisasterRecoveryClusterVmResponse(long mapId) {
        DisasterRecoveryClusterVmMapVO map = disasterRecoveryClusterVmMapDao.findById(mapId);
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(map.getDisasterRecoveryClusterId());
        String url = drcluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drcluster.getId());
        String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        GetDisasterRecoveryClusterVmListResponse response = new GetDisasterRecoveryClusterVmListResponse();
        UserVmJoinVO userVM = userVmJoinDao.findById(map.getVmId());
        response.setObjectName("disasterrecoveryclustervm");
        response.setDrClusterName(drcluster.getName());
        response.setDrClusterVmId(userVM.getUuid());
        response.setDrClusterVmName(userVM.getName());
        response.setDrClusterVmStatus(userVM.getState().toString());
        response.setMirroredVmId(map.getMirroredVmId());
        response.setMirroredVmStatus(map.getMirroredVmStatus());
        response.setMirroredVmName(map.getMirroredVmName());
        response.setMirroredVmVolumeType(map.getMirroredVmVolumeType());
        response.setMirroredVmVolumePath(map.getMirroredVmVolumePath());
        response.setMirroredVmVolumeStatus(map.getMirroredVmVolumeStatus());
        return response;
    }

    public List<GetDisasterRecoveryClusterVmListResponse> setDisasterRecoveryClusterVmListResponse(long clusterId) {
        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findById(clusterId);
        String url = drcluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drcluster.getId());
        String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        List<GetDisasterRecoveryClusterVmListResponse> disasterRecoveryClusterVmListResponse = new ArrayList<>();
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(clusterId);
        if (!CollectionUtils.isEmpty(vmMap)) {
            for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                GetDisasterRecoveryClusterVmListResponse response = new GetDisasterRecoveryClusterVmListResponse();
                UserVmJoinVO userVM = userVmJoinDao.findById(map.getVmId());
                response.setDrClusterName(drcluster.getName());
                response.setDrClusterVmId(userVM.getUuid());
                response.setDrClusterVmName(userVM.getName());
                response.setDrClusterVmStatus(userVM.getState().toString());
                response.setMirroredVmId(map.getMirroredVmId());
                response.setMirroredVmName(map.getMirroredVmName());
                response.setMirroredVmVolumeType(map.getMirroredVmVolumeType());
                response.setMirroredVmVolumePath(map.getMirroredVmVolumePath());
                // 미러링 가상머신 상태 조회
                String moldUrl = url + "/client/api/";
                String moldCommand = "listVirtualMachines";
                String moldMethod = "GET";
                String vmList = DisasterRecoveryClusterUtil.moldListVirtualMachinesAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
                if (vmList != null) {
                    JSONObject jsonObject = new JSONObject(vmList);
                    Object object = jsonObject.get("virtualmachine");
                    JSONArray array;
                    if (object instanceof JSONArray) {
                        array = (JSONArray) object;
                    } else {
                        array = new JSONArray();
                        array.put(object);
                    }
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject jSONObject = array.getJSONObject(i);
                        if (jSONObject.get("name").equals(userVM.getName())) {
                            map.setMirroredVmStatus(jSONObject.get("state").toString());
                            disasterRecoveryClusterVmMapDao.update(map.getId(), map);
                        }
                    }
                }
                response.setMirroredVmStatus(map.getMirroredVmStatus());
                // 미러링 가상머신 볼륨 상태 조회
                String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
                ipList = ipList.replaceAll(",$", "");
                String[] array = ipList.split(",");
                Loop :
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    String glueCommand = "/mirror/image/status/rbd/" +map.getMirroredVmVolumePath();
                    String glueMethod = "GET";
                    String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                    if (mirrorImageStatus != null) {
                        JsonArray drArray = (JsonArray) new JsonParser().parse(mirrorImageStatus).getAsJsonObject().get("peer_sites");
                        if (drArray.size() != 0 && drArray != null) {
                            for (JsonElement dr : drArray) {
                                JsonElement peerName = dr.getAsJsonObject().get("site_name") == null ? null : dr.getAsJsonObject().get("site_name");
                                JsonElement peerState = dr.getAsJsonObject().get("state") == null ? null : dr.getAsJsonObject().get("state");
                                if (peerName != null && peerState != null) {
                                    if (peerState.getAsString().contains("replaying")) {
                                        response.setDrClusterVmVolStatus("READY");
                                        map.setMirroredVmVolumeStatus("SYNCING");
                                    } else if (peerState.getAsString().contains("error")){
                                        response.setDrClusterVmVolStatus("ERROR");
                                        map.setMirroredVmVolumeStatus("ERROR");
                                    } else if (peerState.getAsString().contains("unknown")){
                                        response.setDrClusterVmVolStatus("UNKNOWN");
                                        map.setMirroredVmVolumeStatus("UNKNOWN");
                                    } else {
                                        response.setDrClusterVmVolStatus("SYNCING");
                                        map.setMirroredVmVolumeStatus("READY");
                                    }
                                    disasterRecoveryClusterVmMapDao.update(map.getId(), map);
                                    break Loop;
                                }
                            }
                        }
                    }
                }
                response.setMirroredVmVolumeStatus(map.getMirroredVmVolumeStatus());
                disasterRecoveryClusterVmListResponse.add(response);
            }
        }
        return disasterRecoveryClusterVmListResponse;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_UPDATE, eventDescription = "updating disaster recovery cluster", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public GetDisasterRecoveryClusterListResponse updateDisasterRecoveryCluster(UpdateDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryClusterService.DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery plugin is disabled");
        }
        DisasterRecoveryClusterVO drcluster = null;
        Long drClusterId = cmd.getId();
        String drClusterName = cmd.getName();
        if (drClusterId == null) {
            // secondary cluster로 요청이 온 경우
            drcluster = disasterRecoveryClusterDao.findByName(drClusterName);
        } else {
            drcluster = disasterRecoveryClusterDao.findById(drClusterId);
            if (drcluster == null) {
                throw new InvalidParameterValueException("Invalid Disaster Recovery id specified");
            }
        }
        Long drId = drcluster.getId();
        Map<String, String> drDetails = disasterRecoveryClusterDetailsDao.findDetails(drId);
        String name = drcluster.getName();
        String url = drcluster.getDrClusterUrl();
        drcluster = disasterRecoveryClusterDao.createForUpdate(drcluster.getId());
        if (cmd.getDescription() != null) {
            String drClusterDescription = cmd.getDescription();
            drcluster.setDescription(drClusterDescription);
        }
        if (cmd.getDrClusterUrl() != null) {
            String drClusterUrl = cmd.getDrClusterUrl();
            drcluster.setDrClusterUrl(drClusterUrl);
        }
        if (cmd.getDrClusterGlueIpAddress() != null) {
            String drClusterGlueIpAddress = cmd.getDrClusterGlueIpAddress();
            drcluster.setDrClusterGlueIpAddress(drClusterGlueIpAddress);
        }
        if (cmd.getDetails() != null) {
            Map<String,String> details = cmd.getDetails();
            if (drClusterId == null) {
                // secondary cluster로 요청이 온 경우
                drDetails.put("mirrorscheduleinterval", details.get("mirrorscheduleinterval"));
                drDetails.put("mirrorschedulestarttime", details.get("mirrorschedulestarttime"));
                drcluster.setDetails(drDetails);
                disasterRecoveryClusterDetailsDao.persist(drId, drDetails);
            } else {
                drcluster.setDetails(details);
                disasterRecoveryClusterDetailsDao.persist(drcluster.getId(), details);
                String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
                String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
                String moldUrl = url + "/client/api/";
                String moldCommand = "updateDisasterRecoveryCluster";
                String moldMethod = "GET";
                Map<String, String> moldParams = new HashMap<>();
                moldParams.put("name", name);
                moldParams.put("details[0].mirrorschedulestarttime", details.get("mirrorschedulestarttime"));
                moldParams.put("details[0].mirrorscheduleinterval", details.get("mirrorscheduleinterval"));
                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, moldParams);
                List<DisasterRecoveryClusterVmMapVO> drClusterVmList = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drcluster.getId());
                if (!CollectionUtils.isEmpty(drClusterVmList)) {
                    String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
                    if (ipList != null || !ipList.isEmpty()) {
                        ipList = ipList.replaceAll(",$", "");
                        String[] array = ipList.split(",");
                        for (DisasterRecoveryClusterVmMapVO vmMapVO : drClusterVmList) {
                            UserVmJoinVO userVM = userVmJoinDao.findById(vmMapVO.getVmId());
                            List<VolumeVO> volumes = volsDao.findByInstance(userVM.getId());
                            for (VolumeVO vol : volumes) {
                                String volumeUuid = vol.getPath();
                                Loop :
                                for (int i=0; i < array.length; i++) {
                                    // 미러링 스케줄 설정 업데이트 glue-api 호출
                                    String glueIp = array[i];
                                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                                    String glueCommand = "/mirror/image/status/rbd/" + volumeUuid;
                                    String glueMethod = "GET";
                                    String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                                    if (mirrorImageStatus != null) {
                                        JsonObject statObject = (JsonObject) new JsonParser().parse(mirrorImageStatus).getAsJsonObject();
                                        if (statObject.has("description")) {
                                            if (statObject.get("description").getAsString().equals("local image is primary")) {
                                                glueCommand = "/mirror/image/rbd/" + volumeUuid;
                                                glueMethod = "PUT";
                                                Map<String, String> glueParams = new HashMap<>();
                                                glueParams.put("mirrorPool", "rbd");
                                                glueParams.put("imageName", volumeUuid);
                                                glueParams.put("interval", details.get("mirrorscheduleinterval"));
                                                glueParams.put("startTime", details.get("mirrorschedulestarttime"));
                                                boolean result = DisasterRecoveryClusterUtil.glueImageMirrorSetupUpdateAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                                if (result) {
                                                    break;
                                                }
                                            } else {
                                                moldCommand = "listScvmIpAddress";
                                                String response = DisasterRecoveryClusterUtil.moldListScvmIpAddressAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
                                                if (response != null) {
                                                    String[] scvmList = response.split(",");
                                                    for (int j=0; j < scvmList.length; j++) {
                                                        glueIp = scvmList[j];
                                                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                                                        glueUrl = "https://" + glueIp + ":8080/api/v1";
                                                        glueCommand = "/mirror/image/rbd/" + volumeUuid;
                                                        glueMethod = "PUT";
                                                        Map<String, String> glueParams = new HashMap<>();
                                                        glueParams.put("mirrorPool", "rbd");
                                                        glueParams.put("imageName", volumeUuid);
                                                        glueParams.put("interval", details.get("mirrorscheduleinterval"));
                                                        glueParams.put("startTime", details.get("mirrorschedulestarttime"));
                                                        boolean result = DisasterRecoveryClusterUtil.glueImageMirrorSetupUpdateAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                                        if (result) {
                                                            break Loop;
                                                        }
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (cmd.getDrClusterStatus() != null) {
            String drClusterStatus = cmd.getDrClusterStatus();
            drcluster.setDrClusterStatus(drClusterStatus);
        }
        if (cmd.getMirroringAgentStatus() != null) {
            String mirroringAgentStatus = cmd.getMirroringAgentStatus();
            drcluster.setMirroringAgentStatus(mirroringAgentStatus);
        }
        if (!disasterRecoveryClusterDao.update(drcluster.getId(), drcluster)) {
            throw new CloudRuntimeException(String.format("Failed to update Disaster Recovery ID: %s", drcluster.getUuid()));
        }
        drcluster = disasterRecoveryClusterDao.findById(drcluster.getId());
        return setDisasterRecoveryClusterResponse(drcluster.getId());
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_UPDATE, eventDescription = "updating disaster recovery cluster vm map", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public GetDisasterRecoveryClusterVmListResponse updateDisasterRecoveryClusterVm(UpdateDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        // primary cluster 에서 미러링 가상머신을 추가한 경우 secondary cluster의 vm map DB 업데이트를 위한 코드
        DisasterRecoveryClusterVO drcluster = null;
        Long drClusterId = cmd.getId();
        String drClusterVmId = cmd.getDrClusterVmId();
        String mirrorVmId = cmd.getMirrorVmId();
        String mirrorVmName = cmd.getMirrorVmName();
        String mirrorVmStatus = cmd.getMirrorVmStatus();
        String mirrorVmVolType = cmd.getMirrorVmVolumeType();
        String mirrorVmVolPath = cmd.getMirrorVmVolumePath();
        String mirrorVmVolStatus = cmd.getMirrorVmVolumeStatus();
        String drClusterName = cmd.getDrClusterName();
        if (drClusterId == null) {
            // secondary cluster로 요청이 온 경우
            drcluster = disasterRecoveryClusterDao.findByName(drClusterName);
        } else {
            drcluster = disasterRecoveryClusterDao.findById(drClusterId);
        }
        VMInstanceVO vm = vmDao.findByUuid(drClusterVmId);
        DisasterRecoveryClusterVmMapVO newClusterVmMapVO = new DisasterRecoveryClusterVmMapVO(drcluster.getId(), vm.getId(), mirrorVmId, mirrorVmName, mirrorVmStatus, mirrorVmVolType, mirrorVmVolPath, mirrorVmVolStatus);
        disasterRecoveryClusterVmMapDao.persist(newClusterVmMapVO);
        return setDisasterRecoveryClusterVmResponse(newClusterVmMapVO.getId());
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
                        cmd.getDrClusterGlueIpAddress(), cmd.getDrClusterUrl(), cmd.getDrClusterType(), DisasterRecoveryCluster.DrClusterStatus.Created.toString(), DisasterRecoveryCluster.MirroringAgentStatus.Created.toString());
                disasterRecoveryClusterDao.persist(newCluster);
                Map<String, String> drDetails = disasterRecoveryClusterDetailsDao.findDetails(newCluster.getId());
                if (cmd.getDrClusterApiKey() != null) {
                    drDetails.put(ApiConstants.DR_CLUSTER_API_KEY, cmd.getDrClusterApiKey());
                }
                if (cmd.getDrClusterSecretKey() != null) {
                    drDetails.put(ApiConstants.DR_CLUSTER_SECRET_KEY, cmd.getDrClusterSecretKey());
                }
                if (cmd.getDrClusterPrivateKey() != null) {
                    drDetails.put(ApiConstants.DR_CLUSTER_PRIVATE_KEY, cmd.getDrClusterPrivateKey());
                }
                drDetails.put("mirrorscheduleinterval", "1h");
                drDetails.put("mirrorschedulestarttime", "");
                disasterRecoveryClusterDetailsDao.persist(newCluster.getId(), drDetails);
                return newCluster;
            }
        });
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Disaster recovery cluster name: %s and ID: %s has been created", cluster.getName(), cluster.getUuid()));
        }
        return cluster;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_CREATE, eventDescription = "creating disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean setupDisasterRecoveryCluster(long clusterId) throws CloudRuntimeException {
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(clusterId);
        String drName = drCluster.getName();
        String drDescription = drCluster.getDescription();
        String secUrl = drCluster.getDrClusterUrl();
        String secClusterType = drCluster.getDrClusterType();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String secApiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secSecretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String secPrivateKey = details.get(ApiConstants.DR_CLUSTER_PRIVATE_KEY);
        String secGlueIpAddress = drCluster.getDrClusterGlueIpAddress();
        try {
            FileOutputStream fos = new FileOutputStream("glue.key");
            fos.write(secPrivateKey.getBytes());
            fos.close();
        } catch (IOException e) {
            throw new CloudRuntimeException("Converting the secondary cluster's private key to a file failed.", e);
        }
        File permKey = new File("glue.key");
        String[] properties = getServerProperties();
        ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
        String priUrl = properties[1] + "://" + msHost.getServiceIP() + ":" + properties[0];
        String priClusterType = "primary";
        UserAccount user = accountService.getActiveUserAccount("admin", 1L);
        String priApiKey = user.getApiKey();
        String priSecretKey = user.getSecretKey();
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
        if (secResponse == null) {
            drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
            drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
            disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
            throw new CloudRuntimeException("Failed to request createDisasterRecoveryCluster Mold-API.");
        } else {
            JSONObject jsonObject = new JSONObject(secResponse);
            String jobId = jsonObject.get("jobid").toString();
            int jobStatus = getAsyncJobResult(secUrl + "/client/api/", secApiKey, secSecretKey, jobId);
            if (jobStatus == 2) {
                throw new CloudRuntimeException("CreateDisasterRecoveryCluster Mold-API async job resulted in failure.");
            }
            String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
            if (ipList != null || !ipList.isEmpty()) {
                // 클러스터 설정
                ipList = ipList.replaceAll(",$", "");
                String[] array = ipList.split(",");
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    String glueCommand = "/mirror";
                    String glueMethod = "POST";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("localClusterName", "local");
                    glueParams.put("remoteClusterName", "remote");
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("host", secGlueIpAddress);
                    boolean result = DisasterRecoveryClusterUtil.glueMirrorSetupAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                    if (result) {
                        drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                        disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                        secCommand = "updateDisasterRecoveryCluster";
                        secMethod = "GET";
                        Map<String, String> sucParams = new HashMap<>();
                        sucParams.put("name", drName);
                        sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                        DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                        return true;
                    }
                }
                // 클러스터 설정 실패
                drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                secCommand = "updateDisasterRecoveryCluster";
                secMethod = "GET";
                Map<String, String> sucParams = new HashMap<>();
                sucParams.put("name", drName);
                sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, sucParams);
            } else {
                // scvm IP 조회 실패
                drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                secCommand = "updateDisasterRecoveryCluster";
                secMethod = "GET";
                Map<String, String> errParams = new HashMap<>();
                errParams.put("name", drName);
                errParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Error.toString());
                errParams.put("mirroringagentstatus", DisasterRecoveryCluster.MirroringAgentStatus.Error.toString());
                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, errParams);
                throw new CloudRuntimeException("Failed to lookup primary cluster scvm ip address.");
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
        response.setDrClusterGlueIpAddress(drcluster.getDrClusterGlueIpAddress());
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drcluster.getId());
        if (details != null && !details.isEmpty()) {
            response.setDetails(details);
        }
        response.setCreated(drcluster.getCreated());
        return response;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_DELETE, eventDescription = "deleting disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean deleteDisasterRecoveryCluster(DeleteDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        // Secondary Cluster에서 요청한 경우
        if (drCluster.getDrClusterType().equalsIgnoreCase("primary")) {
            disasterRecoveryClusterDetailsDao.deleteDetails(drCluster.getId());
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMap vm : vmMap) {
                    disasterRecoveryClusterVmMapDao.remove(vm.getId());
                }
            }
            return disasterRecoveryClusterDao.remove(drCluster.getId());
        } else {
            // Primary Cluster에서 요청한 경우
            String drName = drCluster.getName();
            String secUrl = drCluster.getDrClusterUrl();
            String secClusterType = drCluster.getDrClusterType();
            Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
            String secApiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
            String secSecretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
            String secPrivateKey = details.get(ApiConstants.DR_CLUSTER_PRIVATE_KEY);
            String secGlueIpAddress = drCluster.getDrClusterGlueIpAddress();
            try {
                FileOutputStream fos = new FileOutputStream("glue.key");
                fos.write(secPrivateKey.getBytes());
                fos.close();
            } catch (IOException e) {
                throw new CloudRuntimeException("Converting the secondary cluster's private key to a file failed.", e);
            }
            File permKey = new File("glue.key");
            String[] properties = getServerProperties();
            ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
            String priUrl = properties[1] + "://" + msHost.getServiceIP() + ":" + properties[0];
            String priClusterType = "primary";
            UserAccount user = accountService.getActiveUserAccount("admin", 1L);
            String priApiKey = user.getApiKey();
            String priSecretKey = user.getSecretKey();
            // 미러링 클러스터 삭제 glue-api 호출
            String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
            if (ipList != null || !ipList.isEmpty()) {
                ipList = ipList.replaceAll(",$", "");
                String[] array = ipList.split(",");
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    String glueCommand = "/mirror";
                    String glueMethod = "DELETE";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("host", secGlueIpAddress);
                    boolean result = DisasterRecoveryClusterUtil.glueMirrorDeleteAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                    if (result) {
                        disasterRecoveryClusterDetailsDao.deleteDetails(drCluster.getId());
                        disasterRecoveryClusterDao.remove(drCluster.getId());
                        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
                        if (!CollectionUtils.isEmpty(vmMap)) {
                            for (DisasterRecoveryClusterVmMap vm : vmMap) {
                                disasterRecoveryClusterVmMapDao.remove(vm.getId());
                            }
                        }
                        String secCommand = "getDisasterRecoveryClusterList";
                        String secMethod = "GET";
                        Map<String, String> sucParams = new HashMap<>();
                        String drList = DisasterRecoveryClusterUtil.moldGetDisasterRecoveryClusterListAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey);
                        if (drList != null || !drList.isEmpty()) {
                            JSONObject jsonObject = new JSONObject(drList);
                            Object object = jsonObject.get("disasterrecoverycluster");
                            JSONArray arr;
                            if (object instanceof JSONArray) {
                                arr = (JSONArray) object;
                            } else {
                                arr = new JSONArray();
                                arr.put(object);
                            }
                            for (int j = 0; j < arr.length(); j++) {
                                JSONObject jSONObject = arr.getJSONObject(j);
                                if (jSONObject.get("name").equals(drCluster.getName())) {
                                    String primaryDrId = jSONObject.get("id").toString();
                                    secCommand = "deleteDisasterRecoveryCluster";
                                    secMethod = "GET";
                                    sucParams = new HashMap<>();
                                    sucParams.put("id", primaryDrId);
                                    String response = DisasterRecoveryClusterUtil.moldDeleteDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                                    if (response == null) {
                                        throw new CloudRuntimeException("Failed to request DeleteDisasterRecoveryCluster Mold-API.");
                                    } else {
                                        JSONObject jObject = new JSONObject(response);
                                        String jobId = jObject.get("jobid").toString();
                                        int jobStatus = getAsyncJobResult(secUrl + "/client/api/", secApiKey, secSecretKey, jobId);
                                        if (jobStatus == 2) {
                                            throw new CloudRuntimeException("DeleteDisasterRecoveryCluster Mold-API async job resulted in failure.");
                                        }
                                        return true;
                                    }
                                }
                            }
                        } else {
                            return false;
                        }
                        break;
                    }
                }
            } else {
                throw new CloudRuntimeException("Failed to lookup primary cluster scvm ip address.");
            }
            return false;
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_ENABLE, eventDescription = "enabling disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean enableDisasterRecoveryCluster(EnableDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        String drName = drCluster.getName();
        String secUrl = drCluster.getDrClusterUrl();
        String secClusterType = drCluster.getDrClusterType();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String secApiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secSecretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String secPrivateKey = details.get(ApiConstants.DR_CLUSTER_PRIVATE_KEY);
        String secGlueIpAddress = drCluster.getDrClusterGlueIpAddress();
        try {
            FileOutputStream fos = new FileOutputStream("glue.key");
            fos.write(secPrivateKey.getBytes());
            fos.close();
        } catch (IOException e) {
            throw new CloudRuntimeException("Converting the secondary cluster's private key to a file failed.", e);
        }
        File permKey = new File("glue.key");
        String[] properties = getServerProperties();
        ManagementServerHostVO msHost = msHostDao.findByMsid(ManagementServerNode.getManagementServerId());
        String priUrl = properties[1] + "://" + msHost.getServiceIP() + ":" + properties[0];
        String priClusterType = "primary";
        UserAccount user = accountService.getActiveUserAccount("admin", 1L);
        String priApiKey = user.getApiKey();
        String priSecretKey = user.getSecretKey();
        // 미러링 활성화 glue-api 호출
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                String glueCommand = "/mirror/rbd";
                String glueMethod = "POST";
                Map<String, String> glueParams = new HashMap<>();
                glueParams.put("localClusterName", "local");
                glueParams.put("remoteClusterName", "remote");
                glueParams.put("mirrorPool", "rbd");
                glueParams.put("host", secGlueIpAddress);
                boolean result = DisasterRecoveryClusterUtil.glueMirrorEnableAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                if (result) {
                    drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                    drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                    disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                    String secCommand = "getDisasterRecoveryClusterList";
                    String secMethod = "GET";
                    Map<String, String> sucParams = new HashMap<>();
                    String drList = DisasterRecoveryClusterUtil.moldGetDisasterRecoveryClusterListAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey);
                    if (drList != null || !drList.isEmpty()) {
                        JSONObject jsonObject = new JSONObject(drList);
                        Object object = jsonObject.get("disasterrecoverycluster");
                        JSONArray arr;
                        if (object instanceof JSONArray) {
                            arr = (JSONArray) object;
                        } else {
                            arr = new JSONArray();
                            arr.put(object);
                        }
                        for (int j = 0; j < arr.length(); j++) {
                            JSONObject jSONObject = arr.getJSONObject(j);
                            if (jSONObject.get("name").equals(drCluster.getName())) {
                                secCommand = "updateDisasterRecoveryCluster";
                                secMethod = "GET";
                                sucParams.put("name", drName);
                                sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Enabled.toString());
                                sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.MirroringAgentStatus.Enabled.toString());
                                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                                return true;
                            }
                        }
                    } else {
                        return false;
                    }
                }
            }
        } else {
            throw new CloudRuntimeException("Failed to lookup primary cluster scvm ip address.");
        }
        return false;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_DISABLE, eventDescription = "disabling disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean disableDisasterRecoveryCluster(DisableDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        String drName = drCluster.getName();
        String secUrl = drCluster.getDrClusterUrl();
        String secClusterType = drCluster.getDrClusterType();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String secApiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secSecretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String secPrivateKey = details.get(ApiConstants.DR_CLUSTER_PRIVATE_KEY);
        String secGlueIpAddress = drCluster.getDrClusterGlueIpAddress();
        try {
            FileOutputStream fos = new FileOutputStream("glue.key");
            fos.write(secPrivateKey.getBytes());
            fos.close();
        } catch (IOException e) {
            throw new CloudRuntimeException("Converting the secondary cluster's private key to a file failed.", e);
        }
        File permKey = new File("glue.key");
        // 미러링 비활성화 glue-api 호출
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                String glueCommand = "/mirror/rbd";
                String glueMethod = "DELETE";
                Map<String, String> glueParams = new HashMap<>();
                glueParams.put("mirrorPool", "rbd");
                glueParams.put("host", secGlueIpAddress);
                boolean result = DisasterRecoveryClusterUtil.glueMirrorDisableAPI(glueUrl, glueCommand, glueMethod, glueParams, permKey);
                if (result) {
                    drCluster.setDrClusterStatus(DisasterRecoveryCluster.DrClusterStatus.Disabled.toString());
                    drCluster.setMirroringAgentStatus(DisasterRecoveryCluster.MirroringAgentStatus.Disabled.toString());
                    disasterRecoveryClusterDao.update(drCluster.getId(), drCluster);
                    List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
                    if (!CollectionUtils.isEmpty(vmMap)) {
                        for (DisasterRecoveryClusterVmMap vm : vmMap) {
                            disasterRecoveryClusterVmMapDao.remove(vm.getId());
                        }
                    }
                    String secCommand = "getDisasterRecoveryClusterList";
                    String secMethod = "GET";
                    Map<String, String> sucParams = new HashMap<>();
                    String drList = DisasterRecoveryClusterUtil.moldGetDisasterRecoveryClusterListAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey);
                    if (drList != null || !drList.isEmpty()) {
                        JSONObject jsonObject = new JSONObject(drList);
                        Object object = jsonObject.get("disasterrecoverycluster");
                        JSONArray arr;
                        if (object instanceof JSONArray) {
                            arr = (JSONArray) object;
                        } else {
                            arr = new JSONArray();
                            arr.put(object);
                        }
                        for (int j = 0; j < arr.length(); j++) {
                            JSONObject jSONObject = arr.getJSONObject(j);
                            if (jSONObject.get("name").equals(drCluster.getName())) {
                                secCommand = "updateDisasterRecoveryCluster";
                                secMethod = "GET";
                                sucParams.put("name", drName);
                                sucParams.put("drclusterstatus", DisasterRecoveryCluster.DrClusterStatus.Disabled.toString());
                                sucParams.put("mirroringagentstatus", DisasterRecoveryCluster.MirroringAgentStatus.Disabled.toString());
                                DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, sucParams);
                                if (!CollectionUtils.isEmpty(vmMap)) {
                                    for (DisasterRecoveryClusterVmMap vms : vmMap) {
                                        secCommand = "deleteDisasterRecoveryClusterVm";
                                        Map<String, String> vmParams = new HashMap<>();
                                        vmParams.put("drclustername", drCluster.getName());
                                        vmParams.put("virtualmachineid", vms.getMirroredVmId());
                                        DisasterRecoveryClusterUtil.moldDeleteDisasterRecoveryClusterVmAPI(secUrl + "/client/api/", secCommand, secMethod, secApiKey, secSecretKey, vmParams);
                                    }
                                }
                                return true;
                            }
                        }
                    } else {
                        return false;
                    }
                }
            }
        } else {
            throw new CloudRuntimeException("Failed to lookup primary cluster scvm ip address.");
        }
        return false;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_PROMOTE, eventDescription = "promoting disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean promoteDisasterRecoveryCluster(PromoteDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        validateDisasterRecoveryClusterMirrorParameters(drCluster);
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            String glueIp = "";
            String glueUrl = "";
            String glueCommand = "";
            String glueMethod = "";
            int glueStep = 0;
            boolean result = false;
            // DR 상황 발생 시 glue-API로 이미지를 조회하지 않고, vmMap 조회하여 실행
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                    String imageName = map.getMirroredVmVolumePath();
                    Loop :
                    for (int i=0; i < array.length; i++) {
                        glueIp = array[i];
                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                        glueUrl = "https://" + glueIp + ":8080/api/v1";
                        glueCommand = "/mirror/image/status/rbd/" +imageName;
                        glueMethod = "GET";
                        String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                        if (mirrorImageStatus != null) {
                            JsonObject statObject = (JsonObject) new JsonParser().parse(mirrorImageStatus).getAsJsonObject();
                            if (statObject.has("description")) {
                                if (!statObject.get("description").getAsString().equals("local image is primary")) {
                                    Map<String, String> glueParams = new HashMap<>();
                                    glueParams.put("mirrorPool", "rbd");
                                    glueParams.put("imageName", imageName);
                                    glueCommand = "/mirror/image/promote/rbd/" + imageName;
                                    glueMethod = "POST";
                                    while(glueStep < 100) {
                                        glueStep += 1;
                                        result = DisasterRecoveryClusterUtil.glueImageMirrorPromoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                        if (result) {
                                            glueCommand = "/mirror/image/rbd/" + imageName;
                                            glueMethod = "PUT";
                                            glueParams = new HashMap<>();
                                            glueParams.put("mirrorPool", "rbd");
                                            glueParams.put("imageName", imageName);
                                            glueParams.put("interval", details.get("mirrorscheduleinterval"));
                                            glueParams.put("startTime", details.get("mirrorschedulestarttime"));
                                            boolean schedule = DisasterRecoveryClusterUtil.glueImageMirrorSetupUpdateAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                            if (!schedule) {
                                                LOGGER.error("Failed to request ImageMirrorSetupUpdate Glue-API.");
                                                LOGGER.error("The image was promoted successfully, but scheduling the image failed. For volumes with a path of " + imageName + ", please add a schedule manually.");
                                            }
                                            break Loop;
                                        } else {
                                            LOGGER.error("Failed to request ImageMirrorPromote Glue-API.");
                                        }
                                    }
                                    throw new CloudRuntimeException("Failed to promote image, For volumes with a path of " + imageName + ", You must manually promote local images and resync remote image, add a local image snapshots schedule.");
                                } else {
                                    break;
                                }
                            }
                        } else {
                            LOGGER.error("Failed to request mirror image status Glue-API.");
                        }
                    }
                }
                if (glueStep == 0) {
                    throw new CloudRuntimeException("Demote cannot be executed because the current image is in Ready state.");
                }
                promoteParentImage(drCluster);
                timeSleep();
                return result;
            } else {
                throw new CloudRuntimeException("There are no images being mirrored.");
            }
        } else {
            throw new CloudRuntimeException("Failed to lookup secondary cluster scvm ip address.");
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_DEMOTE, eventDescription = "demoting disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean demoteDisasterRecoveryCluster(DemoteDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        validateDemoteDisasterRecoveryClusterMirrorParameters(drCluster);
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            String glueIp = "";
            String glueUrl = "";
            String glueCommand = "";
            String glueMethod = "";
            int glueStep = 0;
            boolean result = false;
            // DR 상황 발생 시 glue-API로 이미지를 조회하지않고, vmMap 조회하여 실행
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                    String imageName = map.getMirroredVmVolumePath();
                    Loop :
                    for (int i=0; i < array.length; i++) {
                        glueIp = array[i];
                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                        glueUrl = "https://" + glueIp + ":8080/api/v1";
                        glueCommand = "/mirror/image/status/rbd/" +imageName;
                        glueMethod = "GET";
                        String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                        if (mirrorImageStatus != null) {
                            JsonObject statObject = (JsonObject) new JsonParser().parse(mirrorImageStatus).getAsJsonObject();
                            if (statObject.has("description")) {
                                if (!statObject.get("description").getAsString().contains("replaying")) {
                                    glueCommand = "/mirror/image/demote/rbd/" + imageName;
                                    glueMethod = "DELETE";
                                    Map<String, String> glueParams = new HashMap<>();
                                    glueParams.put("mirrorPool", "rbd");
                                    glueParams.put("imageName", imageName);
                                    result = DisasterRecoveryClusterUtil.glueImageMirrorDemoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                    if (result) {
                                        glueCommand = "/mirror/image/promote/peer/rbd/" + imageName;
                                        glueMethod = "POST";
                                        while(glueStep < 100) {
                                            glueStep += 1;
                                            try {
                                                Thread.sleep(10000);
                                            } catch (InterruptedException e) {
                                                LOGGER.error("demoteDisasterRecoveryCluster sleep interrupted");
                                            }
                                            result = DisasterRecoveryClusterUtil.glueImageMirrorPromoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                            if (result) {
                                                glueCommand = "/mirror/image/resync/rbd/" + imageName;
                                                glueMethod = "PUT";
                                                boolean resync = DisasterRecoveryClusterUtil.glueImageMirrorResyncAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                                if (resync) {
                                                    glueCommand = "/mirror/image/rbd/" + imageName;
                                                    glueParams = new HashMap<>();
                                                    glueParams.put("mirrorPool", "rbd");
                                                    glueParams.put("imageName", imageName);
                                                    glueParams.put("interval", details.get("mirrorscheduleinterval"));
                                                    glueParams.put("startTime", details.get("mirrorschedulestarttime"));
                                                    glueParams.put("imageRegion", "remote");
                                                    boolean schedule = DisasterRecoveryClusterUtil.glueImageMirrorSetupUpdateAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                                    if (!schedule) {
                                                        LOGGER.error("Failed to request ImageMirrorSetupUpdate Glue-API.");
                                                        LOGGER.error("The image was promoted successfully, but scheduling the image failed. For volumes with a path of " + imageName + ", please add a schedule manually.");
                                                    }
                                                } else {
                                                    LOGGER.error("Failed to request ImageMirrorResync Glue-API.");
                                                    LOGGER.error("The image was promoted successfully, but resyncing the image failed. For volumes with a path of " + imageName + ", Manually set image resync and snapshot schedules.");
                                                }
                                                break Loop;
                                            } else {
                                                LOGGER.error("Failed to request ImageMirrorPromotePeer Glue-API.");
                                            }
                                        }
                                        throw new CloudRuntimeException("Failed to promote remote image, For volumes with a path of " + imageName + ", You must manually promote remote images and resync local image, add a remote image snapshots schedule.");
                                    } else {
                                        LOGGER.error("Failed to request ImageMirrorDemote Glue-API.");
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else {
                            LOGGER.error("Failed to request mirror image status Glue-API.");
                        }
                    }
                }
                if (glueStep == 0) {
                    throw new CloudRuntimeException("Demote cannot be executed because the current image is in Syncing state.");
                }
                timeSleep();
                demoteParentImage(drCluster);
                return result;
            } else {
                throw new CloudRuntimeException("There are no images being mirrored.");
            }
        } else {
            throw new CloudRuntimeException("Failed to lookup secondary cluster scvm ip address.");
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_RESYNC, eventDescription = "resyncing disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean resyncDisasterRecoveryCluster(ResyncDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        validateResyncDisasterRecoveryClusterMirrorParameters(drCluster);
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            String glueIp = "";
            String glueUrl = "";
            String glueCommand = "";
            String glueMethod = "";
            int glueStep = 0;
            boolean result = false;
            // DR 상황 발생 후 Primary 클러스터를 복구하여 재동기화하는 경우 사용
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                    String imageName = map.getMirroredVmVolumePath();
                    Loop :
                    for (int i=0; i < array.length; i++) {
                        glueIp = array[i];
                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                        glueUrl = "https://" + glueIp + ":8080/api/v1";
                        glueCommand = "/mirror/image/status/rbd/" +imageName;
                        glueMethod = "GET";
                        String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                        if (mirrorImageStatus != null) {
                            JsonObject statObject = (JsonObject) new JsonParser().parse(mirrorImageStatus).getAsJsonObject();
                            JsonArray drArray = (JsonArray) new JsonParser().parse(mirrorImageStatus).getAsJsonObject().get("peer_sites");
                            JsonElement peerDescription = null;
                            if (drArray.size() != 0 && drArray != null) {
                                for (JsonElement dr : drArray) {
                                    peerDescription = dr.getAsJsonObject().get("description") == null ? null : dr.getAsJsonObject().get("description");
                                }
                            }
                            if (statObject.has("description") && peerDescription != null) {
                                if (statObject.get("description").getAsString().contains("force promoting") || (peerDescription.getAsString().contains("local image is primary") && statObject.get("description").getAsString().contains("local image is primary"))) {
                                    glueCommand = "/mirror/image/demote/peer/rbd/" + imageName;
                                    glueMethod = "DELETE";
                                    Map<String, String> glueParams = new HashMap<>();
                                    glueParams.put("mirrorPool", "rbd");
                                    glueParams.put("imageName", imageName);
                                    result = DisasterRecoveryClusterUtil.glueImageMirrorDemoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                    if (result) {
                                        while(glueStep < 100) {
                                            glueStep += 1;
                                            glueCommand = "/mirror/image/resync/peer/rbd/" + imageName;
                                            glueMethod = "PUT";
                                            result = DisasterRecoveryClusterUtil.glueImageMirrorResyncAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                            if (result) {
                                                break Loop;
                                            } else {
                                                LOGGER.error("Failed to request ImageMirrorResyncPeer Glue-API.");
                                                LOGGER.error("The peer image was demoted successfully, but resync the image failed. For volumes with a path of " + imageName + ", Manually set image resync.");
                                            }
                                        }
                                        throw new CloudRuntimeException("Failed to resync primary cluster image, For volumes with a path of " + imageName + ", You must manually resync primary cluster image.");
                                    } else {
                                        LOGGER.error("Failed to request ImageMirrorDemotePeer Glue-API.");
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else {
                            LOGGER.error("Failed to request mirror image status Glue-API.");
                        }
                    }
                }
                if (glueStep == 0) {
                    throw new CloudRuntimeException("Resync cannot be executed because the current image is not force promoting state.");
                }
                resyncParentImage(drCluster);
                timeSleep();
                return result;
            } else {
                throw new CloudRuntimeException("There are no images being mirrored.");
            }
        } else {
            throw new CloudRuntimeException("Failed to lookup primary cluster scvm ip address.");
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_CLEAR, eventDescription = "clearing disaster recovery cluster", async = true, resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean clearDisasterRecoveryCluster(ClearDisasterRecoveryClusterCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findById(cmd.getId());
        if (drCluster == null) {
            throw new InvalidParameterValueException("Invalid disaster recovery cluster id specified");
        }
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            String glueIp = "";
            String glueUrl = "";
            String glueCommand = "";
            String glueMethod = "";
            int glueStep = 0;
            boolean result = false;
            // DR 상황 발생 후 Primary 클러스터를 복구하지 않고, 가비지를 삭제하는 경우
            Map<String, String> glueParams = new HashMap<>();
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                    String imageName = map.getMirroredVmVolumePath();
                    for (int i=0; i < array.length; i++) {
                        glueIp = array[i];
                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                        glueUrl = "https://" + glueIp + ":8080/api/v1";
                        glueCommand = "/mirror/image/rbd/" + imageName;
                        glueMethod = "DELETE";
                        glueParams = new HashMap<>();
                        glueParams.put("mirrorPool", "rbd");
                        glueParams.put("imageName", imageName);
                        result = DisasterRecoveryClusterUtil.glueImageMirrorDeleteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                        if (result) {
                            break;
                        }
                    }
                    if (!result) {
                        throw new CloudRuntimeException("Clear cannot be executed because the current image failed to disable mirroring image. volume path : " + imageName);
                    }
                }
            }
            for (int j=0; j < array.length; j++) {
                glueIp = array[j];
                glueUrl = "https://" + glueIp + ":8080/api/v1";
                glueCommand = "/mirror/garbage";
                glueMethod = "DELETE";
                glueParams = new HashMap<>();
                glueParams.put("mirrorPool", "rbd");
                result = DisasterRecoveryClusterUtil.glueMirrorDeleteGarbageAPI(glueUrl, glueCommand, glueMethod, glueParams);
                if (result) {
                    break;
                }
            }
            return result;
        } else {
            throw new CloudRuntimeException("Failed to lookup primary cluster scvm ip address.");
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_CREATE, eventDescription = "creating disaster recovery virtual machine", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean setupDisasterRecoveryClusterVm(CreateDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        validateDisasterRecoveryClusterVmCreateParameters(cmd);
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(cmd.getDrClusterName());
        UserVmJoinVO userVM = userVmJoinDao.findById(cmd.getVmId());
        String url = drCluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String interval = details.get("mirrorscheduleinterval");
        String startTime = details.get("mirrorschedulestarttime");
        String offeringId = "";
        String networkId = "";
        // vm 의 볼륨 미러링 설정 glue-api 호출
        List<VolumeVO> volumes = volsDao.findByInstance(userVM.getId());
        boolean result = false;
        for (VolumeVO vol : volumes) {
            String volumeUuid = vol.getPath();
            String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
            if (ipList != null || !ipList.isEmpty()) {
                ipList = ipList.replaceAll(",$", "");
                String[] array = ipList.split(",");
                for (int i=0; i < array.length; i++) {
                    String glueIp = array[i];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    String glueCommand = "/mirror/image/rbd/" + volumeUuid;
                    String glueMethod = "POST";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("imageName", volumeUuid);
                    glueParams.put("interval", interval);
                    glueParams.put("startTime", startTime);
                    result = DisasterRecoveryClusterUtil.glueImageMirrorSetupAPI(glueUrl, glueCommand, glueMethod, glueParams);
                    if (result) {
                        break;
                    }
                }
                if (!result) {
                    return result;
                }
            } else {
                throw new CloudRuntimeException("secondary cluster scvm list lookup fails.");
            }
        }
        // 미러링 가상머신 생성을 위한 목록 조회 mold-api 호출
        String moldUrl = url + "/client/api/";
        String moldMethod = "GET";
        String moldCommand = "listServiceOfferings";
        List<ServiceOfferingResponse> secDrClusterServiceOfferingListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        for (ServiceOfferingResponse serviceOff : secDrClusterServiceOfferingListResponse) {
            if (serviceOff.getName().equals(cmd.getServiceOfferingName())) {
                offeringId = serviceOff.getId();
            }
        }
        moldCommand = "listNetworks";
        List<NetworkResponse> secDrClusterNetworksListResponse = DisasterRecoveryClusterUtil.getSecDrClusterInfoList(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        for (NetworkResponse net : secDrClusterNetworksListResponse) {
            if (net.getName().equals(cmd.getNetworkName())) {
                networkId = net.getId();
            }
        }
        moldCommand = "listStoragePoolsMetrics";
        String psInfo = DisasterRecoveryClusterUtil.moldListStoragePoolsMetricsAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        String zoneId = new JsonParser().parse(psInfo).getAsJsonObject().get("zoneid").getAsString();
        String poolId = new JsonParser().parse(psInfo).getAsJsonObject().get("id").getAsString();
        moldCommand = "listAccounts";
        String domainId = DisasterRecoveryClusterUtil.moldListAccountsAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        moldCommand = "listDiskOfferings";
        String diskOfferingId = DisasterRecoveryClusterUtil.moldListDiskOfferingsAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        String jobId = null;
        int jobStatus = 0;
        JSONObject jsonObject;
        // 미러링 ROOT 디스크 생성 및 편집 mold-api 호출
        List<VolumeVO> rootVolumes = volsDao.findByInstanceAndType(userVM.getId(), Volume.Type.ROOT);
        VolumeVO rootVol = rootVolumes.get(0);
        String rootVolumeUuid = rootVol.getPath();
        moldMethod = "POST";
        moldCommand = "createVolume";
        Map<String, String> volParams = new HashMap<>();
        volParams.put("diskofferingid", diskOfferingId);
        volParams.put("size", String.valueOf(rootVol.getSize() / (1024 * 1024 * 1024)));
        volParams.put("name", rootVolumeUuid);
        volParams.put("zoneid", zoneId);
        String createVolResult = DisasterRecoveryClusterUtil.moldCreateVolumeAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, volParams);
        if (createVolResult == null) {
            throw new CloudRuntimeException("Failed to request createVolume Mold-API.");
        } else {
            jsonObject = new JSONObject(createVolResult);
            jobId = jsonObject.get("jobid").toString();
            String rootVolumeId = jsonObject.get("id").toString();
            jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
            if (jobStatus == 2) {
                throw new CloudRuntimeException("CreateVolume Mold-API async job resulted in failure.");
            }
            moldMethod = "GET";
            moldCommand = "updateVolume";
            Map<String, String> volUpParams = new HashMap<>();
            volUpParams.put("id", rootVolumeId);
            volUpParams.put("path", rootVolumeUuid);
            volUpParams.put("storageid", poolId);
            volUpParams.put("state", "Ready");
            volUpParams.put("type", rootVol.getVolumeType().toString());
            String updateVolResult = DisasterRecoveryClusterUtil.moldUpdateVolumeAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, volUpParams);
            if (updateVolResult == null) {
                throw new CloudRuntimeException("Failed to request updateVolume Mold-API.");
            } else {
                jsonObject = new JSONObject(updateVolResult);
                jobId = jsonObject.get("jobid").toString();
                jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
                if (jobStatus == 2) {
                    throw new CloudRuntimeException("UpdateVolume Mold-API async job resulted in failure.");
                }
                // 생성된 ROOT 디스크로 미러링 가상머신 생성
                moldMethod = "POST";
                moldCommand = "deployVirtualMachineForVolume";
                Map<String, String> vmParams = new HashMap<>();
                vmParams.put("volumeid", rootVolumeId);
                vmParams.put("zoneid", zoneId);
                vmParams.put("serviceofferingid", offeringId);
                vmParams.put("rootdisksize", String.valueOf(rootVol.getSize() / (1024 * 1024 * 1024)));
                vmParams.put("name", userVM.getName());
                vmParams.put("displayname", userVM.getDisplayName());
                vmParams.put("domainid", domainId);
                vmParams.put("iptonetworklist[0].networkid", networkId);
                vmParams.put("account", "admin");
                vmParams.put("startvm", "false");
                vmParams.put("hypervisor", "KVM");
                vmParams.put("keypairs", "");
                vmParams.put("boottype", "BIOS");
                vmParams.put("affinitygroupids", "");
                vmParams.put("bootmode", "LEGACY");
                vmParams.put("tpmversion", "NONE");
                vmParams.put("dynamicscalingenabled", "true");
                vmParams.put("iothreadsenabled", "true");
                vmParams.put("iodriverpolicy", "io_uring");
                String deployVmResult = DisasterRecoveryClusterUtil.moldDeployVirtualMachineForVolumeAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, vmParams);
                if (deployVmResult == null) {
                    throw new CloudRuntimeException("Failed to request DeployVirtualMachineForVolume Mold-API.");
                } else {
                    jsonObject = new JSONObject(deployVmResult);
                    jobId = jsonObject.get("jobid").toString();
                    String vmId = jsonObject.get("id").toString();
                    jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
                    if (jobStatus == 2) {
                        throw new CloudRuntimeException("DeployVirtualMachineForVolume Mold-API async job resulted in failure.");
                    }
                    DisasterRecoveryClusterVmMapVO newClusterVmMapVO = new DisasterRecoveryClusterVmMapVO(drCluster.getId(), cmd.getVmId(), vmId, userVM.getName(), "Stopped", rootVol.getVolumeType().toString(), rootVolumeUuid, "SYNCING");
                    disasterRecoveryClusterVmMapDao.persist(newClusterVmMapVO);
                    moldMethod = "GET";
                    moldCommand = "updateDisasterRecoveryClusterVm";
                    Map<String, String> vmMapParams = new HashMap<>();
                    vmMapParams.put("drclustername", drCluster.getName());
                    vmMapParams.put("drclustervmid", vmId);
                    vmMapParams.put("drclustermirrorvmid", userVM.getUuid());
                    vmMapParams.put("drclustermirrorvmname", userVM.getName());
                    vmMapParams.put("drclustermirrorvmstatus", userVM.getState().toString());
                    vmMapParams.put("drclustermirrorvmvoltype", rootVol.getVolumeType().toString());
                    vmMapParams.put("drclustermirrorvmvolpath", rootVolumeUuid);
                    vmMapParams.put("drclustermirrorvmvolstatus", "READY");
                    DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterVmAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, vmMapParams);
                    List<VolumeVO> dataVolumes = volsDao.findByInstanceAndType(userVM.getId(), Volume.Type.DATADISK);
                    if (!dataVolumes.isEmpty()) {
                        for (VolumeVO dataVolume : dataVolumes) {
                            // DATA 디스크 생성 및 편집 및 연결 mold-api 호출
                            String dataVolumeUuid = dataVolume.getPath();
                            moldMethod = "POST";
                            moldCommand = "createVolume";
                            volParams.put("diskofferingid", diskOfferingId);
                            volParams.put("size", String.valueOf(dataVolume.getSize() / (1024 * 1024 * 1024)));
                            volParams.put("name", dataVolumeUuid);
                            volParams.put("zoneid", zoneId);
                            String dataVolResult = DisasterRecoveryClusterUtil.moldCreateVolumeAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, volParams);
                            if (dataVolResult == null) {
                                throw new CloudRuntimeException("Failed to request createVolume Mold-API.");
                            } else {
                                jsonObject = new JSONObject(dataVolResult);
                                jobId = jsonObject.get("jobid").toString();
                                String dataVolumeId = jsonObject.get("id").toString();
                                jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
                                if (jobStatus == 2) {
                                    throw new CloudRuntimeException("CreateVolume Mold-API async job resulted in failure.");
                                }
                                moldMethod = "GET";
                                moldCommand = "updateVolume";
                                volUpParams.put("id", dataVolumeId);
                                volUpParams.put("path", dataVolumeUuid);
                                volUpParams.put("storageid", poolId);
                                volUpParams.put("state", "Ready");
                                volUpParams.put("type", dataVolume.getVolumeType().toString());
                                updateVolResult = DisasterRecoveryClusterUtil.moldUpdateVolumeAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, volUpParams);
                                if (updateVolResult == null) {
                                    throw new CloudRuntimeException("Failed to request updateVolume Mold-API.");
                                } else {
                                    jsonObject = new JSONObject(updateVolResult);
                                    jobId = jsonObject.get("jobid").toString();
                                    jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
                                    if (jobStatus == 2) {
                                        throw new CloudRuntimeException("UpdateVolume Mold-API async job resulted in failure.");
                                    }
                                    moldCommand = "attachVolume";
                                    Map<String, String> attParams = new HashMap<>();
                                    attParams.put("id", dataVolumeId);
                                    attParams.put("virtualmachineid", vmId);
                                    String attachVolResult = DisasterRecoveryClusterUtil.moldAttachVolumeAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, attParams);
                                    if (attachVolResult == null) {
                                        throw new CloudRuntimeException("Failed to request attachVolume Mold-API.");
                                    } else {
                                        jsonObject = new JSONObject(attachVolResult);
                                        jobId = jsonObject.get("jobid").toString();
                                        jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
                                        if (jobStatus == 2) {
                                            throw new CloudRuntimeException("AttachVolume Mold-API async job resulted in failure.");
                                        }
                                        moldCommand = "updateDisasterRecoveryClusterVm";
                                        vmMapParams.put("drclustername", drCluster.getName());
                                        vmMapParams.put("drclustervmid", vmId);
                                        vmMapParams.put("drclustermirrorvmid", userVM.getUuid());
                                        vmMapParams.put("drclustermirrorvmname", userVM.getName());
                                        vmMapParams.put("drclustermirrorvmstatus", userVM.getState().toString());
                                        vmMapParams.put("drclustermirrorvmvoltype", dataVolume.getVolumeType().toString());
                                        vmMapParams.put("drclustermirrorvmvolpath", dataVolumeUuid);
                                        vmMapParams.put("drclustermirrorvmvolstatus", "READY");
                                        DisasterRecoveryClusterUtil.moldUpdateDisasterRecoveryClusterVmAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, vmMapParams);
                                        DisasterRecoveryClusterVmMapVO newClusterDataVmMapVO = new DisasterRecoveryClusterVmMapVO(drCluster.getId(), cmd.getVmId(), vmId, userVM.getName(), "Stopped", dataVolume.getVolumeType().toString(), dataVolumeUuid, "SYNCING");
                                        disasterRecoveryClusterVmMapDao.persist(newClusterDataVmMapVO);
                                    }
                                }
                            }
                        }
                    }
                    return true;
                }
            }
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_DELETE, eventDescription = "deleting disaster recovery cluster virtual machine", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean deleteDisasterRecoveryClusterVm(DeleteDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String drName = cmd.getDrClusterName();
        Long vmId = cmd.getId();
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(drName);
        // Secondary Cluster에서 요청한 경우
        if (drCluster.getDrClusterType().equalsIgnoreCase("primary")) {
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                    if (map.getVmId() == vmId) {
                        UserVmJoinVO userVM = userVmJoinDao.findById(map.getVmId());
                        if (userVM != null) {
                            UserVmVO vmVO = userVmDao.findById(userVM.getId());
                            if (vmVO != null) {
                                List<VolumeVO> dataVolumes = volsDao.findByInstanceAndType(userVM.getId(), Volume.Type.DATADISK);
                                if (!dataVolumes.isEmpty()) {
                                    for (VolumeVO dataVolume : dataVolumes) {
                                        Account account = accountDao.findActiveAccount("admin", 1L);
                                        volumeService.detachVolumeViaDestroyVM(dataVolume.getInstanceId(), dataVolume.getId());
                                        Volume result = volumeService.destroyVolume(dataVolume.getId(), account, true, false);
                                    }
                                }
                                try {
                                    UserVm vm = userVmService.destroyVm(vmId, true);
                                    if (!userVmManager.expunge(vmVO)) {
                                        LOGGER.error(String.format("Unable to expunge VM %s : %s, destroying disaster recovery cluster virtual machine will probably fail", vm.getInstanceName(), vm.getUuid()));
                                        return false;
                                    } else {
                                        List<DisasterRecoveryClusterVmMapVO> finalMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterVmId(drCluster.getId(), map.getVmId());
                                        if (!CollectionUtils.isEmpty(finalMap)) {
                                            for (DisasterRecoveryClusterVmMapVO finals : finalMap) {
                                                disasterRecoveryClusterVmMapDao.remove(finals.getId());
                                            }
                                        }
                                        return true;
                                    }
                                } catch (ResourceUnavailableException | ConcurrentOperationException e) {
                                    LOGGER.error(String.format("Failed to destroy VM : %s disaster recovery cluster virtual machine : %s cleanup. Moving on with destroying remaining resources provisioned for the disaster recovery cluster", userVM.getDisplayName(), drCluster.getName()), e);
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
            return true;
        } else {
            // Primary Cluster에서 요청한 경우
            String url = drCluster.getDrClusterUrl();
            Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
            String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
            String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
            String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
            // 미러링 해제 glue-api 호출
            if (ipList != null || !ipList.isEmpty()) {
                UserVmJoinVO userVM = userVmJoinDao.findById(vmId);
                List<VolumeVO> volumes = volsDao.findByInstance(userVM.getId());
                boolean result = false;
                for (VolumeVO vol : volumes) {
                    String volumeUuid = vol.getPath();
                    ipList = ipList.replaceAll(",$", "");
                    String[] array = ipList.split(",");
                    for (int i=0; i < array.length; i++) {
                        String glueIp = array[i];
                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                        String glueUrl = "https://" + glueIp + ":8080/api/v1";
                        String glueCommand = "/mirror/image/rbd/" + volumeUuid;
                        String glueMethod = "DELETE";
                        Map<String, String> glueParams = new HashMap<>();
                        glueParams.put("mirrorPool", "rbd");
                        glueParams.put("imageName", volumeUuid);
                        result = DisasterRecoveryClusterUtil.glueImageMirrorDeleteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                        if (result) {
                            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
                            if (!CollectionUtils.isEmpty(vmMap)) {
                                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                                    UserVmJoinVO vm = userVmJoinDao.findById(map.getVmId());
                                    if (vmId.equals(map.getVmId()) && volumeUuid.equals(map.getMirroredVmVolumePath())) {
                                        String mirrorVmId = map.getMirroredVmId();
                                        disasterRecoveryClusterVmMapDao.remove(map.getId());
                                        List<DisasterRecoveryClusterVmMapVO> finalMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterVmId(drCluster.getId(), vmId);
                                        if (CollectionUtils.isEmpty(finalMap)) {
                                            String moldUrl = url + "/client/api/";
                                            String moldMethod = "GET";
                                            String moldCommand = "deleteDisasterRecoveryClusterVm";
                                            Map<String, String> vmParams = new HashMap<>();
                                            vmParams.put("drclustername", drCluster.getName());
                                            vmParams.put("virtualmachineid", mirrorVmId);
                                            String response = DisasterRecoveryClusterUtil.moldDeleteDisasterRecoveryClusterVmAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, vmParams);
                                            if (response == null) {
                                                throw new CloudRuntimeException("Failed to request deleteDisasterRecoveryClusterVm Mold-API.");
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
                return result;
            } else {
                throw new CloudRuntimeException("Failed to lookup secondary cluster scvm ip address.");
            }
        }
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_START, eventDescription = "starting disaster recovery cluster virtual machine", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean startDisasterRecoveryClusterVm(StartDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String drName = cmd.getDrClusterName();
        Long vmId = cmd.getId();
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(drName);
        String url = drCluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String drVmId = "";
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        if (!CollectionUtils.isEmpty(vmMap)) {
            for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                if (map.getVmId() == vmId) {
                    drVmId = map.getMirroredVmId();
                }
            }
        }
        String moldUrl = url + "/client/api/";
        String moldCommand = "startVirtualMachine";
        String moldMethod = "GET";
        Map<String, String> vmParams = new HashMap<>();
        vmParams.put("considerlasthost", "true");
        vmParams.put("id", drVmId);
        String jobId = DisasterRecoveryClusterUtil.moldStartVirtualMachineAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, vmParams);
        if (jobId == null) {
            throw new CloudRuntimeException("Failed to request StartVirtualMachine Mold-API.");
        } else {
            int jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
            if (jobStatus == 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_STOP, eventDescription = "stopping disaster recovery cluster virtual machine", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean stopDisasterRecoveryClusterVm(StopDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String drName = cmd.getDrClusterName();
        Long vmId = cmd.getId();
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(drName);
        String url = drCluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String drVmId = "";
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        if (!CollectionUtils.isEmpty(vmMap)) {
            for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                if (map.getVmId() == vmId) {
                    drVmId = map.getMirroredVmId();
                }
            }
        }
        String moldUrl = url + "/client/api/";
        String moldCommand = "stopVirtualMachine";
        String moldMethod = "GET";
        Map<String, String> vmParams = new HashMap<>();
        vmParams.put("forced", "true");
        vmParams.put("id", drVmId);
        String jobId = DisasterRecoveryClusterUtil.moldStopVirtualMachineAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, vmParams);
        if (jobId == null) {
            throw new CloudRuntimeException("Failed to request StopVirtualMachine Mold-API.");
        } else {
            int jobStatus = getAsyncJobResult(moldUrl, apiKey, secretKey, jobId);
            if (jobStatus == 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_PROMOTE, eventDescription = "stopping disaster recovery cluster virtual machine", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean promoteDisasterRecoveryClusterVm(PromoteDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String drName = cmd.getDrClusterName();
        Long vmId = cmd.getId();
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(drName);
        String url = drCluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        boolean result = false;
        int glueStep = 0;
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            UserVmJoinVO userVM = userVmJoinDao.findById(vmId);
            List<VolumeVO> volumes = volsDao.findByInstance(userVM.getId());
            for (VolumeVO vol : volumes) {
                String volumeUuid = vol.getPath();
                ipList = ipList.replaceAll(",$", "");
                String[] array = ipList.split(",");
                Loop :
                for (int j=0; j < array.length; j++) {
                    String glueIp = array[j];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    String glueCommand = "/mirror/image/demote/peer/rbd/" + volumeUuid;
                    String glueMethod = "DELETE";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("imageName", volumeUuid);
                    result = DisasterRecoveryClusterUtil.glueImageMirrorDemoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                    if (result) {
                        glueCommand = "/mirror/image/promote/rbd/" + volumeUuid;
                        glueMethod = "POST";
                        while(glueStep < 20) {
                            glueStep += 1;
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                LOGGER.error("promoteDisasterRecoveryClusterVm sleep interrupted");
                            }
                            result = DisasterRecoveryClusterUtil.glueImageMirrorPromoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                            if (result) {
                                glueCommand = "/mirror/image/resync/peer/rbd/" + volumeUuid;
                                glueMethod = "PUT";
                                result = DisasterRecoveryClusterUtil.glueImageMirrorResyncAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                if (result) {
                                    glueCommand = "/mirror/image/rbd/" + volumeUuid;
                                    glueMethod = "PUT";
                                    glueParams = new HashMap<>();
                                    glueParams.put("mirrorPool", "rbd");
                                    glueParams.put("imageName", volumeUuid);
                                    glueParams.put("interval", details.get("mirrorscheduleinterval"));
                                    glueParams.put("startTime", details.get("mirrorschedulestarttime"));
                                    result = DisasterRecoveryClusterUtil.glueImageMirrorSetupUpdateAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                    if (result) {
                                        break Loop;
                                    } else {
                                        // 모의시험 중 디모트한 이미지가 다시 프로모트 될 때 스케줄 설정 작업이 필요함 **
                                        throw new CloudRuntimeException("The image was promoted successfully, but scheduling the image failed. For volumes with a path of " + volumeUuid + ", please add a schedule manually.");
                                    }
                                } else {
                                    // 모의시험 중 디모트한 이미지가 다시 프로모트 될 때 재동기화 작업과 스케줄 설정 작업이 필요함 **
                                    throw new CloudRuntimeException("The image was promoted successfully, but resyncing the image failed. For volumes with a path of " + volumeUuid + ", Manually set image resync and snapshot schedules.");
                                }
                            } else {
                                LOGGER.error("Failed to request ImgageMirrorPromote Glue-API.");
                            }
                        }
                        throw new CloudRuntimeException("Failed to promote image, For volumes with a path of " + volumeUuid + ".");
                    } else {
                        LOGGER.error("Failed to request ImgageMirrorDemotePeer Glue-API.");
                    }
                }
            }
        }
        return result;
    }

    @Override
    @ActionEvent(eventType = DisasterRecoveryClusterEventTypes.EVENT_DR_VM_DEMOTE, eventDescription = "stopping disaster recovery cluster virtual machine", resourceId = 5, resourceType = "DisasterRecoveryCluster")
    public boolean demoteDisasterRecoveryClusterVm(DemoteDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        if (!DisasterRecoveryServiceEnabled.value()) {
            throw new CloudRuntimeException("Disaster Recovery Service plugin is disabled");
        }
        String drName = cmd.getDrClusterName();
        Long vmId = cmd.getId();
        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(drName);
        String url = drCluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        boolean result = false;
        int glueStep = 0;
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            UserVmJoinVO userVM = userVmJoinDao.findById(vmId);
            List<VolumeVO> volumes = volsDao.findByInstance(userVM.getId());
            for (VolumeVO vol : volumes) {
                String volumeUuid = vol.getPath();
                ipList = ipList.replaceAll(",$", "");
                String[] array = ipList.split(",");
                Loop :
                for (int j=0; j < array.length; j++) {
                    String glueIp = array[j];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    String glueCommand = "/mirror/image/demote/rbd/" + volumeUuid;
                    String glueMethod = "DELETE";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("imageName", volumeUuid);
                    result = DisasterRecoveryClusterUtil.glueImageMirrorDemoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                    if (result) {
                        glueCommand = "/mirror/image/promote/peer/rbd/" + volumeUuid;
                        glueMethod = "POST";
                        while(glueStep < 20) {
                            glueStep += 1;
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                LOGGER.error("demoteDisasterRecoveryClusterVm sleep interrupted");
                            }
                            result = DisasterRecoveryClusterUtil.glueImageMirrorPromoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                            if (result) {
                                glueCommand = "/mirror/image/resync/rbd/" + volumeUuid;
                                glueMethod = "PUT";
                                result = DisasterRecoveryClusterUtil.glueImageMirrorResyncAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                if (result) {
                                    break Loop;
                                } else {
                                    LOGGER.error("Failed to request ImageMirrorResync Glue-API.");
                                }
                            } else {
                                LOGGER.error("Failed to request ImageMirrorPromotePeer Glue-API.");
                            }
                        }
                        throw new CloudRuntimeException("Failed to promote remote image, For volumes with a path of " + volumeUuid + ".");
                    } else {
                        LOGGER.error("Failed to request ImageMirrorDemote Glue-API.");
                    }
                }
            }
        }
        return result;
    }

    private void validateDisasterRecoveryClusterVmCreateParameters(final CreateDisasterRecoveryClusterVmCmd cmd) throws CloudRuntimeException {
        final Long vmId = cmd.getVmId();
        final String drClusterName = cmd.getDrClusterName();
        final String networkName = cmd.getNetworkName();
        final String serOfferingName = cmd.getServiceOfferingName();

        if (vmId == null) {
            throw new InvalidParameterValueException("Invalid id for the virtual machine id:" + vmId);
        }
        if (drClusterName == null) {
            throw new InvalidParameterValueException("Invalid name for the disaster recovery cluster name:" + drClusterName);
        }
        if (networkName == null) {
            throw new InvalidParameterValueException("Invalid name for the disaster recovery cluster vm network name:" + networkName);
        }
        if (serOfferingName == null) {
            throw new InvalidParameterValueException("Invalid name for the disaster recovery cluster vm service offering name:" + serOfferingName);
        }

        DisasterRecoveryClusterVO drCluster = disasterRecoveryClusterDao.findByName(drClusterName);

        List<DisasterRecoveryClusterVmMapVO> drVm = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterVmId(drCluster.getId(), vmId);
        if (!CollectionUtils.isEmpty(drVm)) {
            throw new InvalidParameterValueException("A disaster recovery cluster with the same virtual machine id exists:" + vmId);
        }

        String url = drCluster.getDrClusterUrl();
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
        String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
        String moldCommand = "listDiskOfferings";
        String moldUrl = url + "/client/api/";
        String moldMethod = "GET";
        String diskOffering = DisasterRecoveryClusterUtil.moldListDiskOfferingsAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        if (diskOffering == "" || diskOffering.isEmpty()) {
            throw new CloudRuntimeException("A mirroring virtual machine cannot be added because a disk offering with a custom disk size or shared volumes disabled does not exist.");
        }

        UserVmJoinVO userVM = userVmJoinDao.findById(cmd.getVmId());
        List<VolumeVO> volumes = volsDao.findByInstance(userVM.getId());
        for (VolumeVO vol : volumes) {
            DiskOffering offering = diskOfferingDao.findById(vol.getDiskOfferingId());
            if (offering.getShareable()) {
                throw new CloudRuntimeException("Shared volumes are enabled for the virtual machine's disk offering cannot add a mirrored virtual machine.");
            }
        }
        moldCommand = "listVirtualMachines";
        String vmList = DisasterRecoveryClusterUtil.moldListVirtualMachinesAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
        JSONObject jsonObject = new JSONObject(vmList);
        JSONArray jsonArray = jsonObject.getJSONArray("virtualmachine");
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            if (object.get("name").toString().equalsIgnoreCase(userVM.getName())) {
                throw new CloudRuntimeException("A mirroring virtual machine cannot be added because a virtual machine with the same name as the corresponding virtual machine exists in the disaster recovery cluster.");
            }
        }
    }

    private void promoteParentImage(final DisasterRecoveryClusterVO drCluster)throws CloudRuntimeException {
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        ArrayList<String> vmTemplate = new ArrayList<>();
        ArrayList<String> template = new ArrayList<>();
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
        }
        String glueCommand = "";
        String glueMethod = "";
        for (DisasterRecoveryClusterVmMapVO map : vmMap) {
            String[] array = ipList.split(",");
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                glueCommand = "/mirror/image/info/rbd/" +map.getMirroredVmVolumePath();
                glueMethod = "GET";
                String mirrorImageInfo = DisasterRecoveryClusterUtil.glueImageMirrorInfoAPI(glueUrl, glueCommand, glueMethod);
                if (mirrorImageInfo != null) {
                    JsonObject infoObject = (JsonObject) new JsonParser().parse(mirrorImageInfo).getAsJsonObject();
                    if (!infoObject.get("image").getAsString().equals("")) {
                        vmTemplate.add(infoObject.get("image").getAsString());
                        break;
                    }
                }
            }
        }
        if (!vmTemplate.isEmpty()) {
            for (String value : vmTemplate) {
                if (!template.contains(value)) {
                    template.add(value);
                }
            }
        }
        if (!template.isEmpty()) {
            for (String imageName : template) {
                String[] array = ipList.split(",");
                int glueStep = 0;
                boolean result = false;
                Loop :
                for (int j=0; j < array.length; j++) {
                    String glueIp = array[j];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    glueCommand = "/mirror/image/promote/rbd/" + imageName;
                    glueMethod = "POST";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("imageName", imageName);
                    while(glueStep < 100) {
                        glueStep += 1;
                        result = DisasterRecoveryClusterUtil.glueImageMirrorPromoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                        if (result) {
                            break Loop;
                        } else {
                            LOGGER.error("Failed to request ImageMirrorPromote Glue-API.");
                        }
                    }
                }
                if (!result) {
                    throw new CloudRuntimeException("Failed to promote parent image, For volumes with a path of " + imageName + ".");
                }
            }
        }
    }

    private void demoteParentImage(final DisasterRecoveryClusterVO drCluster)throws CloudRuntimeException {
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        ArrayList<String> vmTemplate = new ArrayList<>();
        ArrayList<String> template = new ArrayList<>();
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
        }
        String glueCommand = "";
        String glueMethod = "";
        for (DisasterRecoveryClusterVmMapVO map : vmMap) {
            String[] array = ipList.split(",");
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                glueCommand = "/mirror/image/info/rbd/" +map.getMirroredVmVolumePath();
                glueMethod = "GET";
                String mirrorImageInfo = DisasterRecoveryClusterUtil.glueImageMirrorInfoAPI(glueUrl, glueCommand, glueMethod);
                if (mirrorImageInfo != null) {
                    JsonObject infoObject = (JsonObject) new JsonParser().parse(mirrorImageInfo).getAsJsonObject();
                    if (!infoObject.get("image").getAsString().equals("")) {
                        vmTemplate.add(infoObject.get("image").getAsString());
                        break;
                    }
                }
            }
        }
        if (!vmTemplate.isEmpty()) {
            for (String value : vmTemplate) {
                if (!template.contains(value)) {
                    template.add(value);
                }
            }
        }
        if (!template.isEmpty()) {
            for (String imageName : template) {
                String[] array = ipList.split(",");
                int glueStep = 0;
                boolean result = false;
                Loop :
                for (int j=0; j < array.length; j++) {
                    String glueIp = array[j];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    String glueUrl = "https://" + glueIp + ":8080/api/v1";
                    glueCommand = "/mirror/image/demote/rbd/" + imageName;
                    glueMethod = "DELETE";
                    Map<String, String> glueParams = new HashMap<>();
                    glueParams.put("mirrorPool", "rbd");
                    glueParams.put("imageName", imageName);
                    result = DisasterRecoveryClusterUtil.glueImageMirrorDemoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                    if (result) {
                        glueCommand = "/mirror/image/promote/peer/rbd/" + imageName;
                        glueMethod = "POST";
                        while(glueStep < 100) {
                            glueStep += 1;
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                LOGGER.error("demoteParentImage sleep interrupted");
                            }
                            result = DisasterRecoveryClusterUtil.glueImageMirrorPromoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                            if (result) {
                                glueCommand = "/mirror/image/resync/rbd/" + imageName;
                                glueMethod = "PUT";
                                result = DisasterRecoveryClusterUtil.glueImageMirrorResyncAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                if (!result) {
                                    LOGGER.error("Failed to request ImageMirrorResync Glue-API.");
                                    LOGGER.error("The image was promoted successfully, but resyncing the image failed. For volumes with a path of " + imageName + ", Manually set image resync and snapshot schedules.");
                                }
                                break Loop;
                            } else {
                                LOGGER.error("Failed to request ImageMirrorPromotePeer Glue-API.");
                            }
                        }
                        throw new CloudRuntimeException("Failed to promote remote parent image, For volumes with a path of " + imageName + ", You must manually promote remote images and resync local image, add a remote image snapshots schedule.");
                    } else {
                        LOGGER.error("Failed to request ImageMirrorDemote Glue-API.");
                    }
                }
                if (!result) {
                    throw new CloudRuntimeException("Failed to demote parent image, For volumes with a path of " + imageName + ".");
                }
            }
        }
    }

    private void resyncParentImage(final DisasterRecoveryClusterVO drCluster)throws CloudRuntimeException {
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        ArrayList<String> vmTemplate = new ArrayList<>();
        ArrayList<String> template = new ArrayList<>();
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
        }
        String glueCommand = "";
        String glueMethod = "";
        for (DisasterRecoveryClusterVmMapVO map : vmMap) {
            String[] array = ipList.split(",");
            for (int i=0; i < array.length; i++) {
                String glueIp = array[i];
                ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                String glueUrl = "https://" + glueIp + ":8080/api/v1";
                glueCommand = "/mirror/image/info/rbd/" +map.getMirroredVmVolumePath();
                glueMethod = "GET";
                String mirrorImageInfo = DisasterRecoveryClusterUtil.glueImageMirrorInfoAPI(glueUrl, glueCommand, glueMethod);
                if (mirrorImageInfo != null) {
                    JsonObject infoObject = (JsonObject) new JsonParser().parse(mirrorImageInfo).getAsJsonObject();
                    if (!infoObject.get("image").getAsString().equals("")) {
                        vmTemplate.add(infoObject.get("image").getAsString());
                        break;
                    }
                }
            }
        }
        if (!vmTemplate.isEmpty()) {
            for (String value : vmTemplate) {
                if (!template.contains(value)) {
                    template.add(value);
                }
            }
        }
        if (!template.isEmpty()) {
            String[] array = ipList.split(",");
            for (String imageName : template) {
                String glueIp = "";
                String glueUrl = "";
                int glueStep = 0;
                boolean result = false;
                Loop :
                for (int j=0; j < array.length; j++) {
                    glueIp = array[j];
                    ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                    glueUrl = "https://" + glueIp + ":8080/api/v1";
                    glueCommand = "/mirror/image/status/rbd/" +imageName;
                    glueMethod = "GET";
                    String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                    if (mirrorImageStatus != null) {
                        JsonObject statObject = (JsonObject) new JsonParser().parse(mirrorImageStatus).getAsJsonObject();
                        JsonArray drArray = (JsonArray) new JsonParser().parse(mirrorImageStatus).getAsJsonObject().get("peer_sites");
                        JsonElement peerDescription = null;
                        if (drArray.size() != 0 && drArray != null) {
                            for (JsonElement dr : drArray) {
                                peerDescription = dr.getAsJsonObject().get("description") == null ? null : dr.getAsJsonObject().get("description");
                            }
                        }
                        if (statObject.has("description") && peerDescription != null) {
                            if (statObject.get("description").getAsString().contains("force promoting") || (peerDescription.getAsString().contains("local image is primary") && statObject.get("description").getAsString().contains("local image is primary"))) {
                                glueCommand = "/mirror/image/demote/peer/rbd/" + imageName;
                                glueMethod = "DELETE";
                                Map<String, String> glueParams = new HashMap<>();
                                glueParams.put("mirrorPool", "rbd");
                                glueParams.put("imageName", imageName);
                                result = DisasterRecoveryClusterUtil.glueImageMirrorDemoteAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                if (result) {
                                    while(glueStep < 100) {
                                        glueStep += 1;
                                        glueCommand = "/mirror/image/resync/peer/rbd/" + imageName;
                                        glueMethod = "PUT";
                                        result = DisasterRecoveryClusterUtil.glueImageMirrorResyncAPI(glueUrl, glueCommand, glueMethod, glueParams);
                                        if (result) {
                                            break Loop;
                                        } else {
                                            LOGGER.error("Failed to request ImageMirrorResyncPeer Glue-API.");
                                            LOGGER.error("The peer image was demoted successfully, but resync the image failed. For volumes with a path of " + imageName + ", Manually set image resync.");
                                        }
                                    }
                                    throw new CloudRuntimeException("Failed to resync primary cluster image, For volumes with a path of " + imageName + ", You must manually resync primary cluster image.");
                                } else {
                                    LOGGER.error("Failed to request ImageMirrorDemotePeer Glue-API.");
                                }
                            } else {
                                break;
                            }
                        }
                    } else {
                        LOGGER.error("Failed to request mirror image status Glue-API.");
                    }
                }
                if (glueStep == 0) {
                    throw new CloudRuntimeException("Resync cannot be executed because the current parent image is not force promoting state.");
                }
            }
        }
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

        DisasterRecoveryClusterVO drcluster = disasterRecoveryClusterDao.findByName(name);
        if (drcluster != null) {
            throw new InvalidParameterValueException("A disaster recovery cluster with the same name exists:" + name);
        }
    }

    private void validateResyncDisasterRecoveryClusterMirrorParameters(final DisasterRecoveryClusterVO drCluster) throws CloudRuntimeException {
        Map<String, String> details = disasterRecoveryClusterDetailsDao.findDetails(drCluster.getId());
        List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        if (!CollectionUtils.isEmpty(vmMap)) {
            String url = drCluster.getDrClusterUrl();
            String apiKey = details.get(ApiConstants.DR_CLUSTER_API_KEY);
            String secretKey = details.get(ApiConstants.DR_CLUSTER_SECRET_KEY);
            String moldUrl = url + "/client/api/";
            String moldCommand = "listVirtualMachines";
            String moldMethod = "GET";
            String vmList = DisasterRecoveryClusterUtil.moldListVirtualMachinesAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey);
            if (vmList != null) {
                JSONObject jsonObject = new JSONObject(vmList);
                Object object = jsonObject.get("virtualmachine");
                JSONArray array;
                if (object instanceof JSONArray) {
                    array = (JSONArray) object;
                } else {
                    array = new JSONArray();
                    array.put(object);
                }
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jSONObject = array.getJSONObject(i);
                    for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                        String vmName = map.getMirroredVmName();
                        if (jSONObject.get("name").equals(vmName) && !jSONObject.get("state").equals("Stopped")) {
                            throw new InvalidParameterValueException("Resync functions cannot be executed because there is a running disaster recovery primary cluster virtual machine : " + vmName);
                        }
                    }
                }
            } else {
                throw new InvalidParameterValueException("Resync functions cannot be executed because primary cluster Mold failed to request API.");
            }
        }
    }

    private void validateDisasterRecoveryClusterMirrorParameters(final DisasterRecoveryClusterVO drCluster) throws CloudRuntimeException {
        List<DisasterRecoveryClusterVmMapVO> drClusterVmList = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        ResponseObject.ResponseView respView = ResponseObject.ResponseView.Restricted;
        Account caller = CallContext.current().getCallingAccount();
        if (accountService.isRootAdmin(caller.getId())) {
            respView = ResponseObject.ResponseView.Full;
        }
        String responseName = "drclustervmlist";
        if (!CollectionUtils.isEmpty(drClusterVmList)) {
            for (DisasterRecoveryClusterVmMapVO vmMapVO : drClusterVmList) {
                UserVmJoinVO userVM = userVmJoinDao.findById(vmMapVO.getVmId());
                if (userVM != null) {
                    if (userVM.getState() != VirtualMachine.State.Stopped) {
                        throw new InvalidParameterValueException("Forced promote and demote functions cannot be executed because there is a running disaster recovery secondary cluster virtual machine : " + userVM.getName());
                    }
                }
            }
        }
    }

    private void validateDemoteDisasterRecoveryClusterMirrorParameters(final DisasterRecoveryClusterVO drCluster) throws CloudRuntimeException {
        List<DisasterRecoveryClusterVmMapVO> drClusterVmList = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
        ResponseObject.ResponseView respView = ResponseObject.ResponseView.Restricted;
        Account caller = CallContext.current().getCallingAccount();
        if (accountService.isRootAdmin(caller.getId())) {
            respView = ResponseObject.ResponseView.Full;
        }
        String responseName = "drclustervmlist";
        if (!CollectionUtils.isEmpty(drClusterVmList)) {
            for (DisasterRecoveryClusterVmMapVO vmMapVO : drClusterVmList) {
                UserVmJoinVO userVM = userVmJoinDao.findById(vmMapVO.getVmId());
                if (userVM != null) {
                    if (userVM.getState() != VirtualMachine.State.Stopped) {
                        throw new InvalidParameterValueException("Forced promote and demote functions cannot be executed because there is a running disaster recovery secondary cluster virtual machine : " + userVM.getName());
                    }
                }
            }
        }
        String ipList = Script.runSimpleBashScript("cat /etc/hosts | grep -E 'scvm1-mngt|scvm2-mngt|scvm3-mngt' | awk '{print $1}' | tr '\n' ','");
        if (ipList != null || !ipList.isEmpty()) {
            ipList = ipList.replaceAll(",$", "");
            String[] array = ipList.split(",");
            List<DisasterRecoveryClusterVmMapVO> vmMap = disasterRecoveryClusterVmMapDao.listByDisasterRecoveryClusterId(drCluster.getId());
            if (!CollectionUtils.isEmpty(vmMap)) {
                for (DisasterRecoveryClusterVmMapVO map : vmMap) {
                    String imageName = map.getMirroredVmVolumePath();
                    for (int i=0; i < array.length; i++) {
                        String glueIp = array[i];
                        ///////////////////// glue-api 프로토콜과 포트 확정 시 변경 예정
                        String glueUrl = "https://" + glueIp + ":8080/api/v1";
                        String glueCommand = "/mirror/image/status/rbd/" +imageName;
                        String glueMethod = "GET";
                        String mirrorImageStatus = DisasterRecoveryClusterUtil.glueImageMirrorStatusAPI(glueUrl, glueCommand, glueMethod);
                        if (mirrorImageStatus != null) {
                            JsonObject statObject = (JsonObject) new JsonParser().parse(mirrorImageStatus).getAsJsonObject();
                            JsonArray drArray = (JsonArray) new JsonParser().parse(mirrorImageStatus).getAsJsonObject().get("peer_sites");
                            if (statObject.has("description") && drArray.size() != 0 && drArray != null) {
                                JsonElement peerState = null;
                                for (JsonElement dr : drArray) {
                                    if (dr.getAsJsonObject().get("state") != null) {
                                        peerState = dr.getAsJsonObject().get("state");
                                    }
                                }
                                if (peerState != null) {
                                    if (!statObject.get("description").getAsString().equals("local image is primary") && !peerState.getAsString().contains("replaying") ) {
                                        throw new InvalidParameterValueException("Forced demote functions cannot be executed because peer state is " + peerState.getAsString() + "in volume path : " + imageName);
                                    }
                                }
                            }
                            LOGGER.info(":::::imageName");
                            LOGGER.info(":::::" + imageName);
                            LOGGER.info(":::::statObject.get('description')");
                            LOGGER.info(":::::peerState.getAsString()");
                            LOGGER.info(":::::" + statObject.get("description"));
                            LOGGER.info(":::::" + peerState.getAsString());
                            break;
                        }
                    }
                }
            }
        }
    }

    private int getAsyncJobResult(String moldUrl, String apiKey, String secretKey, String jobId) throws CloudRuntimeException {
        int jobStatus = 0;
        String moldCommand = "queryAsyncJobResult";
        String moldMethod = "GET";
        Map<String, String> params = new HashMap<>();
        params.put("jobid", jobId);
        while (jobStatus == 0) {
            String result = DisasterRecoveryClusterUtil.moldQueryAsyncJobResultAPI(moldUrl, moldCommand, moldMethod, apiKey, secretKey, params);
            if (result != null) {
                jobStatus = Integer.parseInt(result);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOGGER.error("disaster recovery get asyncjob result sleep interrupted error");
                }
            } else {
                throw new CloudRuntimeException("Failed to request queryAsyncJobResult Mold-API.");
            }
        }
        return jobStatus;
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
                protocol = "https";
            } else {
                port = properties.getProperty(HTTP_PORT);
                protocol = "http";
            }
            serverInfo = new String[]{port, protocol};
        } catch (final IOException e) {
            LOGGER.debug("Failed to read configuration from server.properties file", e);
        }
        return serverInfo;
    }

    private synchronized void timeSleep() {
        try {
            Thread.sleep(300 * 1000);
        } catch (InterruptedException e) {
            LOGGER.error("disaster recovery timeSleep interrupted error");
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
        cmdList.add(EnableDisasterRecoveryClusterCmd.class);
        cmdList.add(DisableDisasterRecoveryClusterCmd.class);
        cmdList.add(PromoteDisasterRecoveryClusterCmd.class);
        cmdList.add(DemoteDisasterRecoveryClusterCmd.class);
        cmdList.add(CreateDisasterRecoveryClusterVmCmd.class);
        cmdList.add(UpdateDisasterRecoveryClusterVmCmd.class);
        cmdList.add(DeleteDisasterRecoveryClusterVmCmd.class);
        cmdList.add(StartDisasterRecoveryClusterVmCmd.class);
        cmdList.add(StopDisasterRecoveryClusterVmCmd.class);
        cmdList.add(PromoteDisasterRecoveryClusterVmCmd.class);
        cmdList.add(DemoteDisasterRecoveryClusterVmCmd.class);
        cmdList.add(ResyncDisasterRecoveryClusterCmd.class);
        cmdList.add(ClearDisasterRecoveryClusterCmd.class);
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
