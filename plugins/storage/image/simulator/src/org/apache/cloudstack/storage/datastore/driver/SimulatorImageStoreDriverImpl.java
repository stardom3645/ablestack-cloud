/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudstack.storage.datastore.driver;


import com.cloud.agent.api.storage.DownloadAnswer;
import com.cloud.agent.api.to.DataObjectType;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.NfsTO;
import com.cloud.storage.Storage;
import com.cloud.storage.VMTemplateStorageResourceAssoc;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VolumeDao;
import org.apache.cloudstack.engine.subsystem.api.storage.CreateCmdResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.framework.async.AsyncCallbackDispatcher;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.storage.image.BaseImageStoreDriverImpl;
import org.apache.cloudstack.storage.image.store.ImageStoreImpl;

import javax.inject.Inject;
import java.util.UUID;

public class SimulatorImageStoreDriverImpl extends BaseImageStoreDriverImpl {

    @Inject
    TemplateDataStoreDao _templateStoreDao;
    @Inject
    VMTemplateDao _templateDao;
    @Inject
    VolumeDao _volumeDao;
    @Inject
    VolumeDataStoreDao _volumeStoreDao;

    @Override
    public DataStoreTO getStoreTO(DataStore store) {
        ImageStoreImpl nfsStore = (ImageStoreImpl) store;
        NfsTO nfsTO = new NfsTO();
        nfsTO.setRole(store.getRole());
        nfsTO.setUrl(nfsStore.getUri());
        return nfsTO;
    }



    public String createEntityExtractUrl(DataStore store, String installPath, Storage.ImageFormat format) {
        return null;
    }

    @Override
    public void createAsync(DataStore store, DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        if (data.getType() == DataObjectType.TEMPLATE) {
            this.createTemplate(data, callback);
        } else if (data.getType() == DataObjectType.VOLUME) {
            this.createVolume(data, callback);
        }
    }

    protected void createTemplate(DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        CreateContext<CreateCmdResult> context = new CreateContext<CreateCmdResult>(callback, data);
        AsyncCallbackDispatcher<SimulatorImageStoreDriverImpl, DownloadAnswer> caller = AsyncCallbackDispatcher
                .create(this);
        caller.setContext(context);
        caller.setCallback(caller.getTarget().createTemplateAsyncCallback(null, null));
        String path =  UUID.randomUUID().toString();
        Long size = new Long(5 * 1024L * 1024L);
        DownloadAnswer answer = new DownloadAnswer(null, 100, null, VMTemplateStorageResourceAssoc.Status.DOWNLOADED,
                path, path, size, size, null);
        caller.complete(answer);
        return;
    }

    protected void createVolume(DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        CreateContext<CreateCmdResult> context = new CreateContext<CreateCmdResult>(callback, data);
        AsyncCallbackDispatcher<SimulatorImageStoreDriverImpl, DownloadAnswer> caller = AsyncCallbackDispatcher
                .create(this);
        caller.setContext(context);
        caller.setCallback(caller.getTarget().createVolumeAsyncCallback(null, null));
        String path =  UUID.randomUUID().toString();
        Long size = new Long(5 * 1024L * 1024L);
        DownloadAnswer answer = new DownloadAnswer(null, 100, null, VMTemplateStorageResourceAssoc.Status.DOWNLOADED,
                path, path, size, size, null);
        caller.complete(answer);
        return;
    }
}