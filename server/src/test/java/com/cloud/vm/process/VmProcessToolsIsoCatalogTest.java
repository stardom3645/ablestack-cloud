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
package com.cloud.vm.process;

import static org.junit.Assert.assertEquals;
import java.util.Map;
import org.junit.Test;

public class VmProcessToolsIsoCatalogTest {
    private static final String ZONE = "11111111-1111-4111-8111-111111111111";
    private static final String ISO = "22222222-2222-4222-8222-222222222222";
    private static final String SHA = "a".repeat(64);
    private static final String SHA512 = "b".repeat(128);
    private static String entry(String family, String id, String product, String versions) {
        return "{\"zoneId\":\"" + ZONE + "\",\"isoFamily\":\"" + family + "\",\"osId\":\"" + id
                + "\",\"productType\":\"" + product + "\",\"versions\":" + versions
                + ",\"arch\":\"x86_64\",\"isoId\":\"" + ISO + "\",\"version\":\"process-9\",\"sha256\":\"" + SHA
                + "\",\"sha512\":\"" + SHA512 + "\"}";
    }
    private static final String ROCKY = "[" + entry("rocky", "rocky", "none", "[\"8\",\"9\",\"10\"]") + "]";
    private static Map<String, String> os(String id, String version, String product) {
        return Map.of("family", "mswindows".equals(id) ? "windows" : "linux", "id", id,
                "version", version, "productType", product, "arch", "x86_64");
    }
    private static String status(String catalog, String id, String version, String product, String registered) {
        return VmProcessToolsIsoCatalog.resolve(catalog, ZONE, os(id, version, product), registered).get("status");
    }

    @Test public void oneIsoCoversAllRockyMajorsAndMinors() {
        for (String version : new String[] {"8.10", "9.7", "9.8", "10.2"}) {
            assertEquals(ISO, VmProcessToolsIsoCatalog.resolve(ROCKY, ZONE, os("rocky", version, "none"),
                    "Rocky Linux " + version.split("\\.")[0]).get("isoId"));
        }
        assertEquals(SHA512, VmProcessToolsIsoCatalog.resolve(ROCKY, ZONE, os("rocky", "9.8", "none"),
                "Rocky Linux 9").get("sha512"));
    }
    @Test public void familyAndProductSelectorsAreExplicit() {
        assertEquals("MATCHED", status("[" + entry("ubuntu", "ubuntu", "none", "[\"22.04\",\"24.04\",\"26.04\"]") + "]",
                "ubuntu", "24.04", "none", "Ubuntu 24.04 LTS"));
        assertEquals("MATCHED", status("[" + entry("debian", "debian", "none", "[\"12\",\"13\"]") + "]",
                "debian", "12", "none", "Debian GNU/Linux 12 (64-bit)"));
        assertEquals("MATCHED", status("[" + entry("windows", "mswindows", "server", "[\"2019\",\"2022\",\"2025\"]") + "]",
                "mswindows", "2025", "server", "Windows Server 2025 (64-bit)"));
        assertEquals("MATCHED", status("[" + entry("windows", "mswindows", "client", "[\"11\"]") + "]",
                "mswindows", "11", "client", "Windows 11 (64-bit)"));
        assertEquals("NO_MATCH", status(ROCKY, "rhel", "9.6", "none", "Red Hat Enterprise Linux 9"));
        assertEquals("MATCHED", status("[" + entry("rocky", "rhel", "none", "[\"8\",\"9\",\"10\"]") + "]",
                "rhel", "9.6", "none", "Red Hat Enterprise Linux 9"));
        assertEquals("NO_MATCH", status("[" + entry("windows", "mswindows", "server", "[\"2025\"]") + "]",
                "mswindows", "11", "client", "Windows 11 (64-bit)"));
    }
    @Test public void staleUnknownAndUnsupportedGuestsDoNotRecommend() {
        assertEquals("NOT_CONFIGURED", status("[]", "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("OS_MISMATCH", status(ROCKY, "rocky", "9.8", "none", "Rocky Linux 8"));
        assertEquals("OS_MISMATCH", status(ROCKY, "rocky", "9.8", "none", null));
        assertEquals("UNSUPPORTED_OS", status(ROCKY, "rocky", "11.1", "none", "Rocky Linux 10"));
        assertEquals("UNSUPPORTED_OS", status(ROCKY, "debian", "11", "none", "Debian GNU/Linux 11"));
        assertEquals("UNSUPPORTED_OS", VmProcessToolsIsoCatalog.resolve(ROCKY, ZONE,
                Map.of("family", "linux", "id", "rocky", "version", "9.8", "arch", "aarch64"),
                "Rocky Linux 9").get("status"));
        assertEquals("OS_UNKNOWN", VmProcessToolsIsoCatalog.resolve(ROCKY, ZONE,
                Map.of("family", "unknown", "id", "unknown", "version", "unknown", "arch", "unsupported"),
                "Rocky Linux 9").get("status"));
    }
    @Test public void malformedOrOverlappingCatalogFailsClosed() {
        assertEquals("INVALID_CONFIG", status("bad", "rocky", "9.8", "none", "Rocky Linux 9"));
        String item = ROCKY.substring(1, ROCKY.length() - 1);
        assertEquals("INVALID_CONFIG", status("[" + item + "," + item + "]", "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("INVALID_CONFIG", status("[" + item + "," + item.replace(ISO, "33333333-3333-4333-8333-333333333333") + "]",
                "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("INVALID_CONFIG", status(ROCKY.replace(SHA, "123"), "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("INVALID_CONFIG", status(ROCKY.replace(SHA512, "123"), "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("INVALID_CONFIG", status(ROCKY.replace("x86_64", "aarch64"), "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("INVALID_CONFIG", status(ROCKY.replace("[\"8\",\"9\",\"10\"]", "[\"8.x\"]"),
                "rocky", "9.8", "none", "Rocky Linux 9"));
    }
    @Test public void legacyExactCatalogRemainsReadableDuringMigration() {
        String legacy = "[" + entry("rocky", "rocky", "none", "[\"9\"]")
                .replace("\"isoFamily\":\"rocky\",", "").replace("\"productType\":\"none\",", "")
                .replace("\"versions\":[\"9\"],", "\"osVersion\":\"9.8\",") + "]";
        assertEquals("MATCHED", status(legacy, "rocky", "9.8", "none", "Rocky Linux 9"));
        assertEquals("NO_MATCH", status(legacy, "rocky", "9.7", "none", "Rocky Linux 9"));
        String overlapping = legacy.substring(0, legacy.length() - 1) + "," + ROCKY.substring(1);
        assertEquals("INVALID_CONFIG", status(overlapping, "rocky", "9.8", "none", "Rocky Linux 9"));
    }
}
