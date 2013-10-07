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
package com.cloud.metadata;

import java.util.HashMap;
import java.util.Map;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.dc.DcDetailVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DcDetailsDao;
import com.cloud.server.ResourceMetaDataService;
import com.cloud.storage.VolumeDetailVO;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.vm.NicDetailVO;
import com.cloud.vm.UserVmDetailVO;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.NicDetailDao;
import com.cloud.vm.dao.UserVmDetailsDao;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.api.query.dao.ResourceTagJoinDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.RemoteAccessVpnDao;
import com.cloud.network.rules.dao.PortForwardingRulesDao;
import com.cloud.network.security.dao.SecurityGroupDao;
import com.cloud.network.vpc.dao.StaticRouteDao;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.server.ResourceTag;
import com.cloud.server.ResourceTag.TaggedResourceType;
import com.cloud.server.TaggedResourceService;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.user.AccountManager;
import com.cloud.user.DomainManager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDao;
import com.cloud.utils.db.Transaction;
import com.cloud.uuididentity.dao.IdentityDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.snapshot.dao.VMSnapshotDao;


@Component
@Local(value = { ResourceMetaDataService.class, ResourceMetaDataManager.class })
public class ResourceMetaDataManagerImpl extends ManagerBase implements ResourceMetaDataService, ResourceMetaDataManager {
    public static final Logger s_logger = Logger.getLogger(ResourceMetaDataManagerImpl.class);


