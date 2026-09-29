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

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Selects a registered ISO only when observed and registered guest identities agree. */
public final class VmProcessToolsIsoCatalog {
    private VmProcessToolsIsoCatalog() { }

    private static final Pattern REGISTERED_OS = Pattern.compile(
            "^(Rocky Linux|Red Hat Enterprise Linux(?: Server)?|Ubuntu|Debian(?: GNU/Linux)?|Windows(?: Server)?)\\s+(8|9|10|11|12|13|2019|2022|2025|22\\.04|24\\.04|26\\.04)(?=\\D|$)",
            Pattern.CASE_INSENSITIVE);

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

    private static String major(String version) {
        return version.split("\\.", 2)[0];
    }

    private static String familyFor(String id) {
        if ("rocky".equals(id) || "rhel".equals(id)) return "rocky";
        if ("ubuntu".equals(id)) return "ubuntu";
        if ("debian".equals(id)) return "debian";
        if ("mswindows".equals(id)) return "windows";
        return null;
    }

    private static boolean supported(String id, String product, String version) {
        if (id == null || product == null || version == null) return false;
        if ("rocky".equals(id) || "rhel".equals(id))
            return "none".equals(product) && version.matches("(8|9|10)(\\.[0-9]+)*");
        if ("debian".equals(id))
            return "none".equals(product) && version.matches("(12|13)(\\.[0-9]+)*");
        if ("ubuntu".equals(id))
            return "none".equals(product) && Set.of("22.04", "24.04", "26.04").contains(version);
        if ("mswindows".equals(id))
            return "client".equals(product) && "11".equals(version)
                    || "server".equals(product) && Set.of("2019", "2022", "2025").contains(version);
        return false;
    }

    private static boolean registeredMatches(String name, String id, String product, String version) {
        if (name == null) return false;
        Matcher matcher = REGISTERED_OS.matcher(name);
        if (!matcher.find()) return false;
        String label = matcher.group(1).toLowerCase(Locale.ROOT);
        String registeredId = label.startsWith("rocky") ? "rocky"
                : label.startsWith("red hat") ? "rhel"
                : label.startsWith("ubuntu") ? "ubuntu"
                : label.startsWith("debian") ? "debian" : "mswindows";
        String registeredProduct = "mswindows".equals(registeredId)
                ? label.contains("server") ? "server" : "client" : "none";
        String registeredVersion = matcher.group(2);
        return registeredId.equals(id) && registeredProduct.equals(product)
                && (Set.of("rocky", "rhel", "debian").contains(id)
                        ? registeredVersion.equals(major(version)) : registeredVersion.equals(version));
    }

    private static boolean selectorVersion(String id, String product, String version) {
        return supported(id, product, version)
                && (Set.of("rocky", "rhel", "debian").contains(id) ? major(version).equals(version) : true);
    }

    private static boolean matchesVersion(String id, String selectorVersion, String observedVersion, boolean legacy) {
        return legacy ? selectorVersion.equals(observedVersion)
                : Set.of("rocky", "rhel", "debian").contains(id)
                        ? selectorVersion.equals(major(observedVersion)) : selectorVersion.equals(observedVersion);
    }

