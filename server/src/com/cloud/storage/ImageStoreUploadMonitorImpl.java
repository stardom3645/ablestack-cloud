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
package com.cloud.storage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.command.UploadStatusAnswer;
import org.apache.cloudstack.storage.command.UploadStatusCommand;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreVO;
import org.apache.cloudstack.utils.identity.ManagementServerNode;

import com.cloud.agent.Listener;
import com.cloud.agent.api.AgentControlAnswer;
import com.cloud.agent.api.AgentControlCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.StartupCommand;
import com.cloud.exception.ConnectionException;
import com.cloud.host.Host;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.storage.Volume.Event;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.utils.fsm.StateMachine2;

/**
 * Monitors the progress of upload.
 */
@Component
@Local(value = {ImageStoreUploadMonitor.class})
public class ImageStoreUploadMonitorImpl extends ManagerBase implements ImageStoreUploadMonitor, Listener {

    static final Logger s_logger = Logger.getLogger(ImageStoreUploadMonitorImpl.class);

    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private VolumeDao _volumeDao;
    @Inject
    private VolumeDataStoreDao _volumeDataStoreDao;
    @Inject
    private HostDao _hostDao;
    @Inject
    private EndPointSelector _epSelector;
    @Inject
    private DataStoreManager storeMgr;

