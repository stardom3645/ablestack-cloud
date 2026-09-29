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

import { matchesToolsIsoChecksum } from '@/views/compute/vmProcessToolsChecksum'

describe('Tools ISO metadata checksum', () => {
  const catalog = { sha256: 'a'.repeat(64), sha512: 'b'.repeat(128) }

  it('checks either digest format returned by Cloud', () => {
    expect(matchesToolsIsoChecksum('{SHA-256}' + 'A'.repeat(64), catalog)).toBe(true)
    expect(matchesToolsIsoChecksum('sha512:' + 'B'.repeat(128), catalog)).toBe(true)
    expect(matchesToolsIsoChecksum('b'.repeat(128), catalog)).toBe(true)
  })

  it('rejects missing, malformed or mismatched metadata', () => {
    expect(matchesToolsIsoChecksum('', catalog)).toBe(false)
    expect(matchesToolsIsoChecksum('c'.repeat(128), catalog)).toBe(false)
    expect(matchesToolsIsoChecksum('b'.repeat(128), { sha256: catalog.sha256 })).toBe(false)
    expect(matchesToolsIsoChecksum('{SHA-512}' + 'f'.repeat(64), catalog)).toBe(false)
  })
})
