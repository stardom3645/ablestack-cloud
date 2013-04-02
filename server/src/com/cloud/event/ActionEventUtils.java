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

package com.cloud.event;

import com.cloud.event.dao.EventDao;
import com.cloud.server.ManagementServer;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.framework.events.EventBus;
import org.apache.cloudstack.framework.events.EventBusException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Component
public class ActionEventUtils {
    private static final Logger s_logger = Logger.getLogger(ActionEventUtils.class);

    private static EventDao _eventDao;
    private static AccountDao _accountDao;
    protected static UserDao _userDao;
    protected static EventBus _eventBus = null;

    @Inject EventDao eventDao;
    @Inject AccountDao accountDao;
    @Inject UserDao userDao;

    public ActionEventUtils() {
    }
    
    @PostConstruct
    void init() {
    	_eventDao = eventDao;
    	_accountDao = accountDao;
    	_userDao = userDao;
    }

    public static Long onActionEvent(Long userId, Long accountId, Long domainId, String type, String description) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(),
                type, com.cloud.event.Event.State.Completed);

        Event event = persistActionEvent(userId, accountId, domainId, null, type, Event.State.Completed,
                description, null);

        return event.getId();
    }

    /*
     * Save event after scheduling an async job
     */
    public static Long onScheduledActionEvent(Long userId, Long accountId, String type, String description,
                                              long startEventId) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Scheduled);

        Event event = persistActionEvent(userId, accountId, null, null, type, Event.State.Scheduled,
                description, startEventId);

        return event.getId();
    }

    /*
     * Save event after starting execution of an async job
     */
    public static Long onStartedActionEvent(Long userId, Long accountId, String type, String description,
                                            long startEventId) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Started);

        Event event = persistActionEvent(userId, accountId, null, null, type, Event.State.Started,
                description, startEventId);
        return event.getId();
    }

    public static Long onCompletedActionEvent(Long userId, Long accountId, String level, String type,
                                              String description, long startEventId) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Completed);

        Event event = persistActionEvent(userId, accountId, null, level, type, Event.State.Completed,
                description, startEventId);

        return event.getId();
    }

    public static Long onCreatedActionEvent(Long userId, Long accountId, String level, String type, String description) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Created);

        Event event = persistActionEvent(userId, accountId, null, level, type, Event.State.Created, description, null);

        return event.getId();
    }

    private static Event persistActionEvent(Long userId, Long accountId, Long domainId, String level, String type,
                                           Event.State state, String description, Long startEventId) {
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(type);
        event.setState(state);
        event.setDescription(description);
        if (domainId != null) {
            event.setDomainId(domainId);
        } else {
            event.setDomainId(getDomainId(accountId));
        }
        if (level != null && !level.isEmpty()) {
            event.setLevel(level);
        }
        if (startEventId != null) {
            event.setStartId(startEventId);
        }
        event = _eventDao.persist(event);
        return event;
    }

    private static void publishOnEventBus(long userId, long accountId, String eventCategory,
                                          String eventType, Event.State state) {
        try {
            _eventBus = ComponentContext.getComponent(EventBus.class);
        } catch(NoSuchBeanDefinitionException nbe) {
            return; // no provider is configured to provide events bus, so just return
        }

        org.apache.cloudstack.framework.events.Event event = new org.apache.cloudstack.framework.events.Event(
                ManagementServer.Name,
                eventCategory,
                eventType,
                EventTypes.getEntityForEvent(eventType), null);

        Map<String, String> eventDescription = new HashMap<String, String>();
        Account account = _accountDao.findById(accountId);
        User user = _userDao.findById(userId);
        eventDescription.put("user", user.getUuid());
        eventDescription.put("account", account.getUuid());
        eventDescription.put("event", eventType);
        eventDescription.put("status", state.toString());
        event.setDescription(eventDescription);

        try {
            _eventBus.publish(event);
        } catch (EventBusException e) {
            s_logger.warn("Failed to publish action event on the the event bus.");
        }
    }

    private static long getDomainId(long accountId){
        AccountVO account = _accountDao.findByIdIncludingRemoved(accountId);
        return account.getDomainId();
    }
}
