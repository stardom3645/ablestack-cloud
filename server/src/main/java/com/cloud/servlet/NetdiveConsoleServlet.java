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
package com.cloud.servlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cloudstack.api.command.user.consoleproxy.ConsoleEndpoint;
import org.apache.cloudstack.consoleproxy.ConsoleAccessManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Component("netdiveConsoleServlet")
public class NetdiveConsoleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LogManager.getLogger(NetdiveConsoleServlet.class);

    @Inject
    private ConsoleAccessManager consoleAccessManager;

    private final Gson gson = new GsonBuilder().create();

    @Override
    public void init(ServletConfig config) throws ServletException {
        SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this, config.getServletContext());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String mock = req.getParameter("mock");
        if ("true".equalsIgnoreCase(mock)) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json;charset=UTF-8");

            String scheme = req.isSecure() ? "https" : "http";
            String host = req.getServerName();
            int port = req.getServerPort();

            String base = scheme + "://" + host;
            if (!(("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443))) {
                base += ":" + port;
            }

            Map<String, String> mockResult = new HashMap<>();
            mockResult.put("url", base + "/resource/noVNC/vnc.html?autoconnect=true&show_dot=true&port=8080&token=mock");
            resp.getWriter().print(gson.toJson(mockResult));
            return;
        }

        String vmId = req.getParameter("vmId");
        if (vmId == null || vmId.trim().isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "vmId is required");
            return;
        }

        ConsoleEndpoint endpoint = consoleAccessManager.generateConsoleEndpointForNetdive(vmId, req.getRemoteAddr());
        if (endpoint == null || !endpoint.isResult() || endpoint.getUrl() == null || endpoint.getUrl().trim().isEmpty()) {
            String details = endpoint != null ? endpoint.getDetails() : "failed to create console endpoint";
            LOGGER.warn("Failed to create Netdive console endpoint for vmId={}, details={}", vmId, details);
            sendError(resp, HttpServletResponse.SC_BAD_GATEWAY, details);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json;charset=UTF-8");
        Map<String, String> result = new HashMap<>();
        result.put("url", endpoint.getUrl());
        resp.getWriter().print(gson.toJson(result));
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        resp.getWriter().print(gson.toJson(body));
    }
}
