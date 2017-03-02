package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.reconciliation.Reconciler;

import java.util.Arrays;

/**
 * Phase that implements reconciliation. It has exactly one Step, the reconciliation {@link Step}.
 */
public final class ReconciliationPhase extends DefaultPhase {

    public static ReconciliationPhase create(Reconciler reconciler) {
        return new ReconciliationPhase(reconciler);
    }

    private ReconciliationPhase(Reconciler reconciler) {
        super("Reconciliation", Arrays.asList(ReconciliationStep.create(reconciler)));
    }
}
