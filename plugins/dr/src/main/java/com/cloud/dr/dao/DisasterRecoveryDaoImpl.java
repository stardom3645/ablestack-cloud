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

package com.cloud.dr.dao;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud.security.DisasterRecoveryVO;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
public class DisasterRecoveryDaoImpl extends GenericDaoBase<DisasterRecoveryVO, Long> implements DisasterRecoveryDao {
    protected SearchBuilder<DisasterRecoveryVO> DisasterRecoverysSearchBuilder;

    protected DisasterRecoveryDaoImpl() {
        super();
        DisasterRecoverysSearchBuilder = createSearchBuilder();
        DisasterRecoverysSearchBuilder.and("msHostId", DisasterRecoverysSearchBuilder.entity().getMsHostId(), SearchCriteria.Op.EQ);
        DisasterRecoverysSearchBuilder.and("checkName", DisasterRecoverysSearchBuilder.entity().getCheckName(), SearchCriteria.Op.EQ);
        DisasterRecoverysSearchBuilder.done();
    }

    @Override
    public List<DisasterRecoveryVO> getDisasterRecoverys(long msHostId) {
        SearchCriteria<DisasterRecoveryVO> sc = DisasterRecoverysSearchBuilder.create();
        sc.setParameters("msHostId", msHostId);
        return listBy(sc);
    }

    @Override
    public DisasterRecoveryVO getDisasterRecoveryResult(long msHostId, String checkName) {
        SearchCriteria<DisasterRecoveryVO> sc = DisasterRecoverysSearchBuilder.create();
        sc.setParameters("msHostId", msHostId);
        sc.setParameters("checkName", checkName);
        List<DisasterRecoveryVO> checks = listBy(sc);
        return checks.isEmpty() ? null : checks.get(0);
    }
}
