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

Cloud #1173/#1194 / qemu-exec-tools #65/#79. 이 기능은 `vm.process.management.enabled=true`인 경우에만 이용한다.

관리자는 Global 설정 `vm.process.tools.iso.catalog`에 다음 JSON 배열을 등록한다. 각 항목은 한 영역과 **QGA에서 관측한 OS ID·제품 종류·버전 집합·아키텍처**에 한 ISO를 지정한다. Linux의 `productType`은 `none`, Windows는 `server` 또는 `client`이다. Rocky/RHEL·Debian의 `versions`에는 메이저 버전만 쓰고 Ubuntu/Windows에는 명시 버전을 쓴다. `zoneId`와 `isoId`는 Cloud UUID이다. `sha256`은 GitHub Actions ISO manifest의 값이며, 등록 ISO API가 SHA-512 메타데이터를 반환하는 환경에서는 같은 파일에서 계산한 `sha512`도 등록한다. 연결 직전 Cloud 메타데이터와 해당 길이의 digest를 비교한다.

```json
[
  {
    "zoneId": "<Cloud zone UUID>",
    "isoFamily": "rocky",
    "osId": "rocky",
    "productType": "none",
    "versions": ["8", "9", "10"],
    "arch": "x86_64",
    "isoId": "<registered ISO UUID>",
    "version": "process-9-89806b1",
    "sha256": "<64 lowercase hexadecimal characters>",
    "sha512": "<128 lowercase hexadecimal characters, when Cloud returns SHA-512>"
  }
]
```

지원 **목표**는 Rocky Linux·RHEL 8.x/9.x/10.x, Ubuntu 22.04/24.04/26.04, Debian 12/13, Windows 11 및 Windows Server 2019/2022/2025의 x86_64이다. `isoFamily`는 qemu #79 manifest의 `rocky`·`ubuntu`·`debian`·`windows`와 일치해야 하며, `osId`는 `rocky`·`rhel`·`ubuntu`·`debian`·`mswindows` 중 하나이다. 실제 RHEL은 Rocky와 별도 `osId=rhel` selector로 등록하고 RHEL 게스트 설치 검증 전에는 등록하지 않는다. Windows 11은 `mswindows/client/11`, Server는 `mswindows/server/2019|2022|2025`로 서로 구분한다. 임의 문자열 wildcard와 파일명 추측은 허용하지 않는다.
QGA가 `amd64` 또는 `x86-64`로 보고한 아키텍처는 catalog의 `x86_64`로 정규화하며 그 밖의 아키텍처에는 추천하지 않는다.

동일 selector의 중복이나 복수 ISO 매칭, 잘못된 UUID·SHA·버전은 추천을 거부한다. QGA OS 정보가 없거나 Cloud에 등록된 VM OS와 다르면 자동 추천을 보류한다. QGA 자체가 없는 게스트는 표준 ISO 탭에서 관리자가 OS를 확인한 뒤 수동 연결하고, 설치 후 QGA 관측을 다시 수행한다. `NOT_CONFIGURED`, `OS_UNKNOWN`, `OS_MISMATCH`, `UNSUPPORTED_OS`, `NO_MATCH`, `INVALID_CONFIG`는 각 원인을 별도로 반환한다. 기본값 `[]`은 ISO 추천만 비활성화한다.

기존 `osVersion` 정확 일치 항목은 전환 기간 동안 계속 읽는다. 새 `versions` 형식과 한 catalog에서 혼용할 수 있으나 같은 게스트에 두 selector가 겹치면 거부한다. 운영자는 기존 ISO를 삭제하거나 연결된 미디어를 교체하기 전에 새 ISO의 Ready·SHA를 확인하고, 새 catalog를 설정한 뒤 해당 게스트의 추천값을 검사한다. 문제가 있으면 이전 catalog JSON으로 되돌린다. 기존에 연결된 ISO는 자동 분리하지 않는다.

qemu #79의 `process-9-89806b1` manifest는 Rocky `fe0c2707ea8ad7c758ea4e51431142851a1d1c072a80180774f52b5c78552a52`, Ubuntu `1fd0c01f113a9e059d0401ffa0739f9533b5d53ebc1d9f9db2b9c06073b466c1`, Debian `be90020bf5c382adea84a539bc970d48d84752452a57fd0d6602a4124898a796`, Windows `cb044eb84071e6fb921c4a8e5128ae70ebbca8e5ecfd224537795a76f9171579`의 네 ISO를 제공한다. 테스트 클러스터 Cloud의 `listIsos.checksum`은 SHA-512로 반환되었고, 원본 파일의 SHA-512 계산값과 일치했다. 이는 설치/설정 검증 증거이고 실제 QGA 실행·프로세스 작업의 제품 지원 승인은 Cloud #1177의 별도 gate다.

프로세스 탭은 Cloud의 기존 `attachIso` 비동기 작업과 CD 슬롯 검사를 사용한다. 이미 연결된 ISO를 임의로 분리하지 않는다. 연결 전에 VM 상태·영역·접근 가능 여부·Ready·아키텍처·SHA-256을 다시 확인한다. 메타데이터 해시가 없으면 연결을 거부하고 관리자에게 ISO 등록값을 점검하도록 안내한다.

ISO 연결 성공은 `게스트 설치 대기`이다. 게스트 콘솔에서 관리자 권한으로 설치하거나 복구한다. QGA 실행 RPC가 막힌 상태에서는 QGA로 QGA 자신을 고치려 하지 않는다. Linux는 ISO를 읽기 전용으로 마운트한 뒤 `bash /mnt/ablestack-tools/install-linux.sh --mode process-management`, Windows는 관리자 PowerShell에서 ISO 루트의 `install.bat`를 실행한다. 설치 결과가 재부팅을 요구하면 재부팅 후 다시 검사한다.

`READY`는 재검사에서 필수 QGA RPC 8개가 모두 ENABLED이고, Cloud의 프로세스 읽기 작업이 실제 snapshot을 반환했을 때만 표시한다. 연결·설치 성공 메시지·DB 설정만으로 READY 처리하지 않는다. 확인 실패 시 기존 미디어를 유지하고, 사용자가 설치 결과를 점검한 후 재검사한다.
