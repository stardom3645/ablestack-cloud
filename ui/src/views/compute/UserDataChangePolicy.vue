<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License
for the specific language governing permissions and limitations. -->
<template>
  <details class="userdata-change-policy">
    <summary>{{ $t('label.userdata.clone.policy') }}</summary>
    <p>{{ $t('message.userdata.clone.policy') }}</p>
    <p>{{ $t('message.userdata.clone.apply.vm') }}</p>
    <p>{{ $t('message.userdata.clone.apply.template') }}</p>
    <p>{{ $t('message.userdata.clone.bom') }}</p>
    <p>{{ $t('message.userdata.clone.history.contract') }}</p>
  </details>
</template>
<style scoped>
.userdata-change-policy { margin: 12px 0; overflow-wrap: anywhere; }
summary { cursor: pointer; }
p { margin: 8px 0; }
</style>
