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
package com.cloud.exception;

import java.util.HashMap;

import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.SerialVersionUID;

/**
 *
 */
public class CloudExecutionException extends CloudRuntimeException {
    private final static long serialVersionUID = SerialVersionUID.CloudExecutionException;

    private final ErrorCode code;
    private final HashMap<String, Object> details;

    public CloudExecutionException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        details = new HashMap<String, Object>();
    }

    public ErrorCode getErrorCode() {
        return code;
    }

    public String getErrorMessage() {
        return new StringBuilder("Error Code=").append(code).append("; Error Message=").append(super.toString()).toString();
    }

    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("Error Code=").append(code);
        buff.append("; Error Message=").append(super.toString());
        if (details.size() > 0) {
            buff.append("; Error Details=").append(details.toString());
        }
        return buff.toString();
    }
}
