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
package com.cloud.desktop.cluster;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.desktop.cluster.AddDesktopClusterIpRangeCmd;
import org.apache.cloudstack.api.command.user.desktop.cluster.DeleteDesktopClusterIpRangeCmd;
import org.apache.cloudstack.api.command.user.desktop.cluster.ListDesktopClusterCmd;
import org.apache.cloudstack.api.command.user.desktop.cluster.ListDesktopClusterIpRangeCmd;
import org.apache.cloudstack.api.response.DesktopClusterResponse;
import org.apache.cloudstack.api.response.DesktopClusterIpRangeResponse;
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
import com.cloud.desktop.version.dao.DesktopControllerVersionDao;
import com.cloud.desktop.cluster.dao.DesktopClusterDao;
import com.cloud.desktop.cluster.dao.DesktopClusterIpRangeDao;
import com.cloud.desktop.cluster.dao.DesktopClusterVmMapDao;
import com.cloud.desktop.version.DesktopControllerVersionVO;
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
import com.cloud.event.ActionEvent;
import com.cloud.utils.Ternary;
import com.cloud.utils.net.NetUtils;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.utils.fsm.StateMachine2;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.exception.InvalidParameterValueException;

public class DesktopClusterManagerImpl extends ManagerBase implements DesktopClusterService {

    private static final Logger LOGGER = Logger.getLogger(DesktopClusterManagerImpl.class);

    protected StateMachine2<DesktopCluster.State, DesktopCluster.Event, DesktopCluster> _stateMachine = DesktopCluster.State.getStateMachine();

    @Inject
    public DesktopClusterDao desktopClusterDao;
    @Inject
    private DesktopClusterIpRangeDao desktopClusterIpRangeDao;
    @Inject
    public DesktopClusterVmMapDao desktopClusterVmMapDao;
    @Inject
    public DesktopControllerVersionDao desktopControllerVersionDao;
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

