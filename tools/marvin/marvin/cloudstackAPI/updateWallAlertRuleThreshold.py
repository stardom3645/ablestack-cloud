# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.


"""Updates a Wall(Grafana) alert rule threshold"""
from .baseCmd import *
from .baseResponse import *
class updateWallAlertRuleThresholdCmd (baseCmd):
    typeInfo = {}
    def __init__(self):
        self.isAsync = "false"
        """Threshold operator (gt, lt, between, outside)"""
        """Required"""
        self.operator = None
        self.typeInfo['operator'] = 'string'
        """New threshold value (single-threshold operators)"""
        """Required"""
        self.threshold = None
        self.typeInfo['threshold'] = 'double'
        """권장 방식: Grafana alert rule UID"""
        """Required"""
        self.uid = None
        self.typeInfo['uid'] = 'string'
        """Rule key in 'group:title' format"""
        self.id = None
        self.typeInfo['id'] = 'string'
        """Upper threshold value (only for between/outside operators)"""
        self.threshold2 = None
        self.typeInfo['threshold2'] = 'double'
        self.required = ["operator","threshold","uid",]

class updateWallAlertRuleThresholdResponse (baseResponse):
    typeInfo = {}
    def __init__(self):
        """Rule UID"""
        self.id = None
        self.typeInfo['id'] = 'string'
        """Annotations"""
        self.annotations = None
        self.typeInfo['annotations'] = 'map'
        """Number of breached targets"""
        self.breachedCount = None
        self.typeInfo['breachedCount'] = 'integer'
        """Targets that breach the threshold/operator"""
        self.breachedTargets = None
        self.typeInfo['breachedTargets'] = 'list'
        """Number of samples used for currentValue"""
        self.currentCount = None
        self.typeInfo['currentCount'] = 'integer'
        """Per-target evaluated values (key/value/breached). For binary(0/1) rules, may include only breached targets."""
        self.currentTargets = None
        self.typeInfo['currentTargets'] = 'list'
        """Current evaluated value (avg across instances)"""
        self.currentValue = None
        self.typeInfo['currentValue'] = 'double'
        """Related dashboard URL"""
        self.dashboardurl = None
        self.typeInfo['dashboardurl'] = 'string'
        """Description (HTML)"""
        self.description = None
        self.typeInfo['description'] = 'string'
        """Evaluation time (sec)"""
        self.evaluationTime = None
        self.typeInfo['evaluationTime'] = 'double'
        """Number of firing instances"""
        self.firingCount = None
        self.typeInfo['firingCount'] = 'integer'
        """Evaluation duration, e.g. 300s"""
        setattr(self, 'for', None)
        self.typeInfo['for'] = 'string'
        """Rule health (ok|nodata|error)"""
        self.health = None
        self.typeInfo['health'] = 'string'
        """Alert instances from rules API (labels/state/value/activeAt)"""
        self.instances = None
        self.typeInfo['instances'] = 'list'
        """Whether the rule is paused (grafana_alert.isPaused)"""
        self.isPaused = None
        self.typeInfo['isPaused'] = 'boolean'
        """Paused state as string for table rendering (Paused|Active)"""
        self.ispaused = None
        self.typeInfo['ispaused'] = 'string'
        """Kind label (optional)"""
        self.kind = None
        self.typeInfo['kind'] = 'string'
        """Labels"""
        self.labels = None
        self.typeInfo['labels'] = 'map'
        """Last evaluation RFC3339"""
        self.lastEvaluation = None
        self.typeInfo['lastEvaluation'] = 'string'
        """Latest activeAt in KST (yyyy-MM-dd HH:mm)"""
        self.lastTriggeredAt = None
        self.typeInfo['lastTriggeredAt'] = 'string'
        """Rule title"""
        self.name = None
        self.typeInfo['name'] = 'string'
        """Operator (optional)"""
        self.operator = None
        self.typeInfo['operator'] = 'string'
        """Panel id (from annotations.__panelId__)"""
        self.panel = None
        self.typeInfo['panel'] = 'string'
        """Number of pending instances"""
        self.pendingCount = None
        self.typeInfo['pendingCount'] = 'integer'
        """Query expression"""
        self.query = None
        self.typeInfo['query'] = 'string'
        """Rule group"""
        self.rulegroup = None
        self.typeInfo['rulegroup'] = 'string'
        """Grafana alert rule UID (__alert_rule_uid__)"""
        self.ruleUid = None
        self.typeInfo['ruleUid'] = 'string'
        """Whether this rule is currently silenced by Alertmanager"""
        self.silenced = None
        self.typeInfo['silenced'] = 'boolean'
        """Silence ends at (KST yyyy-MM-dd HH:mm)"""
        self.silenceEndsAt = None
        self.typeInfo['silenceEndsAt'] = 'string'
        """Silence period (KST, 'start ~ end')"""
        self.silencePeriod = None
        self.typeInfo['silencePeriod'] = 'string'
        """Silence starts at (KST yyyy-MM-dd HH:mm)"""
        self.silenceStartsAt = None
        self.typeInfo['silenceStartsAt'] = 'string'
        """Aggregated state (ALERTING|PENDING|OK|NODATA)"""
        self.state = None
        self.typeInfo['state'] = 'string'
        """Summary (HTML)"""
        self.summary = None
        self.typeInfo['summary'] = 'string'
        """Threshold value (optional)"""
        self.threshold = None
        self.typeInfo['threshold'] = 'double'
        """Upper threshold (for between/outside operators)"""
        self.threshold2 = None
        self.typeInfo['threshold2'] = 'double'
        """Rule type (alerting|recording)"""
        self.type = None
        self.typeInfo['type'] = 'string'
        """Grafana rule UID (stable unique key)"""
        self.uid = None
        self.typeInfo['uid'] = 'string'