    private long _nodeId;
    private ScheduledExecutorService _executor = null;
    private int _monitoringInterval;
    private long _uploadOperationTimeout;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Upload-Monitor"));
        _monitoringInterval = 60;
        _uploadOperationTimeout = 10 * 60 * 1000; // 10 minutes
        _nodeId = ManagementServerNode.getManagementServerId();
        return true;
    }

    @Override
    public boolean start() {
        _executor.scheduleWithFixedDelay(new UploadStatusCheck(), _monitoringInterval, _monitoringInterval, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public boolean stop() {
        _executor.shutdownNow();
        return true;
    }

    @Override
    public boolean processAnswers(long agentId, long seq, Answer[] answers) {
        return false;
    }

    @Override
    public boolean processCommands(long agentId, long seq, Command[] commands) {
        return false;
    }

    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
        return null;
    }

    @Override
    public boolean processDisconnect(long agentId, Status state) {
        return false;
    }

    @Override
    public boolean isRecurring() {
        return false;
    }

    @Override
    public int getTimeout() {
        return 0;
    }

    @Override
    public boolean processTimeout(long agentId, long seq) {
        return false;
    }

    @Override
    public void processConnect(Host host, StartupCommand cmd, boolean forRebalance) throws ConnectionException {
    }

    protected class UploadStatusCheck extends ManagedContextRunnable {

        public UploadStatusCheck() {
        }

        @Override
        protected void runInContext() {
            // 1. Select all entries with download_state = Not_Downloaded or Download_In_Progress
            // 2. Get corresponding volume
            // 3. Get EP using _epSelector
            // 4. Check if SSVM is owned by this MS
            // 5. If owned by MS then send command to appropriate SSVM
            // 6. In listener check for the answer and update DB accordingly
            List<VolumeDataStoreVO> volumeDataStores = _volumeDataStoreDao.listByVolumeState(Volume.State.NotUploaded, Volume.State.UploadInProgress);
            for (VolumeDataStoreVO volumeDataStore : volumeDataStores) {
                DataStore dataStore = storeMgr.getDataStore(volumeDataStore.getDataStoreId(), DataStoreRole.Image);
                EndPoint ep = _epSelector.select(dataStore, volumeDataStore.getExtractUrl());
                if (ep == null) {
                    s_logger.warn("There is no secondary storage VM for image store " + dataStore.getName());
                    continue;
                }
                VolumeVO volume = _volumeDao.findById(volumeDataStore.getVolumeId());
                if (volume == null) {
                    s_logger.warn("Volume with id " + volumeDataStore.getVolumeId() + " not found");
                    continue;
                }
                Host host = _hostDao.findById(ep.getId());
                if (host != null && host.getManagementServerId() != null && _nodeId == host.getManagementServerId().longValue()) {
                    UploadStatusCommand cmd = new UploadStatusCommand(volume.getId());
                    Answer answer = ep.sendMessage(cmd);
                    if (answer == null || !(answer instanceof UploadStatusAnswer)) {
                        s_logger.warn("No or invalid answer corresponding to UploadStatusCommand for volume " + volumeDataStore.getVolumeId());
                        continue;
                    }
                    handleStatusResponse((UploadStatusAnswer)answer, volume, volumeDataStore);
                }
            }
        }

        private void handleStatusResponse(final UploadStatusAnswer answer, final VolumeVO volume, final VolumeDataStoreVO volumeDataStore) {
            final StateMachine2<Volume.State, Event, Volume> stateMachine = Volume.State.getStateMachine();
            Transaction.execute(new TransactionCallbackNoReturn() {
                @Override
                public void doInTransactionWithoutResult(TransactionStatus status) {
                    VolumeVO tmpVolume = _volumeDao.findById(volume.getId());
                    VolumeDataStoreVO tmpVolumeDataStore = _volumeDataStoreDao.findById(volumeDataStore.getId());
                    try {
                        switch (answer.getStatus()) {
                        case COMPLETED:
                            tmpVolumeDataStore.setDownloadState(VMTemplateStorageResourceAssoc.Status.UPLOADED);
                            stateMachine.transitTo(tmpVolume, Event.OperationSucceeded, null, _volumeDao);
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Volume " + tmpVolume.getUuid() + " uploaded successfully");
                            }
                            break;
                        case IN_PROGRESS:
                            tmpVolumeDataStore.setDownloadState(VMTemplateStorageResourceAssoc.Status.UPLOAD_IN_PROGRESS);
                            // check for timeout
                            if (tmpVolumeDataStore.getDownloadState() == VMTemplateStorageResourceAssoc.Status.NOT_UPLOADED ||
                                tmpVolumeDataStore.getDownloadState() == VMTemplateStorageResourceAssoc.Status.UPLOAD_IN_PROGRESS) {
                                if (System.currentTimeMillis() - tmpVolumeDataStore.getCreated().getTime() > _uploadOperationTimeout) {
                                    tmpVolumeDataStore.setDownloadState(VMTemplateStorageResourceAssoc.Status.ABANDONED);
                                    stateMachine.transitTo(tmpVolume, Event.OperationTimeout, null, _volumeDao);
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Volume " + tmpVolume.getUuid() + " failed to upload due to operation timed out");
                                    }
                                }
                            }
                            break;
                        case ERROR:
                            tmpVolumeDataStore.setDownloadState(VMTemplateStorageResourceAssoc.Status.UPLOAD_ERROR);
                            stateMachine.transitTo(tmpVolume, Event.OperationFailed, null, _volumeDao);
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Volume " + tmpVolume.getUuid() + " failed to upload. Error details: " + answer.getDetails());
                            }
                            break;
                        case UNKNOWN:
                            // check for timeout
                            if (tmpVolumeDataStore.getDownloadState() == VMTemplateStorageResourceAssoc.Status.NOT_UPLOADED ||
                                tmpVolumeDataStore.getDownloadState() == VMTemplateStorageResourceAssoc.Status.UPLOAD_IN_PROGRESS) {
                                if (System.currentTimeMillis() - tmpVolumeDataStore.getCreated().getTime() > _uploadOperationTimeout) {
                                    tmpVolumeDataStore.setDownloadState(VMTemplateStorageResourceAssoc.Status.ABANDONED);
                                    stateMachine.transitTo(tmpVolume, Event.OperationTimeout, null, _volumeDao);
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Volume " + tmpVolume.getUuid() + " failed to upload due to operation timed out");
                                    }
                                }
                            }
                            break;
                        }
                        _volumeDataStoreDao.update(tmpVolumeDataStore.getId(), tmpVolumeDataStore);
                    } catch (NoTransitionException e) {
                        s_logger.error("Unexpected error " + e.getMessage());
                    }
                }
            });
        }
    }


}
