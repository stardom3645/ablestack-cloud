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

export const ruleUid = rule => rule?.uid || rule?.metadata?.rule_uid || rule?.ruleUid || rule?.id

export function ruleIsPaused (rule) {
  if (typeof rule?.isPaused === 'boolean') return rule.isPaused
  return /^(true|paused|stopped)$/i.test(String(rule?.ispaused || ''))
}

export function ruleIsSilenced (rule) {
  if (typeof rule?.silenced === 'boolean') return rule.silenced
  // The API's display timestamps are in KST, regardless of the browser timezone.
  let end = rule?.silenceEndsAt || ''
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(end)) end = end.replace(' ', 'T') + ':00+09:00'
  return Date.parse(end) > Date.now()
}

export function uniqueRules (rules) {
  const seen = new Set()
  return rules.filter(rule => {
    const uid = ruleUid(rule)
    if (!uid || seen.has(uid)) return false
    seen.add(uid)
    return true
  })
}

export function eligibleRules (rules, action) {
  return uniqueRules(rules).filter(rule => {
    if (action === 'pause') return !ruleIsPaused(rule)
    if (action === 'resume') return ruleIsPaused(rule)
    if (action === 'silence') return !ruleIsSilenced(rule)
    return false
  })
}

// Fetch every page before presenting an all-rules confirmation. Search filters
// and the current list page must never limit an operation labelled "all".
export async function fetchAllRules (request) {
  const rules = []
  const seen = new Set()
  for (let page = 1; ; page++) {
    const json = await request('listWallAlertRules', { page, pagesize: 100 })
    const response = json?.listwallalertrulesresponse
    // CloudStack serializes an empty ListResponse as an empty object.
    const count = response && Object.keys(response).length === 0 ? 0 : Number(response?.count)
    const rows = response?.wallalertruleresponse || []
    if (!response || !Number.isInteger(count) || count < 0 || !Array.isArray(rows)) throw new Error('Invalid rule list')
    for (const rule of rows) {
      const uid = ruleUid(rule)
      if (!uid || seen.has(uid)) throw new Error('Incomplete rule list')
      seen.add(uid)
      rules.push(rule)
    }
    if (rules.length === count) return rules
    if (rules.length > count) throw new Error('Invalid rule count')
    if (!rows.length) throw new Error('Incomplete rule list')
  }
}

// Continue after individual failures, preserving failed targets for retry.
export async function applyRuleAction (request, rules, action, durationMinutes, onProgress = () => {}) {
  const succeeded = []
  const failed = []
  for (const rule of uniqueRules(rules)) {
    try {
      const uid = ruleUid(rule)
      if (action === 'silence') {
        await request('createWallAlertSilence', {
          'labels[0].key': '__alert_rule_uid__',
          'labels[0].value': uid,
          durationMinutes,
          comment: `action:silence:${durationMinutes}m`
        })
      } else if (action === 'pause' || action === 'resume') {
        await request('pauseWallAlertRule', { id: uid, paused: action === 'pause' })
      } else {
        throw new Error('Unsupported rule action')
      }
      succeeded.push(rule)
    } catch (error) {
      failed.push({ rule, error })
    }
    onProgress(succeeded.length + failed.length)
  }
  return { succeeded, failed }
}
