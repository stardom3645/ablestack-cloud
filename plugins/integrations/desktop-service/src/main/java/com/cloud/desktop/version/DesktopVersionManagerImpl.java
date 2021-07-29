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

package com.cloud.desktop.version;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Inject;

import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.desktop.version.ListDesktopControllerVersionsCmd;
import org.apache.cloudstack.api.command.user.desktop.version.AddDesktopControllerVersionCmd;
import org.apache.cloudstack.api.command.user.desktop.version.ListDesktopMasterVersionsCmd;
import org.apache.cloudstack.api.response.DesktopControllerVersionResponse;
import org.apache.cloudstack.api.response.DesktopMasterVersionResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;
import org.apache.cloudstack.api.ApiConstants.DomainDetails;
import org.apache.cloudstack.api.command.user.template.RegisterTemplateCmd;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.query.dao.TemplateJoinDao;
import com.cloud.api.query.vo.TemplateJoinVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.desktop.cluster.DesktopClusterService;
import com.cloud.desktop.version.dao.DesktopControllerVersionDao;
import com.cloud.desktop.version.dao.DesktopMasterVersionDao;
import com.cloud.desktop.version.dao.DesktopTemplateMapDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.AccountManager;
import com.cloud.storage.VMTemplateVO;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.component.ComponentContext;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.event.ActionEvent;
import com.cloud.storage.dao.VMTemplateDao;
import com.google.common.base.Strings;
import com.cloud.template.TemplateApiService;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.InvalidParameterValueException;

public class DesktopVersionManagerImpl extends ManagerBase implements DesktopVersionService {
    public static final Logger LOGGER = Logger.getLogger(DesktopVersionManagerImpl.class.getName());

    @Inject
    private DesktopControllerVersionDao desktopControllerVersionDao;
    @Inject
    private DesktopMasterVersionDao desktopMasterVersionDao;
    @Inject
    private TemplateJoinDao templateJoinDao;
    @Inject
    private DesktopTemplateMapDao desktopTemplateMapDao;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    protected AccountService accountService;
    @Inject
    private TemplateApiService templateService;
    @Inject
    private VMTemplateDao templateDao;
    @Inject
    private AccountManager accountManager;

    private DesktopControllerVersionResponse createDesktopControllerVersionResponse(final DesktopControllerVersion desktopControllerVersion) {
        DesktopControllerVersionResponse response = new DesktopControllerVersionResponse();
        response.setObjectName("desktopcontrollerversion");
        response.setId(desktopControllerVersion.getUuid());
        response.setName(desktopControllerVersion.getName());
        response.setDescription(desktopControllerVersion.getDescription());
        response.setVersion(desktopControllerVersion.getVersion());
        if (desktopControllerVersion.getState() != null) {
            response.setState(desktopControllerVersion.getState().toString());
        }
        DataCenterVO zone = dataCenterDao.findById(desktopControllerVersion.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }
        List<TemplateResponse> templateResponses = new ArrayList<TemplateResponse>();
        List<DesktopTemplateMapVO> templateList = desktopTemplateMapDao.listByVersionId(desktopControllerVersion.getId());
        ResponseView respView = ResponseView.Restricted;
        Account caller = CallContext.current().getCallingAccount();
        if (accountService.isRootAdmin(caller.getId())) {
            respView = ResponseView.Full;
        }
        final String responseName = "template";
        if (templateList != null && !templateList.isEmpty()) {
            for (DesktopTemplateMapVO templateMapVO : templateList) {
                TemplateJoinVO userTemplate = templateJoinDao.findById(templateMapVO.getTemplateId());
                if (userTemplate != null) {
                    TemplateResponse templateResponse = ApiDBUtils.newTemplateResponse(EnumSet.of(DomainDetails.resource), respView, userTemplate);
                    templateResponses.add(templateResponse);
                }
            }
        }
        response.setTemplates(templateResponses);
        return response;
    }

