package com.mesosphere.sdk.offer;

import java.util.List;
import java.util.UUID;

import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Port;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * Utilities relating to creating and reading {@link DiscoveryInfo}s, specifically around Ports and VIPs.
 */
public class DiscoveryUtils {
    private static final String PORT_LABEL_PREFIX = "PORT_";
    private static final String VIP_LABEL_PREFIX = "VIP_";
    private static final String VIP_HOST_TLD = "l4lb.thisdcos.directory";
    private static final DiscoveryInfo.Visibility PUBLIC_PORT_VISIBILITY = DiscoveryInfo.Visibility.EXTERNAL;
    private static final String DEFAULT_VIP_PROTOCOL = "tcp";

    private DiscoveryUtils() {
        // do not instantiate
    }

    /**
     * Returns the appropriate {@link DiscoveryInfo.Visibility} level given the provided boolean {@code advertised}
     * setting.
     */
    public static DiscoveryInfo.Visibility toVisibility(Boolean advertiseSetting) {
        if (advertiseSetting == null) {
            // Option unset, default true
            return PUBLIC_PORT_VISIBILITY;
        }
        return advertiseSetting ? PUBLIC_PORT_VISIBILITY : DiscoveryInfo.Visibility.CLUSTER;
    }

    /**
     * Returns the appropriate protocol string given the provided {@code protocol} setting.
     */
    public static String toProtocol(String protocolSetting) {
        return Strings.isNullOrEmpty(protocolSetting) ? DEFAULT_VIP_PROTOCOL : protocolSetting;
    }

    /**
     * Returns whether the provided {@link DiscoveryInfo.Visibility} should be advertised.
     */
    public static boolean isAdvertise(DiscoveryInfo.Visibility visibility) {
        return visibility != null && visibility == PUBLIC_PORT_VISIBILITY;
    }


    /**
     * Returns a suitable VIP-based hostname for the provided VIP/service name.
     */
    public static String toVIPHost(String serviceName, String vipName) {
        return String.format("%s.%s.%s", vipName, serviceName, VIP_HOST_TLD);
    }

    /**
     * Returns a suitable VIP-based hostname for the provided VIP/service name, with port added to the end.
     */
    public static String toVIPHost(String serviceName, String vipName, int port) {
        return String.format("%s:%d", toVIPHost(serviceName, vipName), port);
    }

    /**
     * Adds or updates the provided port discovery information in the provided {@link DiscoveryInfo.Builder}.
     * If a matching port is already present, the existing port will be updated in-place.
     */
    public static void setPort(
            DiscoveryInfo.Builder builder,
            String taskName,
            String portName,
            int port) {
        initialize(builder, taskName);

        // Search for a matching port entry whose info can be updated in-place:
        Port.Builder existingPort =
                findMatchingPort(builder.getPortsBuilder().getPortsBuilderList(), PORT_LABEL_PREFIX, portName);
        if (existingPort != null) {
            // Update existing entry in-place:
            existingPort.setNumber(port);
        } else {
            // No match found, add a new entry:
            builder.getPortsBuilder().addPortsBuilder()
                    .setNumber(port)
                    .getLabelsBuilder().addLabelsBuilder()
                            .setKey(String.format("%s%s", PORT_LABEL_PREFIX, UUID.randomUUID().toString()))
                            .setValue(portName);
        }
    }

    /**
     * Adds or updates the provided VIP discovery information in the provided {@link DiscoveryInfo.Builder}.
     * If a matching VIP is already present, the existing VIP will be updated in-place.
     */
    public static void setVIP(
            DiscoveryInfo.Builder builder,
            String taskName,
            String vipName,
            int vipPort,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            int destPort) {
        initialize(builder, taskName);

        final String vipLabelValue = String.format("%s:%d", vipName, vipPort);

        // Search for a matching VIP entry whose info can be updated in-place:
        Port.Builder existingPort =
                findMatchingPort(builder.getPortsBuilder().getPortsBuilderList(), VIP_LABEL_PREFIX, vipLabelValue);
        if (existingPort != null) {
            // Update existing entry in-place:
            existingPort
                    .setNumber(destPort)
                    .setProtocol(protocol)
                    .setVisibility(visibility);
        } else {
            // No match found, add a new entry:
            builder.getPortsBuilder().addPortsBuilder()
                    .setNumber(destPort)
                    .setProtocol(protocol)
                    .setVisibility(visibility)
                    .getLabelsBuilder().addLabelsBuilder()
                            .setKey(String.format("%s%s", VIP_LABEL_PREFIX, UUID.randomUUID().toString()))
                            .setValue(vipLabelValue);
        }
    }

    private static Port.Builder findMatchingPort(List<Port.Builder> ports, String labelKeyPrefix, String labelValue) {
        for (Port.Builder portBuilder : ports) {
            for (Label l : portBuilder.getLabels().getLabelsList()) {
                if (l.getKey().startsWith(labelKeyPrefix) && l.getValue().equals(labelValue)) {
                    return portBuilder;
                }
            }
        }
        return null;
    }

    /**
     * Initializes the provided {@link DiscoveryInfo.Builder}.
     */
    private static void initialize(DiscoveryInfo.Builder builder, String taskName) {
        // We always clean up DiscoveryInfo name and visibility:
        // - New DiscoveryInfo: set initial values
        // - Preexisting DiscoveryInfo: repair values from previous versions of the SDK
        builder
            .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
            .setName(taskName);
    }

    /**
     * Parses VIP information out of a {@link DiscoveryInfo} port {@link Label}.
     */
    public static class VIPLabelParser {
        private final String name;
        private final int port;

        private VIPLabelParser(String name, int port) {
            this.name = name;
            this.port = port;
        }

        public static VIPLabelParser parse(String taskName, Label label) throws TaskException {
            if (!label.getKey().startsWith(VIP_LABEL_PREFIX)) {
                return null;
            }
            List<String> namePort = Splitter.on(':').splitToList(label.getValue());
            if (namePort.size() != 2) {
                throw new TaskException(String.format(
                        "Task %s's VIP value for %s is invalid, expected 2 components but got %d: %s",
                        taskName, label.getKey(), namePort.size(), label.getValue()));
            }
            int vipPort;
            try {
                vipPort = Integer.parseInt(namePort.get(1));
            } catch (NumberFormatException e) {
                throw new TaskException(String.format(
                        "Unable to Task %s's VIP port from %s as an int",
                        taskName, label.getValue()), e);
            }
            return new VIPLabelParser(namePort.get(0), vipPort);
        }

        public String getVIPName() {
            return name;
        }

        public int getVIPPort() {
            return port;
        }

        public String getVIPHost(String serviceName) {
            return DiscoveryUtils.toVIPHost(serviceName, name, port);
        }
    }
}
