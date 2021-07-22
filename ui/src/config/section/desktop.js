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
export default {
  name: 'desktop',
  title: 'Desktop Service',
  icon: 'cloud-server',
  children: [
    {
      name: 'desktop',
      title: 'Clusters',
      icon: 'block',
      docHelp: 'adminguide/virtual_machines.html',
      permission: ['listDesktopClusters'],
      columns: ['name', 'state', 'addomainname', 'account', 'zonename'],
      searchFilters: ['name', 'state'],
      details: ['name', 'id', 'description', 'controllerversion', 'account', 'addomainname', 'zonename', 'associatednetworkname', 'adminurl', 'userurl'],
      tabs: [{
        component: () => import('@/views/desktop/DesktopTab.vue')
      }],
      actions: [
        {
          api: 'createAccount',
          icon: 'plus',
          label: 'Deploy Cluster',
          docHelp: 'adminguide/virtual_machines.html#creating-vms',
          listView: true,
          popup: true,
          component: () => import('@/views/desktop/DeployCluster.vue')
        },
        {
          api: 'startVirtualMachine',
          icon: 'caret-right',
          label: 'Enable',
          message: '이 Cluster를 활성화 하시겠습니까?',
          docHelp: 'adminguide/virtual_machines.html#stopping-and-starting-vms',
          dataView: true,
          show: (record) => { return ['Stopped'].includes(record.state) }
        },
        {
          api: 'stopVirtualMachine',
          icon: 'poweroff',
          label: 'Disable',
          message: '이 Cluster를 비활성화 하시겠습니까?',
          docHelp: 'adminguide/virtual_machines.html#stopping-and-starting-vms',
          dataView: true,
          show: (record) => { return ['Running'].includes(record.state) }
        },
        {
          api: 'destroyVirtualMachine',
          icon: 'link',
          label: 'Works Portal Link',
          docHelp: 'adminguide/virtual_machines.html#deleting-vms',
          dataView: true,
          show: (record) => { return ['Running'].includes(record.state) }
        },
        {
          api: 'destroyVirtualMachine',
          icon: 'delete',
          label: 'Destroy Desktop',
          message: '이 Cluster를 삭제하시겠습니까?',
          docHelp: 'adminguide/virtual_machines.html#deleting-vms',
          dataView: true,
          popup: true,
          show: (record) => { return ['Running', 'Stopped'].includes(record.state) },
          component: () => import('@/views/desktop/DestroyCluster.vue')
        }
      ]
    },
    {
      name: 'desktoptemplate',
      title: 'Controller Templates',
      icon: 'shop',
      docHelp: 'adminguide/templates.html',
      permission: ['listDesktopControllerVersions'],
      columns: ['name', 'state', 'version', 'zonename'],
      details: ['name', 'description', 'version', 'templates'],
      searchFilters: ['name', 'zoneid', 'tags'],
      actions: [
        {
          api: 'registerTemplate',
          icon: 'plus',
          label: 'Controller Template Version',
          docHelp: 'adminguide/templates.html#uploading-templates-from-a-remote-http-server',
          listView: true,
          popup: true,
          component: () => import('@/views/desktop/AddDesktopControllerVersion.vue')
        },
        {
          api: 'updateTemplatePermissions',
          icon: 'edit',
          label: 'Manage Controller Version',
          dataView: true,
          popup: true,
          component: () => import('@/views/desktop/UpdateDesktopControllerVersion.vue')
        },
        {
          api: 'updateTemplate',
          icon: 'delete',
          label: 'Delete Controller Version',
          message: '이 Controller Template Version 을 삭제하시겠습니까?',
          dataView: true
        }
      ]
    },
    {
      name: 'mastertemplate',
      title: 'Master Templates',
      icon: 'hdd',
      docHelp: 'adminguide/templates.html',
      permission: ['listDesktopMasterVersions'],
      columns: ['name', 'state', 'version', 'zonename'],
      details: ['name', 'version', 'description', 'templatestate'],
      searchFilters: ['name', 'zoneid', 'tags'],
      actions: [
        {
          api: 'registerTemplate',
          icon: 'plus',
          label: 'Master Template Version',
          docHelp: 'adminguide/templates.html#uploading-templates-from-a-remote-http-server',
          listView: true,
          popup: true,
          component: () => import('@/views/desktop/AddDesktopMasterVersion.vue')
        },
        {
          api: 'updateTemplatePermissions',
          icon: 'edit',
          label: 'Manage Master Version',
          dataView: true,
          popup: true,
          component: () => import('@/views/desktop/UpdateDesktopMasterVersion.vue')
        },
        {
          api: 'updateTemplate',
          icon: 'delete',
          label: 'Delete Master Version',
          message: '이 Master Template Version 을 삭제하시겠습니까?',
          dataView: true
        }
      ]
    }
  ]
}
