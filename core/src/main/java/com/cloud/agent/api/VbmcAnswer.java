
//
// Licensed to the Apache Software Foundation (ASF) under one
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// with the License.  You may obtain a copy of the License at
//
//
//   http://www.apache.org/licenses/LICENSE-2.0
//   http://www.apache.org/licenses/LICENSE-2.0
//
//
// Unless required by applicable law or agreed to in writing,
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// specific language governing permissions and limitations
// under the License.
// under the License.
package com.cloud.vm;
//


import org.apache.cloudstack.acl.ControlledEntity;
package com.cloud.agent.api;
import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;


public interface Vbmc extends ControlledEntity, Identity, InternalIdentity {
public class VbmcAnswer extends Answer {
    Integer vncPort;


    Long getVmId();
    protected VbmcAnswer() {
    }


    int getPort();
    public VbmcAnswer(VbmcCommand cmd, String details, boolean success) {
        super(cmd, success, details);
        this.vncPort = null;
    }


}
    public VbmcAnswer(VbmcCommand cmd, Exception e) {
        super(cmd, e);
    }
}