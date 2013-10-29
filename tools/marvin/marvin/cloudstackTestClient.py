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

import cloudstackConnection
import asyncJobMgr
import dbConnection
from cloudstackAPI import *

'''
@Desc  : CloudStackTestClient is encapsulated class for getting various \
         clients viz., apiclient,dbconnection etc
@Input : mgmtDetails : Management Server Details
         dbSvrDetails: Database Server details of Management \
                       Server. Retrieved from configuration file.
         asyncTimeout :
         defaultWorkerThreads :
         logging :
'''


class cloudstackTestClient(object):
    def __init__(self, mgmtDetails,
                 dbSvrDetails, asyncTimeout=3600,
                 defaultWorkerThreads=10,
                 logging=None):
        self.connection = \
            cloudstackConnection.cloudConnection(mgmtDetails,
                                                 asyncTimeout,
                                                 logging)
        self.apiClient =\
            cloudstackAPIClient.CloudStackAPIClient(self.connection)
        self.dbConnection = None
        if dbSvrDetails is not None:
                self.createDbConnection(dbSvrDetails.dbSvr, dbSvrDetails.port,
                                        dbSvrDetails.user,
                                        dbSvrDetails.passwd, dbSvrDetails.db)
        self.asyncJobMgr = None
        self.id = None
        self.defaultWorkerThreads = defaultWorkerThreads

    @property
    def identifier(self):
        return self.id

    @identifier.setter
    def identifier(self, id):
        self.id = id

    def createDbConnection(self, host="localhost", port=3306, user='cloud',
                           passwd='cloud', db='cloud'):
        self.dbConnection = dbConnection.dbConnection(host, port, user,
                                                      passwd, db)

    def isAdminContext(self):
        """
        A user is a regular user if he fails to listDomains;
        if he is a domain-admin, he can list only domains that are non-ROOT;
        if he is an admin, he can list the ROOT domain successfully
        """
        try:
            listdom = listDomains.listDomainsCmd()
            listdom.name = 'ROOT'
            listdomres = self.apiClient.listDomains(listdom)
            rootdom = listdomres[0].name
            if rootdom == 'ROOT':
                return 1  # admin
            else:
                return 2  # domain-admin
        except:
            return 0  # user

    def createUserApiClient(self, UserName, DomainName, acctType=0):
        if not self.isAdminContext():
            return self.apiClient

        listDomain = listDomains.listDomainsCmd()
        listDomain.listall = True
        listDomain.name = DomainName
        try:
            domains = self.apiClient.listDomains(listDomain)
            domId = domains[0].id
        except:
            cdomain = createDomain.createDomainCmd()
            cdomain.name = DomainName
            domain = self.apiClient.createDomain(cdomain)
            domId = domain.id

        cmd = listAccounts.listAccountsCmd()
        cmd.name = UserName
        cmd.domainid = domId
        try:
            accounts = self.apiClient.listAccounts(cmd)
            acctId = accounts[0].id
        except:
            createAcctCmd = createAccount.createAccountCmd()
            createAcctCmd.accounttype = acctType
            createAcctCmd.domainid = domId
            createAcctCmd.email = "test-" + self.random_gen()\
                + "@cloudstack.org"
            createAcctCmd.firstname = UserName
            createAcctCmd.lastname = UserName
            createAcctCmd.password = 'password'
            createAcctCmd.username = UserName
            acct = self.apiClient.createAccount(createAcctCmd)
            acctId = acct.id

        listuser = listUsers.listUsersCmd()
        listuser.username = UserName

        listuserRes = self.apiClient.listUsers(listuser)
        userId = listuserRes[0].id
        apiKey = listuserRes[0].apikey
        securityKey = listuserRes[0].secretkey

        if apiKey is None:
            registerUser = registerUserKeys.registerUserKeysCmd()
            registerUser.id = userId
            registerUserRes = self.apiClient.registerUserKeys(registerUser)
            apiKey = registerUserRes.apikey
            securityKey = registerUserRes.secretkey

        newUserConnection =\
            cloudstackConnection.cloudConnection(self.connection.mgtSvr,
                                                 self.connection.port,
                                                 self.connection.user,
                                                 self.connection.passwd,
                                                 apiKey, securityKey,
                                                 self.connection.asyncTimeout,
                                                 self.connection.logging)
        self.userApiClient =\
            cloudstackAPIClient.CloudStackAPIClient(newUserConnection)
        self.userApiClient.connection = newUserConnection
        self.userApiClient.hypervisor = self.apiClient.hypervisor
        return self.userApiClient

    def close(self):
        if self.connection is not None:
            self.connection.close()

    def getDbConnection(self):
        return self.dbConnection

    def getApiClient(self):
        self.apiClient.id = self.identifier
        return self.apiClient

    def getUserApiClient(self, account, domain, type=0):
        """
        0 - user
        1 - admin
        2 - domain admin
        """
        self.createUserApiClient(account, domain, type)
        if hasattr(self, "userApiClient"):
            return self.userApiClient
        return None

    def submitCmdsAndWait(self, cmds, workers=1):
        '''FixME, httplib has issue if more than one thread submitted'''
        if self.asyncJobMgr is None:
            self.asyncJobMgr = asyncJobMgr.asyncJobMgr(self.apiClient,
                                                       self.dbConnection)
        return self.asyncJobMgr.submitCmdsAndWait(cmds, workers)

    def submitJob(self, job, ntimes=1, nums_threads=10, interval=1):
        '''
        submit one job and execute the same job ntimes, with nums_threads
        of threads
        '''
        if self.asyncJobMgr is None:
            self.asyncJobMgr = asyncJobMgr.asyncJobMgr(self.apiClient,
                                                       self.dbConnection)
        self.asyncJobMgr.submitJobExecuteNtimes(job, ntimes, nums_threads,
                                                interval)

    def submitJobs(self, jobs, nums_threads=10, interval=1):
        '''submit n jobs, execute them with nums_threads of threads'''
        if self.asyncJobMgr is None:
            self.asyncJobMgr = asyncJobMgr.asyncJobMgr(self.apiClient,
                                                       self.dbConnection)
        self.asyncJobMgr.submitJobs(jobs, nums_threads, interval)