    private static Map<TaggedResourceType, GenericDao<?, Long>> _daoMap=
            new HashMap<TaggedResourceType, GenericDao<?, Long>>();
    @Inject
    AccountManager _accountMgr;
    @Inject
    ResourceTagDao _resourceTagDao;
    @Inject
    ResourceTagJoinDao _resourceTagJoinDao;
    @Inject
    IdentityDao _identityDao;
    @Inject
    DomainManager _domainMgr;
    @Inject
    UserVmDao _userVmDao;
    @Inject
    VolumeDao _volumeDao;
    @Inject
    VMTemplateDao _templateDao;
    @Inject
    SnapshotDao _snapshotDao;
    @Inject
    NetworkDao _networkDao;
    @Inject
    DataCenterDao _dataCenterDao;
    @Inject
    LoadBalancerDao _lbDao;
    @Inject
    PortForwardingRulesDao _pfDao;
    @Inject
    FirewallRulesDao _firewallDao;
    @Inject
    SecurityGroupDao _securityGroupDao;
    @Inject
    RemoteAccessVpnDao _vpnDao;
    @Inject
    IPAddressDao _publicIpDao;
    @Inject
    ProjectDao _projectDao;
    @Inject
    VpcDao _vpcDao;
    @Inject
    StaticRouteDao _staticRouteDao;
    @Inject
    VMSnapshotDao _vmSnapshotDao;
    @Inject
    protected VolumeDetailsDao _volumeDetailDao;
    @Inject
    NicDetailDao _nicDetailDao;
    @Inject
    UserVmDetailsDao _userVmDetailDao;
    @Inject
    NicDao _nicDao;
    @Inject
    DcDetailsDao _dcDetailsDao;
    @Inject
    TaggedResourceService _taggedResourceMgr;
    @Inject
    UserVmDetailsDao _userVmDetail;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {

        _daoMap.put(TaggedResourceType.UserVm, _userVmDao);
        _daoMap.put(TaggedResourceType.Volume, _volumeDao);
        _daoMap.put(TaggedResourceType.Template, _templateDao);
        _daoMap.put(TaggedResourceType.ISO, _templateDao);
        _daoMap.put(TaggedResourceType.Snapshot, _snapshotDao);
        _daoMap.put(TaggedResourceType.Network, _networkDao);
        _daoMap.put(TaggedResourceType.LoadBalancer, _lbDao);
        _daoMap.put(TaggedResourceType.PortForwardingRule, _pfDao);
        _daoMap.put(TaggedResourceType.FirewallRule, _firewallDao);
        _daoMap.put(TaggedResourceType.SecurityGroup, _securityGroupDao);
        _daoMap.put(TaggedResourceType.PublicIpAddress, _publicIpDao);
        _daoMap.put(TaggedResourceType.Project, _projectDao);
        _daoMap.put(TaggedResourceType.Vpc, _vpcDao);
        _daoMap.put(TaggedResourceType.NetworkACL, _firewallDao);
        _daoMap.put(TaggedResourceType.Nic, _nicDao);
        _daoMap.put(TaggedResourceType.StaticRoute, _staticRouteDao);
        _daoMap.put(TaggedResourceType.VMSnapshot, _vmSnapshotDao);
        _daoMap.put(TaggedResourceType.RemoteAccessVpn, _vpnDao);
        _daoMap.put(TaggedResourceType.Zone, _dataCenterDao);
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
    public TaggedResourceType getResourceType(String resourceTypeStr) {

        for (TaggedResourceType type : ResourceTag.TaggedResourceType.values()) {
            if (type.toString().equalsIgnoreCase(resourceTypeStr)) {
                return type;
            }
        }
        throw new InvalidParameterValueException("Invalid resource type " + resourceTypeStr);
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_RESOURCE_DETAILS_CREATE, eventDescription = "creating resource meta data")
    public boolean addResourceMetaData(String resourceId, TaggedResourceType resourceType, Map<String, String> details){

        Transaction txn = Transaction.currentTxn();
        txn.start();

        for (String key : details.keySet()) {
                Long id = _taggedResourceMgr.getResourceId(resourceId, resourceType);

                //check if object exists
                if (_daoMap.get(resourceType).findById(id) == null) {
                    throw new InvalidParameterValueException("Unable to find resource by id " + resourceId +
                            " and type " + resourceType);
                }

                String value = details.get(key);

                if (value == null || value.isEmpty()) {
                    throw new InvalidParameterValueException("Value for the key " + key + " is either null or empty");
                }

                // TODO - Have a better design here.
                if(resourceType == TaggedResourceType.Volume){
                    VolumeDetailVO v = new VolumeDetailVO(id, key, value);
                    _volumeDetailDao.persist(v);
                } else if (resourceType == TaggedResourceType.Nic){
                    NicDetailVO n = new NicDetailVO(id, key, value);
                    _nicDetailDao.persist(n);
                }else if (resourceType == TaggedResourceType.UserVm){
                    UserVmDetailVO userVmDetail = new UserVmDetailVO(id, key, value);
                    _userVmDetailDao.persist(userVmDetail);
                } else if (resourceType == TaggedResourceType.Zone){
                     DcDetailVO dataCenterDetail = new DcDetailVO(id, key, value);
                     _dcDetailsDao.persist(dataCenterDetail);
                } else {
                    throw new InvalidParameterValueException("The resource type " + resourceType + " is not supported by the API yet");
                }

        }

        txn.commit();

        return true;
    }


    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_RESOURCE_DETAILS_DELETE, eventDescription = "deleting resource meta data")
    public boolean deleteResourceMetaData(String resourceId, TaggedResourceType resourceType, String key){

        Long id = _taggedResourceMgr.getResourceId(resourceId, resourceType);
        // TODO - Have a better design here.
        if(resourceType == TaggedResourceType.Volume){
           _volumeDetailDao.removeDetails(id, key);
        } else if(resourceType == TaggedResourceType.Nic){
            _nicDetailDao.removeDetails(id, key);
        } else if(resourceType == TaggedResourceType.UserVm){
            _userVmDetailDao.removeDetails(id, key);
        } else if (resourceType == TaggedResourceType.Zone){
            _dcDetailsDao.removeDetails(id, key);
        }
        else{
            throw new InvalidParameterValueException("The resource type " + resourceType + " is not supported by the API yet");
        }

        return true;
    }


}
