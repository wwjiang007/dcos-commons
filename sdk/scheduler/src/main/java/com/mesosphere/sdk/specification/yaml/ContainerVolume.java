package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Raw YAML container volume.
 */
public class ContainerVolume {
    private final String mode;
    private final String containerPath;
    private final String hostPath;
    private final double size;
    private final String type;

    private ContainerVolume(
            @JsonProperty("mode") String mode,
            @JsonProperty("container-path") String containerPath,
            @JsonProperty("host-path") String hostPath,
            @JsonProperty("size") double size,
            @JsonProperty("type") String type) {
        this.mode = mode;
        this.containerPath = containerPath;
        this.hostPath = hostPath;
        this.size = size;
        this.type = type;
    }

    @JsonProperty("mode")
    public String getMode() {
        return mode;
    }

    @JsonProperty("container-path")
    public String getContainerPath() {
        return containerPath;
    }

    @JsonProperty("host-path")
    public String getHostPath() {
        return hostPath;
    }

    @JsonProperty("size")
    public double getSize() {
        return size;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
