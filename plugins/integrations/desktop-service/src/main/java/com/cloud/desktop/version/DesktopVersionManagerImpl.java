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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.desktop.version.ListDesktopControllerVersionsCmd;
import org.apache.cloudstack.api.response.DesktopControllerVersionResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.api.ApiDBUtils;
import org.apache.cloudstack.api.ApiConstants.DomainDetails;
import com.cloud.api.query.dao.TemplateJoinDao;
import com.cloud.api.query.vo.TemplateJoinVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.desktop.cluster.DesktopClusterService;
import com.cloud.desktop.version.dao.DesktopControllerVersionDao;
import com.cloud.desktop.version.dao.DesktopTemplateMapDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;

public class DesktopVersionManagerImpl extends ManagerBase implements DesktopVersionService {
    public static final Logger LOGGER = Logger.getLogger(DesktopVersionManagerImpl.class.getName());

    @Inject
    private DesktopControllerVersionDao desktopControllerVersionDao;
    @Inject
    private TemplateJoinDao templateJoinDao;
    @Inject
    public DesktopTemplateMapDao desktopTemplateMapDao;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    protected AccountService accountService;

    private DesktopControllerVersionResponse createDesktopControllerVersionResponse(final DesktopControllerVersion desktopControllerVersion) {
        DesktopControllerVersionResponse response = new DesktopControllerVersionResponse();
        response.setObjectName("desktopsupportedversion");
        response.setId(desktopControllerVersion.getUuid());
        response.setName(desktopControllerVersion.getName());
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

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DesktopClusterService.DesktopServiceEnabled.value()) {
            return cmdList;
        }
        cmdList.add(ListDesktopControllerVersionsCmd.class);
        return cmdList;
    }
}
