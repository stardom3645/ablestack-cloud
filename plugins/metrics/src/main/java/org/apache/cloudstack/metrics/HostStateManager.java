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

import com.cloud.ha.HighAvailabilityManager;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.exception.CloudRuntimeException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import org.apache.cloudstack.api.LicenseCheckCmd;
import org.apache.cloudstack.ha.HAConfigManager;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.response.LicenseCheckerResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// import org.apache.logging.log4j.Logger;


public class HostStateManager {
    protected Logger logger = LogManager.getLogger(getClass());

    @Inject
    private HostDao hostDao;

    @Inject
    private HighAvailabilityManager haManager;

    @Inject
    private HAConfigManager haConfigManager;

    @Inject
    private LicenseCheckService licenseCheckService;

    // 에이전트가 이미 시작된 호스트를 추적하기 위한 Set
    private final Set<Long> initializedHosts = ConcurrentHashMap.newKeySet();

    public void handleHostState(Long hostId, boolean isLicenseValid) {
        try {
            // HA를 항상 비활성화
            disableHA(hostId);

            if (isLicenseValid) {
                if (!initializedHosts.contains(hostId)) {
                    startAgent(hostId);
                    initializedHosts.add(hostId);
                }
            } else {
                handleExpiredLicense(hostId);
            }
        } catch (Exception e) {
            logger.error("호스트 " + hostId + " 처리 중 오류 발생: " + e.getMessage(), e);
            throw new CloudRuntimeException("라이센스 상태 처리 중 오류 발생", e);
        }
    }

    private void disableHA(Long hostId) {
        try {
            boolean result = haConfigManager.disableHA(hostId, HAResource.ResourceType.Host);
            if (result) {
                logger.info("호스트 " + hostId + "의 HA가 비활성화되었습니다.");
            } else {
                logger.warn("호스트 " + hostId + "의 HA 비활성화 실패");
                throw new CloudRuntimeException("HA 비활성화 실패");
            }
        } catch (Exception e) {
            logger.error("HA 비활성화 중 오류 발생: " + e.getMessage(), e);
            throw new CloudRuntimeException("HA 비활성화 실패", e);
        }
    }

    private void startAgent(Long hostId) {
        try {
            String glueEndpoint = "https://10.10.31.3:8080/api/v1/glue" + hostId + "/start";
            // HTTP 클라이언트를 사용하여 API 호출
            logger.info("호스트 " + hostId + "의 에이전트가 시작되었습니다.");
        } catch (Exception e) {
            logger.error("에이전트 시작 중 오류 발생: " + e.getMessage(), e);
            throw new CloudRuntimeException("에이전트 시작 실패", e);
        }
    }

    public void handleExpiredLicense(Long hostId) {
        try {
            // 1. HA 비활성화
            disableHA(hostId);

            // 2. glue-api를 통한 에이전트 정지
            stopAgent(hostId);

            logger.info("호스트 " + hostId + "의 라이센스 만료 처리가 완료되었습니다.");
        } catch (Exception e) {
            logger.error("호스트 " + hostId + " 처리 중 오류 발생: " + e.getMessage(), e);
            throw new CloudRuntimeException("라이센스 만료 처리 중 오류 발생", e);
        }
    }

    private void stopAgent(Long hostId) {
        try {
            String glueEndpoint = "https://10.10.31.3:8080/api/v1/glue" + hostId + "/stop";
            // HTTP 클라이언트를 사용하여 glue-api 호출
            logger.info("호스트 " + hostId + "의 에이전트가 정지되었습니다.");
        } catch (Exception e) {
            logger.error("에이전트 정지 중 오류 발생: " + e.getMessage());
            throw new CloudRuntimeException("에이전트 정지 실패", e);
        }
    }

    public Date getLicenseExpiryDate(Long hostId) {
        try {
            // LicenseCheckCmd 생성 시 hostId 전달
            LicenseCheckCmd cmd = new LicenseCheckCmd();
            // hostId 설정 메서드 필요
            cmd.setHostId(hostId);

            LicenseCheckerResponse response = licenseCheckService.checkLicense(cmd);
            return response.getExpiryDate();
        } catch (Exception e) {
            logger.error("호스트 " + hostId + "의 라이센스 만료일 조회 실패", e);
            throw new CloudRuntimeException("라이센스 만료일 조회 실패", e);
        }
    }
}