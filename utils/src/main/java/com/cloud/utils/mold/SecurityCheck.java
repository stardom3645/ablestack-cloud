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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.cloud.utils.PasswordGenerator;
import com.cloud.utils.StringUtils;
import com.cloud.utils.crypt.AeadBase64Encryptor;
import com.cloud.utils.crypt.EncryptionException;

public class SecurityCheck {
    public static void main(String[] args) throws EncryptionException {
        Map<String, String> resultMap = new HashMap<>();
        // Request (Request 및 Response에 포함된 민감한 문자열을 제거하는 프로세스가 정상적으로 동작하는지 확인)
        final String input = "name=SS1&provider=SMB&zoneid=5a60af2b-3025-4f2a-9ecc-8e33bf2b94e3&url=cifs%3A%2F%2F10.102.192.150%2FSMB-Share%2Fsowmya%2Fsecondary%3Fuser%3Dsowmya%26password%3DXXXXX%40123%26domain%3DBLR";
        final String expected = "name=SS1&provider=SMB&zoneid=5a60af2b-3025-4f2a-9ecc-8e33bf2b94e3&url=cifs%3A%2F%2F10.102.192.150%2FSMB-Share%2Fsowmya%2Fsecondary%3Fuser%3Dsowmya%26domain%3DBLR";
        final String result = StringUtils.cleanString(input);
        if (result.equalsIgnoreCase(expected)) {
            resultMap.put("request", "true");
        } else {
            resultMap.put("request", "false");
        }
        // Encrypt (보안기능에 적합한 알고리즘으로 암호화된 암호를 복호화하는 프로세스가 정상적으로 동작하는지 확인)
        final String pwd = "managementkey";
        AeadBase64Encryptor encryptor = new AeadBase64Encryptor(pwd.getBytes(StandardCharsets.UTF_8));
        try {
            final String decrypt = encryptor.decrypt("DlTJUG8rWFjOd3aoHtbBGEcQ/piovBzRJ/bnQ1FACLg=");
            if (decrypt.equalsIgnoreCase("mold")) {
                resultMap.put("encrypt", "true");
            } else {
                resultMap.put("encrypt", "false");
            }
        } catch (EncryptionException e){
            resultMap.put("encrypt", "false");
        }
        // password (password 정책 관련 프로세스가 정상적으로 동작하는지 확인)
        String password = PasswordGenerator.generateRandomPassword(8);
        for (char c : password.toCharArray()) {
            if (!Character.isDigit(c) && Character.isLowerCase(c) && Character.isUpperCase(c)) {
                resultMap.put("password", "true");
            } else {
                resultMap.put("password", "false");
            }
        }
        System.out.printf("%s%n", resultMap);
    }
}
