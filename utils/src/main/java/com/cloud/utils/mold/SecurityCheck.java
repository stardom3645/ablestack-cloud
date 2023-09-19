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
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cloudstack.utils.security.CertUtils;

import com.cloud.utils.StringUtils;
import com.cloud.utils.crypt.AeadBase64Encryptor;
import com.cloud.utils.crypt.EncryptionException;

public class SecurityCheck {
    public static void main(String[] args) throws EncryptionException {
        Map<String, String> resultMap = new HashMap<>();
        // Request (Request 및 Response 에 포함된 민감한 문자열을 제거하는지 확인)
        final String input = "name=SS1&provider=SMB&zoneid=5a60af2b-3025-4f2a-9ecc-8e33bf2b94e3&url=cifs%3A%2F%2F10.102.192.150%2FSMB-Share%2Fsowmya%2Fsecondary%3Fuser%3Dsowmya%26password%3DXXXXX%40123%26domain%3DBLR";
        final String expected = "name=SS1&provider=SMB&zoneid=5a60af2b-3025-4f2a-9ecc-8e33bf2b94e3&url=cifs%3A%2F%2F10.102.192.150%2FSMB-Share%2Fsowmya%2Fsecondary%3Fuser%3Dsowmya%26domain%3DBLR";
        final String result = StringUtils.cleanString(input);
        if (result.equalsIgnoreCase(expected)) {
            resultMap.put("request", "true");
        } else {
            resultMap.put("request", "false");
        }
        // Encrypt (보안기능에 적합한 알고리즘으로 암호화된 암호가 정상적으로 복호화되는지 확인)
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
        // Certificate (보안기능에 적합한 알고리즘으로 암호화된 인증서가 정상적으로 생성되는지 확인)
        try {
            KeyPair caKeyPair = CertUtils.generateRandomKeyPair(1024);
            X509Certificate caCertificate = CertUtils.generateV3Certificate(null, caKeyPair, caKeyPair.getPublic(), "CN=test", "SHA256WithRSAEncryption", 365, null, null);
            final KeyPair clientKeyPair = CertUtils.generateRandomKeyPair(1024);
            final List<String> domainNames = Arrays.asList("domain1.com", "www.2.domain2.com", "3.domain3.com");
            final List<String> addressList = Arrays.asList("1.2.3.4", "192.168.1.1", "2a02:120b:2c16:f6d0:d9df:8ebc:e44a:f181");
            final X509Certificate clientCert = CertUtils.generateV3Certificate(caCertificate, caKeyPair, clientKeyPair.getPublic(),
                    "CN=domain.example", "SHA256WithRSAEncryption", 10, domainNames, addressList);
            clientCert.verify(caKeyPair.getPublic());
            if (clientCert.getIssuerDN().equals(caCertificate.getIssuerDN()) && clientCert.getSigAlgName().equals("SHA256WITHRSA")) {
                resultMap.put("certificate", "true");
            } else {
                resultMap.put("certificate", "false");
            }
        } catch (Exception e) {
            resultMap.put("certificate", "false");
        }
        System.out.printf("%s%n", resultMap);
    }
}
