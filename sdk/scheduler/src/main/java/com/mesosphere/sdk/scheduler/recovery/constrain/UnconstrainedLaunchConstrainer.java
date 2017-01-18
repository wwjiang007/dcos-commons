package com.mesosphere.sdk.scheduler.recovery.constrain;

import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos.Offer.Operation;

/**
 * Implementation of {@link LaunchConstrainer} that always allows launches.
 * <p>
 * This is equivalent to disabling the launch constraining feature.
 */
public class UnconstrainedLaunchConstrainer implements LaunchConstrainer {
    @Override
    public void launchHappened(PodInstance podInstance, Operation launchOperation, RecoveryType recoveryType) {
        //do nothing
    }

    @Override
    public boolean canLaunch(PodInstance podInstance, RecoveryType recoveryType) {
        return true;
    }
}
