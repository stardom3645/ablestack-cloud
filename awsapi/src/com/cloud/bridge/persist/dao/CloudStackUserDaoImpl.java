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
package com.cloud.bridge.persist.dao;

import javax.ejb.Local;

import org.apache.log4j.Logger;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

import com.cloud.bridge.model.CloudStackUserVO;
import com.cloud.bridge.util.EncryptionSecretKeyCheckerUtil;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;

@Local(value={CloudStackUserDao.class})
public class CloudStackUserDaoImpl extends GenericDaoBase<CloudStackUserVO, String> implements CloudStackUserDao {
    public static final Logger logger = Logger.getLogger(CloudStackUserDaoImpl.class);

    public CloudStackUserDaoImpl() {}

    @Override
    public String getSecretKeyByAccessKey( String accessKey ) {
        CloudStackUserVO user = null;
        String cloudSecretKey = null;

        SearchBuilder <CloudStackUserVO> searchByAccessKey = createSearchBuilder();
        searchByAccessKey.and("apiKey", searchByAccessKey.entity().getApiKey(), SearchCriteria.Op.EQ);
        searchByAccessKey.done();
        Transaction txn = Transaction.open(Transaction.CLOUD_DB);
        try {
            txn.start();
            SearchCriteria<CloudStackUserVO> sc = searchByAccessKey.create();
            sc.setParameters("apiKey", accessKey);
            user =  findOneBy(sc);
            if ( user != null && user.getSecretKey() != null) {
                // if the cloud db is encrypted, decrypt the secret_key returned by cloud db before signature generation
                if( EncryptionSecretKeyCheckerUtil.useEncryption() ) {
                    StandardPBEStringEncryptor encryptor = EncryptionSecretKeyCheckerUtil.getEncryptor();
                    cloudSecretKey = encryptor.decrypt( user.getSecretKey() );
                } else {
                    cloudSecretKey = user.getSecretKey();
                }
            }
            return cloudSecretKey;
        } finally {
            txn.close();
        }
    }

}