    private ListResponse<DesktopControllerVersionResponse> createDesktopControllerVersionListResponse(List<DesktopControllerVersionVO> versions) {
        List<DesktopControllerVersionResponse> responseList = new ArrayList<>();
        for (DesktopControllerVersionVO version : versions) {
            responseList.add(createDesktopControllerVersionResponse(version));
        }
        ListResponse<DesktopControllerVersionResponse> response = new ListResponse<>();
        response.setResponses(responseList);
        return response;
    }

    @Override
    @ActionEvent(eventType = DesktopVersionEventTypes.EVENT_DESKTOP_CONTROLLER_VERSION_ADD, eventDescription = "Adding Desktop controller version")
    public DesktopControllerVersionResponse addDesktopControllerVersion(final AddDesktopControllerVersionCmd cmd) {
        if (!DesktopClusterService.DesktopServiceEnabled.value()) {
            throw new CloudRuntimeException("Desktop Service plugin is disabled");
        }
        final String format = cmd.getFormat();
        final String hypervisor = cmd.getHypervisor();
        final String versionName = cmd.getControllerVersionName();
        final String description = cmd.getDescription();
        final String controllerVersion = cmd.getControllerVersion();
        final Long zoneId = cmd.getZoneId();
        final String dcUrl = cmd.getDcUrl();
        final String worksUrl = cmd.getWorksUrl();
        final Long dcOsTypeId = cmd.getDcOsType();
        final Long worksOsTypeId = cmd.getWorksOsType();
        String templateName = "";

        if (compareVersions(controllerVersion, MIN_DESKTOP_CONTOLLER_VERSION) < 0) {
            throw new InvalidParameterValueException(String.format("New supported Kubernetes version cannot be added as %s is minimum version supported by Kubernetes Service", MIN_DESKTOP_CONTOLLER_VERSION));
        }
        if (zoneId != null && dataCenterDao.findById(zoneId) == null) {
            throw new InvalidParameterValueException("Invalid zone specified");
        }
        if (Strings.isNullOrEmpty(dcUrl)) {
            throw new InvalidParameterValueException(String.format("Invalid DC URL for template specified, %s", dcUrl));
        }
        if (Strings.isNullOrEmpty(worksUrl)) {
            throw new InvalidParameterValueException(String.format("Invalid Works URL for template specified, %s", worksUrl));
        }

        if (Strings.isNullOrEmpty(versionName)) {
            throw new InvalidParameterValueException(String.format("Invalid VersionName for template specified, %s", versionName));
        }

        VMTemplateVO template = null;

        //desktop_controller_version 테이블에 버전 추가
        DesktopControllerVersionVO desktopControllerVersionVO = new DesktopControllerVersionVO(versionName, controllerVersion, description, zoneId);
        desktopControllerVersionVO = desktopControllerVersionDao.persist(desktopControllerVersionVO);

        //vm_template 테이블에 dc 템플릿 추가
        try {
            templateName = String.format("%s(Desktop Controller DC-Template)", versionName);
            VirtualMachineTemplate vmTemplate = registerDesktopTemplateVersion(zoneId, templateName, dcUrl, hypervisor, dcOsTypeId, format);
            template = templateDao.findById(vmTemplate.getId());

            //desktop_template_map 테이블에 DC template 매핑 데이터 추가
            DesktopTemplateMapVO desktopTemplateMapVO = new DesktopTemplateMapVO(desktopControllerVersionVO.getId(), template.getId());
            desktopTemplateMapVO = desktopTemplateMapDao.persist(desktopTemplateMapVO);

        } catch (URISyntaxException | IllegalAccessException | NoSuchFieldException | IllegalArgumentException | ResourceAllocationException ex) {
            LOGGER.error(String.format("Unable to register binaries ISO for supported kubernetes version, %s, with url: %s", templateName, dcUrl), ex);
            throw new CloudRuntimeException(String.format("Unable to register binaries ISO for supported kubernetes version, %s, with url: %s", templateName, dcUrl));
        }

        //vm_template 테이블에 works 템플릿 추가
        try {
            templateName = String.format("%s(Desktop Controller Works-Template)", versionName);
            VirtualMachineTemplate vmTemplate = registerDesktopTemplateVersion(zoneId, templateName, worksUrl, hypervisor ,worksOsTypeId ,format);
            template = templateDao.findById(vmTemplate.getId());

            //desktop_template_map 테이블에 works template 매핑 데이터 추가
            DesktopTemplateMapVO desktopTemplateMapVO = new DesktopTemplateMapVO(desktopControllerVersionVO.getId(), template.getId());
            desktopTemplateMapVO = desktopTemplateMapDao.persist(desktopTemplateMapVO);
        } catch (URISyntaxException | IllegalAccessException | NoSuchFieldException | IllegalArgumentException | ResourceAllocationException ex) {
            LOGGER.error(String.format("Unable to register binaries ISO for supported kubernetes version, %s, with url: %s", templateName, worksUrl), ex);
            throw new CloudRuntimeException(String.format("Unable to register binaries ISO for supported kubernetes version, %s, with url: %s", templateName, worksUrl));
        }

        return createDesktopControllerVersionResponse(desktopControllerVersionVO);
    }


