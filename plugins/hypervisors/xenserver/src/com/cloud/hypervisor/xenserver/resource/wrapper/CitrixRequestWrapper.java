//
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
//

package com.cloud.hypervisor.xenserver.resource.wrapper;

import java.util.Hashtable;

import org.apache.cloudstack.storage.command.StorageSubSystemCommand;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.AttachIsoCommand;
import com.cloud.agent.api.AttachVolumeCommand;
import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.CheckNetworkCommand;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.CleanupNetworkRulesCmd;
import com.cloud.agent.api.ClusterVMMetaDataSyncCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.CreateVMSnapshotCommand;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.DeleteVMSnapshotCommand;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmDiskStatsCommand;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.GetVncPortCommand;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.ModifySshKeysCommand;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.NetworkRulesSystemVmCommand;
import com.cloud.agent.api.NetworkRulesVmSecondaryIpCommand;
import com.cloud.agent.api.OvsCreateGreTunnelCommand;
import com.cloud.agent.api.OvsCreateTunnelCommand;
import com.cloud.agent.api.OvsDeleteFlowCommand;
import com.cloud.agent.api.OvsDestroyBridgeCommand;
import com.cloud.agent.api.OvsDestroyTunnelCommand;
import com.cloud.agent.api.OvsFetchInterfaceCommand;
import com.cloud.agent.api.OvsSetTagAndFlowCommand;
import com.cloud.agent.api.OvsSetupBridgeCommand;
import com.cloud.agent.api.OvsVpcPhysicalTopologyConfigCommand;
import com.cloud.agent.api.OvsVpcRoutingPolicyConfigCommand;
import com.cloud.agent.api.PerformanceMonitorCommand;
import com.cloud.agent.api.PingTestCommand;
import com.cloud.agent.api.PlugNicCommand;
import com.cloud.agent.api.PrepareForMigrationCommand;
import com.cloud.agent.api.PvlanSetupCommand;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.RebootRouterCommand;
import com.cloud.agent.api.RevertToVMSnapshotCommand;
import com.cloud.agent.api.ScaleVmCommand;
import com.cloud.agent.api.SecurityGroupRulesCmd;
import com.cloud.agent.api.SetupCommand;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.UnPlugNicCommand;
import com.cloud.agent.api.UpdateHostPasswordCommand;
import com.cloud.agent.api.UpgradeSnapshotCommand;
import com.cloud.agent.api.check.CheckSshCommand;
import com.cloud.agent.api.proxy.CheckConsoleProxyLoadCommand;
import com.cloud.agent.api.proxy.WatchConsoleProxyLoadCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.DestroyCommand;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.agent.api.storage.ResizeVolumeCommand;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.RequestWrapper;
import com.cloud.resource.ServerResource;

public class CitrixRequestWrapper extends RequestWrapper {

    private static CitrixRequestWrapper instance;

    static {
        instance = new CitrixRequestWrapper();
    }

    @SuppressWarnings("rawtypes")
    private final Hashtable<Class<? extends Command>, CommandWrapper> map;

    @SuppressWarnings("rawtypes")
    private CitrixRequestWrapper() {
        map = new Hashtable<Class<? extends Command>, CommandWrapper>();
        init();
    }

