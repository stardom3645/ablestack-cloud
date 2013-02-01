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

package com.cloud.network;

import com.cloud.event.EventCategory;
import com.cloud.event.dao.UsageEventDao;
import com.cloud.network.Network.Event;
import com.cloud.network.Network.State;
import com.cloud.network.dao.NetworkDao;
import com.cloud.server.ManagementServer;
import com.cloud.utils.fsm.StateListener;
import org.apache.cloudstack.framework.events.EventBus;
import org.apache.cloudstack.framework.events.EventBusException;
import org.apache.log4j.Logger;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

public class NetworkStateListener implements StateListener<State, Event, Network> {

    @Inject protected UsageEventDao _usageEventDao;
    @Inject protected NetworkDao _networkDao;

    // get the event bus provider if configured
    @Inject protected EventBus _eventBus;

    private static final Logger s_logger = Logger.getLogger(NetworkStateListener.class);

    public NetworkStateListener(UsageEventDao usageEventDao, NetworkDao networkDao) {
        this._usageEventDao = usageEventDao;
        this._networkDao = networkDao;
    }

    @Override
    public boolean preStateTransitionEvent(State oldState, Event event, State newState, Network vo, boolean status, Object opaque) {
        pubishOnEventBus(event.name(), "preStateTransitionEvent", vo, oldState, newState);
        return true;
    }

    @Override
    public boolean postStateTransitionEvent(State oldState, Event event, State newState, Network vo, boolean status, Object opaque) {
        pubishOnEventBus(event.name(), "postStateTransitionEvent", vo, oldState, newState);
        return true;
    }

    private void pubishOnEventBus(String event, String status, Network vo, State oldState, State newState) {

        if (_eventBus == null) {
            return; // no provider is configured to provide events bus, so just return
        }

        String resourceName = getEntityFromClassName(Network.class.getName());
        org.apache.cloudstack.framework.events.Event eventMsg =  new org.apache.cloudstack.framework.events.Event(
                ManagementServer.Name,
                EventCategory.RESOURCE_STATE_CHANGE_EVENT.getName(),
                event,
                resourceName,
                vo.getUuid());
        Map<String, String> eventDescription = new HashMap<String, String>();
        eventDescription.put("resource", resourceName);
        eventDescription.put("id", vo.getUuid());
        eventDescription.put("old-state", oldState.name());
        eventDescription.put("new-state", newState.name());
        eventMsg.setDescription(eventDescription);
        try {
            _eventBus.publish(eventMsg);
        } catch (EventBusException e) {
            s_logger.warn("Failed to publish state change event on the the event bus.");
        }
    }

    private String getEntityFromClassName(String entityClassName) {
        int index = entityClassName.lastIndexOf(".");
        String entityName = entityClassName;
        if (index != -1) {
            entityName = entityClassName.substring(index+1);
        }
        return entityName;
    }
}
