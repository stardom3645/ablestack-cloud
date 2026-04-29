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

<template>
  <a-modal
    :visible="showUploadModal"
    :closable="!uploadLoading"
    :destroyOnClose="true"
    :title="$t('label.upload')"
    :maskClosable="false"
    :cancelText="$t('label.cancel')"
    @cancel="closeUploadModal"
    :okText="$t('label.upload')"
    :confirmLoading="uploadLoading"
    :okButtonProps="{ disabled: uploadLoading || uploadFileList.length === 0 }"
    :cancelButtonProps="{ disabled: uploadLoading }"
    @ok="uploadFiles()"
    centered
    >
    <a-upload-dragger
      :multiple="true"
      :fileList="uploadFileList"
      listType="picture"
      :disabled="uploadLoading"
      :beforeUpload="beforeUpload"
      @remove="removeUploadFile">
      <p class="ant-upload-drag-icon">
        <cloud-upload-outlined />
      </p>
      <p class="ant-upload-text">
        {{ $t('label.volume.volumefileupload.description') }}
      </p>
    </a-upload-dragger>
    <a-divider dashed/>
    <tooltip-label bold :title="$t('label.upload.path')" :tooltip="$t('label.upload.description')"/>
    <br/>
    <a-input
      v-model:value="uploadDirectory"
      :placeholder="$t('label.upload.description')"
      :disabled="uploadLoading"
      enter-button/>
    <a-divider dashed/>
    <tooltip-label bold :title="$t('label.metadata')" :tooltip="$t('label.metadata.upload.description')"/>
    <KeyValuePairInput :pairs="uploadMetaData" @update-pairs="(pairs) => uploadMetaData = pairs" />
    <div v-if="uploadLoading || uploadTotalCount > 0" class="object-store-upload-progress">
      <a-progress
        :percent="uploadProgressPercent"
        :status="uploadFailedCount > 0 ? 'exception' : uploadLoading ? 'active' : 'success'" />
      <div class="object-store-upload-progress-text">
        업로드 {{ uploadCompletedCount }} / {{ uploadTotalCount }}
        <span v-if="uploadFailedCount > 0">, 실패 {{ uploadFailedCount }}</span>
      </div>
    </div>
  </a-modal>

  <a-drawer
    :visible="showObjectDetails"
    :closable="true"
    :maskClosable="true"
    @close="() => showObjectDetails = false"
    :title="record.name"
    >
    <div>
      <a-row justify="space-between">
        <a-col>
          <tooltip-label :title="$t('label.name')" bold/>
        </a-col>
        <a-col>
          {{ record.name.split('/').pop() }}
        </a-col>
      </a-row>
      <a-row justify="space-between">
        <a-col>
          <tooltip-label :title="$t('label.size')" bold/>
        </a-col>
        <a-col>
          {{ convertBytes(record.size) }}
        </a-col>
      </a-row>
      <a-row justify="space-between">
        <a-col>
          <tooltip-label :title="$t('label.last.updated')" bold/>
        </a-col>
        <a-col>
          {{ $toLocaleDate(record.lastModified) }}
        </a-col>
      </a-row>
      <a-row justify="space-between">
        <a-col>
          <tooltip-label :title="$t('label.url')" :tooltip="$t('label.object.url.description')" bold/>
        </a-col>
        <a-col>
          <a :href="record.url">{{ $t('label.link') }}</a>
        </a-col>
      </a-row>
      <a-row justify="space-between">
        <a-col>
          <tooltip-label :title="$t('label.object.presigned.url')" :tooltip="$t('label.object.presigned.url.description')" bold />
        </a-col>
        <a-col>
          <a :href="record.presignedUrl">{{ $t('label.link') }}</a>
        </a-col>
      </a-row>
        <a-divider>
          <tooltip-label :title="$t('label.metadata')" :tooltip="$t('label.metadata.description')"/>
        </a-divider>
        <template
          v-for="(value,key) in record.metadata"
          :key="key"
          >
          <a-row justify="space-between">
            <a-col>
              <tooltip-label :title="key" bold />
            </a-col>
            <a-col>
              {{ value }}
            </a-col>
          </a-row>
        </template>
    </div>
  </a-drawer>

  <div>
    <a-card class="breadcrumb-card">
      <a-row>
        <a-breadcrumb>
          <a-breadcrumb-item>
            <a @click="openDir('')">
              <HomeOutlined />
              <span v-if="getObjectStorePathRoutes().length === 0" class="object-store-root-path">/</span>
            </a>
          </a-breadcrumb-item>
          <a-breadcrumb-item
            v-for="route in getObjectStorePathRoutes()"
            :key="route.path">
            <a @click="openDir(route.path)">
              {{ route.breadcrumbName }}
            </a>
          </a-breadcrumb-item>
        </a-breadcrumb>
      </a-row>
      <a-divider/>
      <a-row class="object-store-usage-row" :gutter="[16, 8]">
        <a-col>
          <span class="object-store-usage-label">사용량</span>
          <span>{{ convertKB(resource.size || 0) }}</span>
        </a-col>
        <a-col v-if="resource.quota">
          <span class="object-store-usage-label">총용량</span>
          <span>{{ resource.quota }} GiB</span>
        </a-col>
      </a-row>
      <a-row :gutter="[10,10]" :wrap="true">
        <a-col flex="75%">
          <a-input-search
            allowClear
            size="medium"
            v-model:value="searchPrefix"
            :placeholder="$t('label.objectstore.search')"
            :loading="loading"
            @search="listObjects()"
            :enter-button="$t('label.search')"/>
        </a-col>
        <a-col flex="auto">
          <a-button
            :loading="loading"
            style="margin-bottom: 5px"
            shape="round"
            size="medium"
            @click="listObjects()">
            <reload-outlined />
            {{ $t('label.refresh') }}
          </a-button>
        </a-col>
        <a-col flex="auto">
          <a-button
            :loading="loading"
            style="margin-bottom: 5px"
            shape="round"
            size="medium"
            type="primary"
            @click="openUploadModal">
            <upload-outlined />
            {{ $t('label.upload') }}
          </a-button>
        </a-col>
        <a-col flex="auto">
          <tooltip-button
            type="primary"
            size="medium"
            icon="delete-outlined"
            :tooltip="$t('label.delete')"
            v-if="selectedRows.length > 0"
            :danger="true"
            @onClick="removeObjects()"/>
        </a-col>
      </a-row>
    </a-card>

    <div>
      <a-table
        :columns="columns"
        :row-key="record => record"
        :data-source="records"
        :loading="loading"
        :size="'small'"
        :pagination="{ current: page, pageSize: pageSize, total: total, showSizeChanger: false }"
        :row-selection="{ selectedRowsKeys: selectedRows, onChange: onSelectChange }"
        @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key == 'name'">
            <template v-if="record.name === undefined && record.prefix">
              <a @click="openDir(record.prefix)">
                <folder-outlined /> {{ record.prefix.replace(this.browserPath, '').replace('/', '') }}
              </a>
            </template>
            <template v-else>
              <a @click="showObjectDescription(record)">
                {{ record.name.split('/').pop() }}
              </a>
            </template>
          </template>
          <template v-else-if="column.key == 'size'">
            <template v-if="record.name !== undefined && !record.prefix">
              {{ convertBytes(record.size) }}
            </template>
          </template>
          <template v-else-if="column.key == 'lastModified' && record.lastModified">
            {{ $toLocaleDate(record.lastModified) }}
          </template>
        </template>
      </a-table>
    </div>
  </div>

