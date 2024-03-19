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
package org.apache.cloudstack.api.command.user.volume;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.UserCmd;
import org.apache.cloudstack.api.response.DiskOfferingResponse;
import org.apache.cloudstack.api.response.VolumeResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.storage.Snapshot;
import com.cloud.storage.Volume;
import com.cloud.vm.VirtualMachine;

@APICommand(name = "createRbdVolume", responseObject = VolumeResponse.class, description = "Creates a disk volume from a disk offering. This disk volume must still be attached to a virtual machine to make use of it.", responseView = ResponseView.Full, entityType = {
        Volume.class, VirtualMachine.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
        public class CreateRbdVolumeCmd extends CreateVolumeCmd implements UserCmd {    
        private static final String s_name = "createvolumeresponse";

        @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = DiskOfferingResponse.class, required = true,
        description = "id of the storage pool")
        private Long RbdId;


        @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, entityType = DiskOfferingResponse.class, required = true,
        description = "id of the storage pool")
        private String RbdName;

        @Parameter(name = ApiConstants.SIZE, type = CommandType.LONG, required = true, description = "path to list on storage pool")
        private long RbdSize;

        @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class,
        description = "the zone where certificates are uploaded")
        private Long zoneId;

        /////////////////////////////////////////////////////
        /////////////////// Accessors ///////////////////////
        /////////////////////////////////////////////////////

        public Long getZoneId() {
                return zoneId;
        }

        public Long getRbdId() {
                return RbdId;
        }

        public String getRbdName() {
                return RbdName;
        }

        public long getRbdSize() {
                return RbdSize;
        }
        /////////////////////////////////////////////////////
        /////////////// API Implementation///////////////////
        /////////////////////////////////////////////////////

        @Override
        public void execute() {
                CallContext.current().setEventDetails("Volume Id: " + getEntityUuid() + ((getSnapshotId() == null) ? "" : " from snapshot: " + this._uuidMgr.getUuid(Snapshot.class, getSnapshotId())));
                Volume volume = _volumeService.createVolume(this);
                if (volume != null) {
                VolumeResponse response = _responseGenerator.createVolumeResponse(getResponseView(), volume);
                //FIXME - have to be moved to ApiResponseHelper
                if (getSnapshotId() != null) {
                        Snapshot snap = _entityMgr.findById(Snapshot.class, getSnapshotId());
                        if (snap != null) {
                        response.setSnapshotId(snap.getUuid()); // if the volume was
                        // created from a
                        // snapshot,
                        // snapshotId will
                        // be set so we pass
                        // it back in the
                        // response
                        }
                }
                response.setResponseName(getCommandName());
                setResponseObject(response);
                } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create a volume");
                }
        }
}
