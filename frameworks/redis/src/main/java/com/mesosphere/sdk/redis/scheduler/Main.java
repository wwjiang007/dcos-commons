package com.mesosphere.sdk.redis.scheduler;

import com.google.common.base.Joiner;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Template service.
 */
public class Main {

    // Values from scheduler environment (provided by marathon.json.mustache):
    private static final int ENV_REDIS_COUNT = Integer.parseInt(System.getenv("REDIS_COUNT"));

    // Constants relating to the proxylite pod:
    private static final String PROXYLITE_POD_TYPE = "proxylite";

    // Envvars to be set for proxylite:
    private static final String PROXYLITE_ROOT_REDIRECT_ENV = "ROOT_REDIRECT";
    private static final String PROXYLITE_EXTERNAL_ROUTES_ENV = "EXTERNAL_ROUTES";
    private static final String PROXYLITE_INTERNAL_ROUTES_ENV = "INTERNAL_ROUTES";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Exception("Missing file argument");
        }

        RawServiceSpec yamlSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(new File(args[0]));
        DefaultServiceSpec originalServiceSpec = YAMLServiceSpecFactory.generateServiceSpec(yamlSpec);

        // Generate 'proxylite' pod in Java, instead of in the YAML.
        // We do this here in order to customize the proxylite configuration based on the number of Redis tasks.
        List<PodSpec> newPods = new ArrayList<>();
        for (PodSpec podSpec : originalServiceSpec.getPods()) {
            if (podSpec.getType().equals(PROXYLITE_POD_TYPE)) {
                newPods.add(updateProxylitePod(
                        originalServiceSpec.getName(),
                        originalServiceSpec.getApiPort(),
                        ENV_REDIS_COUNT,
                        podSpec));
            } else {
                newPods.add(podSpec);
            }
        }

        new DefaultService(DefaultScheduler.newBuilder(
                DefaultServiceSpec.newBuilder(originalServiceSpec)
                        .pods(newPods)
                        .build())
                .setPlansFrom(yamlSpec));
    }

    private static PodSpec updateProxylitePod(String serviceName, int apiPort, int redisCount, PodSpec pod)
            throws Exception {
        if (pod.getTasks().size() != 1) {
            throw new Exception(String.format(
                    "Expected 1 task in pod '%s', got %d", pod.getType(), pod.getTasks().size()));
        }
        return DefaultPodSpec.newBuilder(pod)
                .tasks(Arrays.asList(updateProxyliteTask(serviceName, apiPort, redisCount, pod.getTasks().get(0))))
                .build();
    }

    private static TaskSpec updateProxyliteTask(String serviceName, int apiPort, int redisCount, TaskSpec task) {
        List<String> cmdResolveHosts = new ArrayList<>();
        List<String> externalRoutes = new ArrayList<>();
        List<String> internalRoutes = new ArrayList<>();
        // support service mgmt endpoint at /v1 as usual
        externalRoutes.add("/v1");
        internalRoutes.add(String.format("http://%s.marathon.mesos:%d/v1", serviceName, apiPort));
        // For each Redis node...
        for (int i = 0; i < redisCount; ++i) {
            // - have proxy wait for the hostname to be resolvable before starting
            cmdResolveHosts.add(String.format("redis-%d-server.%s.mesos", i, serviceName));
            // - route "/ui<n>" to Redis UI at 8443
            externalRoutes.add(String.format("/ui%d", i));
            internalRoutes.add(String.format("https://redis-%d-server.%s.mesos:8443", i, serviceName));
            // - route "/api<n>" to Redis API server at 9443
            externalRoutes.add(String.format("/api%d", i));
            internalRoutes.add(String.format("https://redis-%d-server.%s.mesos:9443", i, serviceName));
        }
        Map<String, String> environmentMap = new HashMap<>();
        environmentMap.put(PROXYLITE_ROOT_REDIRECT_ENV, "/ui0/");
        environmentMap.put(PROXYLITE_EXTERNAL_ROUTES_ENV, Joiner.on(',').join(externalRoutes));
        environmentMap.put(PROXYLITE_INTERNAL_ROUTES_ENV, Joiner.on(',').join(internalRoutes));
        return DefaultTaskSpec.newBuilder(task)
                .commandSpec(DefaultCommandSpec.newBuilder(PROXYLITE_POD_TYPE)
                        .value(String.format(
                                "./bootstrap -resolve-hosts=%s && /proxylite/run.sh",
                                Joiner.on(',').join(cmdResolveHosts)))
                        .environment(environmentMap)
                        .build())
                .build();
    }
}
