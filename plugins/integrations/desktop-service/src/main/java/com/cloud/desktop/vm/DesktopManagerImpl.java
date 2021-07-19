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
package com.cloud.desktop.vm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.desktop.vm.ListDesktopCmd;
import org.apache.cloudstack.api.response.DesktopResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.cloud.api.ApiDBUtils;
import org.apache.cloudstack.api.ApiConstants.VMDetails;
import com.cloud.api.query.vo.UserVmJoinVO;
import com.cloud.api.query.dao.UserVmJoinDao;
import com.cloud.dc.DataCenterVO;
import com.cloud.domain.Domain;
import com.cloud.desktop.vm.dao.DesktopDao;
import com.cloud.desktop.vm.dao.DesktopVmMapDao;
import com.cloud.desktop.version.dao.DesktopSupportedVersionDao;
import com.cloud.desktop.version.DesktopSupportedVersionVO;
import com.cloud.network.Network;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.projects.Project;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountService;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.utils.fsm.StateMachine2;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;

public class DesktopManagerImpl extends ManagerBase implements DesktopService {

    private static final Logger LOGGER = Logger.getLogger(DesktopManagerImpl.class);

    protected StateMachine2<Desktop.State, Desktop.Event, Desktop> _stateMachine = Desktop.State.getStateMachine();

    @Inject
    public DesktopDao desktopDao;
    @Inject
    public DesktopVmMapDao desktopVmMapDao;
    @Inject
    public DesktopSupportedVersionDao desktopSupportedVersionDao;
    @Inject
    protected AccountManager accountManager;
    @Inject
    protected AccountService accountService;
    @Inject
    protected UserVmJoinDao userVmJoinDao;
    @Inject
    protected IPAddressDao ipAddressDao;
    @Inject
    protected NetworkDao networkDao;
    @Inject
    protected ServiceOfferingDao serviceOfferingDao;

    private void logMessage(final Level logLevel, final String message, final Exception e) {
        if (logLevel == Level.WARN) {
            if (e != null) {
                LOGGER.warn(message, e);
            } else {
                LOGGER.warn(message);
            }
        } else {
            if (e != null) {
                LOGGER.error(message, e);
            } else {
                LOGGER.error(message);
            }
        }
    }

    private void logTransitStateAndThrow(final Level logLevel, final String message, final Long desktopId, final Desktop.Event event, final Exception e) throws CloudRuntimeException {
        logMessage(logLevel, message, e);
        if (desktopId != null && event != null) {
            stateTransitTo(desktopId, event);
        }
        if (e == null) {
            throw new CloudRuntimeException(message);
        }
        throw new CloudRuntimeException(message, e);
    }

    private void logAndThrow(final Level logLevel, final String message) throws CloudRuntimeException {
        logTransitStateAndThrow(logLevel, message, null, null, null);
    }

    private void logAndThrow(final Level logLevel, final String message, final Exception ex) throws CloudRuntimeException {
        logTransitStateAndThrow(logLevel, message, null, null, ex);
    }

    protected boolean stateTransitTo(long desktopId, Desktop.Event e) {
        DesktopVO desktop = desktopDao.findById(desktopId);
        try {
            return _stateMachine.transitTo(desktop, e, null, desktopDao);
        } catch (NoTransitionException nte) {
            LOGGER.warn(String.format("Failed to transition state of the Desktop : %s in state %s on event %s", desktop.getName(), desktop.getState().toString(), e.toString()), nte);
            return false;
        }
    }

