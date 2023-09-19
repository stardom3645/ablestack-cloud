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

# Security Check 
# 보안 기능 프로세스가 정상적으로 작동하는지 확인하는 스크립트
# 항목 : encrypt, request
# return : {이름, 결과}

jarfile='/usr/share/cloudstack-common/lib/cloudstack-utils.jar'
result=$(java -classpath $jarfile com.cloud.utils.mold.SecurityCheck)
echo $result
