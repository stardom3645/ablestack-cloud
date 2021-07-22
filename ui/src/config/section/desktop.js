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
      title: 'title.desktop.cluster',
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
          label: 'label.desktop.cluster.deploy',
          docHelp: 'adminguide/virtual_machines.html#creating-vms',
          listView: true,
          popup: true,
          component: () => import('@/views/desktop/DeployCluster.vue')
        },
        {
          api: 'startVirtualMachine',
          icon: 'caret-right',
          label: 'Enable',
          message: 'message.desktop.cluster.enable',
          docHelp: 'adminguide/virtual_machines.html#stopping-and-starting-vms',
          dataView: true,
          show: (record) => { return ['Stopped'].includes(record.state) }
        },
        {
          api: 'stopVirtualMachine',
          icon: 'poweroff',
          label: 'Disable',
          message: 'message.desktop.cluster.disable',
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
          label: 'label.desktop.cluster.delete',
          message: 'message.desktop.cluster.delete',
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
      title: 'title.desktop.controller',
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
          label: 'label.desktop.controller.template.version',
          docHelp: 'adminguide/templates.html#uploading-templates-from-a-remote-http-server',
          listView: true,
          popup: true,
          component: () => import('@/views/desktop/AddDesktopControllerVersion.vue')
        },
        {
          api: 'updateTemplatePermissions',
          icon: 'edit',
          label: 'label.desktop.controller.version.manage',
          dataView: true,
          popup: true,
          component: () => import('@/views/desktop/UpdateDesktopControllerVersion.vue')
        },
        {
          api: 'updateTemplate',
          icon: 'delete',
          label: 'label.desktop.controller.version.delete',
          message: 'message.desktop.controller.version.delete',
          dataView: true
        }
      ]
    },
    {
      name: 'mastertemplate',
      title: 'title.desktop.master',
      icon: 'hdd',
      docHelp: 'adminguide/templates.html',
      permission: ['listDesktopMasterVersions'],
      columns: ['name', 'state', 'version', 'zonename'],
      details: ['name', 'version', 'description', 'templateostype', 'templatestate'],
      searchFilters: ['name', 'zoneid', 'tags'],
      actions: [
        {
          api: 'registerTemplate',
          icon: 'plus',
          label: 'label.desktop.master.template.version',
          docHelp: 'adminguide/templates.html#uploading-templates-from-a-remote-http-server',
          listView: true,
          popup: true,
          component: () => import('@/views/desktop/AddDesktopMasterVersion.vue')
        },
        {
          api: 'updateTemplatePermissions',
          icon: 'edit',
          label: 'label.desktop.master.version.manage',
          dataView: true,
          popup: true,
          component: () => import('@/views/desktop/UpdateDesktopMasterVersion.vue')
        },
        {
          api: 'updateTemplate',
          icon: 'delete',
          label: 'label.desktop.master.version.delete',
          message: 'message.desktop.master.veresion.delete',
          dataView: true
        }
      ]
    }
  ]
}
