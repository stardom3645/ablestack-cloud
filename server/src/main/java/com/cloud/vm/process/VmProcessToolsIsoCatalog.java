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

import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Resolves an administrator-maintained zone/guest tuple to one registered Tools ISO. */
public final class VmProcessToolsIsoCatalog {
    private VmProcessToolsIsoCatalog() { }

    private static String value(JsonObject object, String name) {
        JsonElement item = object.get(name);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing catalog field: " + name);
        String text = item.getAsString();
        if (text.isEmpty() || text.length() > 256) throw new IllegalArgumentException("Invalid catalog field: " + name);
        return text;
    }

    private static String canonicalUuid(String value) {
        if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID");
        return value;
    }

    public static Map<String, String> resolve(String raw, String zoneId, Map<?, ?> os) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) return Map.of("status", "NOT_CONFIGURED");
        if (zoneId == null || os == null || "unknown".equals(os.get("family"))
                || "unknown".equals(os.get("version"))) return Map.of("status", "OS_UNKNOWN");
        try {
            if (raw.length() > 65536) throw new IllegalArgumentException("Catalog too large");
            JsonArray entries = JsonParser.parseString(raw).getAsJsonArray();
            if (entries.size() > 128) throw new IllegalArgumentException("Too many catalog entries");
            Map<String, String> match = null;
            for (JsonElement element : entries) {
                JsonObject item = element.getAsJsonObject();
                String configuredZone = canonicalUuid(value(item, "zoneId"));
                String osId = value(item, "osId");
                String osVersion = value(item, "osVersion");
                String arch = value(item, "arch");
                String isoId = canonicalUuid(value(item, "isoId"));
                String version = value(item, "version");
                String sha = value(item, "sha256").toLowerCase(java.util.Locale.ROOT);
                if (!sha.matches("[0-9a-f]{64}") || !"x86_64".equals(arch))
                    throw new IllegalArgumentException("Invalid Tools ISO identity");
                if (configuredZone.equals(zoneId) && osId.equals(os.get("id"))
                        && osVersion.equals(os.get("version")) && arch.equals(os.get("arch"))) {
                    if (match != null) throw new IllegalArgumentException("Ambiguous Tools ISO mapping");
                    match = Map.of("status", "MATCHED", "isoId", isoId, "version", version,
                            "sha256", sha, "zoneId", configuredZone, "arch", arch);
                }
            }
            return match == null ? Map.of("status", "NO_MATCH") : match;
        } catch (RuntimeException ex) {
            return Map.of("status", "INVALID_CONFIG");
        }
    }
}
