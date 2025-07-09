//
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
//

package com.cloud.agent.api;

<<<<<<<< HEAD:core/src/main/java/com/cloud/agent/api/CleanupVMCommand.java
/**
 * This command will destroy a leftover VM during the expunge process if it wasn't destroyed before.
 *
 */
public class CleanupVMCommand extends Command {
    String vmName;
    boolean executeInSequence;

    public CleanupVMCommand(String vmName) {
        this(vmName, false);
    }
    public CleanupVMCommand(String vmName, boolean executeInSequence) {
        this.vmName = vmName;
        this.executeInSequence = executeInSequence;
========
public class UpdateHaStateCommand extends Command {

    String hostHAState;

    public UpdateHaStateCommand(String hostHAState) {
        this.hostHAState = hostHAState;
    }

    public String getHostHAState() {
        return hostHAState;
>>>>>>>> origin/mold-main#2025:core/src/main/java/com/cloud/agent/api/UpdateHaStateCommand.java
    }

    @Override
    public boolean executeInSequence() {
<<<<<<<< HEAD:core/src/main/java/com/cloud/agent/api/CleanupVMCommand.java
        return executeInSequence;
    }

    public String getVmName() {
        return vmName;
========
        return true;
>>>>>>>> origin/mold-main#2025:core/src/main/java/com/cloud/agent/api/UpdateHaStateCommand.java
    }
}
