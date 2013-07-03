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

""" P1 for stopped Virtual Maschine life cycle
"""
#Import Local Modules
import marvin
from nose.plugins.attrib import attr
from marvin.cloudstackTestCase import *
from marvin.cloudstackAPI import *
from marvin.remoteSSHClient import remoteSSHClient
from marvin.integration.lib.utils import *
from marvin.integration.lib.base import *
from marvin.integration.lib.common import *
#Import System modules
import time


class Services:
    """Test Stopped VM Life Cycle Services
    """

    def __init__(self):
        self.services = {
                "account": {
                    "email": "test@test.com",
                    "firstname": "Test",
                    "lastname": "User",
                    "username": "test",
                    # Random characters are appended in create account to
                    # ensure unique username generated each time
                    "password": "password",
                },
                "virtual_machine":
                {
                    "displayname": "testserver",
                    "username": "root",     # VM creds for SSH
                    "password": "password",
                    "ssh_port": 22,
                    "hypervisor": 'XenServer',
                    "privateport": 22,
                    "publicport": 22,
                    "protocol": 'TCP',
                },
                "service_offering":
                {
                    "name": "Tiny Instance",
                    "displaytext": "Tiny Instance",
                    "cpunumber": 1,
                    "cpuspeed": 100,    # in MHz
                    "memory": 128,      # In MBs
                },
                "disk_offering": {
                    "displaytext": "Small volume",
                    "name": "Small volume",
                    "disksize": 20
                },
                "volume": {
                    "diskname": "DataDisk",
                    "url": '',
                    "format": 'VHD'
                },
                "iso":  # ISO settings for Attach/Detach ISO tests
                {
                    "displaytext": "Test ISO",
                    "name": "testISO",
                    "url": "http://people.apache.org/~tsp/dummy.iso",
                     # Source URL where ISO is located
                    "ostype": 'CentOS 5.3 (64-bit)',
                    "mode": 'HTTP_DOWNLOAD',    # Downloading existing ISO
                },
                "template": {
                    "url": "http://download.cloud.com/releases/2.0.0/UbuntuServer-10-04-64bit.vhd.bz2",
                    "hypervisor": 'XenServer',
                    "format": 'VHD',
                    "isfeatured": True,
                    "ispublic": True,
                    "isextractable": True,
                    "displaytext": "Cent OS Template",
                    "name": "Cent OS Template",
                    "ostype": 'CentOS 5.3 (64-bit)',
                    "templatefilter": 'self',
                    "passwordenabled": True,
                },
            "sleep": 60,
            "timeout": 10,
            #Migrate VM to hostid
            "ostype": 'CentOS 5.3 (64-bit)',
            # CentOS 5.3 (64-bit)
        }


