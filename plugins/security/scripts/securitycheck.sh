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

# # 시간
# now="$(date +%Y)-$(date +%m)-$(date +%d) $(date +%H):$(date +%M):$(date +%S)"

# # mysql 서비스 확인
# systemctl status mysqld | grep -i running &> /dev/null
# if [[ $? == 0 ]]; then
#     echo "mysql,1,$now"
# else
#     echo "mysql,0,$now"
# fi

# # firewalld 서비스 확인
# systemctl status firewalld | grep -i running &> /dev/null
# if [[ $? == 0 ]]; then
#     echo "firewalld,1,$now"
# else
#     echo "firewalld,0,$now"
# fi

# # cloudstack-management 서비스 확인
# systemctl status cloudstack-management | grep -i running &> /dev/null
# if [[ $? == 0 ]]; then
#     echo "management,1,$now"
# else
#     echo "management,0,$now"
# fi

# 출력 : service_name, result (failed 0, success 1), time
# mysql, 0, 2023-07-18 01:42:14
# firewalld, 0, 2023-07-18 01:42:14
# management, 1, 2023-07-18 01:42:14

echo "mysql, 0, 2023-07-18 01:42:14"
echo "firewalld, 0, 2023-07-18 01:42:14"
echo "management, 1, 2023-07-18 01:42:14"