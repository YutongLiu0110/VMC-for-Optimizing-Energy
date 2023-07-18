package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.power.lists.PowerVmList;

import java.util.*;

public class PowerVmAllocationPolicyMigrationM extends PowerVmAllocationPolicyMigrationAbstract {
    private double utilizationThreshold = 0.8; //静态超载阈值

    public PowerVmAllocationPolicyMigrationM(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double utilizationThreshold) {
        super(hostList, vmSelectionPolicy);
        setUtilizationThreshold(utilizationThreshold);
    }

    /**
     * Checks if a host is over utilized, based on CPU usage.
     *
     * @param host the host
     * @return true, if the host is over utilized; false otherwise
     */
    @Override
    protected boolean isHostOverUtilized(PowerHost host) {
        addHistoryEntry(host, getUtilizationThreshold());
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        return utilization > getUtilizationThreshold();
    }

    public PowerHost findHostForVm2(Vm vm, Set<? extends Host> excludedHosts) {
        double minPower = Double.MAX_VALUE;
        PowerHost allocatedHost = null;

        for (PowerHost host : this.<PowerHost> getHostList()) {
            if (excludedHosts.contains(host)) { //排除超载主机
                continue;
            }
            if (host.isSuitableForVm(vm)) { //满足资源约束
                if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
                    //if 主机为开机状态 且迁入虚拟机后会超载  then 跳过该主机
                    continue;
                }
                try {
                    //主要目标：选择一个能耗变化最小的host
                    double powerAfterAllocation = getPowerAfterAllocation(host, vm);//并未将虚拟机实际分配给主机进行的能耗计算
                    if (powerAfterAllocation != -1) {
                        double powerDiff = powerAfterAllocation - host.getPower();
//                        if (powerDiff < minPower) {
                        if ((powerDiff < minPower) && utilization(host) ) { // 测试算法三
                            minPower = powerDiff;
                            allocatedHost = host;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        return allocatedHost;
    }

    protected  boolean utilization (PowerHost host) {
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        return utilization < 0.6 && utilization > 0.4;
    }

    protected List<Map<String, Object>> getNewVmPlacement(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        PowerVmList.sortByCpuUtilization(vmsToMigrate);
        for (Vm vm : vmsToMigrate) {
            PowerHost allocatedHost = findHostForVm2(vm, excludedHosts);
            if (allocatedHost != null) {
                allocatedHost.vmCreate(vm); //在主机上创建虚拟机
                Log.printConcatLine("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());

                Map<String, Object> migrate = new HashMap<String, Object>();
                migrate.put("vm", vm);
                migrate.put("host", allocatedHost);
                migrationMap.add(migrate);
            }
        }
        return migrationMap;
    }

    protected void setUtilizationThreshold(double utilizationThreshold) {
        this.utilizationThreshold = utilizationThreshold;
    }

    protected double getUtilizationThreshold() {
        return utilizationThreshold;
    }
}
