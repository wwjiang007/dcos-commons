package com.mesosphere.sdk.offer.evaluate;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.DiscoveryUtils;

/**
 * Encompasses logic for writing {@link Protos.DiscoveryInfo}s into task information during evaluation.
 */
public interface DiscoveryInfoWriter {

    public void writeTaskDiscoveryInfo(Protos.TaskInfo.Builder taskBuilder, Protos.Resource resource, int port);
    public void writeExecutorDiscoveryInfo(
            Protos.ExecutorInfo.Builder executorBuilder, Protos.Resource resource, int port);

    public static DiscoveryInfoWriter createPortWriter(String portName) {
        return new PortWriter(portName);
    }

    public static DiscoveryInfoWriter createVIPWriter(
            String protocol, Protos.DiscoveryInfo.Visibility visibility, String vipName, Integer vipPort) {
        return new VIPWriter(protocol, visibility, vipName, vipPort);
    }

    /**
     * Writes DiscoveryInfo for (non-VIP) ports.
     */
    static class PortWriter implements DiscoveryInfoWriter {
        private final String portName;

        private PortWriter(String portName) {
            this.portName = portName;
        }

        @Override
        public void writeTaskDiscoveryInfo(Protos.TaskInfo.Builder taskBuilder, Protos.Resource resource, int port) {
            DiscoveryUtils.setPort(taskBuilder.getDiscoveryBuilder(), taskBuilder.getName(), portName, port);
        }

        @Override
        public void writeExecutorDiscoveryInfo(
                Protos.ExecutorInfo.Builder executorBuilder, Protos.Resource resource, int port) {
            DiscoveryUtils.setPort(executorBuilder.getDiscoveryBuilder(), executorBuilder.getName(), portName, port);
        }
    }

    /**
     * Writes DiscoveryInfo for VIPs.
     */
    static class VIPWriter implements DiscoveryInfoWriter {
        private final String protocol;
        private final Protos.DiscoveryInfo.Visibility visibility;
        private final String vipName;
        private final Integer vipPort;

        private VIPWriter(
                String protocol, Protos.DiscoveryInfo.Visibility visibility, String vipName, Integer vipPort) {
            this.protocol = protocol;
            this.visibility = visibility;
            this.vipName = vipName;
            this.vipPort = vipPort;
        }

        @Override
        public void writeTaskDiscoveryInfo(
                Protos.TaskInfo.Builder taskBuilder, Protos.Resource resource, int destPort) {
            DiscoveryUtils.setVIP(
                    taskBuilder.getDiscoveryBuilder(),
                    taskBuilder.getName(),
                    vipName,
                    vipPort,
                    protocol,
                    visibility,
                    destPort);
        }

        @Override
        public void writeExecutorDiscoveryInfo(
                Protos.ExecutorInfo.Builder executorBuilder, Protos.Resource resource, int destPort) {
            DiscoveryUtils.setVIP(
                    executorBuilder.getDiscoveryBuilder(),
                    executorBuilder.getName(),
                    vipName,
                    vipPort,
                    protocol,
                    visibility,
                    destPort);
        }
    }
}
