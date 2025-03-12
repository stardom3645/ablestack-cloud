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


package com.cloud.server;

import com.cloud.alert.AlertManager;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.naming.ConfigurationException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.cloudstack.api.command.admin.outofbandmanagement.LicenseCheckCmd;
import org.apache.cloudstack.api.response.LicenseCheckerResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.springframework.stereotype.Component;


@Component
public class LicenseCheckServiceImpl extends ManagerBase implements LicenseCheckService, Manager, Configurable {

    @Inject
    private AlertManager alertManager;

    @Inject
    private HostDao hostDao;

    @Inject
    private HostStateManager hostStateManager;

    private ScheduledExecutorService executor;

    // License configuration keys
    private static final ConfigKey<Integer> LicenseCheckInterval = new ConfigKey<>("Advanced", Integer.class,
            "license.check.interval", "1",
            "라이센스 체크 주기 (일)", true);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("LicenseCheckInterval"));
        return true;
    }

    @Override
    public boolean start() {
        if(LicenseCheckInterval.value() >= 0) {
            executor.scheduleAtFixedRate(new LicenseCheckTask(), 0, LicenseCheckInterval.value(), TimeUnit.DAYS);
        }
        return true;
    }

    protected class LicenseCheckTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                logger.info("일일 라이센스 체크 시작");
                List<HostVO> hosts = hostDao.listAll();
                checkLicensesForHosts(hosts);
            } catch (Exception e) {
                logger.error("라이센스 체크 중 오류 발생", e);
            }
        }
    }

    // @Override
    public ListResponse<LicenseCheckerResponse> listLicenseChecks(final LicenseCheckCmd cmd) {
        final Long hostId = cmd.getHostId();
        List<HostVO> hosts = getHostsToCheck(hostId);
        return checkLicensesForHosts(hosts);
    }

    @Override
    public LicenseCheckerResponse checkLicense(LicenseCheckCmd cmd) {
        logger.info("라이센스 체크 시작");
        Long hostId = cmd.getHostId();
        if (hostId == null) {
            throw new InvalidParameterValueException("호스트 ID가 필요합니다.");
        }

        HostVO host = hostDao.findById(hostId);
        if (host == null) {
            throw new InvalidParameterValueException("해당 호스트를 찾을 수 없습니다: " + hostId);
        }

        return checkLicensesForHosts(List.of(host)).getResponses().get(0);
    }

    private List<HostVO> getHostsToCheck(Long hostId) {
        if (hostId != null) {
            SearchBuilder<HostVO> sb = hostDao.createSearchBuilder();
            sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
            SearchCriteria<HostVO> sc = sb.create();
            sc.setParameters("id", hostId);
            return hostDao.search(sc, new Filter(HostVO.class, "id", true));
        }
        return hostDao.listAll();
    }

    private ListResponse<LicenseCheckerResponse> checkLicensesForHosts(List<HostVO> hosts) {
        List<LicenseCheckerResponse> responseList = new ArrayList<>();

        for (HostVO host : hosts) {
            try {
                boolean isExpired = isLicenseExpired(host.getId(), host.getPrivateIpAddress());
                LicenseCheckerResponse response = createLicenseResponse(host, !isExpired);
                if (isExpired) {
                    handleExpiredLicense(host);
                } else {
                    hostStateManager.handleHostState(host.getId(), true, host.getPrivateIpAddress());
                }
                responseList.add(response);
            } catch (Exception e) {
                logger.error("호스트 라이센스 체크 중 오류 발생 - 호스트 ID: " + host.getId(), e);
                responseList.add(createErrorResponse(host, e.getMessage()));
            }
        }

        ListResponse<LicenseCheckerResponse> response = new ListResponse<>();
        response.setResponses(responseList);
        return response;
    }

    private boolean isLicenseExpired(Long hostId, String ipAddress) throws Exception {
        HttpURLConnection conn = null;
        try {
            String glueEndpoint = "https://" + ipAddress + "/api/v1/glue" + hostId;
            conn = createSecureConnection(glueEndpoint);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.error("Glue-API 호출 실패 (응답 코드: " + responseCode + ")");
                throw new Exception("라이센스 상태 확인 실패");
            }

            String response = readResponse(conn);
            return parseLicenseResponse(response);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean parseLicenseResponse(String response) throws Exception {
        response = response.trim();

        int startIndex = response.indexOf("\"is_expired\"");
        if (startIndex == -1) {
            throw new Exception("라이센스 만료 정보를 찾을 수 없습니다");
        }

        startIndex = response.indexOf(":", startIndex);
        if (startIndex == -1) {
            throw new Exception("유효하지 않은 응답 형식");
        }

        String expiredStr = response.substring(startIndex + 1).trim();
        if (expiredStr.startsWith("true")) {
            return true;
        } else if (expiredStr.startsWith("false")) {
            return false;
        } else {
            throw new Exception("유효하지 않은 응답 값: " + expiredStr);
        }
    }

    private LicenseCheckerResponse createLicenseResponse(HostVO host, boolean isValid) {
        LicenseCheckerResponse response = new LicenseCheckerResponse();
        response.setObjectName("licensecheck");
        response.setHostId(host.getId());
        response.setSuccess(isValid);
        return response;
    }

    private LicenseCheckerResponse createErrorResponse(HostVO host, String errorMessage) {
        LicenseCheckerResponse response = new LicenseCheckerResponse();
        response.setObjectName("licensecheck");
        response.setHostId(host.getId());
        response.setSuccess(false);
        response.setMessage("라이센스 체크 중 오류 발생: " + errorMessage);
        return response;
    }

    private void handleExpiredLicense(HostVO host) {
        logger.warn("라이센스가 만료되었습니다. 호스트 ID: " + host.getId());
        hostStateManager.handleExpiredLicense(host.getId(), host.getPrivateIpAddress());
        alertManager.sendAlert(
            AlertManager.AlertType.ALERT_TYPE_HOST,
            0L,
            host.getId(),
            "라이센스가 만료되었습니다.",
            "라이센스를 갱신해주세요."
        );
    }

    private HttpURLConnection createSecureConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // SSL 컨텍스트 설정
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, getTrustManagers(), new SecureRandom());
        conn.setSSLSocketFactory(sslContext.getSocketFactory());

        // 호스트네임 검증 설정
        // conn.setHostnameVerifier(new CustomHostnameVerifier());

        return conn;
    }

    private TrustManager[] getTrustManagers() {
        return new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // 클라이언트 인증서 검증 로직
                    logger.debug("클라이언트 인증서 검증: " + authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // 서버 인증서 검증 로직
                    logger.debug("서버 인증서 검증: " + authType);
                }
            }
        };
    }

    // private static class CustomHostnameVerifier implements HostnameVerifier {
    //     @Override
    //     public boolean verify(String hostname, SSLSession session) {
    //         // 호스트네임 검증 로직
    //         // logger.debug("호스트네임 검증: " + hostname);
    //         return true;
    //     }
    // }

    private String readResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    @Override
    public boolean stop() {
        if (executor != null) {
            executor.shutdown();
        }
        return true;
    }

    @Override
    public String getName() {
        return "LicenseCheckService";
    }

    @Override
    public String getConfigComponentName() {
        return LicenseCheckServiceImpl.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{ LicenseCheckInterval };
    }

    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(LicenseCheckCmd.class);
        return cmdList;
    }
}