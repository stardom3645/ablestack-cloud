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

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.cloudstack.engine.subsystem.api.storage.DataObjectInStore;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.Event;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.State;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.storage.DataStoreRole;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.UpdateBuilder;

@Component
public class SnapshotDataStoreDaoImpl extends GenericDaoBase<SnapshotDataStoreVO, Long> implements SnapshotDataStoreDao {
    private static final Logger s_logger = Logger.getLogger(SnapshotDataStoreDaoImpl.class);
    private SearchBuilder<SnapshotDataStoreVO> updateStateSearch;
    private SearchBuilder<SnapshotDataStoreVO> storeSearch;
    private SearchBuilder<SnapshotDataStoreVO> destroyedSearch;
    private SearchBuilder<SnapshotDataStoreVO> snapshotSearch;
    private SearchBuilder<SnapshotDataStoreVO> storeSnapshotSearch;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        storeSearch = createSearchBuilder();
        storeSearch.and("store_id", storeSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeSearch.and("store_role", storeSearch.entity().getRole(), SearchCriteria.Op.EQ);
        storeSearch.done();

        destroyedSearch = createSearchBuilder();
        destroyedSearch.and("store_id", destroyedSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        destroyedSearch.and("store_role", destroyedSearch.entity().getRole(), SearchCriteria.Op.EQ);
        destroyedSearch.and("state", destroyedSearch.entity().getState(), SearchCriteria.Op.EQ);
        destroyedSearch.done();

        updateStateSearch = this.createSearchBuilder();
        updateStateSearch.and("id", updateStateSearch.entity().getId(), Op.EQ);
        updateStateSearch.and("state", updateStateSearch.entity().getState(), Op.EQ);
        updateStateSearch.and("updatedCount", updateStateSearch.entity().getUpdatedCount(), Op.EQ);
        updateStateSearch.done();

        snapshotSearch = createSearchBuilder();
        snapshotSearch.and("snapshot_id", snapshotSearch.entity().getSnapshotId(), SearchCriteria.Op.EQ);
        snapshotSearch.and("store_role", snapshotSearch.entity().getRole(), SearchCriteria.Op.EQ);
        snapshotSearch.done();

        storeSnapshotSearch = createSearchBuilder();
        storeSnapshotSearch.and("snapshot_id", storeSnapshotSearch.entity().getSnapshotId(), SearchCriteria.Op.EQ);
        storeSnapshotSearch.and("store_id", storeSnapshotSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeSnapshotSearch.and("store_role", storeSnapshotSearch.entity().getRole(), SearchCriteria.Op.EQ);
        storeSnapshotSearch.done();

        return true;
    }

    @Override
    public boolean updateState(State currentState, Event event, State nextState, DataObjectInStore vo, Object data) {
        SnapshotDataStoreVO dataObj = (SnapshotDataStoreVO) vo;
        Long oldUpdated = dataObj.getUpdatedCount();
        Date oldUpdatedTime = dataObj.getUpdated();

        SearchCriteria<SnapshotDataStoreVO> sc = updateStateSearch.create();
        sc.setParameters("id", dataObj.getId());
        sc.setParameters("state", currentState);
        sc.setParameters("updatedCount", dataObj.getUpdatedCount());

        dataObj.incrUpdatedCount();

        UpdateBuilder builder = getUpdateBuilder(dataObj);
        builder.set(dataObj, "state", nextState);
        builder.set(dataObj, "updated", new Date());

        int rows = update(dataObj, sc);
        if (rows == 0 && s_logger.isDebugEnabled()) {
            SnapshotDataStoreVO dbVol = findByIdIncludingRemoved(dataObj.getId());
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
    public List<SnapshotDataStoreVO> listByStoreId(long id, DataStoreRole role) {
        SearchCriteria<SnapshotDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        sc.setParameters("store_role", role);
        return listBy(sc);
    }

    @Override
    public void deletePrimaryRecordsForStore(long id) {
        SearchCriteria<SnapshotDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        Transaction txn = Transaction.currentTxn();
        txn.start();
        remove(sc);
        txn.commit();
    }

    @Override
    public SnapshotDataStoreVO findByStoreSnapshot(DataStoreRole role, long storeId, long snapshotId) {
        SearchCriteria<SnapshotDataStoreVO> sc = storeSnapshotSearch.create();
        sc.setParameters("store_id", storeId);
        sc.setParameters("snapshot_id", snapshotId);
        sc.setParameters("store_role", role);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public SnapshotDataStoreVO findBySnapshot(long snapshotId, DataStoreRole role) {
        SearchCriteria<SnapshotDataStoreVO> sc = snapshotSearch.create();
        sc.setParameters("snapshot_id", snapshotId);
        sc.setParameters("store_role", role);
        return findOneBy(sc);
    }

    @Override
    public List<SnapshotDataStoreVO> listDestroyed(long id) {
        SearchCriteria<SnapshotDataStoreVO> sc = destroyedSearch.create();
        sc.setParameters("store_id", id);
        sc.setParameters("store_role", DataStoreRole.Image);
        sc.setParameters("state", ObjectInDataStoreStateMachine.State.Destroyed);
        return listBy(sc);
    }
}