class TestDeployVM(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):

        cls.api_client = super(
                               TestDeployVM,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )

        # Create service offerings, disk offerings etc
        cls.service_offering = ServiceOffering.create(
                                    cls.api_client,
                                    cls.services["service_offering"]
                                    )
        cls.disk_offering = DiskOffering.create(
                                    cls.api_client,
                                    cls.services["disk_offering"]
                                    )
        # Cleanup
        cls._cleanup = [
                        cls.service_offering,
                        cls.disk_offering,
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def setUp(self):

        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.services = Services().services
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.services["iso"]["zoneid"] = self.zone.id
        self.services["virtual_machine"]["template"] = self.template.id
        self.account = Account.create(
                            self.apiclient,
                            self.services["account"],
                            domainid=self.domain.id
                            )
        self.cleanup = [self.account]
        return

    def tearDown(self):
        try:
            self.debug("Cleaning up the resources")
            cleanup_resources(self.apiclient, self.cleanup)
            self.debug("Cleanup complete!")
        except Exception as e:
            self.debug("Warning! Exception in tearDown: %s" % e)

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_01_deploy_vm_no_startvm(self):
        """Test Deploy Virtual Machine with no startVM parameter
        """

        # Validate the following:
        # 1. deploy Vm  without specifying the startvm parameter
        # 2. Should be able to login to the VM.
        # 3. listVM command should return the deployed VM.State of this VM
        #    should be "Running".

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id,
                                    mode=self.zone.networktype
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Running",
                            "VM should be in Running state after deployment"
                        )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_02_deploy_vm_startvm_true(self):
        """Test Deploy Virtual Machine with startVM=true parameter
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=true
        # 2. Should be able to login to the VM.
        # 3. listVM command should return the deployed VM.State of this VM
        #    should be "Running".

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=True,
                                    diskofferingid=self.disk_offering.id,
                                    mode=self.zone.networktype
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Running",
                            "VM should be in Running state after deployment"
                        )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_03_deploy_vm_startvm_false(self):
        """Test Deploy Virtual Machine with startVM=false parameter
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=false
        # 2. Should not be able to login to the VM.
        # 3. listVM command should return the deployed VM.State of this VM
        #    should be "Stopped".
        # 4. Check listRouters call for that account. List routers should
        #    return empty response

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                    diskofferingid=self.disk_offering.id,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Stopped",
            "VM should be in Stopped state after deployment with startvm=false"
        )
        routers = Router.list(
                              self.apiclient,
                              account=self.account.name,
                              domainid=self.account.domainid,
                              listall=True
                              )
        self.assertEqual(
                         routers,
                         None,
                         "List routers should return empty response"
                         )
        self.debug("Destroying instance: %s" % self.virtual_machine.name)
        self.virtual_machine.delete(self.apiclient)
        self.debug("Instance is destroyed!")

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )
        self.debug("Instance destroyed..waiting till expunge interval")

        interval = list_configurations(
                                    self.apiclient,
                                    name='expunge.interval'
                                    )
        delay = list_configurations(
                                    self.apiclient,
                                    name='expunge.delay'
                                    )
        # Sleep to ensure that all resources are deleted
        time.sleep((int(interval[0].value) + int(delay[0].value)))
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.assertEqual(
                            list_vm_response,
                            None,
                            "Check list response returns a valid list"
                        )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_04_deploy_startvm_false_attach_volume(self):
        """Test Deploy Virtual Machine with startVM=false and attach volume
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=false. Attach volume to the instance
        # 2. listVM command should return the deployed VM.State of this VM
        #    should be "Stopped".
        # 3. Attach volume should be successful

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                    diskofferingid=self.disk_offering.id,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Stopped",
            "VM should be in Stopped state after deployment with startvm=false"
        )
        self.debug("Creating a volume in account: %s" %
                                                    self.account.name)
        volume = Volume.create(
                                self.apiclient,
                                self.services["volume"],
                                zoneid=self.zone.id,
                                account=self.account.name,
                                domainid=self.account.domainid,
                                diskofferingid=self.disk_offering.id
                                )
        self.debug("Created volume in account: %s" % self.account.name)
        self.debug("Attaching volume to instance: %s" %
                                                self.virtual_machine.name)
        try:
            self.virtual_machine.attach_volume(self.apiclient, volume)
        except Exception as e:
            self.fail("Attach volume failed!")
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_05_deploy_startvm_false_change_so(self):
        """Test Deploy Virtual Machine with startVM=false and
            change service offering
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=false. Attach volume to the instance
        # 2. listVM command should return the deployed VM.State of this VM
        #    should be "Stopped".
        # 3. Attach volume should be successful
        # 4. Change service offering

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                    diskofferingid=self.disk_offering.id,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Stopped",
            "VM should be in Stopped state after deployment with startvm=false"
        )
        self.debug("Creating a volume in account: %s" %
                                                    self.account.name)
        volume = Volume.create(
                                self.apiclient,
                                self.services["volume"],
                                zoneid=self.zone.id,
                                account=self.account.name,
                                domainid=self.account.domainid,
                                diskofferingid=self.disk_offering.id
                                )
        self.debug("Created volume in account: %s" % self.account.name)
        self.debug("Attaching volume to instance: %s" %
                                                self.virtual_machine.name)
        try:
            self.virtual_machine.attach_volume(self.apiclient, volume)
        except Exception as e:
            self.fail("Attach volume failed!")
        self.debug("Fetching details of medium service offering")
        medium_service_offs = ServiceOffering.list(
                                                  self.apiclient,
                                                  name="Medium Instance"
                                                  )
        if isinstance(medium_service_offs, list):
            medium_service_off = medium_service_offs[0]
        else:
            self.debug("Service offering not found! Creating a new one..")
            medium_service_off = ServiceOffering.create(
                                    self.apiclient,
                                    self.services["service_offering"]
                                    )
            self.cleanup.append(medium_service_off)

        self.debug("Changing service offering for instance: %s" %
                                                    self.virtual_machine.name)
        try:
            self.virtual_machine.change_service_offering(
                                                     self.apiclient,
                                                     medium_service_off.id
                                                     )
        except Exception as e:
            self.fail("Change service offering failed: %s" % e)

        self.debug("Starting the instance: %s" % self.virtual_machine.name)
        self.virtual_machine.start(self.apiclient)
        self.debug("Instance: %s started" % self.virtual_machine.name)

        self.debug("Detaching the disk: %s" % volume.name)
        self.virtual_machine.detach_volume(self.apiclient, volume)
        self.debug("Datadisk %s detached!" % volume.name)

        volumes = Volume.list(
                              self.apiclient,
                              virtualmachineid=self.virtual_machine.id,
                              type='DATADISK',
                  id=volume.id,
                              listall=True
                              )
        self.assertEqual(
                         volumes,
                         None,
                         "List Volumes should not list any volume for instance"
                         )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_06_deploy_startvm_attach_detach(self):
        """Test Deploy Virtual Machine with startVM=false and
            attach detach volumes
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=false. Attach volume to the instance
        # 2. listVM command should return the deployed VM.State of this VM
        #    should be "Stopped".
        # 3. Attach volume should be successful
        # 4. Detach volume from instance. Detach should be successful

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                    diskofferingid=self.disk_offering.id,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Stopped",
            "VM should be in Stopped state after deployment with startvm=false"
        )
        self.debug("Creating a volume in account: %s" %
                                                    self.account.name)
        volume = Volume.create(
                                self.apiclient,
                                self.services["volume"],
                                zoneid=self.zone.id,
                                account=self.account.name,
                                domainid=self.account.domainid,
                                diskofferingid=self.disk_offering.id
                                )
        self.debug("Created volume in account: %s" % self.account.name)
        self.debug("Attaching volume to instance: %s" %
                                                self.virtual_machine.name)
        try:
            self.virtual_machine.attach_volume(self.apiclient, volume)
        except Exception as e:
            self.fail("Attach volume failed!")

        self.debug("Detaching the disk: %s" % volume.name)
        self.virtual_machine.detach_volume(self.apiclient, volume)
        self.debug("Datadisk %s detached!" % volume.name)

        volumes = Volume.list(
                              self.apiclient,
                              virtualmachineid=self.virtual_machine.id,
                              type='DATADISK',
                  id=volume.id,
                              listall=True
                              )
        self.assertEqual(
                         volumes,
                         None,
                         "List Volumes should not list any volume for instance"
                         )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_07_deploy_startvm_attach_iso(self):
        """Test Deploy Virtual Machine with startVM=false and attach ISO
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=false. Attach volume to the instance
        # 2. listVM command should return the deployed VM.State of this VM
        #    should be "Stopped".
        # 3. Attach ISO to the instance. Attach ISO should be successful

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                    diskofferingid=self.disk_offering.id,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Stopped",
            "VM should be in Stopped state after deployment with startvm=false"
        )
        self.debug("Registering a ISO in account: %s" %
                                                    self.account.name)
        iso = Iso.create(
                         self.apiclient,
                         self.services["iso"],
                         account=self.account.name,
                         domainid=self.account.domainid
                         )

        self.debug("Successfully created ISO with ID: %s" % iso.id)
        try:
            iso.download(self.apiclient)
            self.cleanup.append(iso)
        except Exception as e:
            self.fail("Exception while downloading ISO %s: %s"\
                      % (iso.id, e))

        self.debug("Attach ISO with ID: %s to VM ID: %s" % (
                                                    iso.id,
                                                    self.virtual_machine.id
                                                    ))
        try:
            self.virtual_machine.attach_iso(self.apiclient, iso)
        except Exception as e:
            self.fail("Attach ISO failed!")

        vms = VirtualMachine.list(
                              self.apiclient,
                              id=self.virtual_machine.id,
                              listall=True
                              )
        self.assertEqual(
                         isinstance(vms, list),
                         True,
                         "List vms should return a valid list"
                         )
        vm = vms[0]
        self.assertEqual(
                         vm.isoid,
                         iso.id,
                         "The ISO status should be reflected in list Vm call"
                         )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_08_deploy_attach_volume(self):
        """Test Deploy Virtual Machine with startVM=false and
            attach volume already attached to different machine
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=false. Attach volume to the instance
        # 2. listVM command should return the deployed VM.State of this VM
        #    should be "Stopped".
        # 3. Create an instance with datadisk attached to it. Detach DATADISK
        # 4. Attach the volume to first virtual machine.

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine_1 = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine_1.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine_1.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Stopped",
            "VM should be in Stopped state after deployment with startvm=false"
        )

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine_2 = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine_2.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine_2.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
            vm_response.state,
            "Running",
            "VM should be in Stopped state after deployment with startvm=false"
        )

        self.debug(
            "Fetching DATADISK details for instance: %s" %
                                            self.virtual_machine_2.name)
        volumes = Volume.list(
                              self.apiclient,
                              type='DATADISK',
                              account=self.account.name,
                              domainid=self.account.domainid,
                              listall=True
                              )
        self.assertEqual(
                         isinstance(volumes, list),
                         True,
                         "List volumes should return a valid list"
                         )
        volume = volumes[0]

        self.debug("Detaching the disk: %s" % volume.name)

        try:
            self.virtual_machine_2.detach_volume(self.apiclient, volume)
            self.debug("Datadisk %s detached!" % volume.name)
        except Exception as e:
            self.fail("Detach volume failed!")

        self.debug("Attaching volume to instance: %s" %
                                                self.virtual_machine_1.name)
        try:
            self.virtual_machine_1.attach_volume(self.apiclient, volume)
        except Exception as e:
            self.fail("Attach volume failed!")

        volumes = Volume.list(
                              self.apiclient,
                              virtualmachineid=self.virtual_machine_1.id,
                              type='DATADISK',
                              id=volume.id,
                              listall=True
                              )
        self.assertNotEqual(
                         volumes,
                         None,
                         "List Volumes should not list any volume for instance"
                         )
        return


class TestDeployHaEnabledVM(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):

        cls.api_client = super(
                               TestDeployHaEnabledVM,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )

        # Create service, disk offerings  etc
        cls.service_offering = ServiceOffering.create(
                                    cls.api_client,
                                    cls.services["service_offering"],
                                    offerha=True
                                    )
        cls.disk_offering = DiskOffering.create(
                                    cls.api_client,
                                    cls.services["disk_offering"]
                                    )
        # Cleanup
        cls._cleanup = [
                        cls.service_offering,
                        cls.disk_offering,
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def setUp(self):

        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.services = Services().services
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.services["virtual_machine"]["template"] = self.template.id
        self.services["iso"]["zoneid"] = self.zone.id
        self.account = Account.create(
                            self.apiclient,
                            self.services["account"],
                            domainid=self.domain.id
                            )
        self.cleanup = [self.account]
        return

    def tearDown(self):
        try:
            self.debug("Cleaning up the resources")
            cleanup_resources(self.apiclient, self.cleanup)
            self.debug("Cleanup complete!")
        except Exception as e:
            self.debug("Warning! Exception in tearDown: %s" % e)

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_01_deploy_ha_vm_startvm_false(self):
        """Test Deploy HA enabled Virtual Machine with startvm=false
        """

        # Validate the following:
        # 1. deployHA enabled  Vm  with the startvm parameter = false
        # 2. listVM command should return the deployed VM. State of this VM
        #    should be "Created".

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id,
                                    startvm=False
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Stopped",
                            "VM should be in Stopped state after deployment"
                        )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_02_deploy_ha_vm_from_iso(self):
        """Test Deploy HA enabled Virtual Machine from ISO
        """

        # Validate the following:
        # 1. deployHA enabled Vm using ISO with the startvm parameter=true
        # 2. listVM command should return the deployed VM. State of this VM
        #    should be "Running".

        self.iso = Iso.create(
                                self.apiclient,
                                self.services["iso"],
                                account=self.account.name,
                                domainid=self.account.domainid
                                )
        try:
            # Dowanload the ISO
            self.iso.download(self.apiclient)
            self.cleanup.append(self.iso)
        except Exception as e:
            raise Exception("Exception while downloading ISO %s: %s"\
                      % (self.iso.id, e))

        self.debug("Registered ISO: %s" % self.iso.name)
        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    templateid=self.iso.id,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id,
                                    startvm=True
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Running",
                            "VM should be in Running state after deployment"
                        )
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_03_deploy_ha_vm_iso_startvm_false(self):
        """Test Deploy HA enabled Virtual Machine from ISO with startvm=false
        """

        # Validate the following:
        # 1. deployHA enabled Vm using ISO with the startvm parameter=false
        # 2. listVM command should return the deployed VM. State of this VM
        #    should be "Stopped".

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id,
                                    startvm=False
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Stopped",
                            "VM should be in Running state after deployment"
                        )
        return


class TestRouterStateAfterDeploy(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):

        cls.api_client = super(
                               TestRouterStateAfterDeploy,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )

        # Create service offerings, disk offerings etc
        cls.service_offering = ServiceOffering.create(
                                    cls.api_client,
                                    cls.services["service_offering"]
                                    )
        cls.disk_offering = DiskOffering.create(
                                    cls.api_client,
                                    cls.services["disk_offering"]
                                    )
        # Cleanup
        cls._cleanup = [
                        cls.service_offering,
                        cls.disk_offering,
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def setUp(self):

        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.services = Services().services
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.services["virtual_machine"]["template"] = self.template.id
        self.services["iso"]["zoneid"] = self.zone.id
        self.account = Account.create(
                            self.apiclient,
                            self.services["account"],
                            domainid=self.domain.id
                            )
        self.cleanup = [self.account]
        return

    def tearDown(self):
        try:
            self.debug("Cleaning up the resources")
            cleanup_resources(self.apiclient, self.cleanup)
            self.debug("Cleanup complete!")
        except Exception as e:
            self.debug("Warning! Exception in tearDown: %s" % e)

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_01_deploy_vm_no_startvm(self):
        """Test Deploy Virtual Machine with no startVM parameter
        """

        # Validate the following:
        # 1. deploy Vm  without specifying the startvm parameter
        # 2. Should be able to login to the VM.
        # 3. listVM command should return the deployed VM.State of this VM
        #    should be "Running".

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine_1 = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id,
                                    startvm=False
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine_1.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine_1.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Stopped",
                            "VM should be in stopped state after deployment"
                        )
        self.debug("Checking the router state after VM deployment")
        routers = Router.list(
                              self.apiclient,
                              account=self.account.name,
                              domainid=self.account.domainid,
                              listall=True
                              )
        self.assertEqual(
                         routers,
                         None,
                         "List routers should return empty response"
                         )
        self.debug(
            "Deploying another instance (startvm=true) in the account: %s" %
                                                self.account.name)
        self.virtual_machine_2 = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    diskofferingid=self.disk_offering.id,
                                    startvm=True
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine_2.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine_2.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Running",
                            "VM should be in Running state after deployment"
                        )
        self.debug("Checking the router state after VM deployment")
        routers = Router.list(
                              self.apiclient,
                              account=self.account.name,
                              domainid=self.account.domainid,
                              listall=True
                              )
        self.assertEqual(
                         isinstance(routers, list),
                         True,
                         "List routers should not return empty response"
                         )
        for router in routers:
            self.debug("Router state: %s" % router.state)
            self.assertEqual(
                router.state,
                "Running",
                "Router should be in running state when instance is running in the account"
                )
        self.debug("Destroying the running VM:%s" %
                                        self.virtual_machine_2.name)
        self.virtual_machine_2.delete(self.apiclient)
        self.debug("Instance destroyed..waiting till expunge interval")

        interval = list_configurations(
                                    self.apiclient,
                                    name='expunge.interval'
                                    )
        delay = list_configurations(
                                    self.apiclient,
                                    name='expunge.delay'
                                    )
        # Sleep to ensure that all resources are deleted
        time.sleep((int(interval[0].value) + int(delay[0].value)) * 2)

        self.debug("Checking the router state after VM deployment")
        routers = Router.list(
                              self.apiclient,
                              account=self.account.name,
                              domainid=self.account.domainid,
                              listall=True
                              )
        self.assertNotEqual(
                         routers,
                         None,
                         "Router should get deleted after expunge delay+wait"
                         )
        return


class TestDeployVMBasicZone(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):

        cls.api_client = super(
                               TestDeployVMBasicZone,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )

        # Create service offerings, disk offerings etc
        cls.service_offering = ServiceOffering.create(
                                    cls.api_client,
                                    cls.services["service_offering"]
                                    )
        cls.disk_offering = DiskOffering.create(
                                    cls.api_client,
                                    cls.services["disk_offering"]
                                    )
        # Cleanup
        cls._cleanup = [
                        cls.service_offering,
                        cls.disk_offering,
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def setUp(self):

        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.services = Services().services
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.services["iso"]["zoneid"] = self.zone.id
        self.services["virtual_machine"]["template"] = self.template.id
        self.account = Account.create(
                            self.apiclient,
                            self.services["account"],
                            domainid=self.domain.id
                            )
        self.cleanup = [self.account]
        return

    def tearDown(self):
        try:
            self.debug("Cleaning up the resources")
            cleanup_resources(self.apiclient, self.cleanup)
            self.debug("Cleanup complete!")
        except Exception as e:
            self.debug("Warning! Exception in tearDown: %s" % e)

    @attr(tags = ["eip", "basic", "sg"])
    def test_01_deploy_vm_startvm_true(self):
        """Test Deploy Virtual Machine with startVM=true parameter
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=true
        # 2. Should be able to login to the VM.
        # 3. listVM command should return the deployed VM.State of this VM
        #    should be "Running".

        self.debug("Checking the network type of the zone: %s" %
                                                self.zone.networktype)
        self.assertEqual(
                         self.zone.networktype,
                         'Basic',
                         "Zone must be configured in basic networking mode"
                         )
        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=True,
                                    diskofferingid=self.disk_offering.id,
                                    mode=self.zone.networktype
                                )

        self.debug("Deployed instance ion account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Running",
                            "VM should be in Running state after deployment"
                        )
        return

    @attr(tags = ["eip", "basic", "sg"])
    def test_02_deploy_vm_startvm_false(self):
        """Test Deploy Virtual Machine with startVM=true parameter
        """

        # Validate the following:
        # 1. deploy Vm  with the startvm=true
        # 2. Should be able to login to the VM.
        # 3. listVM command should return the deployed VM.State of this VM
        #    should be "Running".

        self.debug("Checking the network type of the zone: %s" %
                                                self.zone.networktype)
        self.assertEqual(
                         self.zone.networktype,
                         'Basic',
                         "Zone must be configured in basic networking mode"
                         )
        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    startvm=False,
                                    mode=self.zone.networktype
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Stopped",
                            "VM should be in stopped state after deployment"
                        )
        self.debug("Starting the instance: %s" % self.virtual_machine.name)
        self.virtual_machine.start(self.apiclient)
        self.debug("Started the instance: %s" % self.virtual_machine.name)

        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Running",
                            "VM should be in running state after deployment"
                        )
        return


