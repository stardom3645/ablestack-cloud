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

package org.apache.cloudstack.storage.command.browser;

import com.cloud.agent.api.storage.StorageCommand;

public class DeleteRbdObjectsCommand extends StorageCommand {

   private String name;

   private String poolType;

   private String poolPath;

    public DeleteRbdObjectsCommand(String name) {
        super();
        this.name = name;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getNames() {
        return name;
    }

    public String getPoolPath() {
        return poolPath;
    }

    public String getPoolType() {
        return poolType;
    }

    public void setPoolType(String poolType) {
        this.poolType = poolType;
    }

    public void setPoolPath(String poolPath) {
        this.poolPath = poolPath;
    }
}
