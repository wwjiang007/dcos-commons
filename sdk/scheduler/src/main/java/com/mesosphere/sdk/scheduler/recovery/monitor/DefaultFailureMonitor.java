package com.mesosphere.sdk.scheduler.recovery.monitor;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DefaultFailureMonitor reports that tasks have Failed permanently when they are so labeled.
 */
public class DefaultFailureMonitor implements FailureMonitor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConfigStore<ServiceSpec> configStore;

    public DefaultFailureMonitor(ConfigStore<ServiceSpec> configStore) {
        this.configStore = configStore;
    }

    @Override
    public boolean hasFailed(Protos.TaskInfo taskInfo) {
        return FailureUtils.isLabeledAsFailed(taskInfo) || !isSticky(taskInfo);
    }

    private boolean isSticky(Protos.TaskInfo taskInfo) {
        try {
           return TaskUtils.getPodInstance(configStore, taskInfo).getPod().isSticky();
        } catch (TaskException e) {
            logger.error("Failed to find pod instance for Task: {}", taskInfo.getTaskId().getValue(), e);
            // Default to sticky to avoid unintentional UNRESERVE / DESTROY operations.
            return true;
        }
    }
}