    private void init() {
        map.put(RebootRouterCommand.class, new CitrixRebootRouterCommandWrapper());
        map.put(CreateCommand.class, new CitrixCreateCommandWrapper());
        map.put(CheckConsoleProxyLoadCommand.class, new CitrixCheckConsoleProxyLoadCommandWrapper());
        map.put(WatchConsoleProxyLoadCommand.class, new CitrixWatchConsoleProxyLoadCommandWrapper());
        map.put(ReadyCommand.class, new CitrixReadyCommandWrapper());
        map.put(GetHostStatsCommand.class, new CitrixGetHostStatsCommandWrapper());
        map.put(GetVmStatsCommand.class, new CitrixGetVmStatsCommandWrapper());
        map.put(GetVmDiskStatsCommand.class, new CitrixGetVmDiskStatsCommandWrapper());
        map.put(CheckHealthCommand.class, new CitrixCheckHealthCommandWrapper());
        map.put(StopCommand.class, new CitrixStopCommandWrapper());
        map.put(RebootCommand.class, new CitrixRebootCommandWrapper());
        map.put(CheckVirtualMachineCommand.class, new CitrixCheckVirtualMachineCommandWrapper());
        map.put(PrepareForMigrationCommand.class, new CitrixPrepareForMigrationCommandWrapper());
        map.put(MigrateCommand.class, new CitrixMigrateCommandWrapper());
        map.put(DestroyCommand.class, new CitrixDestroyCommandWrapper());
        map.put(CreateStoragePoolCommand.class, new CitrixCreateStoragePoolCommandWrapper());
        map.put(ModifyStoragePoolCommand.class, new CitrixModifyStoragePoolCommandWrapper());
        map.put(DeleteStoragePoolCommand.class, new CitrixDeleteStoragePoolCommandWrapper());
        map.put(ResizeVolumeCommand.class, new CitrixResizeVolumeCommandWrapper());
        map.put(AttachVolumeCommand.class, new CitrixAttachVolumeCommandWrapper());
        map.put(AttachIsoCommand.class, new CitrixAttachIsoCommandWrapper());
        map.put(UpgradeSnapshotCommand.class, new CitrixUpgradeSnapshotCommandWrapper());
        map.put(GetStorageStatsCommand.class, new CitrixGetStorageStatsCommandWrapper());
        map.put(PrimaryStorageDownloadCommand.class, new CitrixPrimaryStorageDownloadCommandWrapper());
        map.put(GetVncPortCommand.class, new CitrixGetVncPortCommandWrapper());
        map.put(SetupCommand.class, new CitrixSetupCommandWrapper());
        map.put(MaintainCommand.class, new CitrixMaintainCommandWrapper());
        map.put(PingTestCommand.class, new CitrixPingTestCommandWrapper());
        map.put(CheckOnHostCommand.class, new CitrixCheckOnHostCommandWrapper());
        map.put(ModifySshKeysCommand.class, new CitrixModifySshKeysCommandWrapper());
        map.put(StartCommand.class, new CitrixStartCommandWrapper());
        map.put(OvsSetTagAndFlowCommand.class, new CitrixOvsSetTagAndFlowCommandWrapper());
        map.put(CheckSshCommand.class, new CitrixCheckSshCommandWrapper());
        map.put(SecurityGroupRulesCmd.class, new CitrixSecurityGroupRulesCommandWrapper());
        map.put(OvsFetchInterfaceCommand.class, new CitrixOvsFetchInterfaceCommandWrapper());
        map.put(OvsCreateGreTunnelCommand.class, new CitrixOvsCreateGreTunnelCommandWrapper());
        map.put(OvsDeleteFlowCommand.class, new CitrixOvsDeleteFlowCommandWrapper());
        map.put(OvsVpcPhysicalTopologyConfigCommand.class, new CitrixOvsVpcPhysicalTopologyConfigCommandWrapper());
        map.put(OvsVpcRoutingPolicyConfigCommand.class, new CitrixOvsVpcRoutingPolicyConfigCommandWrapper());
        map.put(CleanupNetworkRulesCmd.class, new CitrixCleanupNetworkRulesCmdWrapper());
        map.put(NetworkRulesSystemVmCommand.class, new CitrixNetworkRulesSystemVmCommandWrapper());
        map.put(OvsCreateTunnelCommand.class, new CitrixOvsCreateTunnelCommandWrapper());
        map.put(OvsSetupBridgeCommand.class, new CitrixOvsSetupBridgeCommandWrapper());
        map.put(OvsDestroyBridgeCommand.class, new CitrixOvsDestroyBridgeCommandWrapper());
        map.put(OvsDestroyTunnelCommand.class, new CitrixOvsDestroyTunnelCommandWrapper());
        map.put(UpdateHostPasswordCommand.class, new CitrixUpdateHostPasswordCommandWrapper());
        map.put(ClusterVMMetaDataSyncCommand.class, new CitrixClusterVMMetaDataSyncCommandWrapper());
        map.put(CheckNetworkCommand.class, new CitrixCheckNetworkCommandWrapper());
        map.put(PlugNicCommand.class, new CitrixPlugNicCommandWrapper());
        map.put(UnPlugNicCommand.class, new CitrixUnPlugNicCommandWrapper());
        map.put(CreateVMSnapshotCommand.class, new CitrixCreateVMSnapshotCommandWrapper());
        map.put(DeleteVMSnapshotCommand.class, new CitrixDeleteVMSnapshotCommandWrapper());
        map.put(RevertToVMSnapshotCommand.class, new CitrixRevertToVMSnapshotCommandWrapper());
        map.put(NetworkRulesVmSecondaryIpCommand.class, new CitrixNetworkRulesVmSecondaryIpCommandWrapper());
        map.put(ScaleVmCommand.class, new CitrixScaleVmCommandWrapper());
        map.put(PvlanSetupCommand.class, new CitrixPvlanSetupCommandWrapper());
        map.put(PerformanceMonitorCommand.class, new CitrixPerformanceMonitorCommandWrapper());
        map.put(NetworkElementCommand.class, new CitrixNetworkElementCommandWrapper());
    }

    public static CitrixRequestWrapper getInstance() {
        return instance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Answer execute(final Command command, final ServerResource serverResource) {
        CommandWrapper<Command, Answer, ServerResource> commandWrapper = map.get(command.getClass());

        // This is temporary. We have to map the classes with several sub-classes better.
        if (commandWrapper == null && command instanceof StorageSubSystemCommand) {
            commandWrapper = map.get(StorageSubSystemCommand.class);
        }
        if (commandWrapper == null && command instanceof NetworkElementCommand) {
            commandWrapper = map.get(NetworkElementCommand.class);
        }

        if (commandWrapper == null) {
            throw new NullPointerException("No key found for '" + command.getClass() + "' in the Map!");
        }

        return commandWrapper.execute(command, serverResource);
    }
}