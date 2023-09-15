//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.utils.mold;

import java.util.HashMap;
import java.util.Map;

import org.springframework.mock.web.MockHttpSession;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import com.cloud.utils.HttpUtils;
import com.cloud.utils.StringUtils;
import com.cloud.utils.crypt.CloudStackEncryptor;
import com.cloud.utils.crypt.EncryptionCLI;

public class SecurityCheck {
    public static void main(String[] args) {
        Map<String, String> resultMap = new HashMap<>();

        // Encrypt (보안기능에 적합한 알고리즘으로 암호화된 암호가 정상적으로 복호화되는지 확인)
        final String pwd = "managementkey";
        CloudStackEncryptor encryptor = new CloudStackEncryptor(pwd, "v2", EncryptionCLI.class);
        if (encryptor.decrypt("DlTJUG8rWFjOd3aoHtbBGEcQ/piovBzRJ/bnQ1FACLg=").equalsIgnoreCase("mold")) {
            resultMap.put("encrypt", "true");
        } else {
            resultMap.put("encrypt", "false");
        }

        // Sessionkey (쿠키 및 파라미터에 세션키가 존재하는지 벨리데이션 체크)
        HttpSession session = null;
        final String sessionKeyString = "sessionkey";
        final String sessionKeyValue = "randomUniqueSessionID";
        session = new MockHttpSession();
        session.setAttribute(sessionKeyString, sessionKeyValue);
        Map<String, Object[]> params = new HashMap<String, Object[]>();
        Cookie[] cookies = new Cookie[]{new Cookie(sessionKeyString, sessionKeyValue)};
        params.put(sessionKeyString, new String[]{sessionKeyValue});
        if (HttpUtils.validateSessionKey(session, params, cookies, sessionKeyString) == true) {
            resultMap.put("sessionkey", "true");
        } else {
            resultMap.put("sessionkey", "false");
        }

        // Request (Request 및 Response 에 포함된 민감한 문자열을 제거하는지 확인)
        final String input = "name=SS1&provider=SMB&zoneid=5a60af2b-3025-4f2a-9ecc-8e33bf2b94e3&url=cifs%3A%2F%2F10.102.192.150%2FSMB-Share%2Fsowmya%2Fsecondary%3Fuser%3Dsowmya%26password%3DXXXXX%40123%26domain%3DBLR";
        final String expected = "name=SS1&provider=SMB&zoneid=5a60af2b-3025-4f2a-9ecc-8e33bf2b94e3&url=cifs%3A%2F%2F10.102.192.150%2FSMB-Share%2Fsowmya%2Fsecondary%3Fuser%3Dsowmya%26domain%3DBLR";
        final String result = StringUtils.cleanString(input);
        if (result.equalsIgnoreCase(expected)) {
            resultMap.put("request", "true");
        } else {
            resultMap.put("request", "false");
        }

        System.out.printf("%s%n", resultMap);
    }
}
