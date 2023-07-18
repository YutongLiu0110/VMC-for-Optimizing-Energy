package org.cloudbus.cloudsim.examples.power.planetlab;

import java.io.IOException;

public class myTest {
    /**
     * The main method.
     *
     * @param args the arguments
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void main(String[] args) throws IOException {
        boolean enableOutput = true;
        boolean outputToFile = false;
        String inputFolder = NonPowerAware.class.getClassLoader().getResource("workload/planetlab").getPath();
        String outputFolder = "output";
        String workload = "20110412"; // PlanetLab workload
        String vmAllocationPolicy = "multi";
//        String vmAllocationPolicy = "lr";
        String vmSelectionPolicy = "mmt"; // Maximum Correlation (MC) VM selection policy
        String parameter = "300"; //最大可容忍能耗为350kWh 300kWh
//        String parameter = "1.2";
        new PlanetLabRunner(
                enableOutput,
                outputToFile,
                inputFolder,
                outputFolder,
                workload,
                vmAllocationPolicy,
                vmSelectionPolicy,
                parameter);
    }
}