    private VirtualMachineTemplate registerDesktopTemplateVersion(final Long zoneId, final String templateName, final String url, final String hypervisor, final Long osTypeId, final String format)throws IllegalAccessException, NoSuchFieldException,
            IllegalArgumentException, ResourceAllocationException, URISyntaxException {
        RegisterTemplateCmd registerTemplateCmd = new RegisterTemplateCmd();
        registerTemplateCmd = ComponentContext.inject(registerTemplateCmd);
        registerTemplateCmd.setTemplateName(templateName);
        registerTemplateCmd.setHypervisor(hypervisor);
        registerTemplateCmd.setFormat(format);
        registerTemplateCmd.setPublic(true);
        registerTemplateCmd.setOsTypeId(osTypeId);
        if (zoneId != null) {
            registerTemplateCmd.setZoneId(zoneId);
        }
        registerTemplateCmd.setDisplayText(templateName);
        registerTemplateCmd.setUrl(url);
        registerTemplateCmd.setAccountName(accountManager.getSystemAccount().getAccountName());
        registerTemplateCmd.setDomainId(accountManager.getSystemAccount().getDomainId());
        return templateService.registerTemplate(registerTemplateCmd);
    }

    public static int compareVersions(String v1, String v2) throws IllegalArgumentException {
        if (Strings.isNullOrEmpty(v1) || Strings.isNullOrEmpty(v2)) {
            throw new IllegalArgumentException(String.format("Invalid version comparision with versions %s, %s", v1, v2));
        }
        if(!isSemanticVersion(v1)) {
            throw new IllegalArgumentException(String.format("Invalid version format, %s. Semantic version should be specified in MAJOR.MINOR.PATCH format", v1));
        }
        if(!isSemanticVersion(v2)) {
            throw new IllegalArgumentException(String.format("Invalid version format, %s. Semantic version should be specified in MAJOR.MINOR.PATCH format", v2));
        }
        String[] thisParts = v1.split("\\.");
        String[] thatParts = v2.split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for(int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ?
                    Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ?
                    Integer.parseInt(thatParts[i]) : 0;
            if(thisPart < thatPart)
                return -1;
            if(thisPart > thatPart)
                return 1;
        }
        return 0;
    }

    private static boolean isSemanticVersion(final String version) {
        if(!version.matches("[0-9]+(\\.[0-9]+)*")) {
            return false;
        }
        String[] parts = version.split("\\.");
        if (parts.length < 3) {
            return false;
        }
        return true;
    }

