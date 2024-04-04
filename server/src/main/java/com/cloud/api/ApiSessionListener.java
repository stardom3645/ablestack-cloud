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
package com.cloud.api;

import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.cloudstack.managed.context.ManagedContextTimerTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.text.SimpleDateFormat;

import com.cloud.user.Account;
import com.cloud.event.EventTypes;
import com.cloud.event.ActionEventUtils;

@WebListener
public class ApiSessionListener implements HttpSessionListener {
    protected static Logger LOGGER = LogManager.getLogger(ApiSessionListener.class.getName());
    private static Map<String, HttpSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService _sessionExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("SessionChecker"));

    /**
     * @return the internal adminstered session count
     */
    public static long getSessionCount() {
        return sessions.size();
    }

    /**
     * @return the size of the internal {@see Map} of sessions
     */
    public static long getNumberOfSessions() {
        return sessions.size();
    }

    /**
     * 접속하려는 세션 제외한 기존의 모든 세션 차단
     */
    public static void deleteAllExistSessionIds(String newSessionId) {
        for (String key : sessions.keySet()) {
            HttpSession ses = sessions.get(key);
            if (ses != null && ses.getAttribute("username") != null && !newSessionId.equals(key.toString())) {
                sessions.get(key.toString()).invalidate();
                sessions.remove(key.toString());
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sessions count: " + getSessionCount());
        }
    }

    /**
     * 같은 username으로 먼저 접속된 세션 ID 목록 조회
     */
    public static List<String> listExistSessionIds(String username, String newSessionId) {
        List<String> doubleLoginSessionIds = new ArrayList<String>();
        for (String key : sessions.keySet()) {
            HttpSession ses = sessions.get(key);
            if (ses != null && ses.getAttribute("username") != null && ses.getAttribute("username").toString().equals(username) && !newSessionId.equals(key.toString())) {
                doubleLoginSessionIds.add(key.toString());
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sessions count: " + getSessionCount());
        }
        return doubleLoginSessionIds;
    }

    /**
     * 선택된 세션 차단
     */
    public static void deleteSessionIds(List<String> arr) {
        if (arr.size() > 0) {
            for (String id : arr) {
                sessions.get(id).invalidate();
                sessions.remove(id);
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sessions count: " + getSessionCount());
        }
    }

    public void sessionCreated(HttpSessionEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Session created by Id : " + event.getSession().getId() + " , session: " + event.getSession().toString() + " , source: " + event.getSource().toString() + " , event: " + event.toString());
        }
        synchronized (this) {
            HttpSession session = event.getSession();
            sessions.put(session.getId(), event.getSession());
            _sessionExecutor.schedule(new SessionCheckTask(event), "10", TimeUnit.SECONDS);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sessions count: " + getSessionCount());
        }
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        if (ApiServer.SecurityFeaturesEnabled.value()) {
            String accountName = "admin";
            Long domainId = 1L;
            Account userAcct = ApiDBUtils.findAccountByNameDomain(accountName, domainId);
            SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
            Date time = new Date(event.getSession().getLastAccessedTime());
            ActionEventUtils.onActionEvent(userAcct.getId(), userAcct.getAccountId(), domainId, EventTypes.EVENT_USER_SESSION_DESTROY,
                "Session destroyed by Id : " + event.getSession().getId() + ", last accessed time : " + date.format(time), new Long(0), null);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Session destroyed by Id : " + event.getSession().getId() + " , session: " + event.getSession().toString() + " , source: " + event.getSource().toString() + " , event: " + event.toString());
        }
        synchronized (this) {
            sessions.remove(event.getSession().getId());
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sessions count: " + getSessionCount());
        }
    }

    protected class SessionCheckTask extends ManagedContextTimerTask {
        HttpSessionEvent _event;

        public SessionCheckTask(final HttpSessionEvent event) {
            _event = event;
        }

        @Override
        protected synchronized void runInContext() {
            try {
                Date acsTime = new Date(event.getSession().getLastAccessedTime());
                Date curTime = new Date();
                LOGGER.info("acsTime : " + acsTime);
                LOGGER.info("curTime : " + curTime);
                long difTime = (curTime.getTime() - acsTime.getTime())/1000;
                LOGGER.info("difTime : " + difTime);
                if (difTime > 600) {
                    LOGGER.info("sessionDestroyed :::::::::::::::::::::::::::::::::::::::::::::::: ");
                    sessionDestroyed(event);
                }
            } catch (Exception e) {
                logger.error("Failed to session timeout check session Id : " + event.getSession().getId());
            }
        }
    }
}
