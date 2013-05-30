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
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.Event;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.State;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.UpdateBuilder;

@Component
public class VolumeDataStoreDaoImpl extends GenericDaoBase<VolumeDataStoreVO, Long> implements VolumeDataStoreDao {
    private static final Logger s_logger = Logger.getLogger(VolumeDataStoreDaoImpl.class);
    private SearchBuilder<VolumeDataStoreVO> updateStateSearch;
    private SearchBuilder<VolumeDataStoreVO> volumeSearch;
    private SearchBuilder<VolumeDataStoreVO> storeSearch;
    private SearchBuilder<VolumeDataStoreVO> storeVolumeSearch;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        storeSearch = createSearchBuilder();
        storeSearch.and("store_id", storeSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeSearch.and("destroyed", storeSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        storeSearch.done();

        volumeSearch = createSearchBuilder();
        volumeSearch.and("volume_id", volumeSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        volumeSearch.and("destroyed", volumeSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        volumeSearch.done();

        storeVolumeSearch = createSearchBuilder();
        storeVolumeSearch.and("store_id", storeVolumeSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        storeVolumeSearch.and("volume_id", storeVolumeSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        storeVolumeSearch.and("destroyed", storeVolumeSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        storeVolumeSearch.done();

        updateStateSearch = this.createSearchBuilder();
        updateStateSearch.and("id", updateStateSearch.entity().getId(), Op.EQ);
        updateStateSearch.and("state", updateStateSearch.entity().getState(), Op.EQ);
        updateStateSearch.and("updatedCount", updateStateSearch.entity().getUpdatedCount(), Op.EQ);
        updateStateSearch.done();
        return true;
    }

    @Override
    public boolean updateState(State currentState, Event event, State nextState, DataObjectInStore vo, Object data) {
        VolumeDataStoreVO dataObj = (VolumeDataStoreVO) vo;
        Long oldUpdated = dataObj.getUpdatedCount();
        Date oldUpdatedTime = dataObj.getUpdated();

        SearchCriteria<VolumeDataStoreVO> sc = updateStateSearch.create();
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
            VolumeDataStoreVO dbVol = findByIdIncludingRemoved(dataObj.getId());
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
    public List<VolumeDataStoreVO> listByStoreId(long id) {
        SearchCriteria<VolumeDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        sc.setParameters("destroyed", false);
        return listIncludingRemovedBy(sc);
    }

    @Override
    public void deletePrimaryRecordsForStore(long id) {
        SearchCriteria<VolumeDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        Transaction txn = Transaction.currentTxn();
        txn.start();
        remove(sc);
        txn.commit();
    }

    @Override
    public VolumeDataStoreVO findByVolume(long volumeId) {
        SearchCriteria<VolumeDataStoreVO> sc = volumeSearch.create();
        sc.setParameters("volume_id", volumeId);
        sc.setParameters("destroyed", false);
        return findOneBy(sc);
    }

    @Override
    public VolumeDataStoreVO findByStoreVolume(long storeId, long volumeId) {
        SearchCriteria<VolumeDataStoreVO> sc = storeVolumeSearch.create();
        sc.setParameters("store_id", storeId);
        sc.setParameters("volume_id", volumeId);
        sc.setParameters("destroyed", false);
        return findOneBy(sc);
    }

    @Override
    public VolumeDataStoreVO findByStoreVolume(long storeId, long volumeId, boolean lock) {
        SearchCriteria<VolumeDataStoreVO> sc = storeVolumeSearch.create();
        sc.setParameters("store_id", storeId);
        sc.setParameters("volume_id", volumeId);
        sc.setParameters("destroyed", false);
        if (!lock)
            return findOneIncludingRemovedBy(sc);
        else
            return lockOneRandomRow(sc, true);
    }

    @Override
    public List<VolumeDataStoreVO> listDestroyed(long id) {
        SearchCriteria<VolumeDataStoreVO> sc = storeSearch.create();
        sc.setParameters("store_id", id);
        sc.setParameters("destroyed", true);
        return listIncludingRemovedBy(sc);
    }
}
