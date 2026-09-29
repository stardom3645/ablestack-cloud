<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->

# 프로세스 관리 ABLESTACK Tools ISO 연결

Cloud #1173 / qemu-exec-tools #65. 이 기능은 `vm.process.management.enabled=true`인 경우에만 이용한다.

관리자는 Global 설정 `vm.process.tools.iso.catalog`에 다음 JSON 배열을 등록한다. 각 항목은 한 영역과 게스트 OS·버전·아키텍처에 대해 정확히 한 ISO를 지정한다. `zoneId`와 `isoId`는 Cloud UUID이며, `sha256`은 GitHub Actions의 ISO manifest와 등록된 ISO 메타데이터의 SHA-256이 일치해야 한다. Cloud ISO 등록 API에는 `checksum={SHA-256}<해시>` 형식으로 전달한다.

```json
[
  {
    "zoneId": "<Cloud zone UUID>",
    "osId": "rocky",
    "osVersion": "9.8",
    "arch": "x86_64",
    "isoId": "<registered ISO UUID>",
    "version": "0.10.0",
    "sha256": "<64 lowercase hexadecimal characters>"
  }
]
```

지원 대상 조합은 Rocky 9.6/9.7/9.8/10.2, Ubuntu 22.04/24.04/26.04, Windows Server 2022/2025의 x86_64이다. Windows의 QGA OS ID는 `mswindows`로 등록한다. 동일 튜플의 중복 항목이나 잘못된 UUID/해시는 전체 카탈로그를 무효화한다. 기본값 `[]`은 ISO 추천을 비활성화하며 프로세스 관측 자체는 변경하지 않는다.

프로세스 탭은 Cloud의 기존 `attachIso` 비동기 작업과 CD 슬롯 검사를 사용한다. 이미 연결된 ISO를 임의로 분리하지 않는다. 연결 전에 VM 상태·영역·접근 가능 여부·Ready·아키텍처·SHA-256을 다시 확인한다. 메타데이터 해시가 없으면 연결을 거부하고 관리자에게 ISO 등록값을 점검하도록 안내한다.

ISO 연결 성공은 `게스트 설치 대기`이다. 게스트 콘솔에서 관리자 권한으로 설치하거나 복구한다. QGA 실행 RPC가 막힌 상태에서는 QGA로 QGA 자신을 고치려 하지 않는다. Linux는 ISO를 읽기 전용으로 마운트한 뒤 `bash /mnt/ablestack-tools/install-linux.sh --mode process-management`, Windows는 관리자 PowerShell에서 ISO 루트의 `install.bat`를 실행한다. 설치 결과가 재부팅을 요구하면 재부팅 후 다시 검사한다.

`READY`는 재검사에서 필수 QGA RPC 8개가 모두 ENABLED이고, Cloud의 프로세스 읽기 작업이 실제 snapshot을 반환했을 때만 표시한다. 연결·설치 성공 메시지·DB 설정만으로 READY 처리하지 않는다. 확인 실패 시 기존 미디어를 유지하고, 사용자가 설치 결과를 점검한 후 재검사한다.