class TestDeployVMFromTemplate(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):

        cls.api_client = super(
                               TestDeployVMFromTemplate,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        # Create service, disk offerings  etc
        cls.service_offering = ServiceOffering.create(
                                    cls.api_client,
                                    cls.services["service_offering"],
                                    offerha=True
                                    )
        cls.disk_offering = DiskOffering.create(
                                    cls.api_client,
                                    cls.services["disk_offering"]
                                    )
        # Cleanup
        cls._cleanup = [
                        cls.service_offering,
                        cls.disk_offering,
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def setUp(self):

        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.services = Services().services
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.account = Account.create(
                            self.apiclient,
                            self.services["account"],
                            domainid=self.domain.id
                            )
        self.template = Template.register(
                                        self.apiclient,
                                        self.services["template"],
                                        zoneid=self.zone.id,
                                        account=self.account.name,
                                        domainid=self.account.domainid
                                        )
        try:
            self.template.download(self.apiclient)
        except Exception as e:
            raise Exception("Template download failed: %s" % e)

        self.cleanup = [self.account]
        return

    def tearDown(self):
        try:
            self.debug("Cleaning up the resources")
            cleanup_resources(self.apiclient, self.cleanup)
            self.debug("Cleanup complete!")
        except Exception as e:
            self.debug("Warning! Exception in tearDown: %s" % e)

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_deploy_vm_password_enabled(self):
        """Test Deploy Virtual Machine with startVM=false & enabledpassword in
        template
        """

        # Validate the following:
        # 1. Create the password enabled template
        # 2. Deploy Vm with this template and passing startvm=false
        # 3. Start VM. Deploy VM should be successful and it should be in Up
        #    and running state

        self.debug("Deploying instance in the account: %s" %
                                                self.account.name)
        self.virtual_machine = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    templateid=self.template.id,
                                    startvm=False,
                                )

        self.debug("Deployed instance in account: %s" %
                                                    self.account.name)
        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(

                            vm_response.state,
                            "Stopped",
                            "VM should be in stopped state after deployment"
                        )
        self.debug("Starting the instance: %s" % self.virtual_machine.name)
        self.virtual_machine.start(self.apiclient)
        self.debug("Started the instance: %s" % self.virtual_machine.name)

        list_vm_response = list_virtual_machines(
                                                 self.apiclient,
                                                 id=self.virtual_machine.id
                                                 )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
            )

        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )
        vm_response = list_vm_response[0]

        self.assertEqual(
                            vm_response.state,
                            "Running",
                            "VM should be in running state after deployment"
                        )
        return