    @Override
    public ListResponse<DesktopControllerVersionResponse> listDesktopControllerVersions(final ListDesktopControllerVersionsCmd cmd) {
        if (!DesktopClusterService.DesktopServiceEnabled.value()) {
            throw new CloudRuntimeException("Desktop Service plugin is disabled");
        }
        final Long versionId = cmd.getId();
        final Long zoneId = cmd.getZoneId();
        Filter searchFilter = new Filter(DesktopControllerVersionVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DesktopControllerVersionVO> sb = desktopControllerVersionDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        SearchCriteria<DesktopControllerVersionVO> sc = sb.create();
        String keyword = cmd.getKeyword();
        if (versionId != null) {
            sc.setParameters("id", versionId);
        }
        if (zoneId != null) {
            SearchCriteria<DesktopControllerVersionVO> scc = desktopControllerVersionDao.createSearchCriteria();
            scc.addOr("zoneId", SearchCriteria.Op.EQ, zoneId);
            scc.addOr("zoneId", SearchCriteria.Op.NULL);
            sc.addAnd("zoneId", SearchCriteria.Op.SC, scc);
        }
        if(keyword != null){
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List <DesktopControllerVersionVO> versions = desktopControllerVersionDao.search(sc, searchFilter);

        return createDesktopControllerVersionListResponse(versions);
    }

    private DesktopMasterVersionResponse createDesktopMasterVersionResponse(final DesktopMasterVersion desktopMasterVersion) {
        DesktopMasterVersionResponse response = new DesktopMasterVersionResponse();
        response.setObjectName("desktopmasterversion");
        response.setId(desktopMasterVersion.getUuid());
        response.setName(desktopMasterVersion.getName());
        response.setDescription(desktopMasterVersion.getDescription());
        response.setVersion(desktopMasterVersion.getVersion());
        if (desktopMasterVersion.getState() != null) {
            response.setState(desktopMasterVersion.getState().toString());
        }
        DataCenterVO zone = dataCenterDao.findById(desktopMasterVersion.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }
        TemplateJoinVO template = templateJoinDao.findById(desktopMasterVersion.getTemplateId());
        if (template != null) {
            response.setTemplateId(template.getUuid());
            response.setTemplateName(template.getName());
            response.setTemplateState(template.getState().toString());
            response.setTemplateOSType(template.getGuestOSName());
        }
        return response;
    }

    private ListResponse<DesktopMasterVersionResponse> createDesktopMasterVersionListResponse(List<DesktopMasterVersionVO> versions) {
        List<DesktopMasterVersionResponse> responseList = new ArrayList<>();
        for (DesktopMasterVersionVO version : versions) {
            responseList.add(createDesktopMasterVersionResponse(version));
        }
        ListResponse<DesktopMasterVersionResponse> response = new ListResponse<>();
        response.setResponses(responseList);
        return response;
    }

    @Override
    public ListResponse<DesktopMasterVersionResponse> listDesktopMasterVersions(final ListDesktopMasterVersionsCmd cmd) {
        if (!DesktopClusterService.DesktopServiceEnabled.value()) {
            throw new CloudRuntimeException("Desktop Service plugin is disabled");
        }
        final Long versionId = cmd.getId();
        final Long zoneId = cmd.getZoneId();
        Filter searchFilter = new Filter(DesktopMasterVersionVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DesktopMasterVersionVO> sb = desktopMasterVersionDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        SearchCriteria<DesktopMasterVersionVO> sc = sb.create();
        String keyword = cmd.getKeyword();
        if (versionId != null) {
            sc.setParameters("id", versionId);
        }
        if (zoneId != null) {
            SearchCriteria<DesktopMasterVersionVO> scc = desktopMasterVersionDao.createSearchCriteria();
            scc.addOr("zoneId", SearchCriteria.Op.EQ, zoneId);
            scc.addOr("zoneId", SearchCriteria.Op.NULL);
            sc.addAnd("zoneId", SearchCriteria.Op.SC, scc);
        }
        if(keyword != null){
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List <DesktopMasterVersionVO> versions = desktopMasterVersionDao.search(sc, searchFilter);

        return createDesktopMasterVersionListResponse(versions);
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DesktopClusterService.DesktopServiceEnabled.value()) {
            return cmdList;
        }
        cmdList.add(ListDesktopControllerVersionsCmd.class);
        cmdList.add(ListDesktopMasterVersionsCmd.class);
        cmdList.add(AddDesktopControllerVersionCmd.class);
        return cmdList;
    }
}