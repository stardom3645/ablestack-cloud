// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

// Cloud ISO metadata may contain SHA-256 or SHA-512; the catalog records the
// manifest SHA-256 and, when needed, SHA-512 calculated from the same ISO.
export function matchesToolsIsoChecksum (checksum, catalog) {
  const value = String(checksum || '').replace(/^\{SHA-(256|512)\}/i, '').replace(/^sha(256|512):/i, '').toLowerCase()
  if (/^[0-9a-f]{64}$/.test(value)) return value === catalog?.sha256
  if (/^[0-9a-f]{128}$/.test(value)) return value === catalog?.sha512
  return false
}