class TestVMAccountLimit(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        cls.api_client = super(
                               TestVMAccountLimit,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )
        cls.services["virtual_machine"]["zoneid"] = cls.zone.id

        # Create Account, VMs etc
        cls.account = Account.create(
                            cls.api_client,
                            cls.services["account"],
                            domainid=cls.domain.id
                            )

        cls.service_offering = ServiceOffering.create(
                                            cls.api_client,
                                            cls.services["service_offering"]
                                            )
        cls._cleanup = [
                        cls.service_offering,
                        cls.account
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            #Cleanup resources used
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.cleanup = []
        return

    def tearDown(self):
        try:
            #Clean up, terminate the created instance, volumes and snapshots
            cleanup_resources(self.apiclient, self.cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_vm_per_account(self):
        """Test VM limit per account
        """

        # Validate the following
        # 1. Set the resource limit for VM per account.
        # 2. Deploy VMs more than limit in that account.
        # 3. AIP should error out

        self.debug(
            "Updating instance resource limit for account: %s" %
                                                self.account.name)
        # Set usage_vm=1 for Account 1
        update_resource_limit(
                              self.apiclient,
                              0,    # Instance
                              account=self.account.name,
                              domainid=self.account.domainid,
                              max=1
                              )
        self.debug(
            "Deploying VM instance in account: %s" %
                                        self.account.name)

        virtual_machine = VirtualMachine.create(
                                self.apiclient,
                                self.services["virtual_machine"],
                                templateid=self.template.id,
                                accountid=self.account.name,
                                domainid=self.account.domainid,
                                serviceofferingid=self.service_offering.id,
                                startvm=False
                                )

        # Verify VM state
        self.assertEqual(
                            virtual_machine.state,
                            'Stopped',
                            "Check VM state is Running or not"
                        )

        # Exception should be raised for second instance (account_1)
        with self.assertRaises(Exception):
            VirtualMachine.create(
                                self.apiclient,
                                self.services["virtual_machine"],
                                templateid=self.template.id,
                                accountid=self.account.name,
                                domainid=self.account.domainid,
                                serviceofferingid=self.service_offering.id,
                                startvm=False
                                )
        return


class TestUploadAttachVolume(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        cls.api_client = super(
                               TestUploadAttachVolume,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)

        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )
        cls.services["virtual_machine"]["zoneid"] = cls.zone.id

        # Create Account, VMs etc
        cls.account = Account.create(
                            cls.api_client,
                            cls.services["account"],
                            domainid=cls.domain.id
                            )

        cls.service_offering = ServiceOffering.create(
                                            cls.api_client,
                                            cls.services["service_offering"]
                                            )
        cls._cleanup = [
                        cls.service_offering,
                        cls.account
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            #Cleanup resources used
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.cleanup = []
        return

    def tearDown(self):
        try:
            #Clean up, terminate the created instance, volumes and snapshots
            cleanup_resources(self.apiclient, self.cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    @attr(tags = ["advanced", "eip", "advancedns", "basic", "sg"])
    def test_upload_attach_volume(self):
        """Test Upload volume and attach to VM in stopped state
        """

        # Validate the following
        # 1. Upload the volume using uploadVolume API call
        # 2. Deploy VM with startvm=false.
        # 3. Attach the volume to the deployed VM in step 2

        self.debug(
                "Uploading the volume: %s" %
                                            self.services["volume"]["diskname"])
        try:
            volume = Volume.upload(
                               self.apiclient,
                               self.services["volume"],
                               zoneid=self.zone.id,
                               account=self.account.name,
                               domainid=self.account.domainid
                               )
            self.debug("Uploading the volume: %s" % volume.name)
            volume.wait_for_upload(self.apiclient)
            self.debug("Volume: %s uploaded successfully")
        except Exception as e:
            self.fail("Failed to upload the volume: %s" % e)

        self.debug(
            "Deploying VM instance in account: %s" %
                                        self.account.name)

        virtual_machine = VirtualMachine.create(
                                self.apiclient,
                                self.services["virtual_machine"],
                                templateid=self.template.id,
                                accountid=self.account.name,
                                domainid=self.account.domainid,
                                serviceofferingid=self.service_offering.id,
                                startvm=False
                                )
        # Verify VM state
        self.assertEqual(
                            virtual_machine.state,
                            'Stopped',
                            "Check VM state is Running or not"
                        )
        with self.assertRaises(Exception):
            virtual_machine.attach_volume(self.apiclient, volume)
            self.debug("Failed to attach the volume as expected")
        return


class TestDeployOnSpecificHost(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        cls.api_client = super(
                               TestDeployOnSpecificHost,
                               cls
                               ).getClsTestClient().getApiClient()
        cls.services = Services().services
        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client, cls.services)
        cls.zone = get_zone(cls.api_client, cls.services)
        cls.template = get_template(
                            cls.api_client,
                            cls.zone.id,
                            cls.services["ostype"]
                            )
        cls.services["virtual_machine"]["zoneid"] = cls.zone.id
        cls.services["virtual_machine"]["template"] = cls.template.id

        cls.service_offering = ServiceOffering.create(
                                            cls.api_client,
                                            cls.services["service_offering"]
                                            )

        cls._cleanup = [
                        cls.service_offering,
                        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            #Cleanup resources used
            cleanup_resources(cls.api_client, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.account = Account.create(
                                     self.apiclient,
                                     self.services["account"],
                                     admin=True,
                                     domainid=self.domain.id
                                     )
        self.cleanup = []
        return

    def tearDown(self):
        try:
            self.account.delete(self.apiclient)
            cleanup_resources(self.apiclient, self.cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    @attr(tags=["advanced", "advancedns", "simulator",
                "api", "basic", "eip", "sg"])
    def test_deployVmOnGivenHost(self):
        """Test deploy VM on specific host
        """

        # Steps for validation
        # 1. as admin list available hosts that are Up
        # 2. deployVM with hostid=above host
        # 3. listVirtualMachines
        # 4. destroy VM
        # Validate the following
        # 1. listHosts returns at least one host in Up state
        # 2. VM should be in Running
        # 3. VM should be on the host that it was deployed on

        hosts = Host.list(
                          self.apiclient,
                          zoneid=self.zone.id,
                          type='Routing',
                          state='Up',
                          listall=True
                          )

        self.assertEqual(
                         isinstance(hosts, list),
                         True,
                         "CS should have atleast one host Up and Running"
                         )

        host = hosts[0]
        self.debug("Deploting VM on host: %s" % host.name)

        try:
            vm = VirtualMachine.create(
                                    self.apiclient,
                                    self.services["virtual_machine"],
                                    templateid=self.template.id,
                                    accountid=self.account.name,
                                    domainid=self.account.domainid,
                                    serviceofferingid=self.service_offering.id,
                                    hostid=host.id
                                    )
            self.debug("Deploy VM succeeded")
        except Exception as e:
            self.fail("Deploy VM failed with exception: %s" % e)

        self.debug("Cheking the state of deployed VM")
        vms = VirtualMachine.list(
                                self.apiclient,
                                id=vm.id,
                                listall=True,
                                account=self.account.name,
                                domainid=self.account.domainid
                                )

        self.assertEqual(
                         isinstance(vms, list),
                         True,
                         "List Vm should return a valid response"
                         )

        vm_response = vms[0]
        self.assertEqual(
                         vm_response.state,
                         "Running",
                         "VM should be in running state after deployment"
                         )
        self.assertEqual(
                         vm_response.hostid,
                         host.id,
                         "Host id where VM is deployed should match"
                         )
        return
