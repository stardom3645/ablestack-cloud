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

package com.cloud.request;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.command.admin.request.AddResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.DeleteResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.ListResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.UpdateResourceRequestCmd;
import org.apache.cloudstack.api.command.admin.request.StateUpdateResourceRequestCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ResourceRequestResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.log4j.Logger;

import com.cloud.api.ApiDBUtils;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.domain.Domain;
import com.cloud.event.ActionEvent;
import com.cloud.request.ResourceRequest.State;
import com.cloud.request.dao.ResourceRequestDao;
import com.cloud.request.dao.ResourceRequestDaoImpl;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Filter;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.exception.InvalidParameterValueException;

public class ResourceRequestManagerImpl extends ManagerBase implements ResourceRequestService {
    public static final Logger LOGGER = Logger.getLogger(ResourceRequestDaoImpl.class.getName());

    @Inject
    private ResourceRequestDao resourceRequestDao;
    @Inject
    protected AccountService accountService;
    @Inject
    private DataCenterDao dataCenterDao;

    @Override
    @ActionEvent(eventType = ResourceRequestEventTypes.EVENT_RESOURCE_REQUEST_ADD, eventDescription = "Adding Resource Request")
    public ResourceRequestResponse addResourceRequest(final AddResourceRequestCmd cmd) {
        if (!ResourceRequestEnabled.value()) {
            throw new CloudRuntimeException("Resource Request plugin is disabled");
        }

        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());

        final String title = cmd.getTitle();
        final String purpose = cmd.getPurpose();
        final String quantity = cmd.getQuantity();
        final String cpu = cmd.getCpu();
        final String memomry = cmd.getMemory();
        final String network = cmd.getNetwork();
        final String volume = cmd.getVolume();
        final String item = cmd.getItem();
        final String state = State.REQUEST.toString();
        final Long domainId = owner.getDomainId();
        final Long accountId = owner.getAccountId();

        ResourceRequestVO resourceRequestVO = new ResourceRequestVO(title, accountId, domainId, purpose, item, quantity, state, cpu, memomry, network, volume);
        resourceRequestDao.persist(resourceRequestVO);