    @Override
    public DesktopResponse createDesktopResponse(long desktopId) {
        DesktopVO desktop = desktopDao.findById(desktopId);
        DesktopResponse response = new DesktopResponse();
        response.setObjectName(Desktop.class.getSimpleName().toLowerCase());
        response.setId(desktop.getUuid());
        response.setName(desktop.getName());
        response.setAdDomainName(desktop.getAdDomainName());
        DataCenterVO zone = ApiDBUtils.findZoneById(desktop.getZoneId());
        response.setZoneId(zone.getUuid());
        response.setZoneName(zone.getName());
        ServiceOfferingVO offering = serviceOfferingDao.findById(desktop.getServiceOfferingId());
        response.setServiceOfferingId(offering.getUuid());
        response.setServiceOfferingName(offering.getName());
        DesktopSupportedVersionVO version = desktopSupportedVersionDao.findById(desktop.getDesktopVersionId());
        if (version != null) {
            response.setDesktopVersionId(version.getUuid());
            response.setDesktopVersionName(version.getName());
        }
        Account account = ApiDBUtils.findAccountById(desktop.getAccountId());
        if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
            Project project = ApiDBUtils.findProjectByProjectAccountId(account.getId());
            response.setProjectId(project.getUuid());
            response.setProjectName(project.getName());
        } else {
            response.setAccountName(account.getAccountName());
        }
        Domain domain = ApiDBUtils.findDomainById(desktop.getDomainId());
        response.setDomainId(domain.getUuid());
        response.setDomainName(domain.getName());
        NetworkVO ntwk = networkDao.findByIdIncludingRemoved(desktop.getNetworkId());
        response.setNetworkId(ntwk.getUuid());
        response.setAssociatedNetworkName(ntwk.getName());
        if (ntwk.getGuestType() == Network.GuestType.Isolated) {
            List<IPAddressVO> ipAddresses = ipAddressDao.listByAssociatedNetwork(ntwk.getId(), true);
            if (ipAddresses != null && ipAddresses.size() == 1) {
                response.setIpAddress(ipAddresses.get(0).getAddress().addr());
                response.setIpAddressId(ipAddresses.get(0).getUuid());
            }
        }
        List<UserVmResponse> vmResponses = new ArrayList<UserVmResponse>();
        List<DesktopVmMapVO> vmList = desktopVmMapDao.listByDesktopId(desktop.getId());
        ResponseView respView = ResponseView.Restricted;
        Account caller = CallContext.current().getCallingAccount();
        if (accountService.isRootAdmin(caller.getId())) {
            respView = ResponseView.Full;
        }
        final String responseName = "virtualmachine";
        if (vmList != null && !vmList.isEmpty()) {
            for (DesktopVmMapVO vmMapVO : vmList) {
                UserVmJoinVO userVM = userVmJoinDao.findById(vmMapVO.getVmId());
                if (userVM != null) {
                    UserVmResponse vmResponse = ApiDBUtils.newUserVmResponse(respView, responseName, userVM,
                        EnumSet.of(VMDetails.nics), caller);
                    vmResponses.add(vmResponse);
                }
            }
        }
        response.setVirtualMachines(vmResponses);
        return response;
    }

    @Override
    public ListResponse<DesktopResponse> listDesktop(ListDesktopCmd cmd) {
        if (!DesktopServiceEnabled.value()) {
            logAndThrow(Level.ERROR, "Desktop Service plugin is disabled");
        }
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();
        final Long desktopId = cmd.getId();
        final String state = cmd.getState();
        final String name = cmd.getName();
        final String keyword = cmd.getKeyword();
        List<DesktopResponse> responsesList = new ArrayList<DesktopResponse>();
        List<Long> permittedAccounts = new ArrayList<Long>();
        Ternary<Long, Boolean, Project.ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<Long, Boolean, Project.ListProjectResourcesCriteria>(cmd.getDomainId(), cmd.isRecursive(), null);
        accountManager.buildACLSearchParameters(caller, desktopId, cmd.getAccountName(), cmd.getProjectId(), permittedAccounts, domainIdRecursiveListProject, cmd.listAll(), false);
        Long domainId = domainIdRecursiveListProject.first();
        Boolean isRecursive = domainIdRecursiveListProject.second();
        Project.ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();
        Filter searchFilter = new Filter(DesktopVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DesktopVO> sb = desktopDao.createSearchBuilder();
        accountManager.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.IN);
        SearchCriteria<DesktopVO> sc = sb.create();
        accountManager.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        if (state != null) {
            sc.setParameters("state", state);
        }
        if(keyword != null){
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        if (desktopId != null) {
            sc.setParameters("id", desktopId);
        }
        if (name != null) {
            sc.setParameters("name", name);
        }
        List<DesktopVO> desktop = desktopDao.search(sc, searchFilter);
        for (DesktopVO cluster : desktop) {
            DesktopResponse desktopResponse = createDesktopResponse(cluster.getId());
            responsesList.add(desktopResponse);
        }
        ListResponse<DesktopResponse> response = new ListResponse<DesktopResponse>();
        response.setResponses(responsesList);
        return response;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DesktopServiceEnabled.value()) {
            return cmdList;
        }

        cmdList.add(ListDesktopCmd.class);
        return cmdList;
    }

    @Override
    public Desktop findById(final Long id) {
        return desktopDao.findById(id);
    }

    @Override
    public String getConfigComponentName() {
        return DesktopService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                DesktopServiceEnabled
        };
    }
}
