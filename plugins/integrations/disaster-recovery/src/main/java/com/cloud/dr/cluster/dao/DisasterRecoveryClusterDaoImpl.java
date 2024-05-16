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

package com.cloud.dr.cluster.dao;

import com.cloud.dr.cluster.DisasterRecoveryClusterDetailsVO;
import com.cloud.dr.cluster.DisasterRecoveryClusterVO;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DisasterRecoveryClusterDaoImpl extends GenericDaoBase<DisasterRecoveryClusterVO, Long> implements DisasterRecoveryClusterDao {
    @Inject
    DisasterRecoveryClusterDetailsDao _disasterRecoveryClusterDetailsDao;
    protected SearchBuilder<DisasterRecoveryClusterVO> DisasterRecoveryClusterListSearchBuilder;

    protected DisasterRecoveryClusterDaoImpl() {
        super();
        DisasterRecoveryClusterListSearchBuilder = createSearchBuilder();
        DisasterRecoveryClusterListSearchBuilder.and("msHostId", DisasterRecoveryClusterListSearchBuilder.entity().getMsHostId(), SearchCriteria.Op.EQ);
        DisasterRecoveryClusterListSearchBuilder.and("name", DisasterRecoveryClusterListSearchBuilder.entity().getName(), SearchCriteria.Op.LIKE);
        DisasterRecoveryClusterListSearchBuilder.and("id", DisasterRecoveryClusterListSearchBuilder.entity().getId(), SearchCriteria.Op.EQ);
        DisasterRecoveryClusterListSearchBuilder.done();
    }

    @Override
    public List<DisasterRecoveryClusterVO> getDisasterRecoveryClusterList(long id) {
        SearchCriteria<DisasterRecoveryClusterVO> sc = DisasterRecoveryClusterListSearchBuilder.create();
        sc.setParameters("id", id);
        return listBy(sc);
    }

    @Override
    public DisasterRecoveryClusterVO getDisasterRecoveryResult(long msHostId, String name) {
        SearchCriteria<DisasterRecoveryClusterVO> sc = DisasterRecoveryClusterListSearchBuilder.create();
        sc.setParameters("msHostId", msHostId);
        sc.setParameters("name", name);
        List<DisasterRecoveryClusterVO> checks = listBy(sc);
        return checks.isEmpty() ? null : checks.get(0);
    }

    @Override
    public void saveDetails(DisasterRecoveryClusterVO tmpl) {
        Map<String, String> detailsStr = tmpl.getDetails();
        if (detailsStr == null) {
            return;
        }
        List<DisasterRecoveryClusterDetailsVO> details = new ArrayList<DisasterRecoveryClusterDetailsVO>();
        for (String key : detailsStr.keySet()) {
            DisasterRecoveryClusterDetailsVO detail = new DisasterRecoveryClusterDetailsVO(tmpl.getId(), key, detailsStr.get(key), true);
            details.add(detail);
        }

        _disasterRecoveryClusterDetailsDao.saveDetails(details);
    }
}
