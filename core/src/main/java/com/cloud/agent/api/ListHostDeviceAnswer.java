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

package com.cloud.agent.api;

import java.util.Collections;
import java.util.List;

public class ListHostDeviceAnswer extends Answer {
    private boolean successMessage;
    private List<String> hostDevicesText;

    public ListHostDeviceAnswer() {
        super();
    }

    public ListHostDeviceAnswer(boolean successMessage, List<String> hostDevicesText) {
        super();
        this.successMessage = successMessage;
        this.hostDevicesText = hostDevicesText;
    }

    public List<String> getHostDevicesTexts() {
        if (hostDevicesText == null) {
            return Collections.emptyList();
        }
        return hostDevicesText;
    }

    public boolean isSuccessMessage() {
        return successMessage;
    }
}
