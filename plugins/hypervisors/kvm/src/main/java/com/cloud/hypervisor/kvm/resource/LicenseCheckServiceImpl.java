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


package com.cloud.hypervisor.kvm.resource;

import com.cloud.alert.AlertManager;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.spec.KeySpec;
import java.text.SimpleDateFormat;
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
import javax.naming.ConfigurationException;

import com.cloud.ha.LicenseCheckCmd;
import com.cloud.response.LicenseCheckerResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.json.JSONObject;


public class LicenseCheckServiceImpl extends ManagerBase implements LicenseCheckService, Manager, Configurable {

    @Inject
    private AlertManager alertManager;

    @Inject
    private HostDao hostDao;

    @Inject
    private HostStateManager hostStateManager;

    private ScheduledExecutorService executor;

    // License configuration keys
    public static final ConfigKey<Integer> LicenseCheckInterval = new ConfigKey<>("Advanced", Integer.class,
            "license.check.interval", "1440",
            "라이센스 체크 간격 (분)", true);

    private static final String LICENSE_FILE_PATH = "/root"; // 라이센스 파일 경로를 하드코딩

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("LicenseCheck"));
        return true;
    }

    @Override
    public boolean start() {
        try {
            checkLicenseValidity();
            // 24시간마다 라이센스 체크 실행
            executor.scheduleAtFixedRate(this::checkLicenseValidity,
                LicenseCheckInterval.value(),
                LicenseCheckInterval.value(),
                TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            logger.error("라이센스 체크 시작 실패", e);
            return false;
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
        return "LicenseCheck";
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { LicenseCheckInterval };
    }

    @Override
    public LicenseCheckerResponse checkLicense(LicenseCheckCmd cmd) {
        try {
            Date expiryDate = hostStateManager.getLicenseExpiryDate(cmd.getHostId());

            LicenseCheckerResponse response = new LicenseCheckerResponse();
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
}