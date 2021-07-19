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
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.command.user.desktop.version.ListDesktopSupportedVersionsCmd;
import org.apache.cloudstack.api.response.DesktopSupportedVersionResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.log4j.Logger;

import com.cloud.api.query.dao.TemplateJoinDao;
import com.cloud.api.query.vo.TemplateJoinVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.desktop.vm.DesktopService;
import com.cloud.desktop.version.dao.DesktopSupportedVersionDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;

public class DesktopVersionManagerImpl extends ManagerBase implements DesktopVersionService {
    public static final Logger LOGGER = Logger.getLogger(DesktopVersionManagerImpl.class.getName());

    @Inject
    private DesktopSupportedVersionDao desktopSupportedVersionDao;
    @Inject
    private TemplateJoinDao templateJoinDao;
    @Inject
    private DataCenterDao dataCenterDao;


    private DesktopSupportedVersionResponse createDesktopSupportedVersionResponse(final DesktopSupportedVersion desktopSupportedVersion) {
        DesktopSupportedVersionResponse response = new DesktopSupportedVersionResponse();
        response.setObjectName("desktopsupportedversion");
        response.setId(desktopSupportedVersion.getUuid());
        response.setName(desktopSupportedVersion.getName());
        response.setVersion(desktopSupportedVersion.getVersion());
        if (desktopSupportedVersion.getState() != null) {
            response.setState(desktopSupportedVersion.getState().toString());
        }
        DataCenterVO zone = dataCenterDao.findById(desktopSupportedVersion.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }
        TemplateJoinVO template = templateJoinDao.findById(desktopSupportedVersion.getTemplateId());
        if (template != null) {
            response.setTemplateId(template.getUuid());
            response.setTemplateName(template.getName());
            response.setTemplateState(template.getState().toString());
        }
        return response;
    }

    private ListResponse<DesktopSupportedVersionResponse> createDesktopSupportedVersionListResponse(List<DesktopSupportedVersionVO> versions) {
        List<DesktopSupportedVersionResponse> responseList = new ArrayList<>();
        for (DesktopSupportedVersionVO version : versions) {
            responseList.add(createDesktopSupportedVersionResponse(version));
        }
        ListResponse<DesktopSupportedVersionResponse> response = new ListResponse<>();
        response.setResponses(responseList);
        return response;
    }

    @Override
    public ListResponse<DesktopSupportedVersionResponse> listDesktopSupportedVersions(final ListDesktopSupportedVersionsCmd cmd) {
        if (!DesktopService.DesktopServiceEnabled.value()) {
            throw new CloudRuntimeException("Desktop Service plugin is disabled");
        }
        final Long versionId = cmd.getId();
        final Long zoneId = cmd.getZoneId();
        Filter searchFilter = new Filter(DesktopSupportedVersionVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DesktopSupportedVersionVO> sb = desktopSupportedVersionDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        SearchCriteria<DesktopSupportedVersionVO> sc = sb.create();
        String keyword = cmd.getKeyword();
        if (versionId != null) {
            sc.setParameters("id", versionId);
        }
        if (zoneId != null) {
            SearchCriteria<DesktopSupportedVersionVO> scc = desktopSupportedVersionDao.createSearchCriteria();
            scc.addOr("zoneId", SearchCriteria.Op.EQ, zoneId);
            scc.addOr("zoneId", SearchCriteria.Op.NULL);
            sc.addAnd("zoneId", SearchCriteria.Op.SC, scc);
        }
        if(keyword != null){
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List <DesktopSupportedVersionVO> versions = desktopSupportedVersionDao.search(sc, searchFilter);

        return createDesktopSupportedVersionListResponse(versions);
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DesktopService.DesktopServiceEnabled.value()) {
            return cmdList;
        }
        cmdList.add(ListDesktopSupportedVersionsCmd.class);
        return cmdList;
    }
}

