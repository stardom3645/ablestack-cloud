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
package org.apache.cloudstack.storage.image.manager;

import org.apache.cloudstack.storage.image.TemplateEvent;
import org.apache.cloudstack.storage.image.TemplateState;
import org.apache.cloudstack.storage.image.db.ImageDataVO;
import org.springframework.stereotype.Component;

import com.cloud.utils.fsm.StateMachine2;

@Component
public class ImageDataManagerImpl implements ImageDataManager {
    private final StateMachine2<TemplateState, TemplateEvent, ImageDataVO> 
        stateMachine = new StateMachine2<TemplateState, TemplateEvent, ImageDataVO>();
    
    public ImageDataManagerImpl() {
        stateMachine.addTransition(TemplateState.Allocated, TemplateEvent.CreateRequested, TemplateState.Creating);
        stateMachine.addTransition(TemplateState.Creating, TemplateEvent.CreateRequested, TemplateState.Creating);
        stateMachine.addTransition(TemplateState.Creating, TemplateEvent.OperationSucceeded, TemplateState.Ready);
        stateMachine.addTransition(TemplateState.Creating, TemplateEvent.OperationFailed, TemplateState.Allocated);
        stateMachine.addTransition(TemplateState.Creating, TemplateEvent.DestroyRequested, TemplateState.Destroying);
        stateMachine.addTransition(TemplateState.Ready, TemplateEvent.DestroyRequested, TemplateState.Destroying);
        stateMachine.addTransition(TemplateState.Allocated, TemplateEvent.DestroyRequested, TemplateState.Destroying);
        stateMachine.addTransition(TemplateState.Destroying, TemplateEvent.DestroyRequested, TemplateState.Destroying);
        stateMachine.addTransition(TemplateState.Destroying, TemplateEvent.OperationFailed, TemplateState.Destroying);
        stateMachine.addTransition(TemplateState.Destroying, TemplateEvent.OperationSucceeded, TemplateState.Destroyed);
    }
    
    @Override
    public StateMachine2<TemplateState, TemplateEvent, ImageDataVO> getStateMachine() {
        return stateMachine;
    }
}