</template>

<script>
import * as Minio from 'minio'
import { api } from '@/api'
import { genericCompare } from '@/utils/sort.js'
import InfoCard from '@/components/view/InfoCard'
import TooltipButton from '@/components/widgets/TooltipButton'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import KeyValuePairInput from '@/components/KeyValuePairInput'

const objectStorePresignedUrlExpiryConfigKey = 'objectstore.presigned.url.expiry.seconds'
const defaultObjectStorePresignedUrlExpirySeconds = 24 * 60 * 60

const normalizeObjectStorePath = path => {
  if (!path || path === '/') {
    return ''
  }
  path = String(path).replace(/^\/+/, '')
  return path.endsWith('/') ? path : `${path}/`
}

const pageSize = 10

export default {
  name: 'ObjectStoreBrowser',
  components: {
    InfoCard,
    TooltipButton,
    TooltipLabel,
    KeyValuePairInput
  },
  props: {
    resource: {
      type: Object,
      required: true
    },
    resourceType: {
      type: String,
      required: true
    }
  },
  data () {
    var columns = [
      {
        key: 'name',
        title: this.$t('label.name'),
        sorter: (a, b) => genericCompare(a?.name || '', b?.name || '')
      },
      {
        key: 'size',
        title: this.$t('label.size'),
        sorter: (a, b) => genericCompare(a?.size || '', b?.size || '')
      },
      {
        key: 'lastModified',
        title: this.$t('label.last.updated'),
        sorter: (a, b) => genericCompare(a?.lastModified || '', b?.lastModified || '')
      }
    ]
    return {
      client: null,
      loading: false,
      records: [],
      browserPath: normalizeObjectStorePath(this.$route.query.browserPath),
      pageSize,
      page: 1,
      pageStartAfterMap: { 1: '' },
      total: 0,
      columns: columns,
      selectedRows: [],
      searchPrefix: '',
      showUploadModal: false,
      uploadFileList: [],
      uploadDirectory: normalizeObjectStorePath(this.$route.query.browserPath),
      uploadMetaData: {},
      uploadLoading: false,
      uploadTotalCount: 0,
      uploadCompletedCount: 0,
      uploadFailedCount: 0,
      objectStorePresignedUrlExpirySeconds: defaultObjectStorePresignedUrlExpirySeconds,
      record: {},
      showObjectDetails: false,
      fetching: false
    }
  },
  computed: {
    uploadProgressPercent () {
      if (!this.uploadTotalCount) {
        return 0
      }
      return Math.round((this.uploadCompletedCount / this.uploadTotalCount) * 100)
    }
  },
  created () {
    this.fetchData()
    this.fetchObjectStorePresignedUrlExpirySeconds()
  },
  methods: {
    openUploadModal () {
      if (this.uploadLoading) {
        return
      }
      this.resetUploadForm()
      this.showUploadModal = true
    },
    closeUploadModal () {
      if (this.uploadLoading) {
        return
      }
      this.showUploadModal = false
      this.resetUploadForm()
    },
    resetUploadProgress () {
      this.uploadTotalCount = 0
      this.uploadCompletedCount = 0
      this.uploadFailedCount = 0
    },
    resetUploadForm () {
      this.uploadFileList = []
      this.uploadDirectory = this.browserPath
      this.uploadMetaData = {}
      this.resetUploadProgress()
    },
    handleTableChange (pagination, filters, sorter) {
      if (this.page !== pagination.current) {
        this.page = pagination.current
      }
    },
    fetchData () {
      this.loading = true
      this.records = []
      this.$router.replace(
        {
          query: {
            ...this.$route.query,
            browserPath: this.browserPath
          }
        }
      )
      if (!this.client) {
        this.initMinioClient()
      } else {
        this.listObjects()
      }
    },
    getObjectStorePathRoutes () {
      let path = ''
      const routeList = []
      for (const route of this.browserPath.split('/')) {
        if (route) {
          path = `${path}${route}/`
          routeList.push({
            path: path,
            breadcrumbName: route
          })
        }
      }
      return routeList
    },
    convertBytes (val) {
      if (val < 1024 * 1024) return `${(val / 1024).toFixed(2)} KB`
      if (val < 1024 * 1024 * 1024) return `${(val / 1024 / 1024).toFixed(2)} MB`
      if (val < 1024 * 1024 * 1024 * 1024) return `${(val / 1024 / 1024 / 1024).toFixed(2)} GB`
      if (val < 1024 * 1024 * 1024 * 1024 * 1024) return `${(val / 1024 / 1024 / 1024 / 1024).toFixed(2)} TB`
      return val
    },
    convertKB (val) {
      if (val < 1024) return `${Number(val).toFixed(2)} KB`
      if (val < 1024 * 1024) return `${(val / 1024).toFixed(2)} MB`
      if (val < 1024 * 1024 * 1024) return `${(val / 1024 / 1024).toFixed(2)} GB`
      if (val < 1024 * 1024 * 1024 * 1024) return `${(val / 1024 / 1024 / 1024).toFixed(2)} TB`
      return val
    },
    openDir (name) {
      const normalizedName = normalizeObjectStorePath(name)
      this.browserPath = normalizedName
      this.uploadDirectory = normalizedName
      this.page = 1
      this.fetchData()
    },
    listObjects () {
      while (this.fetching) {
        // sleep for 500ms
        setTimeout(() => {
          console.log('waiting for previous request to complete')
        }, 500)
      }
      this.fetching = true
      this.records = []
      var stream = this.client.extensions.listObjectsV2WithMetadata(this.resource.name, normalizeObjectStorePath(this.browserPath) + this.searchPrefix, false, '')
      stream.on('data', obj => {
        this.records.push(obj)
      })
      stream.on('end', obj => {
        this.total = this.records.length
        this.loading = false
        this.fetching = false
      })
      stream.on('error', err => {
        console.log(err)
        this.loading = false
        this.fetching = false
      })
    },
    removeObjects () {
      this.loading = true
      this.page = 1
      this.pageStartAfterMap = { 1: '' }
      const objectsToDelete = this.selectedRows.filter((row) => row.name).map((row) => row.name)
      const directoriesToDelete = this.selectedRows.filter((row) => row.prefix).map((row) => row.prefix)
      this.selectedRows = []
      this.removeDirectories(directoriesToDelete)
      if (objectsToDelete.length > 0) {
        this.client.removeObjects(this.resource.name, objectsToDelete, err => {
          if (err) {
            return this.$notification.error({
              message: this.$t('error.execute.api.failed'),
              description: err.message
            })
          }
          this.$notification.success({
            message: this.$t('label.delete'),
            description: this.$t('message.success.remove.objectstore.objects') + ' ' + objectsToDelete.length
          })
          this.listObjects()
        })
      }
    },
    removeDirectories (directoriesToDelete) {
      for (const directory of directoriesToDelete) {
        var objectsList = []
        const stream = this.client.listObjectsV2(this.resource.name, directory, true, '')
        stream.on('data', (obj) => {
          objectsList.push(obj.name)
        })

        stream.on('error', (err) => {
          console.log(err)
        })
        stream.on('end', (err) => {
          if (err) {
            return console.log(err)
          }
          this.client.removeObjects(this.resource.name, objectsList, err => {
            if (err) {
              return this.$notification.error({
                message: this.$t('error.execute.api.failed'),
                description: err.message
              })
            }
            this.$notification.success({
              message: this.$t('label.delete'),
              description: this.$t('message.success.remove.objectstore.directory') + ' ' + directory
            })
            console.log('Removed the objects successfully')
            this.listObjects()
          })
        })
      }
    },
    initMinioClient () {
      if (!this.client) {
        const url = /https?:\/\/([^/]+)\/?/.exec(this.resource.url.split(this.resource.name)[0])[1]
        const isHttps = /^https/.test(this.resource.url)
        this.client = new Minio.Client({
          endPoint: url.split(':')[0],
          port: url.split(':').length > 1 ? parseInt(url.split(':')[1]) : isHttps ? 443 : 80,
          useSSL: isHttps,
          accessKey: this.resource.accesskey,
          secretKey: this.resource.usersecretkey
        })
        this.listObjects()
      }
    },
    fetchObjectStorePresignedUrlExpirySeconds () {
      api('listConfigurations', { name: objectStorePresignedUrlExpiryConfigKey }).then(json => {
        const value = json?.listconfigurationsresponse?.configuration?.[0]?.value
        const expirySeconds = Number(value)
        if (Number.isFinite(expirySeconds) && expirySeconds > 0) {
          this.objectStorePresignedUrlExpirySeconds = Math.floor(expirySeconds)
        }
      }).catch(error => {
        console.warn(`Failed to load ${objectStorePresignedUrlExpiryConfigKey}`, error)
      })
    },
    getObjectStorePresignedUrlExpirySeconds () {
      const expirySeconds = Number(this.objectStorePresignedUrlExpirySeconds)
      if (!Number.isFinite(expirySeconds) || expirySeconds <= 0) {
        return defaultObjectStorePresignedUrlExpirySeconds
      }
      return Math.floor(expirySeconds)
    },
    onSelectChange (selectedRow) {
      this.selectedRows = selectedRow
    },
    beforeUpload (file) {
      if (this.uploadLoading) {
        return false
      }
      this.uploadFileList = [...this.uploadFileList, file]
      return false
    },
    removeUploadFile (file) {
      if (this.uploadLoading) {
        return false
      }
      const index = this.uploadFileList.indexOf(file)
      if (index < 0) {
        return true
      }
      const newFileList = this.uploadFileList.slice()
      newFileList.splice(index, 1)
      this.uploadFileList = newFileList
      return true
    },
    uploadFiles () {
      if (this.uploadLoading) {
        return
      }
      const files = [...this.uploadFileList]
      if (files.length === 0) {
        return
      }
      this.uploadDirectory = normalizeObjectStorePath(this.uploadDirectory)
      if (this.uploadDirectory && !this.uploadDirectory.endsWith('/')) {
        this.uploadDirectory = this.uploadDirectory + '/'
      }
      this.uploadLoading = true
      this.loading = true
      this.uploadTotalCount = files.length
      this.uploadCompletedCount = 0
      this.uploadFailedCount = 0
      const metadata = { ...this.uploadMetaData }
      const promises = files.map(file => {
        const objectName = this.uploadDirectory + file.name
        return this.asyncUploadFile(file, objectName, metadata)
          .catch(error => {
            this.uploadFailedCount++
            throw error
          })
          .finally(() => {
            this.uploadCompletedCount++
          })
      })
      Promise.allSettled(promises).then(results => {
        const failedCount = results.filter(result => result.status === 'rejected').length
        if (failedCount > 0) {
          this.$notification.error({
            message: this.$t('message.upload.failed'),
            description: `${failedCount} / ${files.length}`
          })
          return
        }
        this.showUploadModal = false
        this.resetUploadForm()
        this.listObjects()
      }).finally(() => {
        this.uploadLoading = false
        if (this.uploadFailedCount > 0) {
          this.loading = false
        }
      })
    },
    asyncUploadFile (file, objectName, metadata) {
      return new Promise((resolve, reject) => {
        file.arrayBuffer().then((buffer) => {
          this.client.putObject(this.resource.name, objectName, Buffer.from(buffer), file.size, metadata, err => {
            if (err) {
              return reject(this.$notification.error({
                message: this.$t('message.upload.failed'),
                description: err.message
              }))
            }
            return resolve(this.$notification.success({
              message: this.$t('message.success.upload'),
              description: objectName.split('/').pop()
            }))
          })
        }).catch(reject)
      })
    },
    showObjectDescription (record) {
      this.record = { ...record }
      this.record.url = this.resource.url + '/' + record.name
      this.client.presignedGetObject(this.resource.name, record.name, this.getObjectStorePresignedUrlExpirySeconds(), (err, presignedUrl) => {
        if (err) {
          return this.$notification.error({
            message: this.$t('error.execute.api.failed'),
            description: err.message
          })
        } else {
          this.record.presignedUrl = presignedUrl
        }
        this.showObjectDetails = true
      })
    },
    updateMetadata () {
      this.client.copyObject(
        new Minio.CopySourceOptions({ Bucket: this.resource.name, Object: this.record.name }),
        new Minio.CopyDestinationOptions({ Bucket: this.resource.name, Object: this.record.name, MetadataDirective: 'REPLACE', UserMetadata: this.record.metadata }),
        err => {
          if (err) {
            this.$notification.error({
              message: this.$t('error.execute.api.failed'),
              description: err.message
            })
          }
          this.$notification.success({
            message: this.$t('label.metadata'),
            description: this.$t('message.update.success')
          })
          this.listObjects()
        })
    }
  }
}
</script>

<style scoped>
.object-store-root-path {
  margin-left: 4px;
}

.object-store-usage-row {
  margin-bottom: 12px;
}

.object-store-usage-label {
  color: rgba(0, 0, 0, 0.65);
  font-weight: 600;
  margin-right: 6px;
}

.object-store-upload-progress {
  margin-top: 16px;
}

.object-store-upload-progress-text {
  color: rgba(0, 0, 0, 0.65);
  font-size: 12px;
  margin-top: 4px;
}
</style>
