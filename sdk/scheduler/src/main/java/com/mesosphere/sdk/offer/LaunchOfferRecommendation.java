package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.TaskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public class LaunchOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;
    private final boolean isTransient;
    private final TaskInfo taskInfo;

    public LaunchOfferRecommendation(Offer offer, TaskInfo originalTaskInfo) {
        this.offer = offer;
        this.isTransient = CommonTaskUtils.isTransient(originalTaskInfo);

        TaskInfo.Builder taskInfoBuilder = originalTaskInfo.toBuilder()
                .setSlaveId(offer.getSlaveId());
        if (isTransient) {
            taskInfoBuilder.getTaskIdBuilder().setValue("");
        }
        this.taskInfo = taskInfoBuilder.build();

        Protos.TaskGroupInfo taskGroupInfo = Protos.TaskGroupInfo.newBuilder()
                .addTasks(taskInfo.toBuilder().clearExecutor())
                .build();

        Protos.ExecutorInfo executorInfo = taskInfo.getExecutor();

        Operation.LaunchGroup launchGroup = Operation.LaunchGroup.newBuilder()
                .setTaskGroup(taskGroupInfo)
                .setExecutor(executorInfo)
                .build();

        this.operation = Operation.newBuilder()
                .setType(Operation.Type.LAUNCH_GROUP)
                .setLaunchGroup(launchGroup)
                .build();
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public Offer getOffer() {
        return offer;
    }

    public boolean isTransient() {
        return isTransient;
    }

    /**
     * Returns the original, unpacked {@link TaskInfo} to be launched. This varies from the {@link TaskInfo} stored
     * within the {@link Operation}, which is packed.
     */
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