    private void logTransitStateAndThrow(final Level logLevel, final String message, final Long desktopClusterId, final DesktopCluster.Event event, final Exception e) throws CloudRuntimeException {
        logMessage(logLevel, message, e);
        if (desktopClusterId != null && event != null) {
            stateTransitTo(desktopClusterId, event);
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

    protected boolean stateTransitTo(long desktopCusterId, DesktopCluster.Event e) {
        DesktopClusterVO desktop = desktopClusterDao.findById(desktopCusterId);
        try {
            return _stateMachine.transitTo(desktop, e, null, desktopClusterDao);
        } catch (NoTransitionException nte) {
            LOGGER.warn(String.format("Failed to transition state of the Desktop : %s in state %s on event %s", desktop.getName(), desktop.getState().toString(), e.toString()), nte);
            return false;
        }
    }

    @Override
    public DesktopClusterResponse createDesktopClusterResponse(long desktopCusterId) {
        DesktopClusterVO desktop = desktopClusterDao.findById(desktopCusterId);
        DesktopClusterResponse response = new DesktopClusterResponse();
        response.setObjectName(DesktopCluster.class.getSimpleName().toLowerCase());
        response.setId(desktop.getUuid());
        response.setName(desktop.getName());
        response.setDescription(desktop.getDescription());
        response.setAdDomainName(desktop.getAdDomainName());
        response.setState(desktop.getState().toString());
        DataCenterVO zone = ApiDBUtils.findZoneById(desktop.getZoneId());
        response.setZoneId(zone.getUuid());
        response.setZoneName(zone.getName());
        ServiceOfferingVO offering = serviceOfferingDao.findById(desktop.getServiceOfferingId());
        response.setServiceOfferingId(offering.getUuid());
        response.setServiceOfferingName(offering.getName());
        DesktopControllerVersionVO version = desktopControllerVersionDao.findById(desktop.getDesktopVersionId());
        if (version != null) {
            response.setControllerVersionName(version.getName());
            response.setControllerVersion(version.getVersion());
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
        List<DesktopClusterVmMapVO> vmList = desktopClusterVmMapDao.listByDesktopClusterId(desktop.getId());
        ResponseView respView = ResponseView.Restricted;
        Account caller = CallContext.current().getCallingAccount();
        if (accountService.isRootAdmin(caller.getId())) {
            respView = ResponseView.Full;
        }
        final String responseName = "virtualmachine";
        if (vmList != null && !vmList.isEmpty()) {
            for (DesktopClusterVmMapVO vmMapVO : vmList) {
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
    public ListResponse<DesktopClusterResponse> listDesktopCluster(ListDesktopClusterCmd cmd) {
        if (!DesktopServiceEnabled.value()) {
            logAndThrow(Level.ERROR, "Desktop Service plugin is disabled");
        }
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();
        final Long desktopClusterId = cmd.getId();
        final String state = cmd.getState();
        final String name = cmd.getName();
        final String keyword = cmd.getKeyword();
        List<DesktopClusterResponse> responsesList = new ArrayList<DesktopClusterResponse>();
        List<Long> permittedAccounts = new ArrayList<Long>();
        Ternary<Long, Boolean, Project.ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<Long, Boolean, Project.ListProjectResourcesCriteria>(cmd.getDomainId(), cmd.isRecursive(), null);
        accountManager.buildACLSearchParameters(caller, desktopClusterId, cmd.getAccountName(), cmd.getProjectId(), permittedAccounts, domainIdRecursiveListProject, cmd.listAll(), false);
        Long domainId = domainIdRecursiveListProject.first();
        Boolean isRecursive = domainIdRecursiveListProject.second();
        Project.ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();
        Filter searchFilter = new Filter(DesktopClusterVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DesktopClusterVO> sb = desktopClusterDao.createSearchBuilder();
        accountManager.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.IN);
        SearchCriteria<DesktopClusterVO> sc = sb.create();
        accountManager.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        if (state != null) {
            sc.setParameters("state", state);
        }
        if (keyword != null){
            sc.setParameters("keyword", "%" + keyword + "%");
        }
        if (desktopClusterId != null) {
            sc.setParameters("id", desktopClusterId);
        }
        if (name != null) {
            sc.setParameters("name", name);
        }
        List<DesktopClusterVO> desktop = desktopClusterDao.search(sc, searchFilter);
        for (DesktopClusterVO cluster : desktop) {
            DesktopClusterResponse desktopClusterResponse = createDesktopClusterResponse(cluster.getId());
            responsesList.add(desktopClusterResponse);
        }
        ListResponse<DesktopClusterResponse> response = new ListResponse<DesktopClusterResponse>();
        response.setResponses(responsesList);
        return response;
    }

    @Override
    public DesktopClusterIpRangeResponse createDesktopClusterIpRangeResponse(long ipRangeId) {
        DesktopClusterIpRangeVO desktopIp = desktopClusterIpRangeDao.findById(ipRangeId);
        DesktopClusterIpRangeResponse response = new DesktopClusterIpRangeResponse();
        response.setObjectName(DesktopClusterIpRange.class.getSimpleName().toLowerCase());
        response.setId(desktopIp.getUuid());
        response.setGateway(desktopIp.getGateway());
        response.setNetmask(desktopIp.getNetmask());
        response.setStartIp(desktopIp.getStartIp());
        response.setEndIp(desktopIp.getEndIp());
        DesktopClusterVO desktop = desktopClusterDao.findById(desktopIp.getDesktopClusterId());
        if (desktop != null) {
            response.setDesktopClusterName(desktop.getName());
            NetworkVO desktopNetwork = networkDao.findByIdIncludingRemoved(desktop.getNetworkId());
            response.setNetworkId(desktopNetwork.getUuid());
            response.setAssociatedNetworkName(desktopNetwork.getName());
        }
        return response;
    }

    @Override
    public ListResponse<DesktopClusterIpRangeResponse> listDesktopClusterIpRanges(ListDesktopClusterIpRangeCmd cmd) {
        if (!DesktopServiceEnabled.value()) {
            logAndThrow(Level.ERROR, "Desktop Service plugin is disabled");
        }
        final Long rangeId = cmd.getId();
        final Long desktopClusterId = cmd.getDesktopClusterId();
        Filter searchFilter = new Filter(DesktopClusterIpRangeVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<DesktopClusterIpRangeVO> sb = desktopClusterIpRangeDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("desktopClusterId", sb.entity().getDesktopClusterId(), SearchCriteria.Op.EQ);
        SearchCriteria<DesktopClusterIpRangeVO> sc = sb.create();
        if (rangeId != null) {
            sc.setParameters("id", rangeId);
        }
        if (desktopClusterId != null) {
            sc.setParameters("desktopClusterId", desktopClusterId);
        }
        List<DesktopClusterIpRangeResponse> responsesList = new ArrayList<DesktopClusterIpRangeResponse>();
        List<DesktopClusterIpRangeVO> id = desktopClusterIpRangeDao.search(sc, searchFilter);
        for (DesktopClusterIpRangeVO ip : id) {
            DesktopClusterIpRangeResponse desktopClusterIpRangeResponse = createDesktopClusterIpRangeResponse(ip.getId());
            responsesList.add(desktopClusterIpRangeResponse);
        }
        ListResponse<DesktopClusterIpRangeResponse> response = new ListResponse<DesktopClusterIpRangeResponse>();
        response.setResponses(responsesList);
        return response;
    }

    @Override
    @ActionEvent(eventType = DesktopClusterEventTypes.EVENT_DESKTOP_CLUSTER_IP_RANGE_ADD, eventDescription = "Adding Desktop cluster ip range")
    public DesktopClusterIpRange addDesktopClusterIpRange(final AddDesktopClusterIpRangeCmd cmd) {
        if (!DesktopServiceEnabled.value()) {
            throw new CloudRuntimeException("Desktop Service plugin is disabled");
        }
        boolean ipv4 = false;
        final String gateway = cmd.getGateway();
        final String netmask = cmd.getNetmask();
        final String startIp = cmd.getStartIp();
        final String endIp = cmd.getEndIp();

        if (startIp != null) {
            ipv4 = true;
        }
        if (!ipv4) {
            throw new InvalidParameterValueException("Please specify IP address.");
        }
        if (ipv4) {
            // Make sure the gateway is valid
            if (!NetUtils.isValidIp4(gateway)) {
                throw new InvalidParameterValueException("Please specify a valid gateway");
            }

            // Make sure the netmask is valid
            if (!NetUtils.isValidIp4Netmask(netmask)) {
                throw new InvalidParameterValueException("Please specify a valid netmask");
            }

            final String newCidr = NetUtils.getCidrFromGatewayAndNetmask(gateway, netmask);

            //Make sure start and end ips are with in the range of cidr calculated for this gateway and netmask {
            if (!NetUtils.isIpWithInCidrRange(gateway, newCidr) || !NetUtils.isIpWithInCidrRange(startIp, newCidr) || !NetUtils.isIpWithInCidrRange(endIp, newCidr)) {
                throw new InvalidParameterValueException("Please specify a valid IP range or valid netmask or valid gateway");
            }

            final List<DesktopClusterIpRangeVO> ips = desktopClusterIpRangeDao.listAll();
            for (final DesktopClusterIpRangeVO range : ips) {
                final String otherGateway = range.getGateway();
                final String otherNetmask = range.getNetmask();
                final String otherStartIp = range.getStartIp();
                final String otherEndIp = range.getEndIp();

                // Continue if it's not IPv4
                if ( otherGateway == null || otherNetmask == null ) {
                    continue;
                }
                final String otherCidr = NetUtils.getCidrFromGatewayAndNetmask(otherGateway, otherNetmask);
                if( !NetUtils.isNetworksOverlap(newCidr,  otherCidr)) {
                    continue;
                }

                // extend IP range
                if (!gateway.equals(otherGateway) || !netmask.equals(range.getNetmask())) {
                    throw new InvalidParameterValueException("The IP range has already been added with gateway "
                            + otherGateway + " ,and netmask " + otherNetmask
                            + ", Please specify the gateway/netmask if you want to extend ip range" );
                }
                if (!NetUtils.is31PrefixCidr(newCidr)) {
                    if (NetUtils.ipRangesOverlap(startIp, endIp, otherStartIp, otherEndIp)) {
                        throw new InvalidParameterValueException("The IP range already has IPs that overlap with the new range." +
                                " Please specify a different start IP/end IP.");
                    }
                }
            }
        }

        DesktopClusterVO desktopCluster = desktopClusterDao.findById(cmd.getDesktopClusterId());

        if (desktopCluster == null) {
            throw new InvalidParameterValueException("Invalid desktop cluster specified");
        }
        Long desktopClusterId = desktopCluster.getId();

        final DesktopClusterIpRangeVO cluster = Transaction.execute(new TransactionCallback<DesktopClusterIpRangeVO>() {
            @Override
            public DesktopClusterIpRangeVO doInTransaction(TransactionStatus status) {
                DesktopClusterIpRangeVO newCluster = new DesktopClusterIpRangeVO(desktopClusterId, gateway, netmask, startIp, endIp);
                desktopClusterIpRangeDao.persist(newCluster);
                return newCluster;
            }
        });

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Desktop cluster ID: %s has been created", cluster.getUuid()));
        }
        return cluster;
    }

    @Override
    @ActionEvent(eventType = DesktopClusterEventTypes.EVENT_DESKTOP_CLUSTER_IP_RANGE_DELETE, eventDescription = "Deleting Desktop cluster ip range")
    public boolean deleteDesktopClusterIpRange(final DeleteDesktopClusterIpRangeCmd cmd) {
        if (!DesktopServiceEnabled.value()) {
            throw new CloudRuntimeException("Desktop Service plugin is disabled");
        }
        final Long ipRangeId = cmd.getId();
        DesktopClusterIpRange iprange = desktopClusterIpRangeDao.findById(ipRangeId);
        if (iprange == null) {
            throw new InvalidParameterValueException("Invalid Desktop cluster ip range id specified");
        }

        return desktopClusterIpRangeDao.remove(iprange.getId());
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        if (!DesktopServiceEnabled.value()) {
            return cmdList;
        }

        cmdList.add(ListDesktopClusterCmd.class);
        cmdList.add(ListDesktopClusterIpRangeCmd.class);
        cmdList.add(AddDesktopClusterIpRangeCmd.class);
        cmdList.add(DeleteDesktopClusterIpRangeCmd.class);
        return cmdList;
    }

    @Override
    public DesktopCluster findById(final Long id) {
        return desktopClusterDao.findById(id);
    }

    @Override
    public String getConfigComponentName() {
        return DesktopClusterService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                DesktopServiceEnabled
        };
    }
}
