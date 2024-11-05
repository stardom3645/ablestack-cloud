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
                    -i image name
                    -a action type (enable or disable)\n"
  exit 1
}
#set -x
ImageName=
Action=

while getopts 'i:a:' OPTION
do
  case $OPTION in
  i)
     ImageName="$OPTARG"
     ;;
  a)
     Action="$OPTARG"
     ;;
  *)
     help
     ;;
  esac
done

if [ -z "$ImageName" ] || [ -z "$Action" ]; then
  exit 2
fi

vgName="vg_${ImageName//-/}"
if [ "$Action" = "enable" ]; then
  lvchange --compression y --deduplication y $vgName
elif [ "$Action" = "disable" ]; then
  lvchange --compression n --deduplication n $vgName
fi

exit 0