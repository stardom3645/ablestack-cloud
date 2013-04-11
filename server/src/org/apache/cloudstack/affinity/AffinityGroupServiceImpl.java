package org.apache.cloudstack.affinity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.affinity.dao.AffinityGroupDao;
import org.apache.cloudstack.affinity.dao.AffinityGroupVMMapDao;
import org.apache.log4j.Logger;


import com.cloud.api.query.vo.SecurityGroupJoinVO;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceInUseException;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.security.SecurityGroupManager;
import com.cloud.network.security.SecurityGroupRuleVO;
import com.cloud.network.security.SecurityGroupVMMapVO;
import com.cloud.network.security.SecurityGroupVO;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.UserContext;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.JoinBuilder.JoinType;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.UserVmDao;

@Local(value = { AffinityGroupService.class })
public class AffinityGroupServiceImpl extends ManagerBase implements AffinityGroupService, Manager {

    public static final Logger s_logger = Logger.getLogger(AffinityGroupServiceImpl.class);
    private String _name;

    @Inject
    AccountManager _accountMgr;

    @Inject
    AffinityGroupDao _affinityGroupDao;

    @Inject
    AffinityGroupVMMapDao _affinityGroupVMMapDao;

    @Inject
    private UserVmDao _userVmDao;

    @Override
    public AffinityGroup createAffinityGroup(String account, Long domainId, String affinityGroupName,
            String affinityGroupType, String description) {

        Account caller = UserContext.current().getCaller();
        Account owner = _accountMgr.finalizeOwner(caller, account, domainId, null);

        if (_affinityGroupDao.isNameInUse(owner.getAccountId(), owner.getDomainId(), affinityGroupName)) {
            throw new InvalidParameterValueException("Unable to create affinity group, a group with name "
                    + affinityGroupName
                    + " already exisits.");
        }

        AffinityGroupVO group = new AffinityGroupVO(affinityGroupName, affinityGroupType, description, domainId,
                owner.getId());
        _affinityGroupDao.persist(group);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Created affinity group =" + affinityGroupName);
        }

        return group;
    }

    @DB
    @Override
    @ActionEvent(eventType = EventTypes.EVENT_SECURITY_GROUP_DELETE, eventDescription = "deleting affinity group")
    public boolean deleteAffinityGroup(Long affinityGroupId, String account, Long domainId, String affinityGroupName)
            throws ResourceInUseException {

        Account caller = UserContext.current().getCaller();
        Account owner = _accountMgr.finalizeOwner(caller, account, domainId, null);

        AffinityGroupVO group = null;
        if (affinityGroupId != null) {
            group = _affinityGroupDao.findById(affinityGroupId);
            if (group == null) {
                throw new InvalidParameterValueException("Unable to find affinity group: " + affinityGroupId
                        + "; failed to delete group.");
            }
        } else if (affinityGroupName != null) {
            group = _affinityGroupDao.findByAccountAndName(owner.getAccountId(), affinityGroupName);
            if (group == null) {
                throw new InvalidParameterValueException("Unable to find affinity group: " + affinityGroupName
                        + "; failed to delete group.");
            }
        } else {
            throw new InvalidParameterValueException(
                    "Either the affinity group Id or group name must be specified to delete the group");
        }

        // check permissions
        _accountMgr.checkAccess(caller, null, true, group);

        final Transaction txn = Transaction.currentTxn();
        txn.start();

        group = _affinityGroupDao.lockRow(affinityGroupId, true);
        if (group == null) {
            throw new InvalidParameterValueException("Unable to find affinity group by id " + affinityGroupId);
        }

        List<AffinityGroupVMMapVO> affinityGroupVmMap = _affinityGroupVMMapDao.listByAffinityGroup(affinityGroupId);
        if (!affinityGroupVmMap.isEmpty()) {
            throw new ResourceInUseException("Cannot delete affinity group when it's in use by virtual machines");
        }

        _affinityGroupDao.expunge(affinityGroupId);
        txn.commit();

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Deleted affinity group id=" + affinityGroupId);
        }
        return true;
    }

    @Override
    public Pair<List<? extends AffinityGroup>, Integer> listAffinityGroups(Long affinityGroupId, String affinityGroupName, String affinityGroupType, Long vmId, Long startIndex, Long pageSize) {
        Filter searchFilter = new Filter(AffinityGroupVO.class, "id", Boolean.TRUE, startIndex, pageSize);

        Account caller = UserContext.current().getCaller();

        Long accountId = caller.getAccountId();
        Long domainId = caller.getDomainId();

        SearchBuilder<AffinityGroupVMMapVO> vmInstanceSearch = _affinityGroupVMMapDao.createSearchBuilder();
        vmInstanceSearch.and("instanceId", vmInstanceSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);

        SearchBuilder<AffinityGroupVO> groupSearch = _affinityGroupDao.createSearchBuilder();
        groupSearch.join("vmInstanceSearch", vmInstanceSearch, groupSearch.entity().getId(), vmInstanceSearch.entity()
                .getAffinityGroupId(), JoinBuilder.JoinType.INNER);

        SearchCriteria<AffinityGroupVO> sc = groupSearch.create();

        if (accountId != null) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        }

        if (domainId != null) {
            sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
        }

        if (affinityGroupId != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, affinityGroupId);
        }

        if (affinityGroupName != null) {
            sc.addAnd("name", SearchCriteria.Op.EQ, affinityGroupName);
        }

        if (affinityGroupType != null) {
            sc.addAnd("type", SearchCriteria.Op.EQ, affinityGroupType);
        }

        if (vmId != null) {
            UserVmVO userVM = _userVmDao.findById(vmId);
            if (userVM == null) {
                throw new InvalidParameterValueException("Unable to list affinity groups for virtual machine instance "
                        + vmId + "; instance not found.");
            }
            _accountMgr.checkAccess(caller, null, true, userVM);
            // add join to affinity_groups_vm_map
            sc.setJoinParameters("vmInstanceSearch", "instanceId", vmId);
        }

        Pair<List<AffinityGroupVO>, Integer> result =  _affinityGroupDao.searchAndCount(sc, searchFilter);
        return new Pair<List<? extends AffinityGroup>, Integer>(result.first(), result.second());
    }


    @Override
    public List<String> listAffinityGroupTypes() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        _name = name;
        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public AffinityGroup getAffinityGroup(Long groupId) {
        return _affinityGroupDao.findById(groupId);
    }

}
