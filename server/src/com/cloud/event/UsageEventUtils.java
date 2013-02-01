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

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.event.dao.UsageEventDao;
import com.cloud.server.ManagementServer;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import org.apache.cloudstack.framework.events.EventBus;
import org.apache.cloudstack.framework.events.Event;
import org.apache.cloudstack.framework.events.EventBusException;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Component
public class UsageEventUtils {

    private static UsageEventDao _usageEventDao;
    private static AccountDao _accountDao;
    private static DataCenterDao _dcDao;
    private static final Logger s_logger = Logger.getLogger(UsageEventUtils.class);

    // get the event bus provider if configured
    protected static EventBus _eventBus;

    @Inject UsageEventDao usageEventDao;
    @Inject AccountDao accountDao;
    @Inject DataCenterDao dcDao;
    
    public UsageEventUtils() {
    }
    
    @PostConstruct
    void init() {
    	_usageEventDao = usageEventDao;
    	_accountDao = accountDao;
    	_dcDao = dcDao;
    }
    
    public static void publishUsageEvent(String usageType, long accountId, long zoneId,
                                         long resourceId, String resourceName,
                                         Long offeringId, Long templateId, Long size,
                                         String entityType, String entityUUID) {
        saveUsageEvent(usageType, accountId, zoneId, resourceId, resourceName, offeringId, templateId, size);
        publishUsageEvent(usageType, accountId, zoneId, entityType, entityUUID);
    }

    public static void publishUsageEvent(String usageType, long accountId, long zoneId, long resourceId,
                                         String resourceName, String entityType, String entityUUID) {
        saveUsageEvent(usageType, accountId, zoneId, resourceId, resourceName);
        publishUsageEvent(usageType, accountId, zoneId, entityType, entityUUID);
    }

    public static void publishUsageEvent(String usageType, long accountId, long zoneId,
                                         long ipAddressId, String ipAddress, boolean isSourceNat,
                                         String guestType, boolean isSystem, String entityType, String entityUUID) {
        saveUsageEvent(usageType, accountId, zoneId, ipAddressId, ipAddress, isSourceNat, guestType, isSystem);
        publishUsageEvent(usageType, accountId, zoneId, entityType, entityUUID);
    }

    public static void publishUsageEvent(String usageType, long accountId, long zoneId, long resourceId,
                                         String resourceName, Long offeringId, Long templateId, String resourceType,
                                         String entityType, String entityUUID) {
        saveUsageEvent(usageType, accountId, zoneId, resourceId, resourceName, offeringId, templateId, resourceType);
        publishUsageEvent(usageType, accountId, zoneId, entityType, entityUUID);
    }

    public static void publishUsageEvent(String usageType, long accountId,long zoneId, long vmId,
                                         long securityGroupId, String entityType, String entityUUID) {
        saveUsageEvent(usageType, accountId, zoneId, vmId, securityGroupId);
        publishUsageEvent(usageType, accountId, zoneId, entityType, entityUUID);
    }

    public static void saveUsageEvent(String usageType, long accountId, long zoneId, long resourceId, String resourceName, Long offeringId, Long templateId, Long size) {
        _usageEventDao.persist( new UsageEventVO(usageType, accountId, zoneId, resourceId, resourceName, offeringId, templateId, size));
    }

    public static void saveUsageEvent(String usageType, long accountId, long zoneId, long resourceId, String resourceName) {
        _usageEventDao.persist( new UsageEventVO(usageType, accountId, zoneId, resourceId, resourceName));
    }

    public static void saveUsageEvent(String usageType, long accountId, long zoneId, long ipAddressId, String ipAddress, boolean isSourceNat, String guestType, boolean isSystem) {
        _usageEventDao.persist( new UsageEventVO(usageType, accountId, zoneId, ipAddressId, ipAddress, isSourceNat, guestType, isSystem));
    }

    public static void saveUsageEvent(String usageType, long accountId, long zoneId, long resourceId, String resourceName, Long offeringId, Long templateId, String resourceType) {
        _usageEventDao.persist( new UsageEventVO(usageType, accountId, zoneId, resourceId, resourceName, offeringId, templateId, resourceType));
    }

    public static void saveUsageEvent(String usageType, long accountId,long zoneId, long vmId, long securityGroupId) {
        _usageEventDao.persist( new UsageEventVO( usageType, accountId, zoneId, vmId, securityGroupId));
    }

    private static void publishUsageEvent(String usageEventType, Long accountId, Long zoneId, String resourceType, String resourceUUID) {

        if (_eventBus == null) {
            return; // no provider is configured to provider events bus, so just return
        }

        Account account = _accountDao.findById(accountId);
        DataCenterVO dc = _dcDao.findById(zoneId);

        Event event = new Event(ManagementServer.Name, EventCategory.USAGE_EVENT.getName(), usageEventType,
                resourceType, resourceUUID);

        Map<String, String> eventDescription = new HashMap<String, String>();
        eventDescription.put("account", account.getUuid());
        eventDescription.put("zone", dc.getUuid());
        eventDescription.put("event", usageEventType);
        eventDescription.put("resource", resourceType);
        eventDescription.put("id", resourceUUID);
        event.setDescription(eventDescription);

        try {
            _eventBus.publish(event);
        } catch (EventBusException e) {
            s_logger.warn("Failed to publish usage event on the the event bus.");
        }
    }
}
