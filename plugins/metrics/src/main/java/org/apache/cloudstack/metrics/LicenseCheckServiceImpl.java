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


package org.apache.cloudstack.metrics;

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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.spec.KeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import org.apache.cloudstack.api.LicenseCheckCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.response.LicenseCheckerResponse;
import org.json.JSONObject;
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
            "license.check.interval", "1440",
            "라이센스 체크 주기 (분)", true);

    private static final String LICENSE_FILE_PATH = "/root";

    @Override
    public boolean configure(String name, Map<String, Object> params) {
        try {
            super.configure(name, params);
            executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("LicenseCheck"));
            return true;
        } catch (Exception e) {
            logger.error("Configuration error: " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean start() {
        try {
            // 만료일을 체크하고 에이전트를 제어합니다.
            String expirationDate = getExpirationDate("password", "salt");
            boolean expired = isLicenseExpired(expirationDate);
            controlHostAgent(expired);

            // 만료되지 않은 경우 하루마다 만료일을 체크합니다.
            if (!expired) {
                executor.scheduleAtFixedRate(() -> {
                    boolean isExpired = false;
                    try {
                        isExpired = isLicenseExpired(expirationDate);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    controlHostAgent(isExpired);
                }, 1, 1, TimeUnit.DAYS);
            }
            return true;
        } catch (Exception e) {
            logger.error("라이센스 체크 시작 실패", e);
            return false;
        }
    }

    private boolean isLicenseExpired(String expirationDate) throws Exception {
        Date expDate = new SimpleDateFormat("yyyy-MM-dd").parse(expirationDate);
        return new Date().after(expDate);
    }

    private void controlHostAgent(boolean expired) {
        if (expired) {
            stopAgent();
        } else {
            startAgent();
        }
    }

    private void startAgent() {
        logger.info("Starting agent using HostStateManager...");
        try {
            hostStateManager.handleHostState(null, true); // Assuming null for hostId for demonstration
        } catch (Exception e) {
            logger.error("Error starting agent via HostStateManager", e);
        }
    }

    private void stopAgent() {
        logger.info("Stopping agent using HostStateManager...");
        try {
            hostStateManager.handleExpiredLicense(null); // Assuming null for hostId for demonstration
        } catch (Exception e) {
            logger.error("Error stopping agent via HostStateManager", e);
        }
    }

    private void checkLicenseValidity() {
        try {
            String latestLicense = getLatestLicenseFile(LICENSE_FILE_PATH);
            logger.info("라이센스 파일 경로: " + latestLicense);

            Date expiryDate = getLicenseExpiryDate(latestLicense);
            String formattedDate = new SimpleDateFormat("yyyy-MM-dd").format(expiryDate);
            logger.info("복호화된 라이센스 만료일: " + formattedDate);

            boolean isValid = expiryDate.after(new Date());
            logger.info("라이센스 유효 여부: " + (isValid ? "유효함" : "만료됨"));

            if (!isValid) {
                // 만료 알림만 발송
                List<HostVO> hosts = hostDao.listAll();
                for (HostVO host : hosts) {
                    alertManager.sendAlert(
                        AlertManager.AlertType.ALERT_TYPE_HOST,
                        0L,
                        host.getId(),
                        "라이센스가 만료되었습니다.",
                        "만료일: " + formattedDate + "\n라이센스를 갱신해주세요."
                    );
                }
            }
        } catch (Exception e) {
            logger.error("라이센스 체크 중 오류 발생", e);
            logger.error("상세 오류: ", e);
        }
    }

    private Date getLicenseExpiryDate(String licensePath) throws Exception {
        try {
            logger.info("라이센스 파일 읽기 시작: " + licensePath);
            byte[] licenseData = Files.readAllBytes(Paths.get(licensePath));

            // 원본 파일 내용 출력
            String originalContent = new String(licenseData, StandardCharsets.UTF_8);
            logger.info("원본 라이센스 파일 내용: " + originalContent);

            byte[] ciphertext = Base64.getDecoder().decode(licenseData);

            // AES 복호화
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = generateKey("password", "salt", 32);
            byte[] iv = generateKey("password", "salt", 16);

            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            String decryptedText = new String(plaintext, StandardCharsets.UTF_8);

            // 복호화된 JSON 내용 출력
            logger.info("복호화된 JSON 내용: " + decryptedText);

            // JSON 파싱 및 구조 출력
            JSONObject json = new JSONObject(decryptedText);
            logger.info("JSON 구조:");
            logger.info("  - 전체 키 목록: " + json.keySet());
            logger.info("  - expiry_date 값: " + json.getString("expiryDate"));

            String expiryDateStr = json.getString("expiryDate");
            Date parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(expiryDateStr);
            logger.info("파싱된 만료일: " + new SimpleDateFormat("yyyy-MM-dd").format(parsedDate));

            return parsedDate;
        } catch (Exception e) {
            logger.error("라이센스 복호화 중 오류 발생", e);
            logger.error("상세 오류: ", e);
            throw e;
        }
    }

    private static byte[] generateKey(String password, String salt, int length) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 16384, length * 8);
        return factory.generateSecret(spec).getEncoded();
    }

    private static String getLatestLicenseFile(String dirPath) throws Exception {
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".lic"));
        if (files == null || files.length == 0) {
            throw new Exception("라이센스 파일을 찾을 수 없습니다");
        }

        File latestFile = files[0];
        for (File file : files) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }
        return latestFile.getAbsolutePath();
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

    private String getExpirationDate(String password, String salt) {
        try {
            logger.info("라이센스 파일 읽기 시작: " + LICENSE_FILE_PATH);
            String latestLicense = getLatestLicenseFile(LICENSE_FILE_PATH);
            logger.info("라이센스 파일 경로: " + latestLicense);

            Date expiryDate = getLicenseExpiryDate(latestLicense);
            String formattedDate = new SimpleDateFormat("yyyy-MM-dd").format(expiryDate);
            logger.info("복호화된 라이센스 만료일: " + formattedDate);

            return formattedDate;
        } catch (Exception e) {
            logger.error("라이센스 복호화 중 오류 발생", e);
            return null;
        }
    }

    @Override
    public LicenseCheckerResponse checkLicense(LicenseCheckCmd cmd) {
        try {
            Long hostId = cmd.getHostId();
            if (hostId == null) {
                throw new InvalidParameterValueException("호스트 ID가 필요합니다.");
            }

            HostVO host = hostDao.findById(hostId);
            if (host == null) {
                throw new InvalidParameterValueException("해당 호스트를 찾을 수 없습니다: " + hostId);
            }

            String latestLicense = getLatestLicenseFile(LICENSE_FILE_PATH);
            Date expiryDate = getLicenseExpiryDate(latestLicense);

            LicenseCheckerResponse response = new LicenseCheckerResponse();
            response.setObjectName("licensecheck");
            response.setHostId(host.getId());
            response.setExpiryDate(expiryDate);
            response.setSuccess(true);

            return response;
        } catch (Exception e) {
            logger.error("라이센스 체크 중 오류 발생", e);
            LicenseCheckerResponse response = new LicenseCheckerResponse();
            response.setSuccess(false);
            response.setMessage("라이센스 체크 중 오류 발생: " + e.getMessage());
            return response;
        }
    }
    public ListResponse<LicenseCheckerResponse> listLicenseChecks(final LicenseCheckCmd cmd) {
        final Long hostId = cmd.getHostId();
        Filter searchFilter = new Filter(HostVO.class, "id", true);
        SearchBuilder<HostVO> sb = hostDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("uuid", sb.entity().getUuid(), SearchCriteria.Op.LIKE);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);

        SearchCriteria<HostVO> sc = sb.create();
        // String keyword = cmd.getKeyword();
        if (hostId != null) {
            sc.setParameters("id", hostId);
        }

        // if (keyword != null) {
        //     sc.addOr("uuid", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        //     sc.addOr("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        //     sc.addOr("private_ip_address", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        // }

        List<HostVO> hosts = hostDao.search(sc, searchFilter);
        return createLicenseCheckListResponse(hosts);
    }

    private ListResponse<LicenseCheckerResponse> createLicenseCheckListResponse(List<HostVO> hosts) {
        List<LicenseCheckerResponse> responseList = new ArrayList<>();

        for (HostVO host : hosts) {
            try {
                String latestLicense = getLatestLicenseFile(LICENSE_FILE_PATH);
                Date expiryDate = getLicenseExpiryDate(latestLicense);

                LicenseCheckerResponse response = new LicenseCheckerResponse();
                response.setObjectName("licensecheck");
                response.setHostId(host.getId());
                response.setExpiryDate(expiryDate);
                response.setSuccess(true);
                responseList.add(response);
            } catch (Exception e) {
                logger.error("라이센스 체크 중 오류 발생 - 호스트 ID: " + host.getId(), e);
                LicenseCheckerResponse response = new LicenseCheckerResponse();
                response.setHostId(host.getId());
                response.setSuccess(false);
                response.setMessage("라이센스 체크 중 오류 발생: " + e.getMessage());
                responseList.add(response);
            }
        }

        ListResponse<LicenseCheckerResponse> response = new ListResponse<>();
        response.setResponses(responseList);
        return response;
    }
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(LicenseCheckCmd.class);
        return cmdList;
    }
}