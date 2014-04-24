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

#Import Local Modules
from marvin.cloudstackTestCase import *
from marvin.cloudstackException import *
from marvin.cloudstackAPI import *
from marvin.sshClient import SshClient
from marvin.lib.utils import *
from marvin.lib.base import *
from marvin.lib.common import *
from marvin.lib.utils import checkVolumeSize
from marvin.codes import SUCCESS
from nose.plugins.attrib import attr

class TestVolumes(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
       
        cls.testClient = super(TestVolumes, cls).getClsTestClient()
        cls.api_client = cls.testClient.getApiClient()
        cls.services = cls.testClient.getParsedTestDataConfig()
        # Get Domain, Zone, Template
        cls.domain = get_domain(cls.api_client)
        cls.zone = get_zone(cls.api_client)
        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )
        if cls.zone.localstorageenabled:
            cls.storagetype = 'local'
            cls.services["service_offerings"]["tiny"]["storagetype"] = 'local'
            cls.services["disk_offering"]["storagetype"] = 'local'
        else:
            cls.storagetype = 'shared'
            cls.services["service_offerings"]["tiny"]["storagetype"] = 'shared'
            cls.services["disk_offering"]["storagetype"] = 'shared'

        cls.services['mode'] = cls.zone.networktype
        cls.services["virtual_machine"]["hypervisor"] = 'XenServer'
        cls.services["virtual_machine"]["zoneid"] = cls.zone.id
        cls.services["virtual_machine"]["template"] = cls.template.id
        cls.services["custom_volume"]["zoneid"] = cls.zone.id

        # Creating Disk offering, Service Offering and Account
        cls.disk_offering = DiskOffering.create(
                                    cls.api_client,
                                    cls.services["disk_offering"]
                                    )
        cls.service_offering = ServiceOffering.create(
                                            cls.api_client,
                                            cls.services["service_offerings"]["tiny"]
                                            )
        cls.account = Account.create(
                            cls.api_client,
                            cls.services["account"],
                            domainid=cls.domain.id
                            )

        # Getting authentication for user in newly created Account
        cls.user = cls.account.user[0]

        cls.userapiclient = cls.testClient.getUserApiClient(cls.user.username, cls.domain.name)

        # Creating Virtual Machine
        cls.virtual_machine = VirtualMachine.create(
                                    cls.userapiclient,
                                    cls.services["virtual_machine"],
                                    accountid=cls.account.name,
                                    domainid=cls.account.domainid,
                                    serviceofferingid=cls.service_offering.id,
                                )
        cls._cleanup = [
                        cls.virtual_machine,
                        cls.disk_offering,
                        cls.service_offering,
                        cls.account
                        ]

    def setUp(self):

        self.apiClient = self.testClient.getApiClient()
        self.cleanup = []

    def tearDown(self):
        #Clean up, terminate the created volumes
        cleanup_resources(self.apiClient, self.cleanup)
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cls.apiclient = super(TestVolumes, cls).getClsTestClient().getApiClient()
            cleanup_resources(cls.apiclient, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

   
    def __verify_values(self, expected_vals, actual_vals):
        """  
        @summary: Function to verify expected and actual values
        Step1: Initializing return flag to True
        Step1: Verifying length of expected and actual dictionaries is matching.
               If not matching returning false
        Step2: Listing all the keys from expected dictionary
        Step3: Looping through each key from step2 and verifying expected and actual dictionaries have same value
               If not making return flag to False
        Step4: returning the return flag after all the values are verified
        """
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
                self.debug("expected Value: %s, is not matching with actual value: %s" % (
                                                                                          exp_val,
                                                                                          act_val
                                                                                          ))
        return return_flag

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_01_list_volumes_pagination(self):
        """  
        @summary: Test List Volumes pagination
        
        Step1: Listing all the volumes for a user
        Step2: Verifying listed volumes for account created at class level
        Step3: If number of volumes is less than (page size + 1), then creating them
        Step4: Listing all the volumes again after creation of volumes
        Step5: Verifying the length of the volumes is (page size + 1)
        Step6: Listing all the volumes in page1
        Step7: Verifying that the length of the volumes in page 1 is (page size)
        Step8: Listing all the volumes in page2
        Step9: Verifying that the length of the volumes in page 2 is 1
        Step10: Deleting the volume present in page 2
        Step11: Listing for the volumes on page 2
        Step12: Verifying that there are no volumes present in page 2
        """
        # Listing all the volumes for a user
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        # Verifying listed volumes for account created at class level
        self.assertIsNotNone(
                             list_volumes_before,
                             "create volume from VM failed at class setup method"
                             )
        self.assertEqual(
                         len(list_volumes_before),
                         1,
                         "more than 1 volume created from VM at class level"
                         )

        # If number of volumes is less than (pagesize + 1), then creating them    
        for i in range(0, (self.services["pagesize"])):
            volume_created = Volume.create(
                                   self.userapiclient,
                                   self.services["volume"],
                                   zoneid=self.zone.id,
                                   diskofferingid=self.disk_offering.id
                                   )
            self.assertIsNotNone(
                                 volume_created,
                                 "Volume is not created"
                                 )
            if(i < (self.services["pagesize"] - 1)):
                self.cleanup.append(volume_created)
                
            self.assertEqual(
                             self.services["volume"]["diskname"],
                             volume_created.name,
                             "Newly created volume name and the test data volume name are not matching"
                             )

        # Listing all the volumes again after creation of volumes        
        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"]) 

        # Verifying the length of the volumes is (page size + 1)
        self.assertEqual(
                         len(list_volumes_after),
                         (self.services["pagesize"] + 1),
                         "Number of volumes created is not matching expected"
                         )

        # Listing all the volumes in page1
        list_volumes_page1 = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         page=1,
                                         pagesize=self.services["pagesize"]
                                         )
        self.assertIsNotNone(
                             list_volumes_page1,
                             "No volumes found in Page 1"
                             )
        # Verifying that the length of the volumes in page 1 is (page size)
        self.assertEqual(
                         len(list_volumes_page1),
                         self.services["pagesize"],
                         "List Volume response is not matching with the page size length for page 1"
                         )

        # Listing all the volumes in page2
        list_volumes_page2 = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         page=2,
                                         pagesize=self.services["pagesize"]
                                         )
        self.assertIsNotNone(
                             list_volumes_page2,
                             "No volumes found in Page 2"
                             )
        # Verifying that the length of the volumes in page 2 is 1
        self.assertEqual(
                         len(list_volumes_page2),
                         1,
                         "List Volume response is not matching with the page size length for page 2"
                         )
        volume_page2 = list_volumes_page2[0]

        # Verifying that the volume on page 2 is not present in page1
        for i in range(0, len(list_volumes_page1)):
            volume_page1 = list_volumes_page1[i]
            self.assertNotEquals(
                                 volume_page2.id,
                                 volume_page1.id,
                                 "Volume listed in page 2 is also listed in page 1"
                                 )

        # Deleting a single volume
        Volume.delete(volume_created, self.userapiclient)

        # Listing the volumes in page 2
        list_volume_response = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         page=2,
                                         pagesize=self.services["pagesize"]
                                         )
        # verifying that volume does not exists on page 2
        self.assertEqual(
                        list_volume_response,
                        None,
                        "Volume was not deleted"
                    )
        return

    @attr(tags=["advanced", "basic", "provisioning"]) 
    def test_02_list_volume_byid(self):
        """       
        @summary: Test List Volumes with Id
       
        Step1: Listing all the volumes for a user before creating a data volume
        Step2: Verifying the length of the list as 1
        Step3: Creating a data volume
        Step4: Listing all the volumes for a user after creating a data volume
        Step5: Verifying the list volume size is increased by 1
        Step6: List the volumes by specifying root volume Id
        Step7: Verifying the details of the root volume
        Step8: List the volumes by specifying data volume Id
        Step9: Verifying the details of the data volume
        """
        # Listing all the volumes for a user before creating a data volume
        list_volumes_before = Volume.list(
                                          self.userapiclient,
                                          listall=self.services["listall"]
                                          )
        self.assertIsNotNone(
                             list_volumes_before,
                             "create volume from VM failed at class setup method")
        # Verifying the length of the list as 1
        self.assertEqual(
                         len(list_volumes_before),
                         1,
                         "more than 1 volume created at class level"
                         )
        root_volume = list_volumes_before[0]

        # Creating a data volume
        volume_created = Volume.create(
                                   self.userapiclient,
                                   self.services["volume"],
                                   zoneid=self.zone.id,
                                   diskofferingid=self.disk_offering.id
                                   )
        self.assertIsNotNone(
                             volume_created,
                             "Volume is not created"
                             )
        self.cleanup.append(volume_created)
        
        self.assertEqual(
                         self.services["volume"]["diskname"],
                         volume_created.name,
                         "Newly created volume name and the test data volume name are not matching"
                         )
        # Listing all the volumes for a user after creating a data volume
        list_volumes_after = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"]
                                         )
        self.assertIsNotNone(
                             list_volumes_after,
                             "Volume creation failed"
                             )
        # Verifying the list volume size is increased by 1
        self.assertEqual(
                         len(list_volumes_before) + 1,
                         len(list_volumes_after),
                         "list volume is not matching with Number of volumes created"
                         )

        # Listing a Root Volume by Id and verifying the volume details
        list_volumes_by_id = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         id=root_volume.id
                                         )  
        self.assertIsNotNone(
                             list_volumes_by_id,
                             "Root volume is not listed"
                             ) 
        self.assertEqual(
                         1,
                         len(list_volumes_by_id),
                         "list volume is not matching with Number of volumes created"
                         )
        obtained_volume = list_volumes_by_id[0]

        #Creating expected and actual values dictionaries
        expected_dict = {
                           "id":root_volume.id,
                           "name":root_volume.name,
                           "vmname":self.virtual_machine.name,
                           "state":"Ready",
                           "type":"ROOT",
                           "zoneid":self.zone.id,
                           "account":self.account.name,
                           "storagetype":self.storagetype,
                           "size":self.template.size
                           }
        actual_dict = {
                           "id":obtained_volume.id,
                           "name":obtained_volume.name,
                           "vmname":obtained_volume.vmname,
                           "state":obtained_volume.state,
                           "type":obtained_volume.type,
                           "zoneid":obtained_volume.zoneid,
                           "account":obtained_volume.account,
                           "storagetype":obtained_volume.storagetype,
                           "size":obtained_volume.size,
                           }
        root_volume_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         root_volume_status,
                         "Listed Root Volume details are not as expected"
                         )
        # Listing a Data Volume by Id and verifying the volume details
        list_volumes_by_id = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         id=volume_created.id
                                         )  
        self.assertIsNotNone(
                             list_volumes_by_id,
                             "Data volume is not listed"
                             ) 
        self.assertEqual(
                         len(list_volumes_by_id),
                         1,
                         "list volume is not matching with Number of volumes created"
                         )
        obtained_volume = list_volumes_by_id[0]

        #Creating expected and actual values dictionaries
        expected_dict = {
                           "id":volume_created.id,
                           "name":volume_created.name,
                           "state":"Allocated",
                           "type":"DATADISK",
                           "zoneid":self.zone.id,
                           "account":self.account.name,
                           "storagetype":self.storagetype,
                           "size":self.disk_offering.disksize
                           }
        actual_dict = {
                           "id":obtained_volume.id,
                           "name":obtained_volume.name,
                           "state":obtained_volume.state,
                           "type":obtained_volume.type,
                           "zoneid":obtained_volume.zoneid,
                           "account":obtained_volume.account,
                           "storagetype":obtained_volume.storagetype,
                           "size":obtained_volume.size/(1024*1024*1024),
                           }
        root_volume_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         root_volume_status,
                         "Listed Data Volume details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_03_data_volume_resize(self):
        """  
        @summary: Test to verify creation and resize of data volume
         
        Step1: Listing the volumes for a user before creating data volume
        Step2: Creating a data volume
        Step3: Listing the volumes for a user after creating data volume
        Step4: Attaching and Detaching data volume created to Virtual Machine
        Step5: Verifying if there exists a disk offering with higher size
                If not present creating it
        Step6: Resizing data volume
        """
        # Listing volumes for a user before creating a volume
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        # Creating a data volume
        volume_created = Volume.create(
                                       self.userapiclient,
                                       self.services["volume"],
                                       zoneid=self.zone.id,
                                       diskofferingid=self.disk_offering.id
                                       )
        self.assertIsNotNone(volume_created, "Data volume creation failed")

        self.cleanup.append(volume_created)

        # Listing volumes for a user after creating data volume
        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])
        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Data volume creation failed"
                          )

        # Attaching data volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )

        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )

        # Detaching data volume from Virtual Machine
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )

        # Verifying if there exists a disk offering with higher size. If not present creating it
        list_disk_offerings = DiskOffering.list(self.apiClient)

        large_disk_offering_exists = False
        # Converting disk_size in bytes to GB
        current_disk_size = volume_created.size/(1024*1024*1024)

        for disk_offering in list_disk_offerings:
            if ((disk_offering.disksize > current_disk_size) and (not disk_offering.iscustomized) and disk_offering.storagetype == self.storagetype):
                new_disk_offering = disk_offering
                large_disk_offering_exists = True
                break

        if large_disk_offering_exists == False:
            new_size = (volume_created.size/(1024*1024*1024)) + 1
            self.services["disk_offering"]["disksize"] = new_size
            new_disk_offering = DiskOffering.create(
                                                    self.apiClient,
                                                    self.services["disk_offering"]
                                                    )
            if new_disk_offering is not None:
                self.cleanup.append(new_disk_offering)
        else:
            new_size = new_disk_offering.disksize

        # Resizing data volume
        resized_volume = volume_created.resize(
                                               self.userapiclient,
                                               diskofferingid=new_disk_offering.id,
                                               shrinkok='false',
                                               )
        self.assertIsNotNone(resized_volume, "Resize Volume failed")
        # Verifying data volume size is increased
        self.assertEquals(
                          new_size,
                          (resized_volume.size/(1024*1024*1024)),
                          "volume not resized to expected value"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_04_custom_volume_resize(self):
        """  
        @summary: Test to verify creation and resize of custom volume
         
        Step1: Checking if Custom disk offering already exists.
               If not present then creating custom Disk Offering
        Step2: Listing the volumes for a user before creating custom volume
        Step3: Creating a custom volume
        Step4: Listing the volumes for a user after creating custom volume
        Step5: Attaching and Detaching custom volume created to Virtual Machine
        Step6: Resizing custom volume
        """
        # Listing all the disk offerings
        list_disk_offerings = DiskOffering.list(self.apiClient)

        custom_disk_offering_exists = False

        # Verifying if a custom disk offering already exists
        if list_disk_offerings is not None:
            for disk_offering in list_disk_offerings:
                if (disk_offering.iscustomized and disk_offering.storagetype == self.storagetype):
                    custom_disk_offering = disk_offering
                    custom_disk_offering_exists = True
                    break

        # If a custom disk offering does not exists, then creating a custom disk offering
        if custom_disk_offering_exists == False:
            custom_disk_offering = DiskOffering.create(
                                    self.apiClient,
                                    self.services["disk_offering"],
                                    custom=True
                                    )
            if custom_disk_offering is not None:
                self.cleanup.append(custom_disk_offering)

        # Listing the volumes for a user before creating custom volume
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        # Creating a custom volume
        volume_created = Volume.create_custom_disk(
                                    self.userapiclient,
                                    self.services["custom_volume"],
                                    account=self.account.name,
                                    domainid=self.account.domainid,
                                    diskofferingid=custom_disk_offering.id
                                    )
        self.assertIsNotNone(
                             volume_created,
                             "Custom volume did not get created"
                             )

        self.cleanup.append(volume_created)

        # Listing the volumes for a user after creating custom volume
        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        # Verifyign that volume list is increased by 1 after creation of custion volume
        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Custom volume did not get created"
                          )

        # Attaching custom volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )

        # Detaching custom volume from Virtual Machine
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )

        # Resizing custom volume
        # Increasing custom disk size by 1
        new_size = self.services["custom_volume"]["customdisksize"] + 1
        resized_volume = volume_created.resize(
                                               self.userapiclient,
                                               diskofferingid=custom_disk_offering.id,
                                               shrinkok='false',
                                               size=new_size)
        self.assertIsNotNone(resized_volume, "Resize Volume failed")
        # Verifying that custom disk size is increased
        self.assertEquals(
                          new_size,
                          (resized_volume.size/(1024*1024*1024)),
                          "volume not resized to expected value"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_05_volume_snapshot(self):
        """  
        @summary: Test to verify creation of snapshot from volume and creation of template, volume from snapshot
         
        Step1: Creating a volume
        Step2: Attaching and Detaching custom volume created to Virtual Machine
        Step3: Creating Snapshot from volume
        Step4: Creating Volume from snapshot
        Step5: Creating Template from Snapshot
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )

        self.assertIsNotNone(volume_created, "Volume not created")

        if volume_created is not None:
            self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )
        # Attaching and Detaching custom volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )

        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )
        # Creating Snapshot from volume
        snapshot_created = Snapshot.create(
                                           self.userapiclient,
                                           volume_created.id,
                                           )

        self.assertIsNotNone(snapshot_created, "Snapshot not created")

        self.cleanup.append(snapshot_created)

        #Creating expected and actual values dictionaries
        expected_dict = {
                           "id":volume_created.id,
                           "intervaltype":"MANUAL",
                           "snapshottype":"MANUAL",
                           "volumetype":volume_created.type,
                           "domain":self.domain.id
                           }
        actual_dict = {
                           "id":snapshot_created.volumeid,
                           "intervaltype":snapshot_created.intervaltype,
                           "snapshottype":snapshot_created.snapshottype,
                           "volumetype":snapshot_created.volumetype,
                           "domain":snapshot_created.domainid,
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
        # Creating Volume from snapshot
        cmd = createVolume.createVolumeCmd()
        cmd.name = "-".join([self.services["volume"]["diskname"], random_gen()])
        cmd.snapshotid = snapshot_created.id

        volume_from_snapshot = Volume(self.userapiclient.createVolume(cmd).__dict__)

        self.assertIsNotNone(
                             volume_from_snapshot,
                             "Volume creation failed from snapshot"
                             )
        self.cleanup.append(volume_from_snapshot)

        #Creating expected and actual values dictionaries
        expected_dict = {
                           "snapshotid":snapshot_created.id,
                           "volumetype":snapshot_created.volumetype,
                           "size":self.disk_offering.disksize,
                           "accounr":self.account.name,
                           "domain":self.domain.id,
                           "storagetype":self.storagetype,
                           "zone":self.zone.id
                           }
        actual_dict = {
                           "snapshotid":volume_from_snapshot.snapshotid,
                           "volumetype":volume_from_snapshot.type,
                           "size":volume_from_snapshot.size/(1024*1024*1024),
                           "accounr":volume_from_snapshot.account,
                           "domain":volume_from_snapshot.domainid,
                           "storagetype":volume_from_snapshot.storagetype,
                           "zone":volume_from_snapshot.zoneid,
                           }
        status = self.__verify_values(
                                      expected_dict,
                                      actual_dict
                                      )
        self.assertEqual(
                         True,
                         status,
                         "Volume created from Snapshot details are not as expected"
                         )
        # Creating Template from Snapshot
        list_templates_before = Template.list(self.userapiclient, templatefilter='self')

        if list_templates_before is None:
            templates_before_size = 0
        else:
            templates_before_size = len(list_templates_before)

        cmd = createTemplate.createTemplateCmd()
        cmd.name = self.services["ostype"]
        cmd.displaytext = self.services["ostype"]
        cmd.ostypeid = self.template.ostypeid
        cmd.snapshotid = snapshot_created.id
        cmd.ispublic = False
        cmd.passwordenabled = False

        template_from_snapshot = Template(self.userapiclient.createTemplate(cmd).__dict__)

        self.assertIsNotNone(
                             template_from_snapshot,
                             "Template creation failed from snapshot"
                             )

        self.cleanup.append(template_from_snapshot)

        #Creating expected and actual values dictionaries
        expected_dict = {
                           "name":self.services["ostype"],
                           "ostypeid":self.template.ostypeid,
                           "type":"USER",
                           "zone":self.zone.id,
                           "domain":self.domain.id,
                           "account":self.account.name,
                           "passwordenabled":False,
                           "ispublic":False,
                           "size":self.disk_offering.disksize
                           }
        actual_dict = {
                           "name":template_from_snapshot.name,
                           "ostypeid":template_from_snapshot.ostypeid,
                           "type":template_from_snapshot.templatetype,
                           "zone":template_from_snapshot.zoneid,
                           "domain":template_from_snapshot.domainid,
                           "account":template_from_snapshot.account,
                           "passwordenabled":template_from_snapshot.passwordenabled,
                           "ispublic":template_from_snapshot.ispublic,
                           "size":template_from_snapshot.size/(1024*1024*1024)
                           }
        status = self.__verify_values(
                                      expected_dict,
                                      actual_dict
                                      )
        self.assertEqual(
                         True,
                         status,
                         "Template created from Snapshot details are not as expected"
                         )

        list_templates_after = Template.list(self.userapiclient, templatefilter='self')

        self.assertEquals(
                          templates_before_size + 1,
                          len(list_templates_after),
                          "Template creation failed from snapshot"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_06_volume_snapshot_policy_hourly(self):
        """  
        @summary: Test to verify creation of Hourly Snapshot policies from volume
         
        Step1: Creating a Volume.
        Step2: Attaching volume created in Step2 to virtual machine
        Step3: Detaching the volume created in step2 from virtual machine
        Step4: Listing snapshot policies for a volume created in step1
        Step5: Creating Hourly snapshot policy
        Step6: Listing snapshot policies for a volume created in step1 again
        Step7: Verifyign that the list snapshot policy length is increased by 1
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )

        self.assertIsNotNone(volume_created, "Volume not created")
        self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )

        # Attaching volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )

        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        # Detaching volume created from Virtual Machine
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )
        # Creating Hourly Snapshot Policy from volume
        self.services["recurring_snapshot"]["intervaltype"] = 'hourly'
        self.services["recurring_snapshot"]["schedule"] = '1'

        list_snapshot_policy_before = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        snapshot_policy_before_size = 0

        if list_snapshot_policy_before is not None:
            snapshot_policy_before_size = len(list_snapshot_policy_before)

        snapshot_policy_hourly = SnapshotPolicy.create(
                                                       self.userapiclient,
                                                       volume_created.id,
                                                       self.services["recurring_snapshot"]
                                                       )
        self.assertIsNotNone(
                             snapshot_policy_hourly,
                             "Hourly Snapshot policy creation failed"
                             )
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "schedule":self.services["recurring_snapshot"]["schedule"],
                         "intervaltype":0,
                         "volumeid":volume_created.id
                         }
        actual_dict = {
                       "schedule":snapshot_policy_hourly.schedule,
                       "intervaltype":snapshot_policy_hourly.intervaltype,
                       "volumeid":snapshot_policy_hourly.volumeid
                       }
        status = self.__verify_values(
                                      expected_dict,
                                      actual_dict
                                      )
        self.assertEqual(
                         True,
                         status,
                         "Hourly Snapshot Policy details are not as expected"
                         )

        list_snapshot_policy_after = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        self.assertIsNotNone(
                             list_snapshot_policy_after,
                             "Hourly Snapshot policy creation failed"
                             ) 
        self.assertEquals(
                          snapshot_policy_before_size + 1,
                          len(list_snapshot_policy_after),
                          "Hourly Snapshot policy creation failed"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_07_volume_snapshot_policy_daily(self):
        """  
        @summary: Test to verify creation of Daily Snapshot policies from volume
         
        Step1: Creating a Volume.
        Step2: Attaching volume created in Step2 to virtual machine
        Step3: Detaching the volume created in step2 from virtual machine
        Step4: Listing snapshot policies for a volume created in step1
        Step5: Creating Daily snapshot policy
        Step6: Listing snapshot policies for a volume created in step1 again
        Step7: Verifyign that the list snapshot policy length is increased by 1
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )

        self.assertIsNotNone(volume_created, "Volume not created")
        self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )
        # Attaching volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )

        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        # Detaching volume created from Virtual Machine
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )
        # Creating Daily Snapshot Policy from volume
        self.services["recurring_snapshot"]["intervaltype"] = 'daily'
        self.services["recurring_snapshot"]["schedule"] = '00:00'

        list_snapshot_policy_before = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        snapshot_policy_before_size = 0

        if list_snapshot_policy_before is not None:
            snapshot_policy_before_size = len(list_snapshot_policy_before)

        snapshot_policy_daily = SnapshotPolicy.create(
                                                       self.userapiclient,
                                                       volume_created.id,
                                                       self.services["recurring_snapshot"]
                                                       )
        self.assertIsNotNone(
                             snapshot_policy_daily,
                             "Daily Snapshot policy creation failed"
                             )
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "schedule":self.services["recurring_snapshot"]["schedule"],
                         "intervaltype":1,
                         "volumeid":volume_created.id
                         }
        actual_dict = {
                       "schedule":snapshot_policy_daily.schedule,
                       "intervaltype":snapshot_policy_daily.intervaltype,
                       "volumeid":snapshot_policy_daily.volumeid
                       }
        status = self.__verify_values(
                                      expected_dict,
                                      actual_dict
                                      )
        self.assertEqual(
                         True,
                         status,
                         "Daily Snapshot Policy details are not as expected"
                         )

        list_snapshot_policy_after = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        self.assertIsNotNone(
                             list_snapshot_policy_after,
                             "Daily Snapshot policy creation failed"
                             )
        self.assertEquals(
                          snapshot_policy_before_size + 1,
                          len(list_snapshot_policy_after),
                          "Daily Snapshot policy creation failed"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_08_volume_snapshot_policy_weekly(self):
        """  
        @summary: Test to verify creation of Weekly Snapshot policies from volume
         
        Step1: Creating a Volume.
        Step2: Attaching volume created in Step2 to virtual machine
        Step3: Detaching the volume created in step2 from virtual machine
        Step4: Listing snapshot policies for a volume created in step1
        Step5: Creating Weekly snapshot policy
        Step6: Listing snapshot policies for a volume created in step1 again
        Step7: Verifyign that the list snapshot policy length is increased by 1
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )

        self.assertIsNotNone(volume_created, "Volume not created")
        self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )

        # Attaching volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        # Detaching volume created to Virtual Machine
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )
        # Creating Weekly Snapshot Policy from volume
        self.services["recurring_snapshot"]["intervaltype"] = 'weekly'
        self.services["recurring_snapshot"]["schedule"] = '00:00:1'

        list_snapshot_policy_before = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        snapshot_policy_before_size = 0

        if list_snapshot_policy_before is not None:
            snapshot_policy_before_size = len(list_snapshot_policy_before)

        snapshot_policy_weekly = SnapshotPolicy.create(
                                                       self.userapiclient,
                                                       volume_created.id,
                                                       self.services["recurring_snapshot"]
                                                       )
        self.assertIsNotNone(
                             snapshot_policy_weekly,
                             "Weekly Snapshot policy creation failed"
                             )
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "schedule":self.services["recurring_snapshot"]["schedule"],
                         "intervaltype":2,
                         "volumeid":volume_created.id
                         }
        actual_dict = {
                       "schedule":snapshot_policy_weekly.schedule,
                       "intervaltype":snapshot_policy_weekly.intervaltype,
                       "volumeid":snapshot_policy_weekly.volumeid
                       }
        status = self.__verify_values(
                                      expected_dict,
                                      actual_dict
                                      )
        self.assertEqual(
                         True,
                         status,
                         "Weekly Snapshot Policy details are not as expected"
                         )

        list_snapshot_policy_after = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        self.assertIsNotNone(
                             list_snapshot_policy_after,
                             "Weekly Snapshot policy creation failed"
                             )
        self.assertEquals(
                          snapshot_policy_before_size + 1,
                          len(list_snapshot_policy_after),
                          "Weekly Snapshot policy creation failed"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_09_volume_snapshot_policy_monthly(self):
        """  
        @summary: Test to verify creation of Monthly Snapshot policies from volume
         
        Step1: Creating a Volume.
        Step2: Attaching volume created in Step2 to virtual machine
        Step3: Detaching the volume created in step2 from virtual machine
        Step4: Listing snapshot policies for a volume created in step1
        Step5: Creating Monthly snapshot policy
        Step6: Listing snapshot policies for a volume created in step1 again
        Step7: Verifyign that the list snapshot policy length is increased by 1
        Step8: Deleting monthly snapshot policy created in step5
        Step9: List snapshot policies for a volume again
        Step10: Verifying that the list snapshot policy length is decreased by 1
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )
        self.assertIsNotNone(volume_created, "Volume not created")
        self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )
        # Attaching and Detaching custom volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )
        # Creating Monthly Snapshot Policy from volume
        self.services["recurring_snapshot"]["intervaltype"] = 'monthly'
        self.services["recurring_snapshot"]["schedule"] = '00:00:1'

        list_snapshot_policy_before = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        snapshot_policy_before_size = 0

        if list_snapshot_policy_before is not None:
            snapshot_policy_before_size = len(list_snapshot_policy_before)

        snapshot_policy_monthly = SnapshotPolicy.create(
                                                       self.userapiclient,
                                                       volume_created.id,
                                                       self.services["recurring_snapshot"])
        self.assertIsNotNone(
                             snapshot_policy_monthly,
                             "Monthly Snapshot policy creation failed"
                             )
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "schedule":self.services["recurring_snapshot"]["schedule"],
                         "intervaltype":3,
                         "volumeid":volume_created.id
                         }
        actual_dict = {
                       "schedule":snapshot_policy_monthly.schedule,
                       "intervaltype":snapshot_policy_monthly.intervaltype,
                       "volumeid":snapshot_policy_monthly.volumeid
                       }
        status = self.__verify_values(
                                      expected_dict,
                                      actual_dict
                                      )
        self.assertEqual(
                         True,
                         status,
                         "Monthly Snapshot Policy details are not as expected"
                         )

        list_snapshot_policy_after = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        self.assertIsNotNone(
                             list_snapshot_policy_after,
                             "Monthly Snapshot policy creation failed"
                             )
        self.assertEquals(
                          snapshot_policy_before_size + 1,
                          len(list_snapshot_policy_after),
                          "Monthly Snapshot policy creation failed"
                          )
        # Deleting monthly snapshot policy 
        SnapshotPolicy.delete(snapshot_policy_monthly, self.userapiclient)

        list_snapshot_policies = SnapshotPolicy.list(self.userapiclient, volumeid=volume_created.id)

        self.assertIsNone(
                          list_snapshot_policies, 
                          "Deletion of Monthly Snapshot policy failed"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_10_volume_snapshots_pagination(self):
        """  
        @summary: Test to verify pagination of snapshots for Volume
         
        Step1: Creating a Volume.
        Step2: Attaching volume created in Step2 to virtual machine
        Step3: Detaching the volume created in step2 from virtual machine
        Step4: Listing all the snapshots for a volume
        Step5: Creating Pagesize + 1 number of snapshots for a volume
        Step6: Listing all the snapshots for a volume
        Step7: Verifying that there are pagesize + 1 number of snapshots listsed
        Step8: Listing all the snapshots in page 1
        Step9: Listing all the snapshots in page 2
        Step10: Deleting the snapshot present in page 2
        Step11: Listign the snapshots from page 2 again and verifyign that list returns none
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        # Creating a Volume 
        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )
        self.assertIsNotNone(volume_created, "Volume not created")
        self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )
        #Attaching volume to virtual machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        #Detaching volume from virtual machine
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )

        #Creating 3 Snapshots from volume
        list_snapshot_before = Snapshot.list(
                                             self.userapiclient,
                                             volumeid=volume_created.id,
                                             listall=self.services["listall"]
                                             )
        self.assertIsNone(
                          list_snapshot_before,
                          "Newly created volume is already having snapshots"
                          )

        list_snapshot_before_size = 0
        for i in range(0, 3):
            snapshot_created = Snapshot.create(
                                       self.userapiclient,
                                       volume_created.id,
                                       )
            self.assertIsNotNone(snapshot_created, "Snapshot not created")
            self.cleanup.append(snapshot_created)
     
            self.assertEquals(
                              volume_created.id,
                              snapshot_created.volumeid,
                              "Snapshot not created for given volume"
                              )

        list_snapshot_after = Snapshot.list(
                                            self.userapiclient,
                                            volumeid=volume_created.id,
                                            listall=self.services["listall"]
                                            )
        self.assertEqual(
                         list_snapshot_before_size+3,
                         len(list_snapshot_after),
                         "Number of snapshots created is not matching expected"
                         )
        #Listing all the snapshots in page1
        list_snapshots_page1 = Snapshot.list(
                                             self.userapiclient,
                                             volumeid=volume_created.id,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=2
                                             )
        self.assertEqual(
                         2,
                         len(list_snapshots_page1),
                         "List snapshots response is not matching with the page size length for page 1"
                         )

        #Listing all the snapshots in page2 and ensuring only 1 snapshot is present 
        list_snapshots_page2 = Snapshot.list(
                                             self.userapiclient,
                                             volumeid=volume_created.id,
                                             listall=self.services["listall"],
                                             page=2,
                                             pagesize=2
                                             )
        self.assertEqual(
                         len(list_snapshots_page2),
                         1,
                         "List snapshots response is not matching with the page size length for page 2"
                         )
        snapshot_page2 = list_snapshots_page2[0]

        # Verifying that the snapshot on page 2 is not present in page1
        for i in range(0, len(list_snapshots_page1)):
            snapshot_page1 = list_snapshots_page1[i]
            self.assertNotEquals(
                                 snapshot_page2.id,
                                 snapshot_page1.id,
                                 "Snapshot listed in page 2 is also listed in page 1"
                                 )
        # Deleting a single snapshot and verifying that snapshot does not exists on page 2
        Snapshot.delete(snapshot_created, self.userapiclient)

        list_snapshot_page2 = Snapshot.list(
                                            self.userapiclient,
                                            volumeid=volume_created.id,
                                            listall=self.services["listall"],
                                            page=2,
                                            pagesize=2
                                            )
        self.assertEqual(
                         None,
                         list_snapshot_page2,
                         "Snapshot was not deleted"
                         )
        list_snapshot_page1 = Snapshot.list(
                                            self.userapiclient,
                                            volumeid=volume_created.id,
                                            listall=self.services["listall"],
                                            page=1,
                                            pagesize=2
                                            )
        self.assertEqual(
                         2,
                         len(list_snapshot_page1),
                         "Snapshots on page 1 are not matching"
                         )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_11_volume_extract(self):
        """  
        @summary: Test to verify extract/download a Volume
         
        Step1: Listing Volumes before creating a Volume
        Step2: Creating a Volume.
        Step3: Verifying that created volume is not none and adding to clean up
        Step4: Listing the volumes after creation
        Step5: Verifying that the list volume size is increased by 1
        Step6: Attaching volume created in Step2 to virtual machine
        Step7: Detaching the volume created in step2 from virtual machine
        Step8: Extracting/Downloadign the volume
        Step9: Verifyign that a download URL is created for volume download
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertIsNotNone(
                             list_volumes_before,
                             "volume not created for the vm launched at class level"
                             )
        volume_created = Volume.create(
                                    self.userapiclient,
                                    self.services["volume"],
                                    zoneid=self.zone.id,
                                    diskofferingid=self.disk_offering.id
                                    )

        self.assertIsNotNone(volume_created, "Volume not created")
        self.cleanup.append(volume_created)

        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertIsNotNone(
                             list_volumes_after,
                             "volume creation failed"
                             )
        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "Volume not created"
                          )
        #Attaching and Detaching volume created to Virtual Machine
        self.virtual_machine.attach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        attached_volume = list_volumes[0]

        self.assertIsNotNone(
                             attached_volume.vmname,
                             "VM is not attached to Volume"
                             )
        self.assertEquals(
                          self.virtual_machine.name,
                          attached_volume.vmname,
                          "VM Name is not matching with attached vm"
                          )
        self.virtual_machine.detach_volume(
                                           self.userapiclient,
                                           volume_created
                                           )
        list_volumes = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"],
                                   id=volume_created.id
                                   )
        detached_volume = list_volumes[0]
        self.assertIsNone(
                          detached_volume.vmname,
                          "VM is not detached from volume"
                          )
        #Extract/Download the volume
        self.services["mode"] = "HTTP_DOWNLOAD"

        extract_volume_response = Volume.extract(
                                                 self.userapiclient,
                                                 volume_created.id,
                                                 self.zone.id,
                                                 self.services["mode"]
                                                 )
        self.assertIsNotNone(extract_volume_response, "Extract/Download volume failed")

        self.assertEquals(
                          "DOWNLOAD_URL_CREATED",
                          extract_volume_response.state,
                          "Failed to create Download URL"
                          )
        self.assertIsNotNone(
                             extract_volume_response.url,
                             "Extract/Download volume URL is NULL"
                             )
        self.assertTrue(
                        (extract_volume_response.url.find("https://")!=-1),
                        "Extract/Download volume URL doesnot contain https://"
                        )
        self.assertEquals(
                          volume_created.id,
                          extract_volume_response.id,
                          "Extracted/Downloaded volume is not matching with original volume"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_12_volume_upload(self):
        """  
        @summary: Test to verify upload volume
        
        Step1: Listing the volumes for a user before uploading volume
        Step2: Uploading a volume
        Step3: Listing the volumes for a user after uploading data volume
        Step4: Verifying that the list volume length after upload is increased by 1
        """
        list_volumes_before = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertIsNotNone(
                             list_volumes_before,
                             "volume not created for the vm launched at class level"
                             )
        #Uploading a Volume
        volume_uploaded = Volume.upload(
                                       self.userapiclient,
                                       self.services["upload_volume"],
                                       self.zone.id
                                       )
        self.assertIsNotNone(volume_uploaded, "volume uploading failed")

        self.assertEquals(
                          self.services["upload_volume"]["diskname"],
                          volume_uploaded.name,
                          "Uploaded volume name is not matching with name provided while uploading")

        #Listing the volumes for a user after uploading data volume
        list_volumes_after = Volume.list(self.userapiclient, listall=self.services["listall"])

        self.assertIsNotNone(
                             list_volumes_after,
                             "volume not created for the vm launched at class level"
                             )
        #Asserting that the list volume length after upload is increased by 1
        self.assertEquals(
                          len(list_volumes_before) + 1,
                          len(list_volumes_after),
                          "upload volume failed"
                          )
        return

class TestListInstances(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        try:
            cls._cleanup = []        
            cls.testClient = super(TestListInstances, cls).getClsTestClient()
            cls.api_client = cls.testClient.getApiClient()
            cls.services = cls.testClient.getParsedTestDataConfig()

            # Get Domain, Zone, Template
            cls.domain = get_domain(cls.api_client)
            cls.zone = get_zone(cls.api_client)
            cls.template = get_template(
                                        cls.api_client,
                                        cls.zone.id,
                                        cls.services["ostype"]
                                        )
            if cls.zone.localstorageenabled:
                cls.storagetype = 'local'
                cls.services["service_offerings"]["tiny"]["storagetype"] = 'local'
                cls.services["disk_offering"]["storagetype"] = 'local'
            else:
                cls.storagetype = 'shared'
                cls.services["service_offerings"]["tiny"]["storagetype"] = 'shared'
                cls.services["disk_offering"]["storagetype"] = 'shared'

            cls.services['mode'] = cls.zone.networktype
            cls.services["virtual_machine"]["hypervisor"] = cls.testClient.getHypervisorInfo()
            cls.services["virtual_machine"]["zoneid"] = cls.zone.id
            cls.services["virtual_machine"]["template"] = cls.template.id
            cls.services["custom_volume"]["zoneid"] = cls.zone.id

            # Creating Disk offering, Service Offering and Account
            cls.disk_offering = DiskOffering.create(
                                                    cls.api_client,
                                                    cls.services["disk_offering"]
                                                    )
            cls._cleanup.append(cls.disk_offering)
            cls.service_offering = ServiceOffering.create(
                                                          cls.api_client,
                                                          cls.services["service_offerings"]["tiny"]
                                                          )
            cls._cleanup.append(cls.service_offering)
            cls.account = Account.create(
                                         cls.api_client,
                                         cls.services["account"],
                                         domainid=cls.domain.id
                                         )
            # Getting authentication for user in newly created Account
            cls.user = cls.account.user[0]
            cls.userapiclient = cls.testClient.getUserApiClient(cls.user.username, cls.domain.name)
            # Updating resource Limits
            for i in range(0,12):
                Resources.updateLimit(
                                      cls.api_client,
                                      account=cls.account.name,
                                      domainid=cls.domain.id,
                                      max=-1,
                                      resourcetype=i
                                      )

            cls._cleanup.append(cls.account)
        except Exception as e:
            cls.tearDownClass()
            raise Exception("Warning: Exception in setup : %s" % e)
        return

    def setUp(self):

        self.apiClient = self.testClient.getApiClient()
        self.cleanup = []

    def tearDown(self):
        #Clean up, terminate the created resources
        cleanup_resources(self.apiClient, self.cleanup)
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

        return

    def __verify_values(self, expected_vals, actual_vals):
        """  
        @Desc: Function to verify expected and actual values
        @Steps:
        Step1: Initializing return flag to True
        Step1: Verifying length of expected and actual dictionaries is matching.
               If not matching returning false
        Step2: Listing all the keys from expected dictionary
        Step3: Looping through each key from step2 and verifying expected and actual dictionaries have same value
               If not making return flag to False
        Step4: returning the return flag after all the values are verified
        """
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
                self.debug("expected Value: %s, is not matching with actual value: %s" % (
                                                                                          exp_val,
                                                                                          act_val
                                                                                          ))
        return return_flag

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_01_list_instances_pagination(self):
        """  
        @Desc: Test List Instances pagination
        @Steps:
        Step1: Listing all the Instances for a user
        Step2: Verifying listed Instances for account created at class level
        Step3: If number of volumes is less than (page size + 1), then creating them
        Step4: Listing all the volumes again after creation of volumes
        Step5: Verifying the length of the volumes is (page size + 1)
        Step6: Listing all the volumes in page1
        Step7: Verifying that the length of the volumes in page 1 is (page size)
        Step8: Listing all the volumes in page2
        Step9: Verifying that the length of the volumes in page 2 is 1
        Step10: Deleting the volume present in page 2
        Step11: Listing for the volumes on page 2
        Step12: Verifying that there are no volumes present in page 2
        """
        # Listing all the instances for a user
        list_instances_before = VirtualMachine.list(self.userapiclient, listall=self.services["listall"])

        # Verifying listed instances for account created at class level
        self.assertIsNone(
                          list_instances_before,
                          "Virtual Machine already exists for newly created user"
                          )
        # If number of instances are less than (pagesize + 1), then creating them    
        for i in range(0, (self.services["pagesize"] + 1)):
            vm_created = VirtualMachine.create(
                                               self.userapiclient,
                                               self.services["virtual_machine"],
                                               accountid=self.account.name,
                                               domainid=self.account.domainid,
                                               serviceofferingid=self.service_offering.id,
                                               )
            self.assertIsNotNone(
                                 vm_created,
                                 "VM creation failed"
                                 )
            if(i < (self.services["pagesize"])):
                self.cleanup.append(vm_created)

            self.assertEqual(
                             self.services["virtual_machine"]["displayname"],
                             vm_created.displayname,
                             "Newly created VM name and the test data VM name are not matching"
                             )

        # Listing all the instances again after creating VM's        
        list_instances_after = VirtualMachine.list(self.userapiclient, listall=self.services["listall"])
        status = validateList(list_instances_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of instances after creation failed"
                          )
        # Verifying the length of the instances is (page size + 1)
        self.assertEqual(
                         len(list_instances_after),
                         (self.services["pagesize"] + 1),
                         "Number of instances created is not matching as expected"
                         )

        # Listing all the volumes in page1
        list_instances_page1 = VirtualMachine.list(
                                                   self.userapiclient,
                                                   listall=self.services["listall"],
                                                   page=1,
                                                   pagesize=self.services["pagesize"],
                                                   domainid=self.account.domainid
                                                   )
        status = validateList(list_instances_page1)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of instances in page1 failed"
                          )
        # Verifying that the length of the instances in page 1 is (page size)
        self.assertEqual(
                         self.services["pagesize"],
                         len(list_instances_page1),
                         "List VM response is not matching with the page size length for page 1"
                         )

        # Listing all the VM's in page2
        list_instances_page2 = VirtualMachine.list(
                                                   self.userapiclient,
                                                   listall=self.services["listall"],
                                                   page=2,
                                                   pagesize=self.services["pagesize"],
                                                   domainid=self.account.domainid
                                                   )
        status = validateList(list_instances_page2)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of instances in page2 failed"
                          )
        # Verifying that the length of the VM's in page 2 is 1
        self.assertEqual(
                         1,
                         len(list_instances_page2),
                         "List VM response is not matching with the page size length for page 2"
                         )
        instance_page2 = list_instances_page2[0]

        # Verifying that the VM on page 2 is not present in page1
        for i in range(0, len(list_instances_page1)):
            instance_page1 = list_instances_page1[i]
            self.assertNotEquals(
                                 instance_page2.id,
                                 instance_page1.id,
                                 "VM listed in page 2 is also listed in page 1"
                                 )

        # Deleting a single VM
        VirtualMachine.delete(vm_created, self.userapiclient)

        # Listing the VM's in page 2
        list_instance_response = VirtualMachine.list(
                                                     self.userapiclient,
                                                     listall=self.services["listall"],
                                                     page=2,
                                                     pagesize=self.services["pagesize"],
                                                     domainid=self.account.domainid
                                                     )
        # verifying that VM does not exists on page 2
        self.assertEqual(
                        list_instance_response,
                        None,
                        "VM was not deleted"
                        )
        return
 
    @attr(tags=["advanced", "basic", "selfservice"])
    def test_02_list_Running_vm(self):
        """  
        @Desc: Test List Running VM's
        @Steps:
        Step1: Listing all the Running VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Listing all the Running VMs for a user again
        Step5: Verifying that the size of the list is increased by 1
        Step6: Verifying that the details of the Running VM listed are same as the VM deployed in Step3
        """
        # Listing all the Running VM's for a User
        list_running_vms_before = VirtualMachine.list(
                                                      self.userapiclient,
                                                      listall=self.services["listall"],
                                                      page=1,
                                                      pagesize=self.services["pagesize"],
                                                      domainid=self.account.domainid,
                                                      state="Running"
                                                      )
        self.assertIsNone(
                          list_running_vms_before,
                          "Virtual Machine already exists for newly created user"
                          )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the Running VM's for a User
        list_running_vms_after = VirtualMachine.list(
                                                      self.userapiclient,
                                                      listall=self.services["listall"],
                                                      page=1,
                                                      pagesize=self.services["pagesize"],
                                                      domainid=self.account.domainid,
                                                      state="Running"
                                                      )
        status = validateList(list_running_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Newly created VM is not in Running state"
                          )
        # Verifying list size is 1
        self.assertEquals(
                          1,
                          len(list_running_vms_after),
                          "Running VM list count is not matching"
                          )
        running_vm = list_running_vms_after[0]

        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":"Running",
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":running_vm.id,
                       "name":running_vm.name,
                       "displayname":running_vm.displayname,
                       "state":running_vm.state,
                       "zoneid":running_vm.zoneid,
                       "account":running_vm.account,
                       "template":running_vm.templateid
                       }
        running_vm_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         running_vm_status,
                         "Listed Running VM details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_03_list_Stopped_vm(self):
        """  
        @Desc: Test List Stopped VM's
        @Steps:
        Step1: Listing all the Stopped VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Stopping the VM deployed in step3
        Step5: Listing all the Stopped VMs for a user again
        Step6: Verifying that the size of the list is increased by 1
        Step7: Verifying that the details of the Stopped VM listed are same as the VM stopped in Step4
        """
        # Listing all the Stopped VM's for a User
        list_stopped_vms_before = VirtualMachine.list(
                                                      self.userapiclient,
                                                      listall=self.services["listall"],
                                                      page=1,
                                                      pagesize=self.services["pagesize"],
                                                      domainid=self.account.domainid,
                                                      state="Stopped"
                                                      )
        self.assertIsNone(
                           list_stopped_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Stopping the VM
        VirtualMachine.stop(vm_created, self.userapiclient)
        # Listing all the Stopped VM's for a User
        list_stopped_vms_after = VirtualMachine.list(
                                                      self.userapiclient,
                                                      listall=self.services["listall"],
                                                      page=1,
                                                      pagesize=self.services["pagesize"],
                                                      domainid=self.account.domainid,
                                                      state="Stopped"
                                                      )
        status = validateList(list_stopped_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Stopped VM is not in Stopped state"
                          )
        # Verifying list size is 1
        self.assertEquals(
                          1,
                          len(list_stopped_vms_after),
                          "Stopped VM list count is not matching"
                          )
        stopped_vm = list_stopped_vms_after[0]
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":"Stopped",
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":stopped_vm.id,
                       "name":stopped_vm.name,
                       "displayname":stopped_vm.displayname,
                       "state":stopped_vm.state,
                       "zoneid":stopped_vm.zoneid,
                       "account":stopped_vm.account,
                       "template":stopped_vm.templateid
                       }
        stopped_vm_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         stopped_vm_status,
                         "Listed Stopped VM details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_04_list_Destroyed_vm(self):
        """  
        @Desc: Test List Destroyed VM's
        @Steps:
        Step1: Listing all the Destroyed VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Destroyed the VM deployed in step3
        Step5: Listing all the Destroyed VMs for a user again
        Step6: Verifying that destroyed VM is not listed for User
        Step7: Listing all the destroyed VMs as admin
        Step8: Verifying that the size of the list is 1
        Step9: Verifying that the details of the Destroyed VM listed are same as the VM destroyed in Step4
        """
        # Listing all the Destroyed VM's for a User
        list_destroyed_vms_before = VirtualMachine.list(
                                                      self.userapiclient,
                                                      listall=self.services["listall"],
                                                      page=1,
                                                      pagesize=self.services["pagesize"],
                                                      domainid=self.account.domainid,
                                                      state="Destroyed"
                                                      )
        self.assertIsNone(
                           list_destroyed_vms_before,
                           "Virtual Machine in Destroyed state already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        # Destroying the VM
        VirtualMachine.delete(vm_created, self.userapiclient)
        # Listing all the Destroyed VM's for a User
        list_destroyed_vms_after = VirtualMachine.list(
                                                      self.userapiclient,
                                                      listall=self.services["listall"],
                                                      page=1,
                                                      pagesize=self.services["pagesize"],
                                                      domainid=self.account.domainid,
                                                      state="Destroyed"
                                                      )
        self.assertIsNone(
                          list_destroyed_vms_after,
                          "Destroyed VM is not in destroyed state"
                          )
        # Listing destroyed VMs as admin user
        list_destroyed_vms_admin = VirtualMachine.list(
                                                       self.apiClient,
                                                       listall=self.services["listall"],
                                                       page=1,
                                                       pagesize=self.services["pagesize"],
                                                       domainid=self.account.domainid,
                                                       state="Destroyed",
                                                       id=vm_created.id
                                                       )
        status = validateList(list_destroyed_vms_admin)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Destroyed VM is not in Destroyed state"
                          )
        # Verifying that the length of the destroyed VMs list should be 1
        self.assertEquals(
                          1,
                          len(list_destroyed_vms_admin),
                          "Destroyed VM list count is not matching"
                          )
        destroyed_vm = list_destroyed_vms_admin[0]
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":"Destroyed",
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":destroyed_vm.id,
                       "name":destroyed_vm.name,
                       "displayname":destroyed_vm.displayname,
                       "state":destroyed_vm.state,
                       "zoneid":destroyed_vm.zoneid,
                       "account":destroyed_vm.account,
                       "template":destroyed_vm.templateid
                       }
        destroyed_vm_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         destroyed_vm_status,
                         "Listed Destroyed VM details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_05_list_vm_by_id(self):
        """  
        @Desc: Test List VM by Id
        @Steps:
        Step1: Listing all the VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Listing all the VMs for a user again
        Step5: Verifying that the size of the list is increased by 1
        Step6: List a VM by specifying the Id if the VM deployed in Step3
        Step7: Verifying that the details of the Listed VM are same as the VM deployed in Step3
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              account=self.account.name
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VM's for a User
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=self.services["pagesize"],
                                             domainid=self.account.domainid,
                                             account=self.account.name
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM after creation failed"
                          )
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing a VM by Id
        list_vm_byid = VirtualMachine.list(
                                           self.userapiclient,
                                           listall=self.services["listall"],
                                           id=vm_created.id
                                           )
        status = validateList(list_vm_byid)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM by Id failed"
                          )
        listed_vm = list_vm_byid[0]
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":vm_created.state,
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":listed_vm.id,
                       "name":listed_vm.name,
                       "displayname":listed_vm.displayname,
                       "state":listed_vm.state,
                       "zoneid":listed_vm.zoneid,
                       "account":listed_vm.account,
                       "template":listed_vm.templateid
                       }
        list_vm_status = self.__verify_values(
                                              expected_dict,
                                              actual_dict
                                              )
        self.assertEqual(
                         True,
                         list_vm_status,
                         "Listed VM by Id details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_06_list_vm_by_name(self):
        """  
        @Desc: Test List VM's by Name
        @Steps:
        Step1: Listing all the VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a 2 VM's
        Step4: Listing all the VMs for a user again
        Step5: Verifying that list size is increased by 2
        Step6: Listing the VM by specifying complete name of VM-1 created in step3
        Step7: Verifying that the size of the list is 1
        Step8: Verifying that the details of the listed VM are same as the VM-1 created in step3
        Step9: Listing the VM by specifying the partial name of VM
        Step10: Verifying that the size of the list is 2
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        vms = {}
        for i in range(0, 2):
            # Deploying a VM
            vm_created = VirtualMachine.create(
                                               self.userapiclient,
                                               self.services["virtual_machine"],
                                               accountid=self.account.name,
                                               domainid=self.account.domainid,
                                               serviceofferingid=self.service_offering.id,
                                               )
            self.assertIsNotNone(
                                 vm_created,
                                 "VM creation failed"
                                 )
            self.cleanup.append(vm_created)
            vms.update({i: vm_created})
  
        # Listing all the VM's for a User
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=self.services["pagesize"],
                                             domainid=self.account.domainid,
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM's creation failed"
                          )
        self.assertEquals(
                          2,
                          len(list_vms_after),
                          "VM's list count is not matching"
                          )
        # Listing the VM by complete name
        list_vm_byfullname = VirtualMachine.list(
                                                 self.userapiclient,
                                                 listall=self.services["listall"],
                                                 page=1,
                                                 pagesize=self.services["pagesize"],
                                                 domainid=self.account.domainid,
                                                 name=vms[0].name
                                                 )
        status = validateList(list_vm_byfullname)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list VM by Name"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vm_byfullname),
                          "VM list by full name count is not matching"
                          )
        # Verifying that the details of the listed VM are same as the VM created above
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vms[0].id,
                         "name":vms[0].name,
                         "displayname":vms[0].displayname,
                         "state":vms[0].state,
                         "zoneid":vms[0].zoneid,
                         "account":vms[0].account,
                         "template":vms[0].templateid
                         }
        actual_dict = {
                       "id":list_vm_byfullname[0].id,
                       "name":list_vm_byfullname[0].name,
                       "displayname":list_vm_byfullname[0].displayname,
                       "state":list_vm_byfullname[0].state,
                       "zoneid":list_vm_byfullname[0].zoneid,
                       "account":list_vm_byfullname[0].account,
                       "template":list_vm_byfullname[0].templateid
                       }
        list_vm_status = self.__verify_values(
                                              expected_dict,
                                              actual_dict
                                              )
        self.assertEqual(
                         True,
                         list_vm_status,
                         "Listed VM details are not as expected"
                         )
        # Listing the VM by partial name
        list_vm_bypartialname = VirtualMachine.list(
                                                 self.userapiclient,
                                                 listall=self.services["listall"],
                                                 domainid=self.account.domainid,
                                                 name=vms[0].name[:1]
                                                 )
        status = validateList(list_vm_bypartialname)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list VM by Name"
                          )
        # Verifying that the size of the list is 2
        self.assertEquals(
                          2,
                          len(list_vm_bypartialname),
                          "VM list by full name count is not matching"
                          )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_07_list_vm_by_name_state(self):
        """  
        @Desc: Test List VM's by Name and State
        @Steps:
        Step1: Listing all the VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Listing all the VMs for a user again
        Step5: Verifying that list size is increased by 1
        Step6: Listing the VM by specifying name of VM created in step3 and state as Running (matching name and state)
        Step7: Verifying that the size of the list is 1
        Step8: Verifying that the details of the listed VM are same as the VM created in step3
        Step9: Listing the VM by specifying name of VM created in step3 and state as Stopped (non matching state)
        Step10: Verifying that the size of the list is 0
        Step11: Listing the VM by specifying non matching name and state as Running (non matching name)
        Step12: Verifying that the size of the list is 0
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VM's for a User
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=self.services["pagesize"],
                                             domainid=self.account.domainid,
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM's creation failed"
                          )
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM's list count is not matching"
                          )
        # Listing the VM by matching Name and State
        list_running_vm = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              name=vm_created.name,
                                              state="Running"
                                              )
        status = validateList(list_running_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "List VM by name and state failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_running_vm),
                          "Count of VM list by name and state is not matching"
                          )
        # Verifying that the details of the listed VM are same as the VM created above
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":"Running",
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":list_running_vm[0].id,
                       "name":list_running_vm[0].name,
                       "displayname":list_running_vm[0].displayname,
                       "state":list_running_vm[0].state,
                       "zoneid":list_running_vm[0].zoneid,
                       "account":list_running_vm[0].account,
                       "template":list_running_vm[0].templateid
                       }
        list_vm_status = self.__verify_values(
                                              expected_dict,
                                              actual_dict
                                              )
        self.assertEqual(
                         True,
                         list_vm_status,
                         "Listed VM details are not as expected"
                         )
        # Listing the VM by matching name and non matching state
        list_running_vm = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              name=vm_created.name,
                                              state="Stopped"
                                              )
        self.assertIsNone(
                          list_running_vm,
                          "Listed VM with non matching state"
                          )
        # Listing the VM by non matching name and matching state
        list_running_vm = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              name="name",
                                              state="Running"
                                              )
        self.assertIsNone(
                          list_running_vm,
                          "Listed VM with non matching name"
                          )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_08_list_vm_by_zone(self):
        """  
        @Desc: Test List VM by Zone. 
        This test case is applicable for a setup having multiple zones.
        @Steps:
        Step1: Listing all the zones
        Step2: Checking if there are multiple zones in the setup.
               Continuing below steps only if there are multiple zones
        Step3: Listing template for zone
        Step4: Listing all the VMs for a user
        Step5: Verifying that the size of the list is 0
        Step6: Deploying a VM
        Step7: Listing all the VMs for a user again for matching zone
        Step8: Verifying that the size of the list is 1
        Step9: Verifying that the details of the Listed VM are same as the VM deployed in Step6
        Step10: Listing all the VMs for a user again for non-matching zone
        Step11: Verifying that the size of the list is 0
        """
        # Listing all the zones available
        zones_list = Zone.list(self.apiClient)
        status = validateList(zones_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "zones not available in the given setup"
                          )
        current_zone = self.services["virtual_machine"]["zoneid"]
        current_template = self.services["virtual_machine"]["template"]
        # Checking if there are multiple zones in the setup.
        if not len(zones_list) > 1:
            self.debug("Setup is not having multiple zones")
        else:
            # Getting the template available under the zone
            template = get_template(
                                    self.apiClient,
                                    zones_list[0].id,
                                    self.services["ostype"]
                                    )
            self.assertIsNotNone(
                                 template,
                                 "Template not found for zone"
                                 )
            self.services["virtual_machine"]["zoneid"] = zones_list[0].id
            self.services["virtual_machine"]["template"] = template.id
            # Listing all the VM's for a User
            list_vms_before = VirtualMachine.list(
                                                  self.userapiclient,
                                                  listall=self.services["listall"],
                                                  page=1,
                                                  pagesize=self.services["pagesize"],
                                                  domainid=self.account.domainid,
                                                  zoneid=zones_list[0].id
                                                  )
            self.assertIsNone(
                               list_vms_before,
                               "Virtual Machine already exists for newly created user"
                               )
            # Deploying a VM
            vm_created = VirtualMachine.create(
                                               self.userapiclient,
                                               self.services["virtual_machine"],
                                               accountid=self.account.name,
                                               domainid=self.account.domainid,
                                               serviceofferingid=self.service_offering.id,
                                               )
            self.assertIsNotNone(
                                 vm_created,
                                 "VM creation failed"
                                 )
            self.cleanup.append(vm_created)
            # Listing all the VMs for a user again for matching zone
            list_vms_after = VirtualMachine.list(
                                                 self.userapiclient,
                                                 listall=self.services["listall"],
                                                 page=1,
                                                 pagesize=self.services["pagesize"],
                                                 domainid=self.account.domainid,
                                                 zoneid=zones_list[0].id
                                                 )
            status = validateList(list_vms_after)
            self.assertEquals(
                              PASS,
                              status[0],
                              "VM creation failed"
                              )
            # Verifying that the size of the list is 1
            self.assertEquals(
                              1,
                              len(list_vms_after),
                              "VM list count is not matching"
                              )
            listed_vm = list_vms_after[0]
            # Verifying that the details of the Listed VM are same as the VM deployed above
            #Creating expected and actual values dictionaries
            expected_dict = {
                               "id":vm_created.id,
                               "name":vm_created.name,
                               "displayname":vm_created.displayname,
                               "state":vm_created.state,
                               "zoneid":vm_created.zoneid,
                               "account":vm_created.account,
                               "template":vm_created.templateid
                               }
            actual_dict = {
                               "id":listed_vm.id,
                               "name":listed_vm.name,
                               "displayname":listed_vm.displayname,
                               "state":listed_vm.state,
                               "zoneid":listed_vm.zoneid,
                               "account":listed_vm.account,
                               "template":listed_vm.templateid
                               }
            list_vm_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
            self.assertEqual(
                             True,
                             list_vm_status,
                             "Listed VM by Id details are not as expected"
                             )
            # Listing all the VMs for a user again for non-matching zone
            list_vms = VirtualMachine.list(
                                           self.userapiclient,
                                           listall=self.services["listall"],
                                           page=1,
                                           pagesize=self.services["pagesize"],
                                           domainid=self.account.domainid,
                                           zoneid=zones_list[1].id
                                           )
            self.assertIsNone(
                              list_vms,
                              "VM's listed for non matching zone"
                              )
            self.services["virtual_machine"]["zoneid"] = current_zone
            self.services["virtual_machine"]["template"] = current_template
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_09_list_vm_by_zone_name(self):
        """  
        @Desc: Test List VM by Zone. 
        This test case is applicable for a setup having multiple zones.
        @Steps:
        Step1: Listing all the zones
        Step2: Checking if there are multiple zones in the setup.
               Continuing below steps only if there are multiple zones
        Step3: Listing template for zone
        Step4: Listing all the VMs for a user
        Step5: Verifying that the size of the list is 0
        Step6: Deploying a VM
        Step7: Listing all the VMs for a user again
        Step8: Verifying that list size is increased by 1
        Step9: Listing the VM by specifying name of VM created in step6 and matching zone (matching name and zone)
        Step10: Verifying that the size of the list is 1
        Step11: Verifying that the details of the listed VM are same as the VM created in step3
        Step12: Listing the VM by specifying name of VM created in step6 and non matching zone (non matching zone)
        Step13: Verifying that the size of the list is 0
        Step14: Listing the VM by specifying non matching name and matching zone (non matching name)
        Step15: Verifying that the size of the list is 0
        """
        # Listing all the zones available
        zones_list = Zone.list(self.apiClient)
        status = validateList(zones_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "zones not available in the given setup"
                          )
        current_zone = self.services["virtual_machine"]["zoneid"]
        current_template = self.services["virtual_machine"]["template"]
        # Checking if there are multiple zones in the setup.
        if not len(zones_list) > 1:
            self.debug("Setup is not having multiple Zones")
        else:
            # Getting the template available under the zone
            template = get_template(
                                    self.apiClient,
                                    zones_list[0].id,
                                    self.services["ostype"]
                                    )
            self.assertIsNotNone(
                                 template,
                                 "Template not found for zone"
                                 )
            self.services["virtual_machine"]["zoneid"] = zones_list[0].id
            self.services["virtual_machine"]["template"] = template.id
            # Listing all the VM's for a User
            list_vms_before = VirtualMachine.list(
                                                  self.userapiclient,
                                                  listall=self.services["listall"],
                                                  page=1,
                                                  pagesize=self.services["pagesize"],
                                                  domainid=self.account.domainid,
                                                  zoneid=zones_list[0].id,
                                                  account=self.account.name
                                                  )
            self.assertIsNone(
                               list_vms_before,
                               "Virtual Machine already exists for newly created user"
                               )
            # Deploying a VM
            vm_created = VirtualMachine.create(
                                               self.userapiclient,
                                               self.services["virtual_machine"],
                                               accountid=self.account.name,
                                               domainid=self.account.domainid,
                                               serviceofferingid=self.service_offering.id,
                                               )
            self.assertIsNotNone(
                                 vm_created,
                                 "VM creation failed"
                                 )
            self.cleanup.append(vm_created)
            # Listing all the VMs for a user again for matching zone
            list_vms_after = VirtualMachine.list(
                                                 self.userapiclient,
                                                 listall=self.services["listall"],
                                                 page=1,
                                                 pagesize=self.services["pagesize"],
                                                 domainid=self.account.domainid,
                                                 zoneid=zones_list[0].id,
                                                 account=self.account.name
                                                 )
            status = validateList(list_vms_after)
            self.assertEquals(
                              PASS,
                              status[0],
                              "VM creation failed"
                              )
            # Verifying that the size of the list is 1
            self.assertEquals(
                              1,
                              len(list_vms_after),
                              "VM list count is not matching"
                              )
            # Listing the VM by specifying name of VM created in above and matching zone
            list_vms = VirtualMachine.list(
                                           self.userapiclient,
                                           listall=self.services["listall"],
                                           page=1,
                                           pagesize=self.services["pagesize"],
                                           domainid=self.account.domainid,
                                           zoneid=zones_list[0].id,
                                           name=vm_created.name
                                           )
            status = validateList(list_vms)
            self.assertEquals(
                              PASS,
                              status[0],
                              "Listing VM's by name and zone failed"
                              )
            # Verifying Verifying that the size of the list is 1
            self.assertEquals(
                              1,
                              len(list_vms),
                              "Count of listed VM's by name and zone is not as expected"
                              )
            listed_vm = list_vms[0]
            # Verifying that the details of the Listed VM are same as the VM deployed above
            #Creating expected and actual values dictionaries
            expected_dict = {
                             "id":vm_created.id,
                             "name":vm_created.name,
                             "displayname":vm_created.displayname,
                             "state":vm_created.state,
                             "zoneid":vm_created.zoneid,
                             "account":vm_created.account,
                             "template":vm_created.templateid
                               }
            actual_dict = {
                               "id":listed_vm.id,
                               "name":listed_vm.name,
                               "displayname":listed_vm.displayname,
                               "state":listed_vm.state,
                               "zoneid":listed_vm.zoneid,
                               "account":listed_vm.account,
                               "template":listed_vm.templateid
                               }
            list_vm_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
            self.assertEqual(
                             True,
                             list_vm_status,
                             "Listed VM by Id details are not as expected"
                             )
            # Listing the VM by specifying name of VM created in step3 and non matching zone
            list_vms = VirtualMachine.list(
                                           self.userapiclient,
                                           listall=self.services["listall"],
                                           page=1,
                                           pagesize=self.services["pagesize"],
                                           domainid=self.account.domainid,
                                           zoneid=zones_list[1].id,
                                           name=vm_created.name
                                           )
            self.assertIsNone(
                              list_vms,
                              "VM's listed for non matching zone"
                              )
            # Listing the VM by specifying non matching name of VM and matching zone
            list_vms = VirtualMachine.list(
                                           self.userapiclient,
                                           listall=self.services["listall"],
                                           page=1,
                                           pagesize=self.services["pagesize"],
                                           domainid=self.account.domainid,
                                           zoneid=zones_list[0].id,
                                           name="name"
                                           )
            self.assertIsNone(
                              list_vms,
                              "VM's listed for non matching zone"
                              )
            self.services["virtual_machine"]["zoneid"] = current_zone
            self.services["virtual_machine"]["template"] = current_template
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_10_list_vm_by_zone_name_state(self):
        """  
        @Desc: Test List VM by Zone. 
        @Steps:
        Step1: Listing all the VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Listing all the VMs for a user again
        Step5: Verifying that list size is increased by 1
        Step6: Listing the VM by specifying name of VM created in step3 and matching zone and state as Running
        Step7: Verifying that the size of the list is 1
        Step8: Verifying that the details of the listed VM are same as the VM created in step3
        Step9: Listing the VM by specifying name of VM created in step3 and matching zone and state as Stopped
        Step10: Verifying that the size of the list is 0
        Step11: Listing the VM by name, Zone and account
        Step12: Verifying that the size of the list is 1
        Step13: Verifying that the details of the listed VM are same as the VM created in step3
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              zoneid=self.zone.id,
                                              account=self.account.name
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again for matching zone
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=self.services["pagesize"],
                                             domainid=self.account.domainid,
                                             zoneid=self.zone.id,
                                             account=self.account.name
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing the VM by specifying name of VM created in step3 and matching zone and state as Running
        list_vms = VirtualMachine.list(
                                       self.userapiclient,
                                       listall=self.services["listall"],
                                       page=1,
                                       pagesize=self.services["pagesize"],
                                       domainid=self.account.domainid,
                                       zoneid=self.zone.id,
                                       name=vm_created.name,
                                       state="Running"
                                       )
        status = validateList(list_vms)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing VM's by name and zone failed"
                          )
        # Verifying Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms),
                          "Count of listed VM's by name, zone and state is not as expected"
                          )
        listed_vm = list_vms[0]
        # Verifying that the details of the Listed VM are same as the VM deployed above
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":vm_created.state,
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                       }
        actual_dict = {
                       "id":listed_vm.id,
                       "name":listed_vm.name,
                       "displayname":listed_vm.displayname,
                       "state":listed_vm.state,
                       "zoneid":listed_vm.zoneid,
                       "account":listed_vm.account,
                       "template":listed_vm.templateid
                       }
        list_vm_status = self.__verify_values(
                                              expected_dict,
                                              actual_dict
                                              )
        self.assertEqual(
                         True,
                         list_vm_status,
                         "Listed VM by Id details are not as expected"
                         )
        # Listing the VM by specifying name of VM created in step3, zone and State as Stopped
        list_vms = VirtualMachine.list(
                                       self.userapiclient,
                                       listall=self.services["listall"],
                                       page=1,
                                       pagesize=self.services["pagesize"],
                                       domainid=self.account.domainid,
                                       zoneid=self.zone.id,
                                       name=vm_created.name,
                                       state="Stopped"
                                       )
        self.assertIsNone(
                          list_vms,
                          "VM's listed for non matching zone"
                          )
        # Listing the VM by name, zone and account
        list_vms = VirtualMachine.list(
                                       self.userapiclient,
                                       listall=self.services["listall"],
                                       page=1,
                                       pagesize=self.services["pagesize"],
                                       domainid=self.account.domainid,
                                       zoneid=self.zone.id,
                                       name=vm_created.name,
                                       account=self.account.name
                                       )
        status = validateList(list_vms)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing VM's by name, account and zone failed"
                          )
        # Verifying Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms),
                          "Count of listed VM's by name, zone and account is not as expected"
                          )
        listed_vm = list_vms[0]
        # Verifying that the details of the Listed VM are same as the VM deployed above
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":vm_created.state,
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":listed_vm.id,
                       "name":listed_vm.name,
                       "displayname":listed_vm.displayname,
                       "state":listed_vm.state,
                       "zoneid":listed_vm.zoneid,
                       "account":listed_vm.account,
                       "template":listed_vm.templateid
                       }
        list_vm_status = self.__verify_values(
                                              expected_dict,
                                              actual_dict
                                              )
        self.assertEqual(
                         True,
                         list_vm_status,
                         "Listed VM by Id details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_11_register_reset_vm_sshkey(self):
        """  
        @Desc: Test to verify registering and reset of SSH Key for VM
        @Steps:
        Step1: Deploying a VM
        Step2: Stopping the VM deployed in step1
        Step3: Listing all the SSH Key pairs
        Step4: Registering a SSH Key pair
        Step5: Listing all the SSh Key pairs again
        Step6: Verifying that the key pairs list is increased by 1
        Step7: Resetting the VM SSH Key to the key pair registered in step4
        Step8: Verifying that the registered SSH Key pair is set to the VM
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Stopping the VM deployed above
        vm_created.stop(
                        self.userapiclient,
                        forced=True
                        )
        # Listing VM details
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id
                                     )
        status = validateList(list_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        self.assertEquals(
                          "Stopped",
                          list_vm[0].state,
                          "Stopped VM is not in stopped state"
                          )
        # Listing all the SSH Key pairs
        list_keypairs_before = SSHKeyPair.list(
                                               self.userapiclient
                                               )
        list_keypairs_before_size = 0
        if list_keypairs_before is not None:
            list_keypairs_before_size = len(list_keypairs_before)

        # Registering new Key pair
        new_keypair = SSHKeyPair.register(
                                          self.userapiclient,
                                          name="keypair1",
                                          publickey="ssh-rsa: e6:9a:1e:b5:98:75:88:5d:56:bc:92:7b:43:48:05:b2"
                                          )
        self.assertIsNotNone(
                             new_keypair,
                             "New Key pair generation failed"
                             )
        self.assertEquals(
                          "keypair1",
                          new_keypair.name,
                          "Key Pair not created with given name"
                          )
        # Listing all the SSH Key pairs again
        list_keypairs_after = SSHKeyPair.list(
                                              self.userapiclient
                                              )
        status = validateList(list_keypairs_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of Key pairs failed"
                          )
        # Verifying that list size is increased by 1
        self.assertEquals(
                          list_keypairs_before_size + 1,
                          len(list_keypairs_after),
                          "List count is not matching"
                          )
        # Resetting the VM SSH key to the Key pair created above
        vm_created.resetSshKey(
                               self.userapiclient,
                               keypair=new_keypair.name
                               )
        # Listing VM details again
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id
                                     )
        status = validateList(list_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        self.assertEquals(
                          1,
                          len(list_vm),
                          "VMs list is not as expected"
                          )
        # Verifying that VM's SSH keypair is set to newly created keypair
        self.assertEquals(
                          new_keypair.name,
                          list_vm[0].keypair,
                          "VM is not set to newly created SSH Key pair"
                          )
        return

    @attr(tags=["advanced", "provisioning"])
    def test_12_vm_nics(self):
        """  
        @Desc: Test to verify Nics for a VM
        @Steps:
        Step1: Deploying a VM
        Step2: Listing all the Networks
        Step3: Verifying that the list size is 1
        Step4: Creating 1 network
        Step5: Listing all the networks again
        Step6: Verifying that the list size is 2
        Step7: Verifying that VM deployed in step1 has only 1 nic 
                and it is same as network listed in step3
        Step8: Adding the networks created in step4 to VM deployed in step1
        Step9: Verifying that VM deployed in step1 has 2 nics
        Step10: Verifying that isdefault is set to true for only 1 nic
        Step11: Verifying that isdefault is set to true for the Network created when deployed a VM
        Step12: Making the nic created in step4 as default nic
        Step13: Verifying that isdefault is set to true for only 1 nic
        Step14: Verifying that the isdefault is set to true for the nic created in step4
        Step15: Removing the non-default nic from VM
        Step16: Verifying that VM deployed in step1 has only 1 nic
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing all the networks before
        list_network_before = Network.list(
                                           self.userapiclient,
                                           isdefault="true",
                                           zoneid=self.zone.id,
                                           account=self.account.name,
                                           domainid=self.domain.id
                                           )
        status = validateList(list_network_before)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Default Network not created when deploying a VM"
                          )
        # Verifying that only 1 network is created while deploying a VM
        self.assertEquals(
                          1,
                          len(list_network_before),
                          "More than 1 default network exists"
                          )
        network1 = list_network_before[0]
        # Listing Network Offerings
        network_offerings_list = NetworkOffering.list(
                                                      self.apiClient,
                                                      forvpc="false",
                                                      guestiptype="Isolated",
                                                      state="Enabled",
                                                      supportedservices="SourceNat",
                                                      zoneid=self.zone.id
                                                      )
        self.assertIsNotNone(
                             network_offerings_list,
                             "Isolated Network Offerings with sourceNat enabled are not found"
                             )
        # Creating one more network
        network2 = Network.create(
                                  self.userapiclient,
                                  self.services["network"],
                                  accountid=self.account.name,
                                  domainid=self.domain.id,
                                  networkofferingid=network_offerings_list[0].id,
                                  zoneid=self.zone.id
                                  )
        self.assertIsNotNone(
                             network2,
                             "Network creation failed"
                             )
        self.cleanup.append(network2)
        # Listing all the networks again
        list_network_after = Network.list(
                                          self.userapiclient,
                                          zoneid=self.zone.id,
                                          account=self.account.name,
                                          domainid=self.domain.id
                                          )
        status = validateList(list_network_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "List of Networks failed"
                          )
        # Verifying that list size is 2
        self.assertEquals(
                          2,
                          len(list_network_after),
                          "More than 1 default network exists"
                          )
        # Verifying that VM created is having only 1 nic
        vm_nics_before = vm_created.nic
        self.assertIsNotNone(
                             vm_nics_before,
                             "Nic not found for the VM deployed"
                             )
        self.assertEquals(
                          1,
                          len(vm_nics_before),
                          "VM Nic count is not matching"
                          )
        # Verifying that the nic is same as the default network listed above
        self.assertEquals(
                          network1.id,
                          vm_nics_before[0].networkid,
                          "Default NIC for VM is not as expected"
                          )
        # Adding network2 created above to VM
        VirtualMachine.add_nic(
                               vm_created,
                               self.userapiclient,
                               network2.id
                               )
        # Listing the Vm details again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             id=vm_created.id
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        vm = list_vms_after[0]
        # Verifying that VM nics size is 2 now
        vm_nics_after = vm.nic
        self.assertIsNotNone(
                             vm_nics_after,
                             "Nic not found for the deployed VM"
                             )
        self.assertEquals(
                          2,
                          len(vm_nics_after),
                          "VM NIC's count is not matching"
                          )
        # Verifying that isdefault is set to true for only 1 nic
        default_count = 0
        for i in range(0, len(vm_nics_after)):
            if vm_nics_after[i].isdefault is True:
                default_count = default_count + 1
                default_nic = vm_nics_after[i]
            else:
                non_default_nic = vm_nics_after[i]
     
        self.assertEquals(
                          1,
                          default_count,
                          "Default NIC count is not matching"
                          )
        # Verifying that default NIC is same the network created when VM is deployed
        self.assertEquals(
                          network1.id,
                          default_nic.networkid,
                          "Default NIC is not matching for VM"
                          )
        # Updating network 2 as default NIC
        vm_created.update_default_nic(
                                      self.userapiclient,
                                      non_default_nic.id
                                      )
        # Listing the Vm details again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             id=vm_created.id
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        vm = list_vms_after[0]
        # Verifying that VM nics size is 2 now
        vm_nics_after = vm.nic
        self.assertIsNotNone(
                             vm_nics_after,
                             "Nic not found for the deployed VM"
                             )
        self.assertEquals(
                          2,
                          len(vm_nics_after),
                          "VM NIC's count is not matching"
                          )
        # Verifying that isdefault is set to true for only 1 nic
        default_count = 0
        for i in range(0, len(vm_nics_after)):
            if vm_nics_after[i].isdefault is True:
                default_count = default_count + 1
                default_nic = vm_nics_after[i]
            else:
                non_default_nic = vm_nics_after[i]
     
        self.assertEquals(
                          1,
                          default_count,
                          "Default NIC count is not matching"
                          )
        # Verifying that default NIC is same the newly updated network (network 2)
        self.assertEquals(
                          network2.id,
                          default_nic.networkid,
                          "Default NIC is not matching for VM"
                          )
        # Deleting non default NIC
        vm_created.remove_nic(
                              self.userapiclient,
                              non_default_nic.id
                              )
        # Listing the Vm details again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             id=vm_created.id
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        vm = list_vms_after[0]
        # Verifying that VM nics size is 1 now
        vm_nics_after = vm.nic
        self.assertIsNotNone(
                             vm_nics_after,
                             "Nic not found for the deployed VM"
                             )
        self.assertEquals(
                          1,
                          len(vm_nics_after),
                          "VM NIC's count is not matching"
                          )
        # Verifying the nic network is same as the default nic network
        self.assertEquals(
                          network2.id,
                          vm_nics_after[0].networkid,
                          "VM NIC is not same as expected"
                          )
        return

class TestInstances(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        try:
            cls._cleanup = []        
            cls.testClient = super(TestInstances, cls).getClsTestClient()
            cls.api_client = cls.testClient.getApiClient()
            cls.services = cls.testClient.getParsedTestDataConfig()

            # Get Domain, Zone, Template
            cls.domain = get_domain(cls.api_client)
            cls.zone = get_zone(cls.api_client)
            cls.template = get_template(
                                        cls.api_client,
                                        cls.zone.id,
                                        cls.services["ostype"]
                                        )
            if cls.zone.localstorageenabled:
                cls.storagetype = 'local'
                cls.services["service_offerings"]["tiny"]["storagetype"] = 'local'
                cls.services["disk_offering"]["storagetype"] = 'local'
            else:
                cls.storagetype = 'shared'
                cls.services["service_offerings"]["tiny"]["storagetype"] = 'shared'
                cls.services["disk_offering"]["storagetype"] = 'shared'
   
            cls.services['mode'] = cls.zone.networktype
            cls.services["virtual_machine"]["hypervisor"] = cls.testClient.getHypervisorInfo()
            cls.services["virtual_machine"]["zoneid"] = cls.zone.id
            cls.services["virtual_machine"]["template"] = cls.template.id
            cls.services["custom_volume"]["zoneid"] = cls.zone.id
   
            # Creating Disk offering, Service Offering and Account
            cls.disk_offering = DiskOffering.create(
                                                    cls.api_client,
                                                    cls.services["disk_offering"]
                                                    )
            cls._cleanup.append(cls.disk_offering)
            cls.service_offering = ServiceOffering.create(
                                                          cls.api_client,
                                                          cls.services["service_offerings"]["tiny"]
                                                          )
            cls._cleanup.append(cls.service_offering)
            cls.account = Account.create(
                                         cls.api_client,
                                         cls.services["account"],
                                         domainid=cls.domain.id
                                         )
            # Getting authentication for user in newly created Account
            cls.user = cls.account.user[0]
            cls.userapiclient = cls.testClient.getUserApiClient(cls.user.username, cls.domain.name)
            # Updating resource Limits
            for i in range(0,12):
                Resources.updateLimit(
                                      cls.api_client,
                                      account=cls.account.name,
                                      domainid=cls.domain.id,
                                      max=-1,
                                      resourcetype=i
                                      )
            cls._cleanup.append(cls.account)
        except Exception as e:
            cls.tearDownClass()
            raise Exception("Warning: Exception in setup : %s" % e)
        return

    def setUp(self):

        self.apiClient = self.testClient.getApiClient()
        self.cleanup = []

    def tearDown(self):
        #Clean up, terminate the created resources
        cleanup_resources(self.apiClient, self.cleanup)
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

        return

    def __verify_values(self, expected_vals, actual_vals):
        """  
        @Desc: Function to verify expected and actual values
        @Steps:
        Step1: Initializing return flag to True
        Step1: Verifying length of expected and actual dictionaries is matching.
               If not matching returning false
        Step2: Listing all the keys from expected dictionary
        Step3: Looping through each key from step2 and verifying expected and actual dictionaries have same value
               If not making return flag to False
        Step4: returning the return flag after all the values are verified
        """
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
                self.debug("expected Value: %s, is not matching with actual value: %s" % (
                                                                                          exp_val,
                                                                                          act_val
                                                                                          ))
        return return_flag

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_13_attach_detach_iso(self):
        """  
        @Desc: Test Attach ISO to VM and Detach ISO from VM. 
        @Steps:
        Step1: Listing all the VMs for a user
        Step2: Verifying that the size of the list is 0
        Step3: Deploying a VM
        Step4: Listing all the VMs for a user again
        Step5: Verifying that list size is increased by 1
        Step6: Listing all the ready ISO's
        Step7: If size of the list is >= 1 continuing to next steps
        Step8: Attaching the ISO listed to VM deployed in Step3
        Step9: Verifying that the attached ISO details are associated with VM
        Step10: Detaching the ISO attached in step8
        Step11: Verifying that detached ISO details are not associated with VM
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"],
                                              domainid=self.account.domainid,
                                              zoneid=self.zone.id,
                                              account=self.account.name
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again for matching zone
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=self.services["pagesize"],
                                             domainid=self.account.domainid,
                                             zoneid=self.zone.id,
                                             account=self.account.name
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing the ISO's in ready state
        isos_list = Iso.list(
                             self.userapiclient,
                             isready="true",
                             isofilter="executable",
                             zoneid=self.zone.id
                             )
        # Verifying if size of the list is >= 1
        if isos_list is not None:
            iso_toattach = isos_list[0]
            # Attaching ISO listed to VM deployed
            VirtualMachine.attach_iso(
                                      vm_created,
                                      self.userapiclient,
                                      iso_toattach
                                      )
            list_vm = VirtualMachine.list(
                                          self.userapiclient,
                                          id=vm_created.id
                                          )
            status = validateList(list_vm)
            self.assertEquals(
                              PASS,
                              status[0],
                              "VM listing by Id failed"
                              )
            # Verifying that attached ISO details are present in VM
            self.assertEquals(
                              iso_toattach.name,
                              list_vm[0].isoname,
                              "Attached ISO name is not matching"
                              )
            self.assertEquals(
                              iso_toattach.displaytext,
                              list_vm[0].isodisplaytext,
                              "Attached ISO display is not matching"
                              )
            # Detaching ISO from VM
            VirtualMachine.detach_iso(
                                      vm_created,
                                      self.userapiclient
                                      )
            list_vm = VirtualMachine.list(
                                          self.userapiclient,
                                          id=vm_created.id
                                          )
            status = validateList(list_vm)
            self.assertEquals(
                              PASS,
                              status[0],
                              "VM listing by Id failed"
                              )
            # Verifying that ISO details are NOT present in VM
            self.assertIsNone(
                              list_vm[0].isoname,
                              "ISO not detached from VM"
                              )
        else:
            self.fail("Executable ISO in Ready is not found in the given setup")

        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_14_vm_snapshot_pagination(self):
        """  
        @Desc: Test VM Snapshots pagination. 
        @Steps:
        Step1: Deploying a VM
        Step2: Listing all the Snapshots of the VM deployed in Step 1
        Step3: Verifying that the list size is 0
        Step4: Creating (pagesize + 1) number of Snapshots for the VM
        Step5: Listing all the Snapshots of the VM deployed in Step 1
        Step6: Verifying that the list size is (pagesize + 1) 
        Step7: Listing all the VM snapshots in Page 1 with page size
        Step8: Verifying that size of the list is same as page size
        Step9: Listing all the VM snapshots in Page 2 with page size
        Step10: Verifying that size of the list is 1
        Step11: Deleting VM snapshot in page 2
        Step12: Listing all the VM snapshots in Page 2 with page size
        Step13: Verifying that size of the list is 0
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing all the VM snapshots for VM deployed above
        list_snapshots_before = VmSnapshot.list(
                                                self.userapiclient,
                                                listall=self.services["listall"],
                                                virtualmachineid=vm_created.id
                                                )
        # Verifying that the VM snapshot list is None
        self.assertIsNone(
                          list_snapshots_before,
                          "Snapshots already exists for newly created VM"
                          )
        # Creating pagesize + 1 number of VM snapshots
        for i in range(0, (self.services["pagesize"] + 1)):
            snapshot_created = VmSnapshot.create(
                                                 self.userapiclient,
                                                 vm_created.id,
                                                 )
            self.assertIsNotNone(
                                 snapshot_created,
                                 "Snapshot creation failed"
                                 )

        # Listing all the VM snapshots for VM again
        list_snapshots_after = VmSnapshot.list(
                                                self.userapiclient,
                                                listall=self.services["listall"],
                                                virtualmachineid=vm_created.id
                                                )
        status = validateList(list_snapshots_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM Snapshots creation failed"
                          )
        self.assertEquals(
                          self.services["pagesize"] + 1,
                          len(list_snapshots_after),
                          "Count of VM Snapshots is not matching"
                          )
        # Listing all the VM snapshots in Page 1 with page size
        list_snapshots_page1 = VmSnapshot.list(
                                               self.userapiclient,
                                               listall=self.services["listall"],
                                               virtualmachineid=vm_created.id,
                                               page=1,
                                               pagesize=self.services["pagesize"],
                                               )
        status = validateList(list_snapshots_page1)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM Snapshots failed in page 1"
                          )
        # Verifying the list size is equal to pagesize
        self.assertEquals(
                          self.services["pagesize"],
                          len(list_snapshots_page1),
                          "List VM Snapshot count is not matching in page 1"
                          )
        # Listing all the VM Snapshots in page 2
        list_snapshots_page2 = VmSnapshot.list(
                                               self.userapiclient,
                                               listall=self.services["listall"],
                                               virtualmachineid=vm_created.id,
                                               page=2,
                                               pagesize=self.services["pagesize"],
                                               )
        status = validateList(list_snapshots_page2)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM Snapshots failed in page 2"
                          )
        # Verifying the list size is equal to 1
        self.assertEquals(
                          1,
                          len(list_snapshots_page2),
                          "List VM Snapshot count is not matching in page 2"
                          )
        # Deleting VM Snapshot in page 2
        VmSnapshot.deleteVMSnapshot(
                                    self.userapiclient,
                                    snapshot_created.id
                                    )
        # Listing all the VM Snapshots in page 2 again
        list_snapshots_page2 = VmSnapshot.list(
                                               self.userapiclient,
                                               listall=self.services["listall"],
                                               virtualmachineid=vm_created.id,
                                               page=2,
                                               pagesize=self.services["pagesize"],
                                               )
        # Verifying the list size is equal to 0
        self.assertIsNone(
                          list_snapshots_page2,
                          "VM Snapshots exists in page 2"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_15_revert_vm_to_snapshot(self):
        """  
        @Desc: Test Revert VM to Snapshot functionality. 
        @Steps:
        Step1: Deploying a VM
        Step2: Listing all the Snapshots of the VM deployed in Step 1
        Step3: Verifying that the list size is 0
        Step4: Creating 2 Snapshots for the VM
        Step5: Listing all the Snapshots of the VM deployed in Step 1
        Step6: Verifying that the list size is 2
        Step7: Verifying that only 1 snapshot is have current flag set to True
        Step8: Verifying that the VM snapshot with current flag set as true is the latest snapshot created
        Step9: Reverting VM to snapshot having current flag as false (non current snapshot)
        Step10: Verifying that only 1 VM snapshot is having current flag set as true.
        Step11: Verifying that the VM Snapshot with current flag set to true is the reverted snapshot in Step 8
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing all the VM snapshots for VM deployed above
        list_snapshots_before = VmSnapshot.list(
                                                self.userapiclient,
                                                listall=self.services["listall"],
                                                virtualmachineid=vm_created.id
                                                )
        # Verifying that the VM snapshot list is None
        self.assertIsNone(
                          list_snapshots_before,
                          "Snapshots already exists for newly created VM"
                          )
        # Creating 2 of VM snapshots
        snapshot1 = VmSnapshot.create(
                                      self.userapiclient,
                                      vm_created.id,
                                      )
        self.assertIsNotNone(
                             snapshot1,
                             "Snapshot creation failed"
                             )
        snapshot2 = VmSnapshot.create(
                                      self.userapiclient,
                                      vm_created.id,
                                      )
        self.assertIsNotNone(
                             snapshot2,
                             "Snapshot creation failed"
                             )
        # Listing all the VM snapshots for VM again
        list_snapshots_after = VmSnapshot.list(
                                                self.userapiclient,
                                                listall=self.services["listall"],
                                                virtualmachineid=vm_created.id
                                                )
        status = validateList(list_snapshots_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM Snapshots creation failed"
                          )
        self.assertEquals(
                          2,
                          len(list_snapshots_after),
                          "Count of VM Snapshots is not matching"
                          )
        # Verifying that only 1 snapshot is having current flag set to true 
        # and that snapshot is the latest snapshot created (snapshot2)
        current_count = 0
        for i in range(0, len(list_snapshots_after)):
            if(list_snapshots_after[i].current is True):
                current_count = current_count + 1
                current_snapshot = list_snapshots_after[i]
  
        self.assertEquals(
                          1,
                          current_count,
                          "count of VM Snapshot with current flag as true is not matching"
                          )
        self.assertEquals(
                          snapshot2.id,
                          current_snapshot.id,
                          "Latest snapshot taken is not marked as current"
                          )
        # Reverting the VM to Snapshot 1
        VmSnapshot.revertToSnapshot(
                                    self.userapiclient,
                                    snapshot1.id
                                    )
        # Listing the VM snapshots again
        list_snapshots_after = VmSnapshot.list(
                                                self.userapiclient,
                                                listall=self.services["listall"],
                                                virtualmachineid=vm_created.id
                                                )
        status = validateList(list_snapshots_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM Snapshots creation failed"
                          )
        self.assertEquals(
                          2,
                          len(list_snapshots_after),
                          "Count of VM Snapshots is not matching"
                          )
        # Verifying that only 1 snapshot is having current flag set to true 
        # and that snapshot is snapshot1
        current_count = 0
        for i in range(0, len(list_snapshots_after)):
            if(list_snapshots_after[i].current is True):
                current_count = current_count + 1
                current_snapshot = list_snapshots_after[i]
        self.assertEquals(
                          1,
                          current_count,
                          "count of VM Snapshot with current flag as true is not matching"
                          )
        self.assertEquals(
                          snapshot1.id,
                          current_snapshot.id,
                          "Current flag was set properly after reverting the VM to snapshot"
                          )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_16_list_vm_volumes_pagination(self):
        """  
        @Desc: Test to verify pagination of Volumes for a VM
        @Steps:
        Step1: Deploying a VM
        Step2: Listing all the Volumes of the VM deployed in Step 1
        Step3: Verifying that the list size is 1
        Step4: Creating page size number of volumes
        Step5: Attaching all the volumes created in step4 to VM deployed in Step1
        Step6: Listing all the Volumes for the VM in step1
        Step7: Verifying that the list size is equal to page size + 1
        Step8: Listing all the volumes of VM in page 1
        Step9: Verifying that the list size is equal to page size
        Step10: Listing all the Volumes in Page 2
        Step11: Verifying that the list size is 1
        Step12: Detaching the volume from the VM
        Step13: Listing all the Volumes in Page 2
        Step14: Verifying that list size is 0
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Listing all the Volumes for the VM deployed
        list_volumes_before = Volume.list(
                                          self.userapiclient,
                                          listall=self.services["listall"],
                                          virtualmachineid=vm_created.id
                                          )
        status = validateList(list_volumes_before)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Root volume is not created for VM deployed"
                          )
        # Verifying the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_volumes_before),
                          "Volumes count is not matching"
                          )
        # Creating Page size number of volumes
        for i in range(0, self.services["pagesize"]):
            volume_created = Volume.create(
                                   self.userapiclient,
                                   self.services["volume"],
                                   zoneid=self.zone.id,
                                   diskofferingid=self.disk_offering.id
                                   )
            self.assertIsNotNone(
                                 volume_created,
                                 "Volume is not created"
                                 )
            self.cleanup.append(volume_created)
            # Attaching all the volumes created to VM
            vm_created.attach_volume(
                                     self.userapiclient,
                                     volume_created
                                     )
  
        # List all the volumes for the VM again
        list_volumes_after = Volume.list(
                                          self.userapiclient,
                                          listall=self.services["listall"],
                                          virtualmachineid=vm_created.id
                                          )
        status = validateList(list_volumes_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Volumes are not listed"
                          )
        # Verifying that size of the list is equal to page size + 1
        self.assertEquals(
                          self.services["pagesize"] + 1,
                          len(list_volumes_after),
                          "VM's volume count is not matching"
                          )
        # Listing all the volumes for a VM in page 1
        list_volumes_page1 = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         virtualmachineid=vm_created.id,
                                         page=1,
                                         pagesize=self.services["pagesize"]
                                         )
        status = validateList(list_volumes_page1)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Volumes not listed in page1"
                          )
        # Verifying that list size is equal to page size
        self.assertEquals(
                          self.services["pagesize"],
                          len(list_volumes_page1),
                          "VM's volume count is not matching in page 1"
                          )
        # Listing all the volumes for a VM in page 2
        list_volumes_page2 = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         virtualmachineid=vm_created.id,
                                         page=2,
                                         pagesize=self.services["pagesize"]
                                         )
        status = validateList(list_volumes_page2)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Volumes not listed in page2"
                          )
        # Verifying that list size is equal to 1
        self.assertEquals(
                          1,
                          len(list_volumes_page2),
                          "VM's volume count is not matching in page 1"
                          )
        # Detaching 1 volume from VM
        vm_created.detach_volume(
                                 self.userapiclient,
                                 volume_created
                                 )
        # Listing all the volumes for a VM in page 2 again
        list_volumes_page2 = Volume.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         virtualmachineid=vm_created.id,
                                         page=2,
                                         pagesize=self.services["pagesize"]
                                         )
        # Verifying that there are no volumes present in page 2
        self.assertIsNone(
                          list_volumes_page2,
                          "Volumes listed in page 2"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_17_running_vm_scaleup(self):
        """  
        @Desc: Test to verify change service for Running VM
        @Steps:
        Step1: Checking if dynamic scaling of virtual machines is enabled in zone and template.
                If yes then continuing.
                If not then printing message that scale up is not possible for Running VM
        Step2: Deploying a VM
        Step3: Listing all the existing service offerings
        Step4: If there is a matching Service Offering for scale-up of running VM
                use that service offering. If not create one service offering for scale up.
        Step5: Perform change service (scale up) the Running VM deployed in step1
        Step6: Verifying that VM's service offerings is changed
        """
        # Checking if Dynamic scaling of VM is supported or not
        list_config = Configurations.list(
                                          self.apiClient,
                                          zoneid=self.zone.id,
                                          name="enable.dynamic.scale.vm"
                                          )
        status = validateList(list_config)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of configuration failed"
                          )
        # Checking if dynamic scaling is allowed in Zone and Template
        if not ((list_config[0].value is True) and (self.template.isdynamicallyscalable)):
             self.debug("Scale up of Running VM is not possible as Zone/Template does not support")
        else:
            # Listing all the VM's for a User
            list_vms_before = VirtualMachine.list(
                                                  self.userapiclient,
                                                  listall=self.services["listall"],
                                                  )
            self.assertIsNone(
                               list_vms_before,
                               "Virtual Machine already exists for newly created user"
                               )
            # Deploying a VM
            vm_created = VirtualMachine.create(
                                               self.userapiclient,
                                               self.services["virtual_machine"],
                                               accountid=self.account.name,
                                               domainid=self.account.domainid,
                                               serviceofferingid=self.service_offering.id,
                                               )
            self.assertIsNotNone(
                                 vm_created,
                                 "VM creation failed"
                                 )
            self.cleanup.append(vm_created)
            # Listing details of current Service Offering
            vm_so_list = ServiceOffering.list(
                                              self.userapiclient,
                                              id=vm_created.serviceofferingid
                                              )
            status = validateList(vm_so_list)
            self.assertEquals(
                              PASS,
                              status[0],
                              "Listing of VM Service offering failed"
                              )
            current_so = vm_so_list[0]
            # Listing all the VMs for a user again
            list_vms_after = VirtualMachine.list(
                                                 self.userapiclient,
                                                 listall=self.services["listall"],
                                                 )
            status = validateList(list_vms_after)
            self.assertEquals(
                              PASS,
                              status[0],
                              "VM creation failed"
                              )
            # Verifying that the size of the list is 1
            self.assertEquals(
                              1,
                              len(list_vms_after),
                              "VM list count is not matching"
                              )
            # Listing all the existing service offerings
            service_offerings_list = ServiceOffering.list(
                                                          self.userapiclient,
                                                          virtualmachineid=vm_created.id
                                                          )
            # Verifying if any Service offering available for scale up of VM
            so_exists = False
            if service_offerings_list is not None:
                for i in range(0, len(service_offerings_list)):
                    if not ((current_so.cpunumber > service_offerings_list[i].cpunumber or\
                            current_so.cpuspeed > service_offerings_list[i].cpuspeed or\
                            current_so.memory > service_offerings_list[i].memory) or\
                            (current_so.cpunumber == service_offerings_list[i].cpunumber and\
                            current_so.cpuspeed == service_offerings_list[i].cpuspeed and\
                            current_so.memory == service_offerings_list[i].memory)):
                        if(current_so.storagetype == service_offerings_list[i].storagetype):
                            so_exists = True
                            new_so = service_offerings_list[i]
                            break
            # If service offering does not exists, then creating one service offering for scale up
            if not so_exists:
                self.services["service_offerings"]["small"]["storagetype"] = current_so.storagetype
                new_so = ServiceOffering.create(
                                                self.apiClient,
                                                self.services["service_offerings"]["small"]
                                                )
                self.cleanup.append(new_so)
            # Scaling up the VM
            vm_created.scale_virtualmachine(
                                            self.userapiclient,
                                            new_so.id
                                            )
            # Listing VM details again
            list_vms_after = VirtualMachine.list(
                                                 self.userapiclient,
                                                 id=vm_created.id
                                                 )
            status = validateList(list_vms_after)
            self.assertEquals(
                              PASS,
                              status[0],
                              "Listing of VM failed"
                              )
            self.assertEquals(
                              1,
                              len(list_vms_after),
                              "VMs list is not as expected"
                              )
            # Verifying that VM's service offerings is changed
            self.assertEquals(
                              new_so.id,
                              list_vms_after[0].serviceofferingid,
                              "VM is not containing New Service Offering"
                              )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_18_stopped_vm_change_service(self):
        """  
        @Desc: Test to verify change service for Stopped VM
        @Steps:
        Step1: Deploying a VM
        Step2: Stopping the VM deployed in step1
        Step3: Listing all the existing service offerings
        Step4: If there is a matching Service Offering for change service of stopped VM
                use that service offering. If not create one service offering for change service.
        Step5: Perform change service for the Stopped VM
        Step6: Verifying that VM's service offerings is changed
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing details of current Service Offering
        vm_so_list = ServiceOffering.list(
                                          self.userapiclient,
                                          id=vm_created.serviceofferingid
                                          )
        status = validateList(vm_so_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM Service offering failed"
                          )
        current_so = vm_so_list[0]
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Stopping the VM deployed above
        vm_created.stop(
                        self.userapiclient,
                        forced=True
                        )
        # Listing VM details
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id
                                     )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        self.assertEquals(
                          "Stopped",
                          list_vm[0].state,
                          "Stopped VM is not in stopped state"
                          )
        # Listing all the service offerings
        service_offerings_list = ServiceOffering.list(
                                                      self.userapiclient,
                                                      virtualmachineid=vm_created.id
                                                      )
        # Verifying if any Service offering available for change service of VM
        so_exists = False
        if service_offerings_list is not None:
            for i in range(0, len(service_offerings_list)):
                if ((current_so.id != service_offerings_list[i].id) and\
                   (current_so.storagetype == service_offerings_list[i].storagetype)):
                    so_exists = True
                    new_so = service_offerings_list[i]
                    break
        # If service offering does not exists, then creating one service offering for scale up
        if not so_exists:
            self.services["service_offerings"]["small"]["storagetype"] = current_so.storagetype
            new_so = ServiceOffering.create(
                                            self.apiClient,
                                            self.services["service_offerings"]["small"]
                                            )
            self.cleanup.append(new_so)
        # Changing service for the VM
        vm_created.scale_virtualmachine(
                                        self.userapiclient,
                                        new_so.id
                                        )
        # Listing VM details again
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id
                                     )
        status = validateList(list_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        self.assertEquals(
                          1,
                          len(list_vm),
                          "VMs list is not as expected"
                          )
        # Verifying that VM's service offerings is changed
        self.assertEquals(
                          new_so.id,
                          list_vm[0].serviceofferingid,
                          "VM is not containing New Service Offering"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_19_create_reset_vm_sshkey(self):
        """  
        @Desc: Test to verify creation and reset of SSH Key for VM
        @Steps:
        Step1: Deploying a VM
        Step2: Stopping the VM deployed in step1
        Step3: Listing all the SSH Key pairs
        Step4: Creating a new SSH Key pair
        Step5: Listing all the SSh Key pairs again
        Step6: Verifying that the key pairs list is increased by 1
        Step7: Resetting the VM SSH Key to the key pair created in step4
        Step8: Verifying that the new SSH Key pair is set to the VM
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Stopping the VM deployed above
        vm_created.stop(
                        self.userapiclient,
                        forced=True
                        )
        # Listing VM details
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id
                                     )
        status = validateList(list_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        self.assertEquals(
                          "Stopped",
                          list_vm[0].state,
                          "Stopped VM is not in stopped state"
                          )
        # Listing all the SSH Key pairs
        list_keypairs_before = SSHKeyPair.list(
                                               self.userapiclient
                                               )
        list_keypairs_before_size = 0
        if list_keypairs_before is not None:
            list_keypairs_before_size = len(list_keypairs_before)
  
        # Creating a new Key pair
        new_keypair = SSHKeyPair.create(
                                        self.userapiclient,
                                        name="keypair1",
                                        account=self.account.name,
                                        domainid=self.domain.id
                                        )
        self.assertIsNotNone(
                             new_keypair,
                             "New Key pair generation failed"
                             )
        self.assertEquals(
                          "keypair1",
                          new_keypair.name,
                          "Key Pair not created with given name"
                          )
        # Listing all the SSH Key pairs again
        list_keypairs_after = SSHKeyPair.list(
                                              self.userapiclient
                                              )
        status = validateList(list_keypairs_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of Key pairs failed"
                          )
        # Verifying that list size is increased by 1
        self.assertEquals(
                          list_keypairs_before_size + 1,
                          len(list_keypairs_after),
                          "List count is not matching"
                          )
        # Resetting the VM SSH key to the Key pair created above
        vm_created.resetSshKey(
                               self.userapiclient,
                               keypair=new_keypair.name
                               )
        # Listing VM details again
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id
                                     )
        status = validateList(list_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM failed"
                          )
        self.assertEquals(
                          1,
                          len(list_vm),
                          "VMs list is not as expected"
                          )
        # Verifying that VM's SSH keypair is set to newly created keypair
        self.assertEquals(
                          new_keypair.name,
                          list_vm[0].keypair,
                          "VM is not set to newly created SSH Key pair"
                          )
        return

    @attr(tags=["advanced", "basic", "selfservice"])
    def test_20_update_vm_displayname_group(self):
        """  
        @Desc: Test to verify Update VM details
        @Steps:
        Step1: List all the VM's for a user
        Step2: Deploy a VM with all parameters
        Step3: Listing all the VM's again for the user
        Step4: Verifying that list size is increased by 1
        Step5: Updating VM details - displayname, group
        Step6: Listing the VM deployed in step 2 by ID
        Step7: Verifying that displayname, group details of the VM are updated
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        self.services["virtual_machine"]["keyboard"] = "us"
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           group="groupName"
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Verifying the displayname and group details for deployed VM
        self.assertEquals(
                          self.services["virtual_machine"]["displayname"],
                          vm_created.displayname,
                          "Display name of VM is not as expected"
                          )
        self.assertEquals(
                          "groupName",
                          vm_created.group,
                          "Group of VM is not as expected"
                          )
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Updating the VM details - displayname and group
        vm_created.update(
                          self.userapiclient,
                          displayname="DisplayName",
                          group="Group",
                          haenable=False
                          )
        # Listing VM details again
        list_vm = VirtualMachine.list(
                                      self.userapiclient,
                                      id=vm_created.id,
                                      )
        status = validateList(list_vm)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing of VM by Id failed"
                          )
        self.assertEquals(
                          1,
                          len(list_vm),
                          "Count of List VM by Id is not matching"
                          )
        # Verifying that displayname and group details are updated
        self.assertEquals(
                          "DisplayName",
                          list_vm[0].displayname,
                          "Displayname of VM is not updated"
                          )
        self.assertEquals(
                          "Group",
                          list_vm[0].group,
                          "Group of VM is not updated"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_21_restore_vm(self):
        """  
        @Desc: Test to verify Restore VM
        @Steps:
        Step1: List all the VM's for a user
        Step2: Deploy a VM with all parameters
        Step3: Listing all the VM's again for the user
        Step4: Verifying that list size is increased by 1
        Step5: Restoring the VM deployed in step2
        Step6: Verifying that restored VM details are same as the VM deployed in step2
        """
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Restoring the VM
        restored_vm = vm_created.restore(self.userapiclient)
        self.assertIsNotNone(
                             restored_vm,
                             "VM restore failed"
                             )
        # Verifying the restored VM details
        expected_dict = {
                         "id":vm_created.id,
                         "name":vm_created.name,
                         "displayname":vm_created.displayname,
                         "state":vm_created.state,
                         "zoneid":vm_created.zoneid,
                         "account":vm_created.account,
                         "template":vm_created.templateid
                         }
        actual_dict = {
                       "id":restored_vm.id,
                       "name":restored_vm.name,
                       "displayname":restored_vm.displayname,
                       "state":restored_vm.state,
                       "zoneid":restored_vm.zoneid,
                       "account":restored_vm.account,
                       "template":restored_vm.templateid
                       }
        restored_vm_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         restored_vm_status,
                         "Restored VM details are not as expected"
                         )
        return

    @attr(tags=["advanced", "selfservice"])
    def test_22_deploy_vm_multiple_networks(self):
        """  
        @Desc: Test to verify deploy VM with multiple networks
        @Steps:
        Step1: List all the networks for user
        Step2: If size of list networks is greater than 2 then get all the networks id's
                Else create 2 networks and get network id's
        Step3: List all the VM's for a user
        Step4: Deploy a VM with multiple network id's
        Step5: Listing all the VM's again for the user
        Step6: Verifying that list size is increased by 1
        Step7: Verify that VM is associated with multiple networks
        """
        # Listing all the networks available
        networks_list_before = Network.list(
                                            self.userapiclient,
                                            listall=self.services["listall"]
                                            )
        networks_list_size = 0
        if networks_list_before is not None:
            networks_list_size = len(networks_list_before)
     
        # Listing Network Offerings
        network_offerings_list = NetworkOffering.list(
                                                      self.apiClient,
                                                      forvpc="false",
                                                      guestiptype="Isolated",
                                                      state="Enabled",
                                                      supportedservices="SourceNat",
                                                      zoneid=self.zone.id
                                                      )
        status = validateList(network_offerings_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Isolated Network Offerings with sourceNat enabled are not found"
                          )
        while networks_list_size < 2:
            # Creating a network
            network = Network.create(
                                      self.userapiclient,
                                      self.services["network"],
                                      accountid=self.account.name,
                                      domainid=self.domain.id,
                                      networkofferingid=network_offerings_list[0].id,
                                      zoneid=self.zone.id
                                      )
            self.assertIsNotNone(
                                 network,
                                 "Network creation failed"
                                 )
            self.cleanup.append(network)
            networks_list_size = networks_list_size + 1
 
        # Listing the networks again
        networks_list_after = Network.list(
                                           self.userapiclient,
                                           listall=self.services["listall"]
                                           )
        status = validateList(network_offerings_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing networks failed"
                          )
        # populating network id's
        networkids = networks_list_after[0].id + "," + networks_list_after[1].id
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           networkids=networkids,
                                           serviceofferingid=self.service_offering.id,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Verifying that the NIC's in VM created are same as provided
        vm_nics = vm_created.nic
        # Verifying that the size of nics is 2
        self.assertEquals(
                          2,
                          len(vm_nics),
                          "NIC's count in VM created is not matching"
                          )
        # Verifying that NIC network ID's are as expected
        for i in range(0, len(vm_nics)):
            if vm_nics[i].isdefault is True:
                self.assertEquals(
                                  networks_list_after[0].id,
                                  vm_nics[i].networkid,
                                  "Default NIC is not as expected"
                                  )
            else:
                self.assertEquals(
                                  networks_list_after[1].id,
                                  vm_nics[i].networkid,
                                  "Non Default NIC is not as expected"
                                  )
        return

    @attr(tags=["basic", "provisioning"])
    def test_23_deploy_vm_multiple_securitygroups(self):
        """  
        @Desc: Test to verify deploy VM with multiple Security Groups
        @Steps:
        Step1: List all the security groups for user
        Step2: If size of list security groups is greater than 2 then get all the security groups id's
                Else creating 2 security groups and get security groups id's
        Step3: List all the VM's for a user
        Step4: Deploy a VM with multiple security groups id's
        Step5: Listing all the VM's again for the user
        Step6: Verifying that list size is increased by 1
        Step7: Verify that VM is associated with multiple security groups
        """
        # Listing all the security groups available
        security_groups_list = SecurityGroup.list(
                                                  self.userapiclient,
                                                  listall=self.services["listall"],
                                                  domainid=self.domain.id
                                                  )
        security_groups_list_size = 0
        if security_groups_list is not None:
            security_groups_list_size = len(security_groups_list)
     
        while security_groups_list_size < 2:
            # Creating a security group
            security_group = SecurityGroup.create(
                                                  self.userapiclient,
                                                  self.services["security_group"],
                                                  account=self.account.name,
                                                  domainid=self.domain.id
                                                  )
            self.assertIsNotNone(
                                 security_group,
                                 "Security Group creation failed"
                                 )
            self.cleanup.append(security_group)
            security_groups_list_size = security_groups_list_size + 1
     
        # Listing the networks again
        security_groups_list = SecurityGroup.list(
                                                  self.userapiclient,
                                                  listall=self.services["listall"],
                                                  domainid=self.domain.id
                                                  )
        status = validateList(security_groups_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Listing Security Groups failed"
                          )
        # populating Security Groups id's
        securitygroupids = {security_groups_list[0].id , security_groups_list[1].id}
        # Listing all the VM's for a User
        list_vms_before = VirtualMachine.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              )
        self.assertIsNone(
                           list_vms_before,
                           "Virtual Machine already exists for newly created user"
                           )
        # Deploying a VM
        vm_created = VirtualMachine.create(
                                           self.userapiclient,
                                           self.services["virtual_machine"],
                                           accountid=self.account.name,
                                           domainid=self.account.domainid,
                                           serviceofferingid=self.service_offering.id,
                                           securitygroupids=securitygroupids,
                                           )
        self.assertIsNotNone(
                             vm_created,
                             "VM creation failed"
                             )
        self.cleanup.append(vm_created)
        # Listing all the VMs for a user again
        list_vms_after = VirtualMachine.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             )
        status = validateList(list_vms_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM creation failed"
                          )
        # Verifying that the size of the list is 1
        self.assertEquals(
                          1,
                          len(list_vms_after),
                          "VM list count is not matching"
                          )
        # Verifying that the Security Groups's in VM created are same as provided
        vm_securitygroups = vm_created.securitygroup
        # Verifying that the size of security groups is 2
        self.assertEquals(
                          2,
                          len(vm_securitygroups),
                          "Security Groups count in VM created is not matching"
                          )
        # Verifying that Security Group network ID's are as expected
        vm_securitygroups_flag = True
        for i in range(0, len(vm_securitygroups)):
            if ((vm_securitygroups[i].id != security_groups_list[0].id) and\
                (vm_securitygroups[i].id != security_groups_list[1].id)):
                vm_securitygroups_flag = False
                break
     
        self.assertEquals(
                          True,
                          vm_securitygroups_flag,
                          "Security Groups in VM are not same as created"
                          )
        return

