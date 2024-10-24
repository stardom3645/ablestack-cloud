#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
help() {
  printf "Usage: $0
                    -p template file path\n
                    -u template file uuid\n"
  exit 1
}
#set -x
TempPath=
TmpFile=
Uuid=

while getopts 'p:' OPTION
do
  case $OPTION in
  p)
     TempPath="$OPTARG"
     ;;
  n)
     TmpFile="$OPTARG"
     ;;
  u)
     Uuid="$OPTARG"
     ;;
  *)
     help
     ;;
  esac
done

# 1 nbd 활성화
# vgchange -an vg_12df76b6ba4d4d1781627131620bbd94
# qemu-nbd -d /dev/nbd0
modprobe nbd

# 2 사용하지 않는 nbd 확인
targetNbd=$(lsblk /dev/nbd* -p |grep 0B | cut -d ' ' -f 1 | grep -v '^$' | head -n 1)
echo "1 : " $targetNbd

# TmpFile="/nfs/secondary/template/tmpl/2/224/c3aaf7dd-57ed-4f4d-8be6-88abfd646d51.qcow2"
fileName=$(find / -path "*$TempPath/template.properties" 2>/dev/null | head -n 1  | xargs grep '^filename=' | cut -d'=' -f2)
TmpFile=$(find / -path "*$TempPath/$fileName" 2>/dev/null | head -n 1)

echo "2 : "  $fileName
echo "2 : "  $TmpFile

# nbd 연결
sudo qemu-nbd -c $targetNbd $TmpFile

# 4 pv 확인

# 5 해당 pv의 vg 확인
max_checks=60 # 최대 체크 횟수
for ((i=1; i<=max_checks; i++)); do
    # lsblk 결과를 가져오고 jq로 처리
    child_name=$(lsblk "$targetNbd" -p -J | jq -r '.blockdevices[0].children[0].name')

    # 결과가 null이 아닌지 확인
    if [[ "$child_name" != "null" ]]; then
        break
    fi
    sleep 1  # 1초 대기
done

firstPartitionPath=$(lsblk $targetNbd -p -J |jq -r '.blockdevices[0].children[0].name')

echo "3 : " $targetNbd
echo "3 : " $firstPartitionPath
ex_vg_name=$(sudo pvs $firstPartitionPath --reportformat json |jq -r '.report[0].pv[0].vg_name')
echo "4 : " $ex_vg_name

# 6 vg 비활성화
sudo vgchange -an $ex_vg_name

# 7 vg 이름 랜덤하게 변경
temp_uuid=ab999218-95c7-422c-af1d-cfc68c5a0eb6
sudo vgrename $ex_vg_name $temp_uuid

# 8 pv uuid 변경
sudo vgchange --uuid $temp_uuid

# 9 vg uuid 변경
sudo pvchange --uuid $firstPartitionPath

# 10 nbd 해제
sudo qemu-nbd -d $targetNbd

exit 0