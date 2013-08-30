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
package org.apache.cloudstack.storage.datastore.db;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.cloudstack.engine.subsystem.api.storage.DataObjectInStore;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.State;

import com.cloud.storage.Storage;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.fsm.StateObject;

/**
 * Join table for image_data_store and volumes
 * 
 */
@Entity
@Table(name = "volume_store_ref")
public class VolumeDataStoreVO implements StateObject<ObjectInDataStoreStateMachine.State>, DataObjectInStore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "store_id")
    private long dataStoreId;

    @Column(name = "volume_id")
    private long volumeId;

    @Column(name = "zone_id")
    private long zoneId;

    @Column(name = GenericDaoBase.CREATED_COLUMN)
    private Date created = null;

    @Column(name = "last_updated")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date lastUpdated = null;

    @Column(name = "download_pct")
    private int downloadPercent;

    @Column(name = "size")
    private long size;

    @Column(name = "physical_size")
    private long physicalSize;

    @Column(name = "download_state")
    @Enumerated(EnumType.STRING)
    private Status downloadState;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "local_path")
    private String localDownloadPath;

    @Column(name = "error_str")
    private String errorString;

    @Column(name = "job_id")
    private String jobId;

    @Column(name = "install_path")
    private String installPath;

    @Column(name = "url")
    private String downloadUrl;

    @Column(name = "download_url")
    private String extractUrl;

    @Column(name = "destroyed")
    boolean destroyed = false;

    @Column(name = "update_count", updatable = true, nullable = false)
    protected long updatedCount;

    @Column(name = "updated")
    @Temporal(value = TemporalType.TIMESTAMP)
    Date updated;

    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    ObjectInDataStoreStateMachine.State state;

    @Column(name = "ref_cnt")
    Long refCnt = 0L;

    public String getInstallPath() {
        return installPath;
    }

    @Override
    public long getDataStoreId() {
        return dataStoreId;
    }

    public void setDataStoreId(long storeId) {
        this.dataStoreId = storeId;
    }

    public long getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(long volumeId) {
        this.volumeId = volumeId;
    }

    public long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    public int getDownloadPercent() {
        return downloadPercent;
    }

    public void setDownloadPercent(int downloadPercent) {
        this.downloadPercent = downloadPercent;
    }

    public void setDownloadState(Status downloadState) {
        this.downloadState = downloadState;
    }

    public long getId() {
        return id;
    }

    public Date getCreated() {
        return created;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date date) {
        lastUpdated = date;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    public Status getDownloadState() {
        return downloadState;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public VolumeDataStoreVO(long hostId, long volumeId) {
        super();
        this.dataStoreId = hostId;
        this.volumeId = volumeId;
        this.state = ObjectInDataStoreStateMachine.State.Allocated;
        this.refCnt = 0L;
    }

    public VolumeDataStoreVO(long hostId, long volumeId, Date lastUpdated, int downloadPercent, Status downloadState,
            String localDownloadPath, String errorString, String jobId, String installPath, String downloadUrl,
            String checksum) {
        // super();
        this.dataStoreId = hostId;
        this.volumeId = volumeId;
        // this.zoneId = zoneId;
        this.lastUpdated = lastUpdated;
        this.downloadPercent = downloadPercent;
        this.downloadState = downloadState;
        this.localDownloadPath = localDownloadPath;
        this.errorString = errorString;
        this.jobId = jobId;
        this.installPath = installPath;
        this.setDownloadUrl(downloadUrl);
        this.checksum = checksum;
        this.refCnt = 0L;
    }

    public VolumeDataStoreVO() {
        this.refCnt = 0L;
    }

    public void setLocalDownloadPath(String localPath) {
        this.localDownloadPath = localPath;
    }

    public String getLocalDownloadPath() {
        return localDownloadPath;
    }

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }

    public String getErrorString() {
        return errorString;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public boolean equals(Object obj) {
        if (obj instanceof VolumeDataStoreVO) {
            VolumeDataStoreVO other = (VolumeDataStoreVO) obj;
            return (this.volumeId == other.getVolumeId() && this.dataStoreId == other.getDataStoreId());
        }
        return false;
    }

    public int hashCode() {
        Long tid = new Long(volumeId);
        Long hid = new Long(dataStoreId);
        return tid.hashCode() + hid.hashCode();
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public void setPhysicalSize(long physicalSize) {
        this.physicalSize = physicalSize;
    }

    public long getPhysicalSize() {
        return physicalSize;
    }

    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
    }

    public boolean getDestroyed() {
        return destroyed;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public long getVolumeSize() {
        return -1;
    }

    public String toString() {
        return new StringBuilder("VolumeDataStore[").append(id).append("-").append(volumeId).append("-").append(dataStoreId)
                .append(installPath).append("]").toString();
    }

    public long getUpdatedCount() {
        return this.updatedCount;
    }

    public void incrUpdatedCount() {
        this.updatedCount++;
    }

    public void decrUpdatedCount() {
        this.updatedCount--;
    }

    public Date getUpdated() {
        return updated;
    }

    @Override
    public ObjectInDataStoreStateMachine.State getState() {
        // TODO Auto-generated method stub
        return this.state;
    }

    public void setState(ObjectInDataStoreStateMachine.State state) {
        this.state = state;
    }

    @Override
    public long getObjectId() {
        return this.getVolumeId();
    }

    @Override
    public State getObjectInStoreState() {
        return this.state;
    }

    public Long getRefCnt() {
        return refCnt;
    }

    public void incrRefCnt() {
        this.refCnt++;
    }

    public void decrRefCnt() {
        this.refCnt--;
    }

    public String getExtractUrl() {
        return extractUrl;
    }

    public void setExtractUrl(String extractUrl) {
        this.extractUrl = extractUrl;
    }
}