class TestSnapshots(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        try:
            cls._cleanup = []        
            cls.testClient = super(TestSnapshots, cls).getClsTestClient()
            cls.api_client = cls.testClient.getApiClient()
            cls.services = cls.testClient.getParsedTestDataConfig()
            # Get Domain, Zone, Template
            cls.domain = get_domain(cls.api_client)
            cls.zone = get_zone(cls.api_client)
            cls.template = get_template(
                                cls.api_client,
                                cls.zone.id,
                                cls.services["ostype"]
                                )
            if cls.zone.localstorageenabled:
                cls.storagetype = 'local'
                cls.services["service_offerings"]["tiny"]["storagetype"] = 'local'
                cls.services["disk_offering"]["storagetype"] = 'local'
            else:
                cls.storagetype = 'shared'
                cls.services["service_offerings"]["tiny"]["storagetype"] = 'shared'
                cls.services["disk_offering"]["storagetype"] = 'shared'
    
            cls.services['mode'] = cls.zone.networktype
            cls.services["virtual_machine"]["hypervisor"] = cls.testClient.getHypervisorInfo()
            cls.services["virtual_machine"]["zoneid"] = cls.zone.id
            cls.services["virtual_machine"]["template"] = cls.template.id
            cls.services["custom_volume"]["zoneid"] = cls.zone.id
            # Creating Disk offering, Service Offering and Account
            cls.disk_offering = DiskOffering.create(
                                        cls.api_client,
                                        cls.services["disk_offering"]
                                        )
            cls._cleanup.append(cls.disk_offering)
            cls.service_offering = ServiceOffering.create(
                                                cls.api_client,
                                                cls.services["service_offerings"]["tiny"]
                                                )
            cls._cleanup.append(cls.service_offering)
            cls.account = Account.create(
                                cls.api_client,
                                cls.services["account"],
                                domainid=cls.domain.id
                                )
            # Getting authentication for user in newly created Account
            cls.user = cls.account.user[0]
            cls.userapiclient = cls.testClient.getUserApiClient(cls.user.username, cls.domain.name)
            cls._cleanup.append(cls.account)
            # Creating Virtual Machine
            cls.virtual_machine = VirtualMachine.create(
                                        cls.userapiclient,
                                        cls.services["virtual_machine"],
                                        accountid=cls.account.name,
                                        domainid=cls.account.domainid,
                                        serviceofferingid=cls.service_offering.id,
                                    )
            cls._cleanup.append(cls.virtual_machine)
        except Exception as e:
            cls.tearDownClass()
            raise Exception("Warning: Exception in setup : %s" % e)
        return

    def setUp(self):

        self.apiClient = self.testClient.getApiClient()
        self.cleanup = []

    def tearDown(self):
        #Clean up, terminate the created resources
        cleanup_resources(self.apiClient, self.cleanup)
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

        return

    def __verify_values(self, expected_vals, actual_vals):
        """  
        @Desc: Function to verify expected and actual values
        @Steps:
        Step1: Initializing return flag to True
        Step1: Verifying length of expected and actual dictionaries is matching.
               If not matching returning false
        Step2: Listing all the keys from expected dictionary
        Step3: Looping through each key from step2 and verifying expected and actual dictionaries have same value
               If not making return flag to False
        Step4: returning the return flag after all the values are verified
        """
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
                self.debug("expected Value: %s, is not matching with actual value: %s" % (
                                                                                          exp_val,
                                                                                          act_val
                                                                                          ))
        return return_flag

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_01_list_volume_snapshots_pagination(self):
        """  
        @Desc: Test to List Volume Snapshots pagination
        @steps:
        Step1: Listing all the volume snapshots for a user
        Step2: Verifying that list size is 0
        Step3: Creating (page size + 1) number of volume snapshots
        Step4: Listing all the volume snapshots again for a user
        Step5: Verifying that list size is (page size + 1)
        Step6: Listing all the volume snapshots in page1
        Step7: Verifying that list size is (page size)
        Step8: Listing all the volume snapshots in page2
        Step9: Verifying that list size is 1
        Step10: Deleting the volume snapshot present in page 2
        Step11: Listing all the volume snapshots in page2
        Step12: Verifying that list size is 0
        """
        # Listing all the volume snapshots for a User
        list_vol_snaps_before = Snapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"]
                                              )
        # Verifying list size is 0
        self.assertIsNone(
                          list_vol_snaps_before,
                          "Volume snapshots exists for newly created user"
                          )
        # Listing the root volumes available for the user
        volumes_list = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"]
                                   )
        status = validateList(volumes_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Root volume did not get created while deploying a VM"
                          )
        # Verifying list size to be 1
        self.assertEquals(
                          1,
                          len(volumes_list),
                          "More than 1 root volume created for deployed VM"
                          )
        root_volume = volumes_list[0]
        # Creating pagesize + 1 number of volume snapshots
        for i in range(0, (self.services["pagesize"] + 1)):
            snapshot_created = Snapshot.create(
                                               self.userapiclient,
                                               root_volume.id,
                                               )
            self.assertIsNotNone(
                                 snapshot_created,
                                 "Snapshot creation failed"
                                 )
            self.cleanup.append(snapshot_created)

        # Listing all the volume snapshots for user again
        list_vol_snaps_after = Snapshot.list(
                                             self.userapiclient,
                                             listall=self.services["listall"]
                                             )
        status = validateList(list_vol_snaps_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Volume snapshot creation failed"
                          )
        # Verifying that list size is pagesize + 1
        self.assertEquals(
                          self.services["pagesize"] + 1,
                          len(list_vol_snaps_after),
                          "Failed to create pagesize + 1 number of Volume snapshots"
                          )
        # Listing all the volume snapshots in page 1
        list_vol_snaps_page1 = Snapshot.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=1,
                                             pagesize=self.services["pagesize"]
                                             )
        status = validateList(list_vol_snaps_page1)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list volume snapshots in page 1"
                          )
        # Verifying the list size to be equal to pagesize
        self.assertEquals(
                          self.services["pagesize"],
                          len(list_vol_snaps_page1),
                          "Size of volume snapshots in page 1 is not matching"
                          )
        # Listing all the volume snapshots in page 2
        list_vol_snaps_page2 = Snapshot.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=2,
                                             pagesize=self.services["pagesize"]
                                             )
        status = validateList(list_vol_snaps_page2)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list volume snapshots in page 2"
                          )
        # Verifying the list size to be equal to pagesize
        self.assertEquals(
                          1,
                          len(list_vol_snaps_page2),
                          "Size of volume snapshots in page 2 is not matching"
                          )
        # Deleting the volume snapshot present in page 2
        Snapshot.delete(
                        snapshot_created,
                        self.userapiclient
                        )
        # Listing all the snapshots in page 2 again
        list_vol_snaps_page2 = Snapshot.list(
                                             self.userapiclient,
                                             listall=self.services["listall"],
                                             page=2,
                                             pagesize=self.services["pagesize"]
                                             )
        # Verifying that list size is 0
        self.assertIsNone(
                          list_vol_snaps_page2,
                          "Volume snapshot not deleted from page 2"
                          )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_02_list_volume_snapshots_byid(self):
        """  
        @Desc: Test to List Volume Snapshots by Id
        @Steps:
        Step1: Listing all the volume snapshots for a user
        Step2: Verifying that list size is 0
        Step3: Creating a volume snapshot
        Step4: Listing all the volume snapshots again for a user
        Step5: Verifying that list size is 1
        Step6: Listing all the volume snapshots by specifying snapshot id
        Step7: Verifying that list size is 1
        Step8: Verifying details of the listed volume snapshot
        """
        # Listing all the volume snapshots for a User
        list_vol_snaps_before = Snapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"]
                                              )
        # Verifying list size is 0
        self.assertIsNone(
                          list_vol_snaps_before,
                          "Volume snapshots exists for newly created user"
                          )
        # Listing the root volumes available for the user
        volumes_list = Volume.list(
                                   self.userapiclient,
                                   listall=self.services["listall"]
                                   )
        status = validateList(volumes_list)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Root volume did not get created while deploying a VM"
                          )
        # Verifying list size to be 1
        self.assertEquals(
                          1,
                          len(volumes_list),
                          "More than 1 root volume created for deployed VM"
                          )
        root_volume = volumes_list[0]
        # Creating a volume snapshot
        snapshot_created = Snapshot.create(
                                           self.userapiclient,
                                           root_volume.id,
                                           )
        self.assertIsNotNone(
                             snapshot_created,
                             "Snapshot creation failed"
                             )
        self.cleanup.append(snapshot_created)
        # Listing all the volume snapshots for user again
        list_vol_snaps_after = Snapshot.list(
                                             self.userapiclient,
                                             listall=self.services["listall"]
                                             )
        status = validateList(list_vol_snaps_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Volume snapshot creation failed"
                          )
        # Verifying that list size is 1
        self.assertEquals(
                          1,
                          len(list_vol_snaps_after),
                          "Failed to create Volume snapshot"
                          )
        # Listing volume snapshot by id
        list_vol_snapshot = Snapshot.list(
                                          self.userapiclient,
                                          listall=self.services["listall"],
                                          id=snapshot_created.id
                                          )
        status = validateList(list_vol_snapshot)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list Volume snapshot by Id"
                          )
        # Verifying that list size is 1
        self.assertEquals(
                          1,
                          len(list_vol_snapshot),
                          "Size of the list volume snapshot by Id is not matching"
                          )
        # Verifying details of the listed snapshot to be same as snapshot created above
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":snapshot_created.id,
                         "name":snapshot_created.name,
                         "state":snapshot_created.state,
                         "intervaltype":snapshot_created.intervaltype,
                         "account":snapshot_created.account,
                         "domain":snapshot_created.domainid,
                         "volume":snapshot_created.volumeid
                         }
        actual_dict = {
                       "id":list_vol_snapshot[0].id,
                       "name":list_vol_snapshot[0].name,
                       "state":list_vol_snapshot[0].state,
                       "intervaltype":list_vol_snapshot[0].intervaltype,
                       "account":list_vol_snapshot[0].account,
                       "domain":list_vol_snapshot[0].domainid,
                       "volume":list_vol_snapshot[0].volumeid
                       }
        vol_snapshot_status = self.__verify_values(
                                                   expected_dict,
                                                   actual_dict
                                                   )
        self.assertEqual(
                         True,
                         vol_snapshot_status,
                         "Listed Volume Snapshot details are not as expected"
                         )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_03_list_vm_snapshots_pagination(self):
        """  
        @Desc: Test to List VM Snapshots pagination
        @Steps:
        Step1: Listing all the VM snapshots for a user
        Step2: Verifying that list size is 0
        Step3: Creating (page size + 1) number of VM snapshots
        Step4: Listing all the VM snapshots again for a user
        Step5: Verifying that list size is (page size + 1)
        Step6: Listing all the VM snapshots in page1
        Step7: Verifying that list size is (page size)
        Step8: Listing all the VM snapshots in page2
        Step9: Verifying that list size is 1
        Step10: Deleting the VM snapshot present in page 2
        Step11: Listing all the volume snapshots in page2
        Step12: Verifying that list size is 0
        """
        # Listing all the VM snapshots for a User
        list_vm_snaps_before = VmSnapshot.list(
                                               self.userapiclient,
                                               listall=self.services["listall"]
                                               )
        # Verifying list size is 0
        self.assertIsNone(
                          list_vm_snaps_before,
                          "VM snapshots exists for newly created user"
                          )
        # Creating pagesize + 1 number of VM snapshots
        for i in range(0, (self.services["pagesize"] + 1)):
            snapshot_created = VmSnapshot.create(
                                                 self.userapiclient,
                                                 self.virtual_machine.id,
                                                 )
            self.assertIsNotNone(
                                 snapshot_created,
                                 "Snapshot creation failed"
                                 )

        # Listing all the VM snapshots for user again
        list_vm_snaps_after = VmSnapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"]
                                              )
        status = validateList(list_vm_snaps_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM snapshot creation failed"
                          )
        # Verifying that list size is pagesize + 1
        self.assertEquals(
                          self.services["pagesize"] + 1,
                          len(list_vm_snaps_after),
                          "Failed to create pagesize + 1 number of VM snapshots"
                          )
        # Listing all the VM snapshots in page 1
        list_vm_snaps_page1 = VmSnapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=1,
                                              pagesize=self.services["pagesize"]
                                              )
        status = validateList(list_vm_snaps_page1)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list vm snapshots in page 1"
                          )
        # Verifying the list size to be equal to pagesize
        self.assertEquals(
                          self.services["pagesize"],
                          len(list_vm_snaps_page1),
                          "Size of vm snapshots in page 1 is not matching"
                          )
        # Listing all the vm snapshots in page 2
        list_vm_snaps_page2 = VmSnapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=2,
                                              pagesize=self.services["pagesize"]
                                              )
        status = validateList(list_vm_snaps_page2)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list vm snapshots in page 2"
                          )
        # Verifying the list size to be equal to pagesize
        self.assertEquals(
                          1,
                          len(list_vm_snaps_page2),
                          "Size of vm snapshots in page 2 is not matching"
                          )
        # Deleting the vm snapshot present in page 2
        VmSnapshot.deleteVMSnapshot(
                                    self.userapiclient,
                                    snapshot_created.id
                                    )
        # Listing all the snapshots in page 2 again
        list_vm_snaps_page2 = VmSnapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"],
                                              page=2,
                                              pagesize=self.services["pagesize"]
                                              )
        # Verifying that list size is 0
        self.assertIsNone(
                          list_vm_snaps_page2,
                          "VM snapshot not deleted from page 2"
                          )
        # Deleting all the existing VM snapshots
        list_vm_snaps =  VmSnapshot.list(
                                         self.userapiclient,
                                         listall=self.services["listall"],
                                         )
        status = validateList(list_vm_snaps)
        self.assertEquals(
                          PASS,
                          status[0],
                          "All VM snapshots deleted"
                          )
        # Verifying that list size is equal to page size
        self.assertEquals(
                          self.services["pagesize"],
                          len(list_vm_snaps),
                          "VM Snapshots count is not matching"
                          )
        # Deleting all the existing VM snapshots
        for i in range(0, len(list_vm_snaps)):
            VmSnapshot.deleteVMSnapshot(
                                    self.userapiclient,
                                    list_vm_snaps[i].id
                                    )
        return

    @attr(tags=["advanced", "basic", "provisioning"])
    def test_04_list_vm_snapshots_byid(self):
        """  
        @summary: Test to List VM Snapshots by Id

        Step1: Listing all the VM snapshots for a user
        Step2: Verifying that list size is 0
        Step3: Creating a VM snapshot
        Step4: Listing all the VM snapshots again for a user
        Step5: Verifying that list size is 1
        Step6: Listing all the VM snapshots by specifying snapshot id
        Step7: Verifying that list size is 1
        Step8: Verifying details of the listed VM snapshot
        """
        # Listing all the VM snapshots for a User
        list_vm_snaps_before = VmSnapshot.list(
                                               self.userapiclient,
                                               listall=self.services["listall"]
                                               )
        # Verifying list size is 0
        self.assertIsNone(
                          list_vm_snaps_before,
                          "VM snapshots exists for newly created user"
                          )
        # Creating a VM snapshot
        snapshot_created = VmSnapshot.create(
                                             self.userapiclient,
                                             self.virtual_machine.id,
                                             )
        self.assertIsNotNone(
                             snapshot_created,
                             "Snapshot creation failed"
                             )
        # Listing all the VM snapshots for user again
        list_vm_snaps_after = VmSnapshot.list(
                                              self.userapiclient,
                                              listall=self.services["listall"]
                                              )
        status = validateList(list_vm_snaps_after)
        self.assertEquals(
                          PASS,
                          status[0],
                          "VM snapshot creation failed"
                          )
        # Verifying that list size is 1
        self.assertEquals(
                          1,
                          len(list_vm_snaps_after),
                          "Failed to create VM snapshot"
                          )
        # Listing vm snapshot by id
        list_vm_snapshot = VmSnapshot.list(
                                           self.userapiclient,
                                           listall=self.services["listall"],
                                           vmsnapshotid=snapshot_created.id
                                          )
        status = validateList(list_vm_snapshot)
        self.assertEquals(
                          PASS,
                          status[0],
                          "Failed to list VM snapshot by Id"
                          )
        # Verifying that list size is 1
        self.assertEquals(
                          1,
                          len(list_vm_snapshot),
                          "Size of the list vm snapshot by Id is not matching"
                          )
        # Verifying details of the listed snapshot to be same as snapshot created above
        #Creating expected and actual values dictionaries
        expected_dict = {
                         "id":snapshot_created.id,
                         "name":snapshot_created.name,
                         "state":snapshot_created.state,
                         "vmid":snapshot_created.virtualmachineid,
                         }
        actual_dict = {
                       "id":list_vm_snapshot[0].id,
                       "name":list_vm_snapshot[0].name,
                       "state":list_vm_snapshot[0].state,
                       "vmid":list_vm_snapshot[0].virtualmachineid,
                       }
        vm_snapshot_status = self.__verify_values(
                                                  expected_dict,
                                                  actual_dict
                                                  )
        self.assertEqual(
                         True,
                         vm_snapshot_status,
                         "Listed VM Snapshot details are not as expected"
                         )
        return