    public static Map<String, String> resolve(String raw, String zoneId, Map<?, ?> os, String registeredOsName) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) return Map.of("status", "NOT_CONFIGURED");
        if (zoneId == null || os == null) return Map.of("status", "OS_UNKNOWN");
        String id = os.get("id") instanceof String ? ((String) os.get("id")).toLowerCase(Locale.ROOT) : null;
        String version = os.get("version") instanceof String ? (String) os.get("version") : null;
        String arch = os.get("arch") instanceof String ? (String) os.get("arch") : null;
        String product = "mswindows".equals(id) && os.get("productType") instanceof String
                ? (String) os.get("productType") : "mswindows".equals(id) ? null : "none";
        String expectedFamily = "mswindows".equals(id) ? "windows" : "linux";
        if (id == null || version == null || "unknown".equals(version) || arch == null
                || !expectedFamily.equals(os.get("family")) || product == null)
            return Map.of("status", "OS_UNKNOWN");
        if (!"x86_64".equals(arch) || !supported(id, product, version))
            return Map.of("status", "UNSUPPORTED_OS");
        if (!registeredMatches(registeredOsName, id, product, version))
            return Map.of("status", "OS_MISMATCH");
        try {
            if (raw.length() > 65536) throw new IllegalArgumentException("Catalog too large");
            JsonArray entries = JsonParser.parseString(raw).getAsJsonArray();
            if (entries.size() > 128) throw new IllegalArgumentException("Too many catalog entries");
            Map<String, String> match = null;
            Set<String> selectors = new HashSet<>();
            Set<String> legacySelectors = new HashSet<>();
            Set<String> majorSelectors = new HashSet<>();
            for (JsonElement element : entries) {
                JsonObject item = element.getAsJsonObject();
                String configuredZone = canonicalUuid(value(item, "zoneId"));
                String osId = value(item, "osId");
                String isoFamily = familyFor(osId);
                if (isoFamily == null) throw new IllegalArgumentException("Unsupported catalog OS");
                String configuredArch = value(item, "arch");
                String isoId = canonicalUuid(value(item, "isoId"));
                String packageVersion = value(item, "version");
                String sha = value(item, "sha256").toLowerCase(Locale.ROOT);
                if (!sha.matches("[0-9a-f]{64}") || !"x86_64".equals(configuredArch))
                    throw new IllegalArgumentException("Invalid Tools ISO identity");
                String sha512 = item.has("sha512") ? value(item, "sha512").toLowerCase(Locale.ROOT) : null;
                if (sha512 != null && !sha512.matches("[0-9a-f]{128}"))
                    throw new IllegalArgumentException("Invalid Tools ISO SHA-512");
                boolean legacy = item.has("osVersion");
                String configuredProduct;
                Set<String> versions = new HashSet<>();
                if (legacy) {
                    if (item.has("versions") || item.has("isoFamily") || item.has("productType"))
                        throw new IllegalArgumentException("Mixed catalog schema");
                    configuredProduct = "mswindows".equals(osId) ? "server" : "none";
                    String exact = value(item, "osVersion");
                    if (!supported(osId, configuredProduct, exact)) throw new IllegalArgumentException("Invalid legacy version");
                    versions.add(exact);
                } else {
                    if (!isoFamily.equals(value(item, "isoFamily")))
                        throw new IllegalArgumentException("ISO family does not match guest OS");
                    configuredProduct = value(item, "productType");
                    if (!item.has("versions") || !item.get("versions").isJsonArray())
                        throw new IllegalArgumentException("Missing versions");
                    JsonArray configuredVersions = item.getAsJsonArray("versions");
                    if (configuredVersions.size() == 0 || configuredVersions.size() > 16)
                        throw new IllegalArgumentException("Invalid versions");
                    for (JsonElement configuredVersion : configuredVersions) {
                        if (!configuredVersion.isJsonPrimitive() || !configuredVersion.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Invalid version");
                        String v = configuredVersion.getAsString();
                        if (!selectorVersion(osId, configuredProduct, v) || !versions.add(v))
                            throw new IllegalArgumentException("Duplicate or unsupported version");
                    }
                }
                for (String v : versions) {
                    String prefix = configuredZone + "/" + osId + "/" + configuredProduct + "/" + configuredArch + "/";
                    String selector = prefix + v;
                    if (!selectors.add(selector)) throw new IllegalArgumentException("Ambiguous Tools ISO mapping");
                    if (legacy && Set.of("rocky", "rhel", "debian").contains(osId)) {
                        if (majorSelectors.contains(prefix + major(v)))
                            throw new IllegalArgumentException("Legacy selector overlaps a major selector");
                        legacySelectors.add(selector);
                    } else if (Set.of("rocky", "rhel", "debian").contains(osId)) {
                        for (String exact : legacySelectors) {
                            if (exact.equals(selector) || exact.startsWith(selector + "."))
                                throw new IllegalArgumentException("Major selector overlaps a legacy selector");
                        }
                        majorSelectors.add(selector);
                    }
                    if (configuredZone.equals(zoneId) && osId.equals(id) && configuredProduct.equals(product)
                            && configuredArch.equals(arch) && matchesVersion(id, v, version, legacy)) {
                        if (match != null) throw new IllegalArgumentException("Ambiguous Tools ISO mapping");
                        java.util.Map<String, String> selected = new java.util.HashMap<>(Map.of(
                                "status", "MATCHED", "isoId", isoId, "version", packageVersion,
                                "sha256", sha, "zoneId", configuredZone, "arch", configuredArch, "isoFamily", isoFamily));
                        if (sha512 != null) selected.put("sha512", sha512);
                        match = Map.copyOf(selected);
                    }
                }
            }
            return match == null ? Map.of("status", "NO_MATCH") : match;
        } catch (RuntimeException ex) {
            return Map.of("status", "INVALID_CONFIG");
        }
    }
}
