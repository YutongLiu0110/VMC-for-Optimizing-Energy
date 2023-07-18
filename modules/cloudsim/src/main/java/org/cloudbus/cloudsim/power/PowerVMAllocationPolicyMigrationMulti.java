package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.lists.PowerVmList;

import java.util.*;

public class PowerVMAllocationPolicyMigrationMulti extends PowerVmAllocationPolicyMigrationAbstract {

    private double higherUtilizationThreshold = 0.9; //默认初始超载阈值
    private double lowerUtilizationThreshold = 0.3; //静态低载阈值，相应的提高低载阈值可以提高负载均衡程度

    protected double energyMax; //DC最大可容忍能耗

    private PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy;

    public PowerVMAllocationPolicyMigrationMulti(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double energyMax,
            PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy) {
        super(hostList, vmSelectionPolicy);
        setEnergyMax(energyMax);
        setFallbackVmAllocationPolicy(fallbackVmAllocationPolicy);
    }

    //超载主机判断
    @Override
    protected boolean isHostOverUtilized(PowerHost host) {
        int count = 0;
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host; //获取历史负载
        double[] utilizationHistory = _host.getUtilizationHistory();
        int length = 10;

        if (utilizationHistory.length < length) { //历史负载长度小于10，直接判断，不采用防抖动防误触处理
            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
        }

        updateHigherUtilizationThreshold(); //动态更新阈值
        for (int i = 0; i < length; i++) {
            if (utilizationHistory[length - i - 1] > getHigherUtilizationThreshold())
                //获取主机最新的10个负载记录
                count ++;
        }

        //addHistoryEntry(host, getHigherUtilizationThreshold());

        return count > 5; //10个记录中有超过5个大于超载阈值，则判定为超载
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
//                        if (powerDiff < minPower) { //只测试算法一
                        if ((powerDiff < minPower) && utilization(host) ) { //测试算法一、算法三的综合效果
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
        return utilization < 0.7 && utilization > 0.5;
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

    //获得低载主机
    protected PowerHost getUnderUtilizedHost(Set<? extends Host> excludedHosts) {
        for (PowerHost host : this.<PowerHost> getHostList()) {
            if (excludedHosts.contains(host)) {
                continue;
            }
            double utilization = host.getUtilizationOfCpu();
            if (utilization > 0 && utilization <= getLowerUtilizationThreshold())
                return host;
        }
        return null;
    }

    //获得当前时刻的数据中心能耗
    protected double getEnergy() {
        double DatacenterEnergy = 0;
        double timeDiff = 300; //300秒
        for (PowerHost host : this.<PowerHost> getHostList()) {
            double previousUtilizationOfCpu = host.getPreviousUtilizationOfCpu(); //5分钟前物理机CPU利用率
            double utilizationOfCpu = host.getUtilizationOfCpu(); //当前时刻物理机CPU利用率
            double HostEnergy = host.getEnergyLinearInterpolation( //物理机能耗，通过线性插值获得
                    previousUtilizationOfCpu,
                    utilizationOfCpu,
                    timeDiff);
            DatacenterEnergy += HostEnergy;//将主机能耗累加得到数据中心能耗
        }
        return DatacenterEnergy;
    }

    protected void updateHigherUtilizationThreshold() { //能耗感知的动态超载阈值
        double energy = getEnergy() / (3600 * 1000); //kWh
        if (energy < 0.5 * getEnergyMax()) { //理想低能耗状态时
            higherUtilizationThreshold = 0.9;
        } else if (energy > 0.75 * getEnergyMax()) { //高能耗状态时，阈值进入定系数减小过程
            higherUtilizationThreshold = higherUtilizationThreshold * 0.9;
            if (higherUtilizationThreshold < 0.6) {
                higherUtilizationThreshold = 0.6;
            }
        } else { //较高能耗状态时，阈值进入定步长减小过程
            higherUtilizationThreshold -= 0.05;
            if (higherUtilizationThreshold < 0.7) {
                higherUtilizationThreshold = 0.7;
            }
        }
    }

    protected double getHigherUtilizationThreshold() {
        return higherUtilizationThreshold;
    }
    protected double getLowerUtilizationThreshold() {
        return lowerUtilizationThreshold;
    }

    protected void setEnergyMax(double energyMax1) {
        this.energyMax = energyMax1;
    }
    protected double getEnergyMax() {
        return energyMax;
    }

    public void setFallbackVmAllocationPolicy(
            PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy) {
        this.fallbackVmAllocationPolicy = fallbackVmAllocationPolicy;
    }
    public PowerVmAllocationPolicyMigrationAbstract getFallbackVmAllocationPolicy() {
        return fallbackVmAllocationPolicy;
    }

}
