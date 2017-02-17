package com.mesosphere.sdk.couchbase.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;

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
    private static final String TASK_NAME_REFRESH_NODE = "refresh-node";
    private static final String TASK_NAME_ADD_SEED_DATA_NODE = "add-seed-data-node";

    private static final String POD_NAME_DATA = "couchbase_data";
    private static final String POD_NAME_INDEX = "couchbase_index";
    private static final String POD_NAME_QUERY = "couchbase_query";

    private static final String PHASE_DATA = "setup-data-nodes";
    private static final String PHASE_INDEX = "setup-index-nodes";
    private static final String PHASE_QUERY = "setup-query-nodes";
    private static final String PHASE_REBALANCE = "rebalance-cluster";
    private static final String PHASE_LOAD_SAMPLE_DATA = "load-sample-data";

    private static final String ENV_DEMO_DATA_INSTALL_BEER_SAMPLE = "DEMO_DATA_INSTALL_BEER_SAMPLE";
    private static final String ENV_DEMO_DATA_INSTALL_TRAVEL_SAMPLE = "DEMO_DATA_INSTALL_TRAVEL_SAMPLE";
    private static final String ENV_DEMO_DATA_INSTALL_GAMESIM_SAMPLE = "DEMO_DATA_INSTALL_GAMESIM_SAMPLE";

    private final ServiceSpec serviceSpec;
    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final StepFactory stepFactory;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            ServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                    YAMLServiceSpecFactory.generateRawSpecFromYAML(new File(args[0])));

            Main main = new Main(serviceSpec);

            Plan deployPlan = main.getDeployPlan();

            Collection<Plan> plans = Collections.singleton(deployPlan);
            DefaultScheduler.Builder builder = DefaultScheduler.newBuilder(serviceSpec)
                    .setPlans(plans)
                    .setConfigStore(main.getConfigStore())
                    .setStateStore(main.getStateStore());
            new DefaultService(builder);
        }
    }

    public Main(ServiceSpec serviceSpec) throws Exception {
        this.serviceSpec = serviceSpec;
        this.configStore = DefaultScheduler.createConfigStore(serviceSpec);
        this.stateStore = DefaultScheduler.createStateStore(serviceSpec);
        this.stepFactory = new DefaultStepFactory(configStore, stateStore);
    }

    private Plan getDeployPlan() throws Exception {
        List<Phase> phases = new ArrayList<>();

        phases.add(getDataPhase());
        phases.add(getIndexQueryPhase(PHASE_INDEX, POD_NAME_INDEX, TASK_NAME_ADD_INDEX_NODE));
        phases.add(getIndexQueryPhase(PHASE_QUERY, POD_NAME_QUERY, TASK_NAME_ADD_QUERY_NODE));
        phases.add(getRebalancePhase());
        phases.add(getSampleBucketPhase());

        // TODO: how do we scale down?
        // 1. unreserve resources
        // 2. kill task
        // 3. remove state associated with task

        return new DefaultPlan(DEPLOY_PLAN_NAME, phases, new SerialStrategy<>());
    }

    private Phase getDataPhase() throws Exception {
        List<Step> steps = new ArrayList<Step>();

        PodSpec podSpec = getPodSpecByName(serviceSpec, POD_NAME_DATA);
        PodInstance seedInstance = new DefaultPodInstance(podSpec, 0);

        steps.addAll(seedNodeSteps(seedInstance));
        for (int i = 1; i < podSpec.getCount(); i++) {
            PodInstance podInstance = new DefaultPodInstance(podSpec, i);
            steps.addAll(serverSteps(podInstance, TASK_NAME_ADD_DATA_NODE, true));
        }

        return new DefaultPhase(PHASE_DATA, steps);
    }

    private Phase getIndexQueryPhase(
            String phaseName,
            String podName,
            String addTaskName) throws Exception {
        List<Step> steps = new ArrayList<Step>();
        PodSpec podSpec = getPodSpecByName(serviceSpec, podName);

        for (int i = 0; i < podSpec.getCount(); i++) {
            PodInstance podInstance = new DefaultPodInstance(podSpec, i);
            steps.addAll(serverSteps(podInstance, addTaskName, true));
        }
        return new DefaultPhase(phaseName, steps);
    }

    private Phase getRebalancePhase() throws Exception {
        List<Step> steps = new ArrayList<>();

        PodSpec dataPodSpec = getPodSpecByName(serviceSpec, POD_NAME_DATA);
        PodInstance seedInstance = new DefaultPodInstance(dataPodSpec, 0);

        steps.add(createPendingStep(seedInstance, TASK_NAME_REBALANCE_CLUSTER));
        return new DefaultPhase(PHASE_REBALANCE, steps);
    }

    private Phase getSampleBucketPhase() throws Exception {
        List<Step> steps = new ArrayList<>();

        PodSpec dataPodSpec = getPodSpecByName(serviceSpec, POD_NAME_DATA);
        PodInstance seedInstance = new DefaultPodInstance(dataPodSpec, 0);

        if (System.getenv(ENV_DEMO_DATA_INSTALL_BEER_SAMPLE) != null) {
            steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_ADD_BEER_SAMPLE_BUCKET)));
        }
        if (System.getenv(ENV_DEMO_DATA_INSTALL_TRAVEL_SAMPLE) != null) {
            steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_ADD_TRAVEL_SAMPLE_BUCKET)));
        }
        if (System.getenv(ENV_DEMO_DATA_INSTALL_GAMESIM_SAMPLE) != null) {
            steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_ADD_GAMESIM_SAMPLE_BUCKET)));
        }

        return new DefaultPhase(PHASE_LOAD_SAMPLE_DATA, steps);
    }

    public StateStore getStateStore() {
        return this.stateStore;
    }

    public ConfigStore<ServiceSpec> getConfigStore() {
        return this.configStore;
    }

    private Optional<String> getCurrentVersion(String taskName) throws Exception {
        Optional<Protos.TaskInfo> taskInfo = stateStore.fetchTask(taskName);
        if (taskInfo.isPresent()) {
            return Optional.of(
                    configStore.fetch(
                            CommonTaskUtils.getTargetConfiguration(taskInfo.get()))
                            .getVersion());
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> getCurrentVersion(PodInstance podInstance, String taskType) throws Exception {
        String taskName = TaskSpec.getInstanceName(podInstance, taskType);
        return getCurrentVersion(taskName);
    }

    private List<Step> seedNodeSteps(PodInstance seedInstance) throws Exception {
        List<Step> steps = new ArrayList<>();

        steps.addAll(serverSteps(seedInstance, TASK_NAME_ADD_SEED_DATA_NODE, false));

        if (!getCurrentVersion(seedInstance, TASK_NAME_SERVER).isPresent()) {
            steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_INITIALIZE_CLUSTER)));
            steps.add(stepFactory.getStep(seedInstance, Collections.singleton(TASK_NAME_RENAME_SEED_HOST)));
        }

        return steps;
    }

    private List<Step> serverSteps(
            PodInstance podInstance,
            String addNodeTask,
            boolean alwaysAddNode) throws Exception {
        List<Step> steps = new ArrayList<>();

        Step serverStep = createPendingStep(podInstance, TASK_NAME_SERVER);

        boolean needsUpdate = needsUpdate(podInstance);
        if (needsUpdate) {
            steps.add(createPendingStep(podInstance, TASK_NAME_REFRESH_NODE));
        }
        steps.add(serverStep);

        Step addNodeStep = stepFactory.getStep(podInstance, Collections.singleton(addNodeTask));
        if (needsUpdate) {
            ((DeploymentStep) addNodeStep).setStatus(Status.PENDING);
        }
        if (needsUpdate || alwaysAddNode) {
            steps.add(addNodeStep);
        }

        return steps;
    }

    private boolean needsUpdate(PodInstance podInstance) throws Exception {
        Optional<String> oldVersion = getCurrentVersion(podInstance, TASK_NAME_SERVER);
        return oldVersion.isPresent() && !oldVersion.get().equals(serviceSpec.getVersion());
    }

    private Step createPendingStep(PodInstance podInstance, String taskType) throws Exception {
        Step step = stepFactory.getStep(podInstance, Collections.singleton(taskType));
        ((DeploymentStep) step).setStatus(Status.PENDING);
        return step;
    }

    private static PodSpec getPodSpecByName(ServiceSpec serviceSpec, String name) {
        return serviceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals(name))
                .findAny()
                .get();
    }
}
