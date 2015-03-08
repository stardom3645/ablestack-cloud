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
""" P1 tests for Browser Based Upload Volumes
"""
# Import Local Modules

import marvin
from nose.plugins.attrib import attr
from marvin.cloudstackTestCase import cloudstackTestCase, unittest
from marvin.cloudstackAPI import *
from marvin.lib.utils import *
from marvin.lib.base import *
from marvin.lib.common import *
from marvin.codes import PASS,FAILED,SUCCESS,XEN_SERVER

from marvin.sshClient import SshClient

import requests

import wget

import random

import string

import telnetlib
import os
import urllib
import time
import tempfile
_multiprocess_shared_ = True

class TestBrowseUploadVolume(cloudstackTestCase):

    """
    Testing Browse Upload Volume Feature
    """
    @classmethod
    def setUpClass(cls):
        cls.testClient = super(TestBrowseUploadVolume,cls).getClsTestClient()
        #print cls.testClient.getParsedTestDataConfig()
        cls.testdata = cls.testClient.getParsedTestDataConfig()
        cls.apiclient = cls.testClient.getApiClient()
        cls.hypervisor = cls.testClient.getHypervisorInfo()
        cls._cleanup = []
        cls.cleanup = []
        cls.uploadvolumeformat="VHD"
        cls.storagetype = 'shared'

        hosts = list_hosts(
            cls.apiclient,
            type="Routing"
        )

        if hosts is None:
            raise unittest.SkipTest(
                "There are no hypervisor's available.Check listhosts response")
        for hypervisorhost in hosts :
                 if hypervisorhost.hypervisor == "XenServer":
                     cls.uploadvolumeformat="VHD"
                     break
                 elif hypervisorhost.hypervisor== "VMware":
                     cls.uploadvolumeformat="OVA"
                     break
                 elif hypervisorhost.hypervisor=="KVM":
                     cls.uploadvolumeformat="QCOW2"
                     break
                 else:
                     break

        cls.uploadurl=cls.testdata["browser_upload_volume"][cls.uploadvolumeformat]["url"]
        cls.volname=cls.testdata["browser_upload_volume"][cls.uploadvolumeformat]["diskname"]
        cls.md5sum=cls.testdata["browser_upload_volume"][cls.uploadvolumeformat]["checksum"]
        cls.zone = get_zone(cls.apiclient, cls.testClient.getZoneForTests())
        cls.domain = get_domain(cls.apiclient)
        cls.pod = get_pod(cls.apiclient, cls.zone.id)

        cls.account = Account.create(
            cls.apiclient,
            cls.testdata["account"],
            domainid=cls.domain.id
        )

        cls.template = get_template(
            cls.apiclient,
            cls.zone.id)

        if cls.template == FAILED:
                raise unittest.SkipTest(
                    "Check for default cent OS template readiness ")
        cls.service_offering = ServiceOffering.create(
            cls.apiclient, 
            cls.testdata["service_offering"]
        )
        cls.disk_offering = DiskOffering.create(
            cls.apiclient,
            cls.testdata["browser_upload_volume"]["browser_resized_disk_offering"],
            custom=True
        )
        cls._cleanup = [
            cls.account,
            cls.service_offering,
            cls.disk_offering
        ]



    def __verify_values(self, expected_vals, actual_vals):

        return_flag = True

        if len(expected_vals) != len(actual_vals):
            return False

        keys = expected_vals.keys()
        for i in range(0, len(expected_vals)):
            exp_val = expected_vals[keys[i]]
            act_val = actual_vals[keys[i]]
            if exp_val == act_val:
                return_flag = return_flag and True
            else:
                return_flag = return_flag and False
                self.debug(
                    "expected Value: %s, is not matching with actual value:\
                    %s" %
                    (exp_val, act_val))
        return return_flag

    def validate_uploaded_volume(self,up_volid,volumestate):

        list_volume_response = Volume.list(
                    self.apiclient,
                    id=up_volid
                )
        self.assertNotEqual(
                    list_volume_response,
                    None,
                    "Check if volume exists in ListVolumes"
                )

        self.assertEqual(
                    list_volume_response[0].state,
                    volumestate,
                    "Check volume state in ListVolumes"
                )

    def browse_upload_volume(self):
        cmd = getUploadParamsForVolume.getUploadParamsForVolumeCmd()
        cmd.zoneid = self.zone.id
        cmd.format = self.uploadvolumeformat
        cmd.name=self.volname+self.account.name+(random.choice(string.ascii_uppercase))
        cmd.account=self.account.name
        cmd.domainid=self.domain.id
        getuploadparamsresponce=self.apiclient.getUploadParamsForVolume(cmd)

        signt=getuploadparamsresponce.signature
        posturl=getuploadparamsresponce.postURL
        metadata=getuploadparamsresponce.metadata
        expiredata=getuploadparamsresponce.expires
        #url = 'http://10.147.28.7/templates/rajani-thin-volume.vhd'
        url=self.uploadurl

        uploadfile = url.split('/')[-1]
        r = requests.get(url, stream=True)
        with open(uploadfile, 'wb') as f:
            for chunk in r.iter_content(chunk_size=1024): 
                if chunk: # filter out keep-alive new chunks
                    f.write(chunk)
                    f.flush()

        #uploadfile='rajani-thin-volume.vhd'

        #files={'file':('rajani-thin-volume.vhd',open(uploadfile,'rb'),'application/octet-stream')}

        #headers={'X-signature':signt,'X-metadata':metadata,'X-expires':expiredata}

        files={'file':(uploadfile,open(uploadfile,'rb'),'application/octet-stream')}

        headers={'X-signature':signt,'X-metadata':metadata,'X-expires':expiredata}

        results = requests.post(posturl,files=files,headers=headers,verify=False)
        time.sleep(60)

        print results.status_code
        if results.status_code !=200: 
            self.fail("Upload is not fine")

        self.validate_uploaded_volume(getuploadparamsresponce.id,'Uploaded')

        return(getuploadparamsresponce)

    def browse_upload_volume_with_md5(self):
        cmd = getUploadParamsForVolume.getUploadParamsForVolumeCmd()
        cmd.zoneid = self.zone.id
        cmd.format = self.uploadvolumeformat
        cmd.name=self.volname+self.account.name+(random.choice(string.ascii_uppercase))
        cmd.account=self.account.name
        cmd.domainid=self.domain.id
        cmd.checksum=self.md5sum
        getuploadparamsresponce=self.apiclient.getUploadParamsForVolume(cmd)

        signt=getuploadparamsresponce.signature
        posturl=getuploadparamsresponce.postURL
        metadata=getuploadparamsresponce.metadata
        expiredata=getuploadparamsresponce.expires
        #url = 'http://10.147.28.7/templates/rajani-thin-volume.vhd'
        url=self.uploadurl

        uploadfile = url.split('/')[-1]
        r = requests.get(url, stream=True)
        with open(uploadfile, 'wb') as f:
            for chunk in r.iter_content(chunk_size=1024): 
                if chunk: # filter out keep-alive new chunks
                    f.write(chunk)
                    f.flush()

        #uploadfile='rajani-thin-volume.vhd'

        #files={'file':('rajani-thin-volume.vhd',open(uploadfile,'rb'),'application/octet-stream')}

        #headers={'X-signature':signt,'X-metadata':metadata,'X-expires':expiredata}

        files={'file':(uploadfile,open(uploadfile,'rb'),'application/octet-stream')}

        headers={'X-signature':signt,'X-metadata':metadata,'X-expires':expiredata}

        results = requests.post(posturl,files=files,headers=headers,verify=False)
        time.sleep(60)

        print results.status_code
        if results.status_code !=200: 
            self.fail("Upload is not fine")

        self.validate_uploaded_volume(getuploadparamsresponce.id,'Uploaded')

        return(getuploadparamsresponce)

    def validate_vm(self,vmdetails,vmstate):

        time.sleep(120 )
        vm_response = VirtualMachine.list(
                self.apiclient,
                id=vmdetails.id,
            )
        self.assertEqual(
                isinstance(vm_response, list),
                True,
                "Check list VM response for valid list"
            )

            # Verify VM response to check whether VM deployment was successful
        self.assertNotEqual(
                len(vm_response),
                0,
                "Check VMs available in List VMs response"
            )

        deployedvm = vm_response[0]
        self.assertEqual(
                deployedvm.state,
                vmstate,
                "Check the state of VM"
            )

    def deploy_vm(self):
            virtual_machine = VirtualMachine.create(
                                                    self.apiclient,
                                                    self.testdata["virtual_machine"],
                                                    templateid=self.template.id,
                                                    zoneid=self.zone.id,
                                                    accountid=self.account.name,
                                                    domainid=self.account.domainid,
                                                    serviceofferingid=self.service_offering.id,
                                                )
            self.validate_vm(virtual_machine,'Running')
            return(virtual_machine)

    def attach_volume(self,vmlist,volid):

        list_volume_response = Volume.list(
                    self.apiclient,
                    id=volid
                )
        print list_volume_response[0]
        vmlist.attach_volume(
                    self.apiclient,
                    list_volume_response[0]
                )
        list_volume_response = Volume.list(
                self.apiclient,
                virtualmachineid=vmlist.id,
                type='DATADISK',
                listall=True
            )
        self.assertNotEqual(
                list_volume_response,
                None,
                "Check if volume exists in ListVolumes")
        self.assertEqual(
                isinstance(list_volume_response, list),
                True,
                "Check list volumes response for valid list")
        self.validate_uploaded_volume(volid,'Ready')


    def reboot_vm(self,vmdetails):
        vmdetails.reboot(self.apiclient)
        self.validate_vm(vmdetails,'Running')

    def stop_vm(self,vmdetails):
        vmdetails.stop(self.apiclient)
        self.validate_vm(vmdetails,'Stopped')

    def start_vm(self,vmdetails):
        vmdetails.start(self.apiclient)
        self.validate_vm(vmdetails,'Running')

    def vmoperations(self,vmdetails):
        self.reboot_vm(vmdetails)

        self.stop_vm(vmdetails)

        self.start_vm(vmdetails)


    def detach_volume(self,vmdetails,volid):
        """Detach a Volume attached to a VM
        """
        list_volume_response = Volume.list(
                    self.apiclient,
                    id=volid
                )
        print list_volume_response[0]
        vmdetails.detach_volume(self.apiclient,list_volume_response[0])

        # Sleep to ensure the current state will reflected in other calls
        time.sleep(self.testdata["sleep"])

        list_volume_response = Volume.list(
            self.apiclient,
            id=volid
        )
        self.assertNotEqual(
            list_volume_response,
            None,
            "Check if volume exists in ListVolumes"
        )
        self.assertEqual(
            isinstance(list_volume_response, list),
            True,
            "Check list volumes response for valid list"
        )
        volume = list_volume_response[0]
        self.assertEqual(
            volume.virtualmachineid,
            None,
            "Check if volume state (detached) is reflected"
        )

        self.assertEqual(
            volume.vmname,
            None,
            "Check if volume state (detached) is reflected"
        )
        return


    def restore_vm(self,vmdetails):
        #TODO: SIMENH: add another test the data on the restored VM.
        """Test recover Virtual Machine
        """

        #cmd = recoverVirtualMachine.recoverVirtualMachineCmd()
        cmd = restoreVirtualMachine.restoreVirtualMachineCmd()
        cmd.virtualmachineid = vmdetails.id
        self.apiclient.recoverVirtualMachine(cmd)

        list_vm_response = VirtualMachine.list(
                                            self.apiclient,
                                            id=vmdetails.id
                                            )
        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )

        self.assertNotEqual(
                            len(list_vm_response),
                            0,
                            "Check VM available in List Virtual Machines"
                        )

        self.assertEqual(
                            list_vm_response[0].state,
                            "Running",
                            "Check virtual machine is in Running state"
                        )

        return

    def deletevolume_fail(self,volumeid):
        """Delete a Volume attached to a VM
        """

        cmd = deleteVolume.deleteVolumeCmd()
        cmd.id = volumeid
        success= False
        try:
            self.apiclient.deleteVolume(cmd)
        except Exception as ex:
            if "Please specify a volume that is not attached to any VM" in str(ex):
                success = True
        self.assertEqual(
                success,
                True,
                "DeleteVolume - verify Ready State volume (attached to a VM) is handled appropriately not to get deleted ")

        return

    def deletevolume(self,volumeid):
        """Delete a Volume attached to a VM
        """

        cmd = deleteVolume.deleteVolumeCmd()
        cmd.id = volumeid

        self.apiclient.deleteVolume(cmd)

        list_volume_response = Volume.list(
                                            self.apiclient,
                                            id=volumeid,
                                            type='DATADISK'
                                            )
        self.assertEqual(
                        list_volume_response,
                        None,
                        "Check if volume exists in ListVolumes"
                    )
        return

    def download_volume(self,volumeid):

        cmd = extractVolume.extractVolumeCmd()
        cmd.id = volumeid
        cmd.mode = "HTTP_DOWNLOAD"
        cmd.zoneid = self.zone.id
        extract_vol = self.apiclient.extractVolume(cmd)

        try:
            formatted_url = urllib.unquote_plus(extract_vol.url)
            self.debug("Attempting to download volume at url %s" % formatted_url)
            response = urllib.urlopen(formatted_url)
            self.debug("response from volume url %s" % response.getcode())
            fd, path = tempfile.mkstemp()
            self.debug("Saving volume %s to path %s" %(volumeid, path))
            os.close(fd)
            with open(path, 'wb') as fd:
                fd.write(response.read())
            self.debug("Saved volume successfully")
        except Exception:
            self.fail(
                "Extract Volume Failed with invalid URL %s (vol id: %s)" \
                % (extract_vol.url, volumeid)
            )



    def resize_fail(self,volumeid):

        cmd                = resizeVolume.resizeVolumeCmd()
        cmd.id             = volumeid
        cmd.diskofferingid = self.disk_offering.id
        success            = False
        try:
            self.apiclient.resizeVolume(cmd)
        except Exception as ex:
            if "Volume should be in ready or allocated state before attempting a resize" in str(ex):
                success = True
        self.assertEqual(
                success,
                True,
                "ResizeVolume - verify Uploaded State volume is handled appropriately")


    def resize_volume(self,volumeid):

        """Test resize a volume"""

        self.testdata["browser_upload_volume"]["browser_resized_disk_offering"]["disksize"] = 20

        disk_offering_20_GB = DiskOffering.create(
                                    self.apiclient,
                                    self.testdata["browser_upload_volume"]["browser_resized_disk_offering"]
                                    )
        self.cleanup.append(disk_offering_20_GB)

        cmd= resizeVolume.resizeVolumeCmd()
        cmd.id= volumeid
        cmd.diskofferingid = disk_offering_20_GB.id

        self.apiclient.resizeVolume(cmd)

        count = 0
        success = False
        while count < 3:
            list_volume_response = Volume.list(
                                                self.apiclient,
                                                id=volumeid,
                                                type='DATADISK'
                                                )
            for vol in list_volume_response:
                if vol.id == volumeid and int(vol.size) == (int(disk_offering_20_GB.disksize) * (1024** 3)) and vol.state == 'Ready':
                    success = True
            if success:
                break
            else:
                time.sleep(10)
                count += 1

        self.assertEqual(
                         success,
                         True,
                         "Check if the data volume resized appropriately"
                         )

        return


    def destroy_vm(self,vmdetails):

        vmdetails.delete(self.apiclient, expunge=False)

        list_vm_response = VirtualMachine.list(
                                            self.apiclient,
                                            id=vmdetails.id
                                            )
        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )

        self.assertNotEqual(
                            len(list_vm_response),
                            0,
                            "Check VM available in List Virtual Machines"
                        )

        self.assertEqual(
                            list_vm_response[0].state,
                            "Destroyed",
                            "Check virtual machine is in destroyed state"
                        )
        return


    def recover_destroyed_vm(self,vmdetails):

        cmd = recoverVirtualMachine.recoverVirtualMachineCmd()
        cmd.id = vmdetails.id
        self.apiclient.recoverVirtualMachine(cmd)

        list_vm_response = VirtualMachine.list(
                                            self.apiclient,
                                            id=vmdetails.id
                                            )
        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )

        self.assertNotEqual(
                            len(list_vm_response),
                            0,
                            "Check VM available in List Virtual Machines"
                        )

        self.assertEqual(
                            list_vm_response[0].state,
                            "Stopped",
                            "Check virtual machine is in Stopped state"
                        )

        return

    def volume_snapshot(self,volumedetails):
        """
        @summary: Test to verify creation of snapshot from volume
        and creation of template, volume from snapshot
        """

        list_volumes = Volume.list(
            self.apiclient,
            id=volumedetails.id
        )
        # Creating Snapshot from volume
        snapshot_created = Snapshot.create(
            self.apiclient,
            volumedetails.id,
        )

        self.assertIsNotNone(snapshot_created, "Snapshot not created")

        self.cleanup.append(snapshot_created)

        # Creating expected and actual values dictionaries
        expected_dict = {
            "id": volumedetails.id,
            "intervaltype": "MANUAL",
            "snapshottype": "MANUAL",
            "volumetype": list_volumes[0].type,
            "domain": self.domain.id
        }
        actual_dict = {
            "id": snapshot_created.volumeid,
            "intervaltype": snapshot_created.intervaltype,
            "snapshottype": snapshot_created.snapshottype,
            "volumetype": snapshot_created.volumetype,
            "domain": snapshot_created.domainid,
        }
        status = self.__verify_values(
            expected_dict,
            actual_dict
        )
        self.assertEqual(
            True,
            status,
            "Snapshot created from Volume details are not as expected"
        )
        return(snapshot_created)

    def volume_snapshot_volume(self,snapshot_created):

        # Creating Volume from snapshot
        cmd = createVolume.createVolumeCmd()
        cmd.name = "-".join([self.testdata["volume"]
                             ["diskname"], random_gen()])
        cmd.snapshotid = snapshot_created.id

        volume_from_snapshot = Volume(
            self.apiclient.createVolume(cmd).__dict__)

        self.assertIsNotNone(
            volume_from_snapshot,
            "Volume creation failed from snapshot"
        )

        # Creating expected and actual values dictionaries
        #expected_dict = {
            #"snapshotid": snapshot_created.id,
            #"volumetype": snapshot_created.volumetype,
           # "size": self.disk_offering.disksize,
          #  "storagetype": self.storagetype,
         #   "zone": self.zone.id
        #}
        #actual_dict = {
         #   "snapshotid": volume_from_snapshot.snapshotid,
         #   "volumetype": volume_from_snapshot.type,
          #  "size": volume_from_snapshot.size / (1024 * 1024 * 1024),
           # "storagetype": volume_from_snapshot.storagetype,
            #"zone": volume_from_snapshot.zoneid,
        #}
        #status = self.__verify_values(
         #   expected_dict,
          #  actual_dict
        #)
        #self.assertEqual(
         #   True,
          #  status,
           # "Volume created from Snapshot details are not as expected"
        #)
        return

    def volume_snapshot_template(self,snapshot_created):
        # Creating Template from Snapshot
        list_templates_before = Template.list(
            self.apiclient,
            templatefilter='self')

        if list_templates_before is None:
            templates_before_size = 0
        else:
            templates_before_size = len(list_templates_before)

        cmd = createTemplate.createTemplateCmd()
        cmd.name = self.testdata["ostype"]
        cmd.displaytext = self.testdata["ostype"]
        cmd.ostypeid = self.template.ostypeid
        cmd.snapshotid = snapshot_created.id
        cmd.ispublic = False
        cmd.passwordenabled = False

        template_from_snapshot = Template(
            self.apiclient.createTemplate(cmd).__dict__)

        self.assertIsNotNone(
            template_from_snapshot,
            "Template creation failed from snapshot"
        )

        self.cleanup.append(template_from_snapshot)

        # Creating expected and actual values dictionaries
        expected_dict = {
            "name": self.testdata["ostype"],
            "ostypeid": self.template.ostypeid,
            "type": "USER",
            "zone": self.zone.id,
            "passwordenabled": False,
            "ispublic": False,
            "size": self.disk_offering.disksize
        }
        actual_dict = {
            "name": template_from_snapshot.name,
            "ostypeid": template_from_snapshot.ostypeid,
            "type": template_from_snapshot.templatetype,
            "zone": template_from_snapshot.zoneid,
            "passwordenabled": template_from_snapshot.passwordenabled,
            "ispublic": template_from_snapshot.ispublic,
            "size": template_from_snapshot.size / (1024 * 1024 * 1024)
        }
        status = self.__verify_values(
            expected_dict,
            actual_dict
        )
        #self.assertEqual(
         #   True,
          #  status,
           # "Template created from Snapshot details are not as expected"
        #)

        list_templates_after = Template.list(
            self.apiclient,
            templatefilter='self')

        self.assertEquals(
            templates_before_size + 1,
            len(list_templates_after),
            "Template creation failed from snapshot"
        )
        return


    def waitForSystemVMAgent(self, vmname):
        timeout = self.testdata["timeout"]

        while True:
            list_host_response = list_hosts(
                                                 self.apiclient,
                                                 name=vmname
                                                )

            if list_host_response and list_host_response[0].state == 'Up':
                break

            if timeout == 0:
                raise Exception("Timed out waiting for SSVM agent to be Up")

            time.sleep(self.testdata["sleep"])
            timeout = timeout - 1


    def ssvm_internals(self):

        list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        systemvmtype='secondarystoragevm',
                                        state='Running',
                                        zoneid=self.zone.id
                                        )
        self.assertEqual(
                            isinstance(list_ssvm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        ssvm = list_ssvm_response[0]

        hosts = list_hosts(
                           self.apiclient,
                           id=ssvm.hostid
                           )
        self.assertEqual(
                            isinstance(hosts, list),
                            True,
                            "Check list response returns a valid list"
                        )
        host = hosts[0]

        self.debug("Running SSVM check script")

        if self.hypervisor.lower() in ('vmware', 'hyperv'):
            #SSH into SSVMs is done via management server for Vmware and Hyper-V
            result = get_process_status(
                                self.apiclient.connection.mgtSvr,
                                22,
                                self.apiclient.connection.user,
                                self.apiclient.connection.passwd,
                                ssvm.privateip,
                                "/usr/local/cloud/systemvm/ssvm-check.sh |grep -e ERROR -e WARNING -e FAIL",
                                hypervisor=self.hypervisor
                                )
        else:
            try:
                host.user, host.passwd = get_host_credentials(self.config, host.ipaddress)
                result = get_process_status(
                                    host.ipaddress,
                                    22,
                                    host.user,
                                    host.passwd,
                                    ssvm.linklocalip,
                                    "/usr/local/cloud/systemvm/ssvm-check.sh |grep -e ERROR -e WARNING -e FAIL"
                                )
            except KeyError:
                self.skipTest("Marvin configuration has no host credentials to check router services")
        res = str(result)
        self.debug("SSVM script output: %s" % res)

        self.assertEqual(
                            res.count("ERROR"),
                            1,
                            "Check for Errors in tests"
                        )

        self.assertEqual(
                            res.count("WARNING"),
                            1,
                            "Check for warnings in tests"
                        )

        #Check status of cloud service
        if self.hypervisor.lower() in ('vmware', 'hyperv'):
            #SSH into SSVMs is done via management server for Vmware and Hyper-V
            result = get_process_status(
                                self.apiclient.connection.mgtSvr,
                                22,
                                self.apiclient.connection.user,
                                self.apiclient.connection.passwd,
                                ssvm.privateip,
                                "service cloud status",
                                hypervisor=self.hypervisor
                                )
        else:
            try:
                host.user, host.passwd = get_host_credentials(self.config, host.ipaddress)
                result = get_process_status(
                                    host.ipaddress,
                                    22,
                                    host.user,
                                    host.passwd,
                                    ssvm.linklocalip,
                                    "service cloud status"
                                    )
            except KeyError:
                self.skipTest("Marvin configuration has no host credentials to check router services")
        res = str(result)
        self.debug("Cloud Process status: %s" % res)
        # cloud.com service (type=secstorage) is running: process id: 2346
        self.assertEqual(
                            res.count("is running"),
                            1,
                            "Check cloud service is running or not"
                        )
        return

    def list_sec_storage_vm(self):

        list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        systemvmtype='secondarystoragevm',
                                        state='Running',
                                        )
        self.assertEqual(
                            isinstance(list_ssvm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        #Verify SSVM response
        self.assertNotEqual(
                            len(list_ssvm_response),
                            0,
                            "Check list System VMs response"
                        )

        list_zones_response = list_zones(self.apiclient)
        
        self.assertEqual(
                            isinstance(list_zones_response, list),
                            True,
                            "Check list response returns a valid list"
                        )

        self.debug("Number of zones: %s" % len(list_zones_response))
        self.debug("Number of SSVMs: %s" % len(list_ssvm_response))
        # Number of Sec storage VMs = No of Zones
        self.assertEqual(
                            len(list_ssvm_response),
                            len(list_zones_response),
                            "Check number of SSVMs with number of zones"
                        )
        #For each secondary storage VM check private IP,
        #public IP, link local IP and DNS
        for ssvm in list_ssvm_response:

            self.debug("SSVM state: %s" % ssvm.state)
            self.assertEqual(
                            ssvm.state,
                            'Running',
                            "Check whether state of SSVM is running"
                        )

            self.assertEqual(
                            hasattr(ssvm, 'privateip'),
                            True,
                            "Check whether SSVM has private IP field"
                            )

            self.assertEqual(
                            hasattr(ssvm, 'linklocalip'),
                            True,
                            "Check whether SSVM has link local IP field"
                            )

            self.assertEqual(
                            hasattr(ssvm, 'publicip'),
                            True,
                            "Check whether SSVM has public IP field"
                            )

            #Fetch corresponding ip ranges information from listVlanIpRanges
            ipranges_response = list_vlan_ipranges(
                                                   self.apiclient,
                                                   zoneid=ssvm.zoneid
                                                   )
            self.assertEqual(
                            isinstance(ipranges_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
            iprange = ipranges_response[0]
            
            #Fetch corresponding Physical Network of SSVM's Zone
            listphyntwk = PhysicalNetwork.list(
                            self.apiclient,
                            zoneid=ssvm.zoneid
                            )
            
            # Execute the following assertion in all zones except EIP-ELB Zones
            if not (self.zone.networktype.lower() == 'basic' and isinstance(NetScaler.list(self.apiclient,physicalnetworkid=listphyntwk[0].id), list) is True):
                self.assertEqual(
                            ssvm.gateway,
                            iprange.gateway,
                            "Check gateway with that of corresponding ip range"
                            )

            #Fetch corresponding zone information from listZones
            zone_response = list_zones(
                                       self.apiclient,
                                       id=ssvm.zoneid
                                       )
            self.assertEqual(
                            isinstance(zone_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
            self.assertEqual(
                            ssvm.dns1,
                            zone_response[0].dns1,
                            "Check DNS1 with that of corresponding zone"
                            )

            self.assertEqual(
                            ssvm.dns2,
                            zone_response[0].dns2,
                            "Check DNS2 with that of corresponding zone"
                            )
        return

    def stop_ssvm(self):

        list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        systemvmtype='secondarystoragevm',
                                        state='Running',
                                        zoneid=self.zone.id
                                        )
        self.assertEqual(
                            isinstance(list_ssvm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        ssvm = list_ssvm_response[0]

        hosts = list_hosts(
                           self.apiclient,
                           id=ssvm.hostid
                           )
        self.assertEqual(
                            isinstance(hosts, list),
                            True,
                            "Check list response returns a valid list"
                        )
        host = hosts[0]

        self.debug("Stopping SSVM: %s" % ssvm.id)
        cmd = stopSystemVm.stopSystemVmCmd()
        cmd.id = ssvm.id
        self.apiclient.stopSystemVm(cmd)
        
        timeout = self.testdata["timeout"]
        while True:
            list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        id=ssvm.id
                                        )
            if isinstance(list_ssvm_response, list):
                if list_ssvm_response[0].state == 'Running':
                    break
            if timeout == 0:
                raise Exception("List SSVM call failed!")
            
            time.sleep(self.testdata["sleep"])
            timeout = timeout - 1
        
        self.assertEqual(
                            isinstance(list_ssvm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        ssvm_response = list_ssvm_response[0]
        self.debug("SSVM state after debug: %s" % ssvm_response.state)
        self.assertEqual(
                        ssvm_response.state,
                        'Running',
                        "Check whether SSVM is running or not"
                        )
        # Wait for the agent to be up
        self.waitForSystemVMAgent(ssvm_response.name)

        # Call above tests to ensure SSVM is properly running
        self.list_sec_storage_vm()


    def reboot_ssvm(self):

        list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        systemvmtype='secondarystoragevm',
                                        state='Running',
                                        zoneid=self.zone.id
                                        )
    
        self.assertEqual(
                            isinstance(list_ssvm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        
        ssvm_response = list_ssvm_response[0]

        hosts = list_hosts(
                           self.apiclient,
                           id=ssvm_response.hostid
                           )
        self.assertEqual(
                            isinstance(hosts, list),
                            True,
                            "Check list response returns a valid list"
                        )
        host = hosts[0]

        #Store the public & private IP values before reboot
        old_public_ip = ssvm_response.publicip
        old_private_ip = ssvm_response.privateip

        self.debug("Rebooting SSVM: %s" % ssvm_response.id)
        cmd = rebootSystemVm.rebootSystemVmCmd()
        cmd.id = ssvm_response.id
        self.apiclient.rebootSystemVm(cmd)

        timeout = self.testdata["timeout"]
        while True:
            list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        id=ssvm_response.id
                                        )
            if isinstance(list_ssvm_response, list):
                if list_ssvm_response[0].state == 'Running':
                    break
            if timeout == 0:
                raise Exception("List SSVM call failed!")
            
            time.sleep(self.testdata["sleep"])
            timeout = timeout - 1

        ssvm_response = list_ssvm_response[0]
        self.debug("SSVM State: %s" % ssvm_response.state)
        self.assertEqual(
                        'Running',
                        str(ssvm_response.state),
                        "Check whether CPVM is running or not"
                        )

        self.assertEqual(
                    ssvm_response.publicip,
                    old_public_ip,
                    "Check Public IP after reboot with that of before reboot"
                    )

        self.assertEqual(
                    ssvm_response.privateip,
                    old_private_ip,
                    "Check Private IP after reboot with that of before reboot"
                    )

        # Wait for the agent to be up
        self.waitForSystemVMAgent(ssvm_response.name)

        return

    def destroy_ssvm(self):

        list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        systemvmtype='secondarystoragevm',
                                        state='Running',
                                        zoneid=self.zone.id
                                        )
        self.assertEqual(
                            isinstance(list_ssvm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        ssvm_response = list_ssvm_response[0]

        old_name = ssvm_response.name

        self.debug("Destroying SSVM: %s" % ssvm_response.id)
        cmd = destroySystemVm.destroySystemVmCmd()
        cmd.id = ssvm_response.id
        self.apiclient.destroySystemVm(cmd)

        timeout = self.testdata["timeout"]
        while True:
            list_ssvm_response = list_ssvms(
                                        self.apiclient,
                                        zoneid=self.zone.id,
                                        systemvmtype='secondarystoragevm'
                                        )
            if isinstance(list_ssvm_response, list):
                if list_ssvm_response[0].state == 'Running':
                    break
            if timeout == 0:
                raise Exception("List SSVM call failed!")
            
            time.sleep(self.testdata["sleep"])
            timeout = timeout - 1

        ssvm_response = list_ssvm_response[0]

        # Verify Name, Public IP, Private IP and Link local IP
        # for newly created SSVM
        self.assertNotEqual(
                        ssvm_response.name,
                        old_name,
                        "Check SSVM new name with name of destroyed SSVM"
                        )
        self.assertEqual(
                        hasattr(ssvm_response, 'privateip'),
                        True,
                        "Check whether SSVM has private IP field"
                        )

        self.assertEqual(
                        hasattr(ssvm_response, 'linklocalip'),
                        True,
                        "Check whether SSVM has link local IP field"
                        )

        self.assertEqual(
                        hasattr(ssvm_response, 'publicip'),
                        True,
                        "Check whether SSVM has public IP field"
                        )
        
        # Wait for the agent to be up
        self.waitForSystemVMAgent(ssvm_response.name)

        return

    @attr(tags = ["advanced", "advancedns", "smoke", "basic"], required_hardware="true")
    def test_01_Browser_volume_Life_cycle_tpath(self):
        """
        Test Browser_volume_Life_cycle - This includes upload volume,attach to a VM, write data ,Stop ,Start, Reboot,Reset  of a VM, detach,attach back to the VM, delete volumes  
        """
        try:

            self.debug("========================= Test 1: Upload Browser based volume and validate ========================= ")
            browseup_vol=self.browse_upload_volume()

            self.debug("========================= Test 2: Deploy a VM , Attach Uploaded Browser based volume and validate VM Operations========================= ")

            vm1details=self.deploy_vm()

            self.attach_volume(vm1details,browseup_vol.id)

            self.vmoperations(vm1details)

            self.debug("========================= Test 3: Restore VM with Uploaded volume attached========================= ")

            self.restore_vm(vm1details)

            self.debug("========================= Test 4: Detach Uploaded volume and validation of VM operations after detach========================= ")

            self.detach_volume(vm1details,browseup_vol.id)

            self.vmoperations(vm1details)

            self.debug("========================= Test 5: Deploy New VM,Attach the detached Uploaded volume and validate VM operations after attach========================= ")

            vm2details=self.deploy_vm()

            self.attach_volume(vm2details,browseup_vol.id)

            self.vmoperations(vm2details)

            self.debug("========================= Test 6: Detach Uploaded volume and resize detached uploaded volume========================= ")

            self.detach_volume(vm2details,browseup_vol.id)

            self.resize_volume(browseup_vol.id)

            self.debug("========================= Test 7: Attach resized uploaded volume and validate VM operations========================= ")

            self.attach_volume(vm2details,browseup_vol.id)

            self.vmoperations(vm2details)

            self.debug("========================= Test 8: Try resizing uploaded state volume and validate the error scenario========================= ")

            browseup_vol2=self.browse_upload_volume()

            self.resize_fail(browseup_vol2.id)

            self.debug("========================= Test 9: Attach multiple uploaded volumes to a VM and validate VM operations========================= ")

            browseup_vol3=self.browse_upload_volume()

            self.attach_volume(vm2details,browseup_vol2.id)

            self.attach_volume(vm2details,browseup_vol3.id)

            self.vmoperations(vm2details)

            self.debug("========================= Test 10:  Detach and delete uploaded volume========================= ")

            self.detach_volume(vm2details,browseup_vol2.id)

            self.deletevolume(browseup_vol2.id)

            self.debug("========================= Test 11:  Detach and download uploaded volume========================= ")

            self.detach_volume(vm2details,browseup_vol3.id)

            self.download_volume(browseup_vol3.id)

            self.debug("========================= Test 12:  Delete detached uploaded volume========================= ")

            self.deletevolume(browseup_vol3.id)


            self.debug("========================= Test 13:  Delete Uploaded State volume========================= ")

            browseup_vol4=self.browse_upload_volume()

            self.deletevolume(browseup_vol4.id)

            self.debug("========================= Test 14:  Destroy VM which has Uploaded volumes attached========================= ")

            vm4details=self.deploy_vm()

            newvolumetodestoy_VM=self.browse_upload_volume()

            self.attach_volume(vm4details,newvolumetodestoy_VM.id)

            self.destroy_vm(vm4details)

            self.debug("========================= Test 15:  Recover destroyed VM which has Uploaded volumes attached========================= ")

            self.recover_destroyed_vm(vm4details)
            self.destroy_vm(vm4details)

            self.deletevolume(newvolumetodestoy_VM.id)

            self.debug("========================= Test 16:  Delete attached Uploaded volume which is in ready state and it should not be allowed to delete========================= ")

            vm5details=self.deploy_vm()
            browseup_vol5=self.browse_upload_volume()
            self.attach_volume(vm5details,browseup_vol5.id)
            self.deletevolume_fail(browseup_vol5.id)

            self.debug("========================= Test 17:  Create Volume Backup Snapshot uploaded volume attached to the VM========================= ")

            vm6details=self.deploy_vm()
            browseup_vol6=self.browse_upload_volume()

            self.attach_volume(vm6details,browseup_vol6.id)

            snapshotdetails=self.volume_snapshot(browseup_vol6)

            self.debug("========================= Test 18:  Create Volume from Backup Snapshot of attached uploaded volume========================= ")

            self.volume_snapshot_volume(snapshotdetails)

            self.debug("========================= Test 19:  Create template from Backup Snapshot of attached uploaded volume========================= ")
            self.volume_snapshot_template(snapshotdetails)

            self.deletevolume(browseup_vol6.id)

            self.debug("========================= Test 20: Upload Browser based volume with checksum and validate ========================= ")
            browseup_vol_withchecksum=self.browse_upload_volume_with_md5()

            self.debug("========================= Test 21: Deploy a VM , Attach Uploaded Browser based volume with checksum and validate VM Operations========================= ")

            vm7details=self.deploy_vm()

            self.attach_volume(vm7details,browseup_vol_withchecksum.id)

            self.debug("========================= Test 22: Detach Uploaded volume with checksum and validation of VM operations after detach========================= ")

            self.detach_volume(vm7details,browseup_vol_withchecksum.id)
            self.deletevolume(browseup_vol_withchecksum.id)

            self.vmoperations(vm7details)

            self.destroy_vm(vm7details)


        except Exception as e:
            self.fail("Exception occurred  : %s" % e)
        return


    @attr(tags = ["advanced", "advancedns", "smoke", "basic"], required_hardware="true")
    def test_02_SSVM_Life_Cycle_With_Browser_Volume_TPath(self):
        """
        Test SSVM_Life_Cycle_With_Browser_Volume_TPath - This includes SSVM life cycle followed by Browser volume upload operations
        """
        try:
            
            self.debug("========================= Test 23: Stop and Start SSVM and Perform Browser based volume validations ========================= ")

            self.stop_ssvm()
            ssvm1browseup_vol=self.browse_upload_volume()

            ssvm1vm1details=self.deploy_vm()

            self.attach_volume(ssvm1vm1details,ssvm1browseup_vol.id)

            self.vmoperations(ssvm1vm1details)

            self.detach_volume(ssvm1vm1details,ssvm1browseup_vol.id)

            self.deletevolume(ssvm1browseup_vol.id)
            self.destroy_vm(ssvm1vm1details)

            self.debug("========================= Test 24: Reboot SSVM and Perform Browser based volume validations ========================= ")

            self.reboot_ssvm()
            ssvm2browseup_vol=self.browse_upload_volume()

            ssvm2vm1details=self.deploy_vm()

            self.attach_volume(ssvm2vm1details,ssvm2browseup_vol.id)

            self.vmoperations(ssvm2vm1details)

            self.detach_volume(ssvm2vm1details,ssvm2browseup_vol.id)

            self.deletevolume(ssvm2browseup_vol.id)

            self.destroy_vm(ssvm2vm1details)

            self.debug("========================= Test 25: Reboot SSVM and Perform Browser based volume validations ========================= ")

            self.destroy_ssvm()
            ssvm3browseup_vol=self.browse_upload_volume()

            ssvm3vm1details=self.deploy_vm()

            self.attach_volume(ssvm3vm1details,ssvm3browseup_vol.id)

            self.vmoperations(ssvm3vm1details)

            self.detach_volume(ssvm3vm1details,ssvm3browseup_vol.id)

            self.deletevolume(ssvm3browseup_vol.id)

            self.destroy_vm(ssvm3vm1details)

        except Exception as e:
            self.fail("Exception occurred  : %s" % e)
        return


    @classmethod
    def tearDownClass(self):
        try:
            self.apiclient = super(TestBrowseUploadVolume,self).getClsTestClient().getApiClient()
            cleanup_resources(self.apiclient, self._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return
