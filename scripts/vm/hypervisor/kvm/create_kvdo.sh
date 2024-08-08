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
                    -p rbd pool name
                    -n pool auth username
                    -i image name
                    -s image size\n"
  exit 1
}
#set -x
PoolName=
PoolAuthUserName=
ImageName=
ImageSize=

while getopts 'p:i:n:s:' OPTION
do
  case $OPTION in
  p)
     PoolName="$OPTARG"
     ;;
  n)
     PoolAuthUserName="$OPTARG"
     ;;
  i)
     ImageName="$OPTARG"
     ;;
  s)
     ImageSize="$OPTARG"
     ;;
  *)
     help
     ;;
  esac
done

if [ -z "$PoolName" ] || [ -z "$ImageName" ]; then
  exit 2
fi

# rbd image가 존재하는지 체크
devicePath=$(rbd showmapped | grep "$PoolName[ ]*$ImageName" | grep -o "[^ ]*[ ]*$")
if [ -z "$devicePath" ]; then
  # device no exist
  devicePath=$(rbd map "$PoolName/$ImageName" --id "$PoolAuthUserName")
fi

# 파티션이 존재하는지 확인
partitionExist=$(lsblk $devicePath -p -J |jq -r '.blockdevices[0].children')
vg_name=vg_$(echo $ImageName| sed 's/-//g')
if [ "$partitionExist" == "null" ]; then
  # create partition
  parted --script $devicePath mklabel gpt
  parted --script $devicePath mkpart primary 1 100%

  firstPartitionPath=$(lsblk $devicePath -p -J |jq -r '.blockdevices[0].children[0].name')

  # create pv
  pvcreate $firstPartitionPath

  # create vg
  vgcreate $vg_name $firstPartitionPath

  # create lv
  size=$(expr $ImageSize - 10485760 )
  lvcreate --type vdo --name ablestack_kvdo --size $size"B" --virtualsize $size"B" $vg_name
else
  vgchange -ay $vg_name
fi

exit 0