        return createResourceRequestResponse(resourceRequestVO);
    }

    private ResourceRequestResponse createResourceRequestResponse(final ResourceRequest resourceRequest) {
        ResourceRequestVO rrvo = resourceRequestDao.findById(resourceRequest.getId());
        ResourceRequestResponse response = new ResourceRequestResponse();
        response.setObjectName("resourcerequest");
        response.setId(resourceRequest.getUuid());
        response.setTitle(resourceRequest.getTitle());

        Account account = ApiDBUtils.findAccountById(resourceRequest.getAccountId());
        response.setAccountName(account.getAccountName());

        if (resourceRequest.getDomainApprover() > 0) {
            Account domainAccount = ApiDBUtils.findAccountById(resourceRequest.getDomainApprover());
            response.setDomainApprover(domainAccount.getAccountName());
        }
        if (resourceRequest.getAdminApprover() > 0) {
            Account AdminAccount = ApiDBUtils.findAccountById(resourceRequest.getAdminApprover());
            response.setAdminApprover(AdminAccount.getAccountName());
        }
        response.setPurpose(resourceRequest.getPurpose());
        response.setItem(resourceRequest.getItem());
        response.setQuantity(resourceRequest.getQuantity());
        response.setState(resourceRequest.getState().toString());

        response.setCpu(resourceRequest.getCpu());
        response.setMemory(resourceRequest.getMemory());
        if ("DELETE NETWORK".equals(resourceRequest.getItem())) {
            response.setNetworkIds(resourceRequest.getNetwork());
        } else {
            response.setNetwork(resourceRequest.getNetwork());
        }

        if ("DELETE VOLUME".equals(resourceRequest.getItem())) {
            response.setVolumeIds(resourceRequest.getVolume());
        } else {
            response.setVolume(resourceRequest.getVolume());
        }
        response.setComment(resourceRequest.getComment());
        response.setCreated(resourceRequest.getCreated());

        Domain domain = ApiDBUtils.findDomainById(rrvo.getDomainId());
        response.setDomainId(domain.getUuid());
        response.setDomainName(domain.getName());

        if (resourceRequest.getState() != null) {
            response.setState(resourceRequest.getState().toString());
        }
        DataCenterVO zone = dataCenterDao.findById(rrvo.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }
        return response;
    }

    private ListResponse<ResourceRequestResponse> createResourceRequestListResponse(List<ResourceRequestVO> rr) {
        List<ResourceRequestResponse> responseList = new ArrayList<>();
        for (ResourceRequestVO r : rr) {
            responseList.add(createResourceRequestResponse(r));
        }
        ListResponse<ResourceRequestResponse> response = new ListResponse<>();
        response.setResponses(responseList);
        return response;
    }

    @Override
    public ListResponse<ResourceRequestResponse> listResourceRequest(final ListResourceRequestCmd cmd) {
        if (!ResourceRequestEnabled.value()) {
            throw new CloudRuntimeException("Resource Request plugin is disabled");
        }
        final Long id = cmd.getId();
        final Long zoneId = cmd.getZoneId();
        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());
        final Long accountId = owner.getAccountId();
        final Long domainId = owner.getDomainId();

        Filter searchFilter = new Filter(ResourceRequestVO.class, "created", false, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<ResourceRequestVO> sb = resourceRequestDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("domainId", sb.entity().getDomainId(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getTitle(), SearchCriteria.Op.LIKE);
        SearchCriteria<ResourceRequestVO> sc = sb.create();
        String keyword = cmd.getKeyword();
        if (id != null) {
            sc.setParameters("id", id);
        }
        if (owner.getRoleId() == RoleType.User.getId()) {
            if (accountId != null) {
                sc.setParameters("accountId", accountId);
            }
        } else if (owner.getRoleId() == RoleType.DomainAdmin.getId()) {
            if (accountId != null) {
                sc.setParameters("domainId", domainId);
            }
        }
        // else if (owner.getRoleId() == RoleType.Admin.getId()) {}

        if (zoneId != null) {
            SearchCriteria<ResourceRequestVO> scc = resourceRequestDao.createSearchCriteria();
            scc.addOr("zoneId", SearchCriteria.Op.EQ, zoneId);
            scc.addOr("zoneId", SearchCriteria.Op.NULL);
            sc.addAnd("zoneId", SearchCriteria.Op.SC, scc);
        }
        if (keyword != null) {
            sc.addOr("uuid", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        List<ResourceRequestVO> rr = resourceRequestDao.search(sc, searchFilter);

        return createResourceRequestListResponse(rr);
    }

    @Override
    @ActionEvent(eventType = ResourceRequestEventTypes.EVENT_RESOURCE_REQUEST_DELETE, eventDescription = "Deleting Resource Request", async = true)
    public boolean deleteResourceRequest(final DeleteResourceRequestCmd cmd) {
        if (!ResourceRequestEnabled.value()) {
            throw new CloudRuntimeException("Resource Request plugin is disabled");
        }
        final Long id = cmd.getId();
        ResourceRequest rr = resourceRequestDao.findById(id);

        if (rr == null) {
            throw new InvalidParameterValueException("Invalid Resource Request id specified");
        }

        return resourceRequestDao.remove(rr.getId());
    }

    @Override
    @ActionEvent(eventType = ResourceRequestEventTypes.EVENT_RESOURCE_REQUEST_UPDATE, eventDescription = "Updating Resource Request")
    public ResourceRequestResponse stateUpdateResourceRequest(final StateUpdateResourceRequestCmd cmd) {
        if (!ResourceRequestEnabled.value()) {
            throw new CloudRuntimeException("Resource Request plugin is disabled");
        }
        final Long id = cmd.getId();
        ResourceRequestVO rrvo = resourceRequestDao.findById(id);
        if (rrvo == null) {
            throw new InvalidParameterValueException("Invalid Resource Request id specified");
        }
        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());
        rrvo = resourceRequestDao.createForUpdate(rrvo.getId());

        if (cmd.getComment() != null && !cmd.getComment().isEmpty()) {
            rrvo.setComment(cmd.getComment());
            rrvo.setState(State.REJECTION.toString());
            if(owner.getRoleId() == RoleType.Admin.getId()){
                rrvo.setAdminApprover(owner.getAccountId());
            } else if (owner.getRoleId() == RoleType.DomainAdmin.getId()) {
                rrvo.setDomainApprover(owner.getAccountId());
            }
        } else {
            if(owner.getRoleId() == RoleType.Admin.getId()){
                rrvo.setState(State.ADMIN_APPROVAL.toString());
                rrvo.setAdminApprover(owner.getAccountId());
            } else if (owner.getRoleId() == RoleType.DomainAdmin.getId()) {
                rrvo.setState(State.DOMAIN_APPROVAL.toString());
                rrvo.setDomainApprover(owner.getAccountId());
            }
        }

       if (!resourceRequestDao.update(rrvo.getId(), rrvo)) {
            throw new CloudRuntimeException(String.format("Failed to update Resource Request ID: %s", rrvo.getUuid()));
        }
        rrvo = resourceRequestDao.findById(rrvo.getId());
        return createResourceRequestResponse(rrvo);
    }

    @Override
    @ActionEvent(eventType = ResourceRequestEventTypes.EVENT_RESOURCE_REQUEST_UPDATE, eventDescription = "Updating Resource Request")
    public ResourceRequestResponse updateResourceRequest(final UpdateResourceRequestCmd cmd) {
        if (!ResourceRequestEnabled.value()) {
            throw new CloudRuntimeException("Resource Request plugin is disabled");
        }
        final Long id = cmd.getId();
        // ResourceRequest.State state = null;
        ResourceRequestVO rrvo = resourceRequestDao.findById(id);
        if (rrvo == null) {
            throw new InvalidParameterValueException("Invalid Resource Request id specified");
        }

        final String title = cmd.getTitle();
        final String purpose = cmd.getPurpose();
        final String quantity = cmd.getQuantity();
        final String cpu = cmd.getCpu();
        final String memomry = cmd.getMemory();
        final String network = cmd.getNetwork();
        final String volume = cmd.getVolume();

        rrvo = resourceRequestDao.createForUpdate(rrvo.getId());
        rrvo.setTitle(title);
        rrvo.setPurpose(purpose);
        rrvo.setQuantity(quantity);
        rrvo.setCpu(cpu);
        rrvo.setMemory(memomry);
        rrvo.setNetwork(network);
        rrvo.setVolume(volume);

       if (!resourceRequestDao.update(rrvo.getId(), rrvo)) {
            throw new CloudRuntimeException(String.format("Failed to update Resource Request ID: %s", rrvo.getUuid()));
        }
        rrvo = resourceRequestDao.findById(rrvo.getId());
        return createResourceRequestResponse(rrvo);
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!ResourceRequestEnabled.value()) {
            return cmdList;
        }

        cmdList.add(ListResourceRequestCmd.class);
        cmdList.add(AddResourceRequestCmd.class);
        cmdList.add(DeleteResourceRequestCmd.class);
        cmdList.add(UpdateResourceRequestCmd.class);
        cmdList.add(StateUpdateResourceRequestCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return ResourceRequestService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                ResourceRequestEnabled
        };
    }
}