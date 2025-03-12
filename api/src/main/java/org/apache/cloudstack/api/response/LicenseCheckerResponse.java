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

package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import java.util.Date;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;


public class LicenseCheckerResponse extends BaseResponse {
    @SerializedName(ApiConstants.HOST_ID)
    @Param(description = "the id of the host")
    private Long HostId;

    @SerializedName(ApiConstants.EXPIRY_DATE)
    @Param(description = "license expiry date")
    private Date expiryDate;

    @SerializedName(ApiConstants.SUCCESS)
    @Param(description = "license success")
    private String success;

    public Long getHostId() {
        return HostId;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public String getSuccess() {
        return success;
    }

    public void setHostId(long hostId) {
        this.HostId = Long.valueOf(hostId);
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setSuccess(boolean isValid) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setSuccess'");
    }

    public void setMessage(Object object) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setMessage'");
    }
}
