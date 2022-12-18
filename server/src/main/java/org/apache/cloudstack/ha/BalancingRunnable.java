package org.apache.cloudstack.ha;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;

import com.cloud.vm.UserVmService;
import com.cloud.host.dao.HostDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.ManagementServerException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.VirtualMachineMigrationException;
import org.apache.cloudstack.api.ServerApiException;
import com.cloud.host.HostVO;
import com.cloud.vm.VMInstanceVO;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.ResponseGenerator;

import org.apache.cloudstack.api.ApiErrorCode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class BalancingRunnable implements Runnable {
    public static final Logger LOG = Logger.getLogger(BalancingRunnable.class);

    @Inject
    private HostDao hostDao;

    @Inject
    protected UserVmService userVmService;

    @Inject
    private VMInstanceDao vmInstanceDao;

    @Inject
    public ResponseGenerator responseGenerator;

    public void balancingCheck (long clusterId) {
        LOG.info("======================");
        LOG.info("clusterId = " + clusterId);
        HashMap<Long, Long> hostMemMap = new HashMap<Long, Long>();
        // List keyList = new ArrayList();
        // List<? extends HostVO> hosts = hostDao.findByClusterId(clusterId);
        // LOG.info("Hostsss = "+hosts);
        for (final HostVO host: hostDao.findByClusterId(clusterId)) {
        // for (HostVO host : hosts) {
            LOG.info("Host = "+host);
            LOG.info("Host id = "+host.getId());
            LOG.info("Host cap = "+host.getCapabilities());
            HostResponse hostResponse = responseGenerator.createHostResponse(host);
            LOG.info("hostResponse =" + hostResponse);
            LOG.info(hostResponse.getId());
            LOG.info("allocated = " + hostResponse.getMemoryAllocated());
            LOG.info("total = " + hostResponse.getMemoryTotal());
            LOG.info("used = " + hostResponse.getMemoryUsed());
            LOG.info(hostResponse.getMemoryAllocated()*100/hostResponse.getMemoryTotal());
            LOG.info("======================");

            // hostMemMap.put(hostResponse.getId(), hostResponse.getMemoryUsed()*100/hostResponse.getMemoryTotal());
            hostMemMap.put(host.getId(), hostResponse.getMemoryAllocated()*100/hostResponse.getMemoryTotal());

            // keyList.add(hostResponse.getMemoryAllocated()*100/hostResponse.getMemoryTotal());
            // keyList.add(host.getPrivateIpAddress());

            // hostMemMap.put(hostResponse.getId(), keyList);
            // {hostResponse.getMemoryAllocated()*100/hostResponse.getMemoryTotal(), host.getPrivateIpAddress()};
        }

        // Comparator 정의
        Comparator<Entry<Long, Long>> comparator = new Comparator<Entry<Long, Long>>() {
            @Override
            public int compare(Entry<Long, Long> e1, Entry<Long, Long> e2) {
                return e1.getValue().compareTo(e2.getValue());
            }
        };

        // Max Value의 key, value
        Entry<Long, Long> maxEntry = Collections.max(hostMemMap.entrySet(), comparator);

        // Min Value의 key, value
        Entry<Long, Long> minEntry = Collections.min(hostMemMap.entrySet(), comparator);

        LOG.info("start======================");
        LOG.info("maxEntry = " + maxEntry.getValue() + ", minEntry = " + minEntry.getValue());
        LOG.info("persent = " + (maxEntry.getValue() - minEntry.getValue()));
        //memory 값이 10% 이상 차이나면 vm migration
        if ((maxEntry.getValue() - minEntry.getValue()) > 10 ) {
            String hostIp = "";
            for (final HostVO host: hostDao.findByClusterId(clusterId)) {
                LOG.info("maxEntry.getKey() = "+maxEntry.getKey());
                LOG.info("host.getUuid() = "+host.getUuid());
                LOG.info("host.getId() = "+host.getId());
                if(host.getId() == maxEntry.getKey()) {
                    hostIp = host.getPrivateIpAddress();
                }
            }
            balancingMonitor(maxEntry.getKey(), hostIp);
        }

        //1분 체크
        try {
            Thread.sleep(60000);
            balancingCheck(clusterId);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        LOG.info("end======================");
    }

    private void balancingMonitor(Long hostId, String hostIp) {
        // List<? extends VMInstanceVO> vmList = vmInstanceDao.listByHostId(hostId);
        Map<Long, Long> vmMemMap = new ConcurrentHashMap<Long, Long>();

        for (final VMInstanceVO vm: vmInstanceDao.listByHostId(hostId)) {
            //host ip 조회
            LOG.info("hostId = "+hostId);
            // HostVO host = hostDao.findByUuid(hostId);
            // LOG.info("hostIp = "+host.getPrivateIpAddress());
            String instanceName = vm.getInstanceName();
            String s;
            String vm_pid = "";
            Long oomScore;
            Process p;
            try {
                //vm pid
                LOG.info("instanceName = "+instanceName);
                // String cmd = "ps -aux | grep "+ instanceName +" | awk '{print $2}' | head -1";
                String cmd = "ssh root@"+ hostIp +" 'ps -aux | grep "+ instanceName +"' | awk '{print $2}' | head -1";
                LOG.info("cmd = "+cmd);
                p = Runtime.getRuntime().exec(cmd);
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuffer sb = new StringBuffer();
                while ((s = br.readLine()) != null)
                    sb.append(s);
                LOG.info("sb = "+sb);
                vm_pid = sb.toString();
                p.waitFor();
                p.destroy();

                LOG.info("vm_pid = "+vm_pid);

                //oom_score
                // cmd = "cat /proc/"+ vm_pid +"/oom_score";
                cmd = "ssh root@"+ hostIp +" cat /proc/"+ vm_pid +"/oom_score";
                p = Runtime.getRuntime().exec(cmd);
                br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                sb = new StringBuffer();
                while ((s = br.readLine()) != null)
                    sb.append(s);
                oomScore = Long.parseLong(sb.toString());
                p.waitFor();
                p.destroy();

                LOG.info("oomScore = "+oomScore);
                LOG.info("vm.getId() = "+vm.getId());

                vmMemMap.put(vm.getId(), oomScore);
            } catch (Exception e) {
            }
        }

        // Comparator 정의
        Comparator<Entry<Long, Long>> comparator = new Comparator<Entry<Long, Long>>() {
            @Override
            public int compare(Entry<Long, Long> e1, Entry<Long, Long> e2) {
                return e1.getValue().compareTo(e2.getValue());
            }
        };

        // Min Value의 key, value
        Entry<Long, Long> minEntry = Collections.min(vmMemMap.entrySet(), comparator);

        LOG.info("vm minEntry = "+minEntry.getKey());

        //vm migration
        try {
            userVmService.migrateVirtualMachine(minEntry.getKey(), null);
        } catch (ResourceUnavailableException ex) {
            LOG.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        } catch (VirtualMachineMigrationException | ConcurrentOperationException | ManagementServerException e) {
            LOG.warn("Exception: ", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
    }

    /*
    public static void main(long clusterId) {
        LOG.info("======================");
        Map<String, Long> hostMemMap = new ConcurrentHashMap<String, Long>();
        List<? extends HostVO> hosts = hostDao.findByClusterId(clusterId);
        for (HostVO host : hosts) {
            LOG.info("Host = "+host);
            LOG.info("Host id = "+host.getId());
            LOG.info("Host cap = "+host.getCapabilities());
            HostResponse hostResponse = _responseGenerator.createHostResponse(host);
            LOG.info("hostResponse =" + hostResponse);
            LOG.info(hostResponse.getId());
            LOG.info(hostResponse.getMemoryAllocated());
            LOG.info(hostResponse.getMemoryTotal());
            LOG.info(hostResponse.getMemoryUsed());
            LOG.info(hostResponse.getMemoryUsed()*100/hostResponse.getMemoryTotal());
            LOG.info("======================");

            // hostMemMap.put(hostResponse.getId(), hostResponse.getMemoryUsed()*100/hostResponse.getMemoryTotal());
            hostMemMap.put(hostResponse.getId(), hostResponse.getMemoryAllocated()*100/hostResponse.getMemoryTotal());
        }

        // // Max
        // Entry<String, Long> maxEntry = null;

        // // Iterator
        // Set<Entry<String, Long>> entrySet = hostMemMap.entrySet();
        // for (Entry<String, Long> entry : entrySet) {
        //     if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
        //         maxEntry = entry;
        //      }
        // }

        // Comparator 정의
        Comparator<Entry<String, Long>> comparator = new Comparator<Entry<String, Long>>() {
            @Override
            public int compare(Entry<String, Long> e1, Entry<String, Long> e2) {
                return e1.getValue().compareTo(e2.getValue());
            }
        };

        // Max Value의 key, value
        Entry<String, Long> maxEntry = Collections.max(hostMemMap.entrySet(), comparator);

        // Min Value의 key, value
        Entry<String, Long> minEntry = Collections.min(hostMemMap.entrySet(), comparator);

        LOG.info("start======================");
        LOG.info("maxEntry = " + maxEntry.getValue() + ", minEntry = " + minEntry.getValue());

        if ((maxEntry.getValue() - minEntry.getValue()) > 10 ) {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        LOG.info("end======================");

    }*/
}