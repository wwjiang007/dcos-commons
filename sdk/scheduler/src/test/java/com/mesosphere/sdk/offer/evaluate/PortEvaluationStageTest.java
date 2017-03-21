package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PortEvaluationStageTest {
    @ClassRule
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    @Test
    public void testReserveDynamicPort() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "dyn-port-name",
                0,
                Optional.of("test-port"),
                DiscoveryInfoWriter.createPortWriter("dyn-port-name"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "TEST_PORT");
        Assert.assertEquals(variable.getValue(), "10000");
    }

    @Test
    public void testReserveKnownPort() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 10000, 10000);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "known-port-name",
                10000,
                Optional.of("test-port"),
                DiscoveryInfoWriter.createPortWriter("known-port-name"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "TEST_PORT");
        Assert.assertEquals(variable.getValue(), "10000");
    }

    @Test
    public void testReserveKnownPortFails() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 1111, 1111);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "known-port-name",
                10001,
                Optional.of("test-port"),
                DiscoveryInfoWriter.createPortWriter("known-port-name"));
        EvaluationOutcome outcome =
                portEvaluationStage.evaluate(new MesosResourcePool(offer), new PodInfoBuilder(offerRequirement));
        Assert.assertFalse(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
    }

    @Test
    public void testPortEnvCharConversion() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(5000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "dyn-port-name",
                0,
                Optional.of("port?test.port"),
                DiscoveryInfoWriter.createPortWriter("dyn-port-name"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                5000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "PORT_TEST_PORT");
        Assert.assertEquals(variable.getValue(), "5000");
    }

    @Test
    public void testGetClaimedDynamicPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedPorts = ResourceTestUtils.getExpectedRanges("ports", 0, 0, resourceId);
        Protos.Resource offeredPorts = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(expectedPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Protos.TaskInfo.Builder builder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        builder.getCommandBuilder().getEnvironmentBuilder().addVariablesBuilder()
                .setName("PORT_TEST_PORT")
                .setValue("10000");

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                expectedPorts,
                TestConstants.TASK_NAME,
                "dyn-port-name",
                0,
                Optional.of("port-test-port"),
                DiscoveryInfoWriter.createPortWriter("dyn-port-name"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Assert.assertEquals(0, mesosResourcePool.getReservedPool().size());
    }

    @Test
    public void testPortOnHealthCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-healthcheck.yml");
        StateStore stateStore = Mockito.mock(StateStore.class);
        DefaultOfferRequirementProvider provider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID());
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(podInstance, TaskUtils.getTaskNames(podInstance)));
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 10000, 10000);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        String taskName = "pod-type-0-" + TestConstants.TASK_NAME;
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                taskName,
                "hc-port-name",
                10000,
                Optional.of("test-port"),
                DiscoveryInfoWriter.createPortWriter("hc-port-name"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
        boolean portInTaskEnv = false;
        for (int i = 0; i < taskBuilder.getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInTaskEnv = true;
            }
        }
        Assert.assertTrue(portInTaskEnv);
        boolean portInHealthEnv = false;
        for (int i = 0; i < taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }

    @Test
    public void testPortOnReadinessCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-readinesscheck.yml");
        StateStore stateStore = Mockito.mock(StateStore.class);
        DefaultOfferRequirementProvider provider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID());
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(podInstance, TaskUtils.getTaskNames(podInstance)));
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 10000, 10000);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        String taskName = "pod-type-0-" + TestConstants.TASK_NAME;
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                taskName,
                "rc-port-name",
                10000,
                Optional.of("test-port"),
                DiscoveryInfoWriter.createPortWriter("rc-port-name"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
        boolean portInTaskEnv = false;
        for (int i = 0; i < taskBuilder.getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInTaskEnv = true;
            }
        }
        Assert.assertTrue(portInTaskEnv);
        boolean portInHealthEnv = false;
        Optional<Protos.HealthCheck> readinessCheck = CommonTaskUtils.getReadinessCheck(taskBuilder.build());
        for (int i = 0; i < readinessCheck.get().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = readinessCheck.get().getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }

    @Test
    public void testVIPDiscoveryInfoPopulated() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "test-port",
                10000,
                Optional.empty(),
                DiscoveryInfoWriter.createVIPWriter("sctp", DiscoveryInfo.Visibility.CLUSTER, "test-vip", 80));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());

        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), 10000);
        Assert.assertEquals(port.getProtocol(), "sctp");

        Protos.Label vipLabel = port.getLabels().getLabels(0);
        Assert.assertEquals(discoveryInfo.getName(), TestConstants.TASK_NAME);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");
    }

    @Test
    public void testVIPIsReused() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedPorts = ResourceUtils.setLabel(
                ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId),
                TestConstants.HAS_VIP_LABEL,
                "test-vip:80");
        Protos.Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(expectedPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                expectedPorts,
                TestConstants.TASK_NAME,
                "test-port",
                10000,
                Optional.empty(),
                DiscoveryInfoWriter.createVIPWriter("sctp", DiscoveryInfo.Visibility.CLUSTER, "test-vip", 80));

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());

        String portVIPLabel = discoveryInfo.getPorts().getPorts(0).getLabels().getLabels(0).getKey();
        String taskVIPLabel = offerRequirement.getTaskRequirements().iterator().next()
                .getTaskInfo().getDiscovery().getPorts().getPorts(0).getLabels().getLabels(0).getKey();
        Assert.assertEquals(portVIPLabel, taskVIPLabel);
    }

    @Test
    public void testVIPPortNumberIsUpdated() throws InvalidRequirementException {
        Protos.Resource desiredPorts = ResourceUtils.setLabel(
                ResourceTestUtils.getDesiredRanges("ports", 10000, 10000),
                TestConstants.HAS_VIP_LABEL,
                "test-vip:80");
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedPorts(8000, 8000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        // Update the resource to have a different port, so that the TaskInfo's DiscoveryInfo mirrors the case where
        // a new port has been requested but we want to reuse the old VIP definition.
        Protos.Resource.Builder resourceBuilder = desiredPorts.toBuilder();
        resourceBuilder.clearRanges().getRangesBuilder().addRangeBuilder().setBegin(8000).setEnd(8000);
        desiredPorts = resourceBuilder.build();

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "test-port",
                8000,
                Optional.empty(),
                DiscoveryInfoWriter.createVIPWriter("sctp", DiscoveryInfo.Visibility.CLUSTER, "test-vip", 80));

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());
        Assert.assertEquals(8000, discoveryInfo.getPorts().getPorts(0).getNumber());
    }

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec = ServiceSpecTestUtils.getPodInstance(serviceSpecFileName);

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule((offer, offerRequirement, taskInfos) -> EvaluationOutcome.pass(this, "pass for test"))
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }
}
