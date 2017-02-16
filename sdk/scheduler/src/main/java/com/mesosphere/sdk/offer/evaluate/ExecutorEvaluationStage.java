package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.executor.ExecutorUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that executor IDs match between
 * the two and setting the executor ID for a newly-launching pod.
 */
public class ExecutorEvaluationStage implements OfferEvaluationStage {
    private final Protos.ExecutorID executorId;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Instantiate with an expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. If not found,
     * the offer will be rejected by this stage.
     * @param executorId the executor ID to look for in incoming offers
     */
    public ExecutorEvaluationStage(Protos.ExecutorID executorId) {
        this.executorId = executorId;
    }

    /**
     * Instantiate with no expected {@link org.apache.mesos.Protos.ExecutorID} to check for in offers. A new ID will
     * be created for the {@link org.apache.mesos.Protos.ExecutorInfo} at evaluation time.
     */
    public ExecutorEvaluationStage() {
        this(null);
    }

    @Override
    public EvaluationOutcome evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) {
        if (!offerRequirement.getExecutorRequirementOptional().isPresent()) {
            return pass(this, "No executor requirement defined");
        }

        Protos.Offer offer = mesosResourcePool.getOffer();
        Protos.ExecutorInfo executorInfo = offerRequirement.getExecutorRequirementOptional()
                .get().getExecutorInfo();
        if (!hasExpectedExecutorId(offer)) {
            return fail(this,
                    "Offer does not contain the needed Executor ID: '%s'",
                    executorInfo.getExecutorId().getValue().toString());
        }

        String msg = "ERROR: message failed to be set";
        // Set executor ID *after* the other check above for its presence:
        Protos.ExecutorID newExecutorId;
        if (executorId != null && !executorId.getValue().isEmpty()) {
            newExecutorId = executorId;
            msg = String.format("Offer contains the matching Executor ID: %s", newExecutorId.getValue());
        } else {
            newExecutorId = ExecutorUtils.toExecutorId(executorInfo.getName());
            msg = String.format("No Executor ID expected, generated: %s", newExecutorId.getValue());
        }

        offerRequirement.updateExecutorRequirement(executorInfo.toBuilder()
                .setExecutorId(newExecutorId)
                .build());

        return pass(this, msg);
    }

    private boolean hasExpectedExecutorId(Protos.Offer offer) {
        if (executorId == null) {
            return true;
        }

        for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
            if (execId.equals(executorId)) {
                return true;
            }
        }

        return false;
    }
}
