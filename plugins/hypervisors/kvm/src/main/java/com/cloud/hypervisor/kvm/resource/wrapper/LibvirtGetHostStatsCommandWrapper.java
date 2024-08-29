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

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetHostStatsAnswer;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.HostStatsEntry;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.utils.Pair;

import javax.inject.Inject;

import org.apache.cloudstack.utils.linux.CPUStat;
import org.apache.cloudstack.utils.linux.MemStat;
import com.cloud.utils.script.Script;

@ResourceWrapper(handles =  GetHostStatsCommand.class)
public final class LibvirtGetHostStatsCommandWrapper extends CommandWrapper<GetHostStatsCommand, Answer, LibvirtComputingResource> {

    @Inject
    private VolumeDao _volumeDao;

    @Override
    public Answer execute(final GetHostStatsCommand command, final LibvirtComputingResource libvirtComputingResource) {
        CPUStat cpuStat = libvirtComputingResource.getCPUStat();
        MemStat memStat = libvirtComputingResource.getMemStat();

        final double cpuUtil = cpuStat.getCpuUsedPercent();
        final double loadAvg = cpuStat.getCpuLoadAverage();

        final Pair<Double, Double> nicStats = libvirtComputingResource.getNicStats(libvirtComputingResource.getPublicBridgeName());

        final HostStatsEntry hostStats = new HostStatsEntry(command.getHostId(), cpuUtil, nicStats.first() / 1024, nicStats.second() / 1024, "host", memStat.getTotal() / 1024, memStat.getAvailable() / 1024, 0, loadAvg);
        String[] ret = Script.runSimpleBashScript("vdostats | awk 'NR > 1 {print $1, $6}' | tr '\n' '/'").split("/");
        for(int i=0 ; i < ret.length ; i++){
            //volume id and saving data % extraction
            String[] kvdoInfo = ret[i].split(" ");
            String volume_uuid = kvdoInfo[0].replace("vg_", "").replace("-vpool0-vpool", "");
            if (volume_uuid.length() == 32) {
                String uuid = volume_uuid.substring(0, 8) + "-" +
                    volume_uuid.substring(8, 12) + "-" +
                    volume_uuid.substring(12, 16) + "-" +
                    volume_uuid.substring(16, 20) + "-" +
                    volume_uuid.substring(20, 32);
                VolumeVO volumeVO = _volumeDao.findByUuid(uuid);
                volumeVO.setSavingStats(kvdoInfo[1]);
                _volumeDao.update(volumeVO.getId(), volumeVO);
            }
        }
        return new GetHostStatsAnswer(command, hostStats);
    }
}
