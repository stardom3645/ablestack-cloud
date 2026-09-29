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
    private static final String CATALOG = "[{\"zoneId\":\"" + ZONE + "\",\"osId\":\"rocky\",\"osVersion\":\"9.8\","
            + "\"arch\":\"x86_64\",\"isoId\":\"" + ISO + "\",\"version\":\"0.10.0\",\"sha256\":\"" + SHA + "\"}]";
    private static final Map<String, String> OS = Map.of("family", "linux", "id", "rocky", "version", "9.8", "arch", "x86_64");

    @Test public void exactZoneOsArchResolves() {
        assertEquals(ISO, VmProcessToolsIsoCatalog.resolve(CATALOG, ZONE, OS).get("isoId"));
        assertEquals(SHA, VmProcessToolsIsoCatalog.resolve(CATALOG, ZONE, OS).get("sha256"));
    }
    @Test public void unknownAndWrongOsDoNotRecommendMedia() {
        assertEquals("NOT_CONFIGURED", VmProcessToolsIsoCatalog.resolve("[]", ZONE, OS).get("status"));
        assertEquals("NO_MATCH", VmProcessToolsIsoCatalog.resolve(CATALOG, ZONE,
                Map.of("family", "linux", "id", "rocky", "version", "10.2", "arch", "x86_64")).get("status"));
        assertEquals("OS_UNKNOWN", VmProcessToolsIsoCatalog.resolve(CATALOG, ZONE,
                Map.of("family", "unknown", "id", "unknown", "version", "unknown", "arch", "unsupported")).get("status"));
    }
    @Test public void corruptedOrAmbiguousCatalogFailsClosed() {
        assertEquals("INVALID_CONFIG", VmProcessToolsIsoCatalog.resolve("bad", ZONE, OS).get("status"));
        String duplicate = "[" + CATALOG.substring(1, CATALOG.length() - 1) + ","
                + CATALOG.substring(1, CATALOG.length() - 1) + "]";
        assertEquals("INVALID_CONFIG", VmProcessToolsIsoCatalog.resolve(duplicate, ZONE, OS).get("status"));
        assertEquals("INVALID_CONFIG", VmProcessToolsIsoCatalog.resolve(CATALOG.replace(SHA, "123"), ZONE, OS).get("status"));
    }
}
