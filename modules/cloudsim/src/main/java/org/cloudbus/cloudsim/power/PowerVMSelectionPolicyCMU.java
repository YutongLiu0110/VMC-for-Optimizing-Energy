package org.cloudbus.cloudsim.power;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.MathUtil;

public class PowerVMSelectionPolicyCMU extends PowerVmSelectionPolicy {

    private PowerVmSelectionPolicy fallbackPolicy;

    public PowerVMSelectionPolicyCMU(final PowerVmSelectionPolicy fallbackPolicy) {
        super();
        setFallbackPolicy(fallbackPolicy);
    }

    @Override
    public Vm getVmToMigrate(PowerHost host) {
        List<PowerVm> migratableVms = getMigratableVms(host);//从给定主机获取可迁移VM的列表
        if (migratableVms.isEmpty()) {
            return null;
        }
        Vm vmToMigrate = null;
        List<Double> rs = null;
        try {
            rs = getCorrelationCoefficients(getUtilizationMatrix(migratableVms));//通过利用率矩阵来获取相关系数
        } catch (IllegalArgumentException e) {
            return getFallbackPolicy().getVmToMigrate(host);//通过备用方案返回需要迁移的虚拟机
        }

        double minMetric = Double.MAX_VALUE;
        int i = 0;
        for (Vm vm : migratableVms) {
            if (vm.isInMigration()) {
                continue;
            }
            double metric = vm.getTotalUtilizationOfCpuMips(CloudSim.clock()) / vm.getMips();
            double r = rs.get(i);//获取相关系数
            if ((metric < minMetric) && (Math.abs(r) > 0.5)) {
                minMetric = metric;
                vmToMigrate = vm;
            }
            i++;
        }
        return vmToMigrate;
    }

    protected double[][] getUtilizationMatrix(final List<PowerVm> vmList) {//获取给定VM列表的CPU利用率百分比矩阵
        int n = vmList.size();
                /*@todo It gets the min size of the history among all VMs considering
                that different VMs can have different history sizes.
                However, the j loop is not using the m variable
                but the size of the vm list. If a VM list has
                a size greater than m, it will thow an exception.
                It as to be included a test case for that.*/
        int m = getMinUtilizationHistorySize(vmList);//得到虚拟机历史利用率的大小
        double[][] utilization = new double[n][m];//构造二维数组来存储相关系数
        for (int i = 0; i < n; i++) {
            List<Double> vmUtilization = vmList.get(i).getUtilizationHistory();//获取每台虚拟机的历史利用率
            for (int j = 0; j < vmUtilization.size(); j++) {
                utilization[i][j] = vmUtilization.get(j);
            }
        }
        return utilization;
    }
    protected int getMinUtilizationHistorySize(final List<PowerVm> vmList) {
        int minSize = Integer.MAX_VALUE;
        for (PowerVm vm : vmList) {
            int size = vm.getUtilizationHistory().size();
            if (size < minSize) {
                minSize = size;
            }
        }
        return minSize;
    }
    protected List<Double> getCorrelationCoefficients(final double[][] data) {//获得相关系数
        int n = data.length;
        int m = data[0].length;
        List<Double> correlationCoefficients = new LinkedList<Double>();
        for (int i = 0; i < n; i++) {
            double[][] x = new double[n - 1][m];
            int k = 0;
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    x[k++] = data[j];
                }
            }

            // Transpose the matrix so that it fits the linear model
            double[][] xT = new Array2DRowRealMatrix(x).transpose().getData();

            // RSquare is the "coefficient of determination"
            correlationCoefficients.add(MathUtil.createLinearRegression(xT,
                    data[i]).calculateRSquared());
        }
        return correlationCoefficients;
    }
    public PowerVmSelectionPolicy getFallbackPolicy() {
        return fallbackPolicy;
    }
    public void setFallbackPolicy(final PowerVmSelectionPolicy fallbackPolicy) {
        this.fallbackPolicy = fallbackPolicy;
    }
}
