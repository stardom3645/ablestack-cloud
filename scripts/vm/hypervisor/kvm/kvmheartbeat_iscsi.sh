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
                    -h host
                    -r write/read hb log 
                    -c cleanup
                    -t interval between read hb log\n"
  exit 1
}
#set -x
PoolName=rbd
PoolAuthUserName=admin
HostIP=
interval=
rflag=0
cflag=0

while getopts 'h:t:rc' OPTION
do
  case $OPTION in
  h)
     HostIP="$OPTARG"
     ;;
  t)
     interval="$OPTARG"
     ;;
  r)
     rflag=1 
     ;;
  c)
    cflag=1
     ;;
  *)
     help
     ;;
  esac
done

if [ -z "$PoolName" ]; then
  exit 2
fi

keyringFile="/etc/ceph/ceph.client.admin.keyring"
confFile="/etc/ceph/ceph.conf"

get_monhost() {
  username=$(cat /etc/ceph/ceph.conf | grep v1)
  mon1=$(echo $username | cut -d ',' -f2 | sed 's/v1://g' | sed 's/:6789\/0]//g')
  mon2=$(echo $username | cut -d ',' -f4 | sed 's/v1://g' | sed 's/:6789\/0]//g')
  mon3=$(echo $username | cut -d ',' -f6 | sed 's/v1://g' | sed 's/:6789\/0]//g')

  Monhost=$mon1','$mon2','$mon3
}

write_hbLog() {
  #write the heart beat log
  persist=$(sg_persist -ik /dev/mapper/mpatha)
  if [ $? -eq 0 ]
  then
    timestamp=$(date +%s)
    obj=$(rados -p $PoolName ls --id $PoolAuthUserName | grep hb-$HostIP)
    if [ $? -gt 0 ]; then
      rados -p $PoolName create hb-$HostIP --id $PoolAuthUserName
    fi
    echo $timestamp | rados -p $PoolName put hb-$HostIP - --id $PoolAuthUserName
    if [ $? -gt 0 ]; then
      printf "Failed to create rbd file"
      return 2
    fi
    return 0
  fi
}

check_hbLog() {
#check the heart beat log
  now=$(date +%s)
  hb=$(rados -p $PoolName get hb-$HostIP - --id $PoolAuthUserName)
  diff=$(expr $now - $hb)
  if [ $diff -gt $interval ]; then
    return $diff
  fi
  return 0
}

if [ "$rflag" == "1" ]; then
  check_hbLog
  diff=$?
  if [ $diff == 0 ]; then
    echo "=====> ALIVE <====="
  else
    echo "=====> Considering host as DEAD because last write on [Iscsi] was [$diff] seconds ago, but the max interval is [$interval] <======"
  fi
  exit 0
elif [ "$cflag" == "1" ]; then
  /usr/bin/logger -t heartbeat "kvmheartbeat_iscsi.sh will reboot system because it was unable to write the heartbeat to the storage."
  sync &
  sleep 5
  echo b > /proc/sysrq-trigger
  exit $?
else
  write_hbLog
  exit 0
fi
