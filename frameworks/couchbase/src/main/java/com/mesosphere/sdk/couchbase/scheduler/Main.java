package com.mesosphere.sdk.couchbase.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;

import java.io.File;
import java.util.*;

import static com.mesosphere.sdk.offer.Constants.DEPLOY_PLAN_NAME;

/**
 * Template service.
 */
public class Main {
    private static final String TASK_NAME_SERVER = "server";
    private static final String TASK_NAME_ADD_DATA_NODE = "add-data-node";
    private static final String TASK_NAME_ADD_INDEX_NODE = "add-index-node";
    private static final String TASK_NAME_ADD_QUERY_NODE = "add-query-node";
    private static final String TASK_NAME_INITIALIZE_CLUSTER = "initialize-cluster";
    private static final String TASK_NAME_RENAME_SEED_HOST = "rename-seed-host";
    private static final String TASK_NAME_REBALANCE_CLUSTER = "rebalance-cluster";
    private static final String TASK_NAME_ADD_BEER_SAMPLE_BUCKET = "add-beer-sample-bucket";
    private static final String TASK_NAME_ADD_TRAVEL_SAMPLE_BUCKET = "add-travel-sample-bucket";
    private static final String TASK_NAME_ADD_GAMESIM_SAMPLE_BUCKET = "add-gamesim-sample-bucket";

    private static final String POD_NAME_DATA = "couchbase_data";
    private static final String POD_NAME_INDEX = "couchbase_index";
    private static final String POD_NAME_QUERY = "couchbase_query";

    private static final String PHASE_DATA = "setup-data-nodes";
    private static final String PHASE_INDEX = "setup-index-nodes";
    private static final String PHASE_QUERY = "setup-query-nodes";
    private static final String PHASE_REBALANCE = "rebalance-cluster";
    private static final String PHASE_LOAD_SAMPLE_DATA = "load-sample-data";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(new File(args[0]));
            ServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);

            ConfigStore<ServiceSpec> configStore = DefaultScheduler.createConfigStore(serviceSpec);
            StateStore stateStore = DefaultScheduler.createStateStore(serviceSpec);
            StepFactory stepFactory = new DefaultStepFactory(configStore, stateStore);

            Plan deployPlan = getDeployPlan(serviceSpec, stepFactory);

            Collection<Plan> plans = Collections.singleton(deployPlan);
            DefaultScheduler.Builder builder = DefaultScheduler.newBuilder(serviceSpec)
                    .setPlans(plans)
                    .setConfigStore(configStore)
                    .setStateStore(stateStore);
            new DefaultService(builder);
        }
    }

    private static Plan getDeployPlan(ServiceSpec serviceSpec, StepFactory stepFactory) throws Exception {
        List<Phase> phases = new ArrayList<>();

        phases.add(getDataPhase(serviceSpec, stepFactory));
        phases.add(getIndexQueryPhase(serviceSpec, stepFactory, PHASE_INDEX, POD_NAME_INDEX, TASK_NAME_ADD_INDEX_NODE));
        phases.add(getIndexQueryPhase(serviceSpec, stepFactory, PHASE_QUERY, POD_NAME_QUERY, TASK_NAME_ADD_QUERY_NODE));
        phases.add(getRebalancePhase(serviceSpec, stepFactory));
        phases.add(getSampleBucketPhase(serviceSpec, stepFactory));

        // TODO: how do we scale down?
        // 1. unreserve resources
        // 2. kill task
        // 3. remove state associated with task

        return new DefaultPlan(DEPLOY_PLAN_NAME, phases, new SerialStrategy<>());
    }

    private static Phase getSampleBucketPhase(ServiceSpec serviceSpec, StepFactory stepFactory) throws Exception {
        List<Step> steps = new ArrayList<>();

        PodSpec dataPodSpec = getPodSpecByName(serviceSpec, POD_NAME_DATA);
        PodInstance seedInstance = new DefaultPodInstance(dataPodSpec, 0);

        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_ADD_BEER_SAMPLE_BUCKET)));
        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_ADD_TRAVEL_SAMPLE_BUCKET)));
        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_ADD_GAMESIM_SAMPLE_BUCKET)));

        return new DefaultPhase(PHASE_LOAD_SAMPLE_DATA, steps);
    }

    private static Phase getRebalancePhase(ServiceSpec serviceSpec, StepFactory stepFactory) throws Exception {
        List<Step> steps = new ArrayList<>();

        PodSpec dataPodSpec = getPodSpecByName(serviceSpec, POD_NAME_DATA);
        PodInstance seedInstance = new DefaultPodInstance(dataPodSpec, 0);

        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_REBALANCE_CLUSTER)));
        return new DefaultPhase(PHASE_REBALANCE, steps);
    }

    private static Phase getIndexQueryPhase(
            ServiceSpec serviceSpec,
            StepFactory  stepFactory,
            String phaseName,
            String podName,
            String addTaskName) throws Exception {
        List<Step> steps = new ArrayList<Step>();
        PodSpec podSpec = getPodSpecByName(serviceSpec, podName);

        for (int i = 1; i < podSpec.getCount(); i++) {
            PodInstance podInstance = new DefaultPodInstance(podSpec, i);
            steps.add(stepFactory.getStep(podInstance, Collections.singleton(TASK_NAME_SERVER)));
            steps.add(stepFactory.getStep(podInstance, Collections.singleton(addTaskName)));
        }
        return new DefaultPhase(phaseName, steps);
    }

    private static Phase getDataPhase(ServiceSpec serviceSpec, StepFactory stepFactory) throws Exception {
        List<Step> steps = new ArrayList<Step>();

        PodSpec dataPodSpec = getPodSpecByName(serviceSpec, POD_NAME_DATA);
        PodInstance seedInstance = new DefaultPodInstance(dataPodSpec, 0);
        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_SERVER)));
        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_INITIALIZE_CLUSTER)));
        steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_RENAME_SEED_HOST)));

        for (int i = 1; i < dataPodSpec.getCount(); i++) {
            PodInstance podInstance = new DefaultPodInstance(dataPodSpec, i);
            steps.add(stepFactory.getStep(podInstance, Collections.singleton(TASK_NAME_SERVER)));
            steps.add(stepFactory.getStep(podInstance, Collections.singleton(TASK_NAME_ADD_DATA_NODE)));
        }
        return new DefaultPhase(PHASE_DATA, steps);
    }

    private static PodSpec getPodSpecByName(ServiceSpec serviceSpec, String name) {
        return serviceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals(name))
                .findAny()
                .get();
    }
}
