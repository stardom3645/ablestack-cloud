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
package org.apache.cloudstack.storage.image.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.engine.subsystem.api.storage.DataObjectInStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.Event;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.State;
import org.apache.cloudstack.engine.subsystem.api.storage.ZoneScope;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.storage.DataStoreRole;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.UpdateBuilder;

import edu.emory.mathcs.backport.java.util.Collections;

@Component
public class TemplateDataStoreDaoImpl extends GenericDaoBase<TemplateDataStoreVO, Long> implements TemplateDataStoreDao {
    private static final Logger s_logger = Logger.getLogger(TemplateDataStoreDaoImpl.class);
    private SearchBuilder<TemplateDataStoreVO> updateStateSearch;
    private SearchBuilder<TemplateDataStoreVO> storeSearch;
    private SearchBuilder<TemplateDataStoreVO> templateSearch;
    private SearchBuilder<TemplateDataStoreVO> templateRoleSearch;
    private SearchBuilder<TemplateDataStoreVO> storeTemplateSearch;
    private SearchBuilder<TemplateDataStoreVO> storeTemplateStateSearch;
    private SearchBuilder<TemplateDataStoreVO> storeTemplateDownloadStatusSearch;

    @Inject
    private DataStoreManager _storeMgr;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        storeSearch = createSearchBuilder();
        storeSearch.and("store_id", storeSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeSearch.and("destroyed", storeSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        storeSearch.done();

        templateSearch = createSearchBuilder();
        templateSearch.and("template_id", templateSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        templateSearch.and("destroyed", templateSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        templateSearch.done();

        templateRoleSearch = createSearchBuilder();
        templateRoleSearch.and("template_id", templateRoleSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        templateRoleSearch.and("store_role", templateRoleSearch.entity().getDataStoreRole(), SearchCriteria.Op.EQ);
        templateRoleSearch.and("destroyed", templateRoleSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        templateRoleSearch.done();

        updateStateSearch = this.createSearchBuilder();
        updateStateSearch.and("id", updateStateSearch.entity().getId(), Op.EQ);
        updateStateSearch.and("state", updateStateSearch.entity().getState(), Op.EQ);
        updateStateSearch.and("updatedCount", updateStateSearch.entity().getUpdatedCount(), Op.EQ);
        updateStateSearch.done();

        storeTemplateSearch = createSearchBuilder();
        storeTemplateSearch.and("template_id", storeTemplateSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        storeTemplateSearch.and("store_id", storeTemplateSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeTemplateSearch.and("destroyed", storeTemplateSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        storeTemplateSearch.done();

        storeTemplateStateSearch = createSearchBuilder();
        storeTemplateStateSearch.and("template_id", storeTemplateStateSearch.entity().getTemplateId(),
                SearchCriteria.Op.EQ);
        storeTemplateStateSearch.and("store_id", storeTemplateStateSearch.entity().getDataStoreId(),
                SearchCriteria.Op.EQ);
        storeTemplateStateSearch.and("states", storeTemplateStateSearch.entity().getState(), SearchCriteria.Op.IN);
        storeTemplateStateSearch.and("destroyed", storeTemplateStateSearch.entity().getDestroyed(),
                SearchCriteria.Op.EQ);
        storeTemplateStateSearch.done();

        storeTemplateDownloadStatusSearch = createSearchBuilder();
        storeTemplateDownloadStatusSearch.and("template_id",
                storeTemplateDownloadStatusSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        storeTemplateDownloadStatusSearch.and("store_id", storeTemplateDownloadStatusSearch.entity().getDataStoreId(),
                SearchCriteria.Op.EQ);
        storeTemplateDownloadStatusSearch.and("downloadState", storeTemplateDownloadStatusSearch.entity()
                .getDownloadState(), SearchCriteria.Op.IN);
        storeTemplateDownloadStatusSearch.and("destroyed", storeTemplateDownloadStatusSearch.entity().getDestroyed(),
                SearchCriteria.Op.EQ);
        storeTemplateDownloadStatusSearch.done();

        storeTemplateSearch = createSearchBuilder();
        storeTemplateSearch.and("store_id", storeTemplateSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeTemplateSearch.and("template_id", storeTemplateSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        storeTemplateSearch.and("destroyed", storeTemplateSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        storeTemplateSearch.done();

        return true;
    }

    @Override
    public boolean updateState(State currentState, Event event, State nextState, DataObjectInStore vo, Object data) {
        TemplateDataStoreVO dataObj = (TemplateDataStoreVO) vo;
        Long oldUpdated = dataObj.getUpdatedCount();
        Date oldUpdatedTime = dataObj.getUpdated();

        SearchCriteria<TemplateDataStoreVO> sc = updateStateSearch.create();
        sc.setParameters("id", dataObj.getId());
        sc.setParameters("state", currentState);
        sc.setParameters("updatedCount", dataObj.getUpdatedCount());

        dataObj.incrUpdatedCount();

        UpdateBuilder builder = getUpdateBuilder(dataObj);
        builder.set(dataObj, "state", nextState);
        builder.set(dataObj, "updated", new Date());
        if (nextState == State.Destroyed) {
            builder.set(dataObj, "destroyed", true);
        }

        int rows = update(dataObj, sc);
        if (rows == 0 && s_logger.isDebugEnabled()) {
            TemplateDataStoreVO dbVol = findByIdIncludingRemoved(dataObj.getId());
            if (dbVol != null) {
                StringBuilder str = new StringBuilder("Unable to update ").append(dataObj.toString());
                str.append(": DB Data={id=").append(dbVol.getId()).append("; state=").append(dbVol.getState())
                        .append("; updatecount=").append(dbVol.getUpdatedCount()).append(";updatedTime=")
                        .append(dbVol.getUpdated());
                str.append(": New Data={id=").append(dataObj.getId()).append("; state=").append(nextState)
                        .append("; event=").append(event).append("; updatecount=").append(dataObj.getUpdatedCount())
                        .append("; updatedTime=").append(dataObj.getUpdated());
                str.append(": stale Data={id=").append(dataObj.getId()).append("; state=").append(currentState)
                        .append("; event=").append(event).append("; updatecount=").append(oldUpdated)
                        .append("; updatedTime=").append(oldUpdatedTime);
            } else {
                s_logger.debug("Unable to update objectIndatastore: id=" + dataObj.getId()
                        + ", as there is no such object exists in the database anymore");
            }
        }
        return rows > 0;
    }

    @Override
    public List<TemplateDataStoreVO> listByStoreId(long id) {
        SearchCriteria<TemplateDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        sc.setParameters("destroyed", false);
        return listIncludingRemovedBy(sc);
    }

    @Override
    public List<TemplateDataStoreVO> listDestroyed(long id) {
        SearchCriteria<TemplateDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        sc.setParameters("destroyed", true);
        return listIncludingRemovedBy(sc);
    }

    @Override
    public void deletePrimaryRecordsForStore(long id) {
        SearchCriteria<TemplateDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        Transaction txn = Transaction.currentTxn();
        txn.start();
        remove(sc);
        txn.commit();
    }

    @Override
    public void deletePrimaryRecordsForTemplate(long templateId) {
        SearchCriteria<TemplateDataStoreVO> sc = templateSearch.create();
        sc.setParameters("template_id", templateId);
        Transaction txn = Transaction.currentTxn();
        txn.start();
        expunge(sc);
        txn.commit();
    }

    @Override
    public List<TemplateDataStoreVO> listByTemplateStore(long templateId, long storeId) {
        SearchCriteria<TemplateDataStoreVO> sc = storeTemplateSearch.create();
        sc.setParameters("template_id", templateId);
        sc.setParameters("store_id", storeId);
        sc.setParameters("destroyed", false);
        return search(sc, null);
    }

    @Override
    public List<TemplateDataStoreVO> listByTemplateStoreStatus(long templateId, long storeId, State... states) {
        SearchCriteria<TemplateDataStoreVO> sc = storeTemplateStateSearch.create();
        sc.setParameters("template_id", templateId);
        sc.setParameters("store_id", storeId);
        sc.setParameters("states", (Object[]) states);
        sc.setParameters("destroyed", false);
        return search(sc, null);
    }

    @Override
    public List<TemplateDataStoreVO> listByTemplateStoreDownloadStatus(long templateId, long storeId, Status... status) {
        SearchCriteria<TemplateDataStoreVO> sc = storeTemplateDownloadStatusSearch.create();
        sc.setParameters("template_id", templateId);
        sc.setParameters("store_id", storeId);
        sc.setParameters("downloadState", (Object[]) status);
        sc.setParameters("destroyed", false);
        return search(sc, null);
    }

    @Override
    public List<TemplateDataStoreVO> listByTemplateZoneDownloadStatus(long templateId, Long zoneId, Status... status) {
        // get all elgible image stores
        List<DataStore> imgStores = this._storeMgr.getImageStoresByScope(new ZoneScope(zoneId));
        if (imgStores != null) {
            List<TemplateDataStoreVO> result = new ArrayList<TemplateDataStoreVO>();
            for (DataStore store : imgStores) {
                List<TemplateDataStoreVO> sRes = this.listByTemplateStoreDownloadStatus(templateId, store.getId(),
                        status);
                if (sRes != null && sRes.size() > 0) {
                    result.addAll(sRes);
                }
            }
            return result;
        }
        return null;
    }

    @Override
    public TemplateDataStoreVO findByTemplateZoneDownloadStatus(long templateId, Long zoneId, Status... status) {
        // get all elgible image stores
        List<DataStore> imgStores = this._storeMgr.getImageStoresByScope(new ZoneScope(zoneId));
        if (imgStores != null) {
            for (DataStore store : imgStores) {
                List<TemplateDataStoreVO> sRes = this.listByTemplateStoreDownloadStatus(templateId, store.getId(),
                        status);
                if (sRes != null && sRes.size() > 0) {
                    Collections.shuffle(sRes);
                    return sRes.get(0);
                }
            }
        }
        return null;
    }

    @Override
    public TemplateDataStoreVO findByStoreTemplate(long storeId, long templateId) {
        SearchCriteria<TemplateDataStoreVO> sc = storeTemplateSearch.create();
        sc.setParameters("store_id", storeId);
        sc.setParameters("template_id", templateId);
        sc.setParameters("destroyed", false);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public TemplateDataStoreVO findByStoreTemplate(long storeId, long templateId, boolean lock) {
        SearchCriteria<TemplateDataStoreVO> sc = storeTemplateSearch.create();
        sc.setParameters("store_id", storeId);
        sc.setParameters("template_id", templateId);
        sc.setParameters("destroyed", false);
        if (!lock)
            return findOneIncludingRemovedBy(sc);
        else
            return lockOneRandomRow(sc, true);
    }

    @Override
    public TemplateDataStoreVO findByTemplate(long templateId, DataStoreRole role) {
        SearchCriteria<TemplateDataStoreVO> sc = templateRoleSearch.create();
        sc.setParameters("template_id", templateId);
        sc.setParameters("store_role", role);
        sc.setParameters("destroyed", false);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public List<TemplateDataStoreVO> listByTemplate(long templateId) {
        SearchCriteria<TemplateDataStoreVO> sc = templateSearch.create();
        sc.setParameters("template_id", templateId);
        sc.setParameters("destroyed", false);
        return search(sc, null);
    }

    @Override
    public TemplateDataStoreVO findByTemplateZone(long templateId, Long zoneId, DataStoreRole role) {
        // get all elgible image stores
        List<DataStore> imgStores = null;
        if (role == DataStoreRole.Image) {
            imgStores = this._storeMgr.getImageStoresByScope(new ZoneScope(zoneId));
        } else if (role == DataStoreRole.ImageCache) {
            imgStores = this._storeMgr.getImageCacheStores(new ZoneScope(zoneId));
        }
        if (imgStores != null) {
            for (DataStore store : imgStores) {
                List<TemplateDataStoreVO> sRes = this.listByTemplateStore(templateId, store.getId());
                if (sRes != null && sRes.size() > 0) {
                    return sRes.get(0);
                }
            }
        }
        return null;
    }

}
