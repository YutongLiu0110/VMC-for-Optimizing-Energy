/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.util.MathUtil;

/**
 * A VM selection policy that selects for migration the VM with the Maximum Correlation Coefficient (MCC) among 
 * a list of migratable VMs.
 * 
 * <br/>If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:<br/>
 * 
 * <ul>
 * <li><a href="http://dx.doi.org/10.1002/cpe.1867">Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012</a>
 * </ul>
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 3.0
 */
public class PowerVmSelectionPolicyMaximumCorrelation extends PowerVmSelectionPolicy {

	/** The fallback VM selection policy to be used when
         * the  Maximum Correlation policy doesn't have data to be computed. */
	private PowerVmSelectionPolicy fallbackPolicy;

	/**
	 * Instantiates a new PowerVmSelectionPolicyMaximumCorrelation.
	 * 
	 * @param fallbackPolicy the fallback policy
	 */
	public PowerVmSelectionPolicyMaximumCorrelation(final PowerVmSelectionPolicy fallbackPolicy) {
		super();
		setFallbackPolicy(fallbackPolicy);
	}

	@Override
	public Vm getVmToMigrate(final PowerHost host) {//得到需要迁移的虚拟机
		List<PowerVm> migratableVms = getMigratableVms(host);//从给定主机获取可迁移VM的列表
		if (migratableVms.isEmpty()) {
			return null;
		}
		List<Double> metrics = null;
		try {
			metrics = getCorrelationCoefficients(getUtilizationMatrix(migratableVms));//通过利用率矩阵来获取相关系数
		} catch (IllegalArgumentException e) { // the degrees of freedom must be greater than zero
			return getFallbackPolicy().getVmToMigrate(host);//通过备用方案返回需要迁移的虚拟机
		}
		double maxMetric = Double.MIN_VALUE;//定义最大标准
		int maxIndex = 0;//定义最大标准对应的虚拟机的下标
		for (int i = 0; i < metrics.size(); i++) {//遍历相关系数列表
			double metric = metrics.get(i);//依次获取每一个相关系数
			if (metric > maxMetric) {//如果相关系数大于最大标准
				maxMetric = metric;//更新相关系数
				maxIndex = i;//得到对应的坐标
			}
		}
		return migratableVms.get(maxIndex);//返回对应的虚拟机
	}

	/**
	 * Gets the CPU utilization percentage matrix for a given list of VMs.
	 * 
	 * @param vmList the VM list
	 * @return the CPU utilization percentage matrix, where each line i
         * is a VM and each column j is a CPU utilization percentage history for that VM.
	 */
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

	/**
	 * Gets the min CPU utilization percentage history size among a list of VMs.
	 * 
	 * @param vmList the VM list
	 * @return the min CPU utilization percentage history size of the VM list
	 */
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

	/**
	 * Gets the correlation coefficients.
	 * 
	 * @param data the data
	 * @return the correlation coefficients
	 */
	protected List<Double> getCorrelationCoefficients(final double[][] data) {
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

	/**
	 * Gets the fallback policy.
	 * 
	 * @return the fallback policy
	 */
	public PowerVmSelectionPolicy getFallbackPolicy() {
		return fallbackPolicy;
	}

	/**
	 * Sets the fallback policy.
	 * 
	 * @param fallbackPolicy the new fallback policy
	 */
	public void setFallbackPolicy(final PowerVmSelectionPolicy fallbackPolicy) {
		this.fallbackPolicy = fallbackPolicy;
	}

}
