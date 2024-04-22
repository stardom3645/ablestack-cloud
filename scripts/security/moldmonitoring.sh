#!/bin/bash

# mold-monitoring 서비스가 실행 시 동작하는 스크립트로 ps 프로세스 감지와 http 헤더 감지함.

# ps 프로세스 감지
commandname="org.apache.cloudstack.ServerDaemon"
count=$(ps ax -o command | grep "$commandname" | grep -v "^grep" | wc -l)
if [ "$count" -eq 0 ]; then
        echo "Error : mold 프로세스를 찾지 못했습니다."
        exit 1
else
        echo "Success : mold 프로세스가 정상적으로 감지되었습니다."
fi

# http 헤더 감지
hostname=$(hostname -i)
url="https://$hostname:8443/client/#/user/login"
httpstatus=$(curl -k -s "$url" -o /dev/null -w "%{http_code}")
curlresult=$?
if [ "$curlresult" -ne 0 ]; then
        echo "Error : HTTP 접속 이상 - curl exit staus [$curlresult]."
        exit 1
elif [ "$httpstatus" -ge 400 ]; then
        echo "Error : HTTP status 이상 - HTTP status [$httpstatus]."
        exit 1
else
        echo "Success : mold http 헤더가 정상적으로 감지되었습니다."
        exit 0
fi
