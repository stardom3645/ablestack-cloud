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

# 1 nbd enable
# qemu-nbd -d /dev/nbd0
sudo modprobe nbd

# 2 Check for unused nbd
targetNbd=$(lsblk /dev/nbd* -p |grep 0B | cut -d ' ' -f 1 | grep -v '^$' | head -n 1)

fileName=$(find / -path "*$TempPath/template.properties" 2>/dev/null | head -n 1  | xargs grep '^filename=' | cut -d'=' -f2)
TmpFile=$(find / -path "*$TempPath/$fileName" 2>/dev/null | head -n 1)

# nbd connection
sudo qemu-nbd -c $targetNbd $TmpFile

# 5 Check the vg of the corresponding pv
max_checks=60 # Maximum number of checks
for ((i=1; i<=max_checks; i++)); do
    child_size=$(lsblk "$targetNbd" -p -J | jq -r '.blockdevices[0].size')

    if [[ "$child_size" != "0B" ]]; then
        break
    fi
    sleep 1
done

firstPartitionPath=$(lsblk $targetNbd -p -J |jq -r '.blockdevices[0].name')

ex_vg_name=$(sudo pvs $firstPartitionPath --reportformat json |jq -r '.report[0].pv[0].vg_name')

# 6 vg disable
sudo vgchange -an $ex_vg_name

# 7 Change vg name
sudo vgrename $ex_vg_name $Uuid

# 8 change pv uuid
sudo vgchange --uuid $Uuid

# 9 change vg uuid
sudo pvchange --uuid $firstPartitionPath

# 10 nbd Disconnect
sudo qemu-nbd -d $targetNbd

exit 0