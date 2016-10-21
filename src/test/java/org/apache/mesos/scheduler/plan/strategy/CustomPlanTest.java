package org.apache.mesos.scheduler.plan.strategy;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.graphstream.algorithm.Toolkit.diameter;
import static org.mockito.Mockito.when;

/**
 * These tests do not validate plan behavior.  See the PhaseBuilder, PlanBuilder, and Strategy tests.
 * These test serve as validation of ease of custom plan construction, similar to the CustomTaskSetTest.
 */
public class CustomPlanTest {
    @Mock Block parallelBlock0;
    @Mock Block parallelBlock1;
    @Mock Block parallelBlock2;
    @Mock Block parallelBlock3;

    @Mock Block diamondBlock0;
    @Mock Block diamondBlock1;
    @Mock Block diamondBlock2;
    @Mock Block diamondBlock3;

    @Mock Block serialBlock0;
    @Mock Block serialBlock1;
    @Mock Block serialBlock2;
    @Mock Block serialBlock3;

    private static final String SERVICE_NAME = "test-service";
    private static final String ROOT_ZK_PATH = "/test-root-path";

    private static final int TASK_A_COUNT = 4;
    private static final String TASK_A_NAME = "A";

    private static final int TASK_B_COUNT = 4;
    private static final String TASK_B_NAME = "B";

    private static final int TASK_C_COUNT = 4;
    private static final String TASK_C_NAME = "C";

    private Collection<Block> blocks;
    private ServiceSpecification serviceSpecification;
    private static TestingServer testingServer;
    private static StateStore stateStore;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
        testingServer.start();
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testingServer.getConnectString());
    }

    private void initializeBlock(Block block, String name) {
        when(block.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block.getName()).thenReturn(name);
        when(block.getStatus()).thenReturn(Status.PENDING);
        when(block.isPending()).thenReturn(true);
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        initializeBlock(parallelBlock0, "parallelBlock0");
        initializeBlock(parallelBlock1, "parallelBlock1");
        initializeBlock(parallelBlock2, "parallelBlock2");
        initializeBlock(parallelBlock3, "parallelBlock3");

        initializeBlock(diamondBlock0, "diamondBlock0");
        initializeBlock(diamondBlock1, "diamondBlock1");
        initializeBlock(diamondBlock2, "diamondBlock2");
        initializeBlock(diamondBlock3, "diamondBlock3");

        initializeBlock(serialBlock0, "serialBlock0");
        initializeBlock(serialBlock1, "serialBlock1");
        initializeBlock(serialBlock2, "serialBlock2");
        initializeBlock(serialBlock3, "serialBlock3");

        blocks = Arrays.asList(parallelBlock0, parallelBlock1, parallelBlock2, parallelBlock3);

        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_A_NAME,
                                TASK_A_COUNT),
                        TestTaskSetFactory.getTaskSet(
                                TASK_B_NAME,
                                TASK_B_COUNT),
                        TestTaskSetFactory.getTaskSet(
                                TASK_C_NAME,
                                TASK_C_COUNT));
            }
        };
    }

    @Test
    public void testCustomPlanFromPhasesDoesntThrow() throws DependencyStrategyHelper.InvalidDependencyException {
        Phase parallelPhase = getParallelPhase();
        Phase serialPhase = getSerialPhase();
        Phase diamondPhase = getDiamondPhase();

        String planName = "Plan Root";
        DefaultPlanBuilder planBuilder = new DefaultPlanBuilder(planName);
        planBuilder.addDependency(serialPhase, diamondPhase);
        planBuilder.addDependency(diamondPhase, parallelPhase);

        Plan plan = planBuilder.build();

        Graph graph = new SingleGraph("Plan", false, true);
        graph = addElement(graph, parallelPhase);
        graph = addElement(graph, serialPhase);
        graph = addElement(graph, diamondPhase);
        graph = addElement(graph, plan);

        graph.getNodeIterator().forEachRemaining(node -> node.addAttribute("ui.label", node.getId()));
        graph.getNode(planName).addAttribute("ui.style", "fill-color: rgb(0,100,255); size: 30;");
        graph.getNode("parallel").addAttribute("ui.style", "fill-color: rgb(0,216,0); size: 20;");
        graph.getNode("diamond").addAttribute("ui.style", "fill-color: rgb(0,216,0); size: 20;");
        graph.getNode("serial").addAttribute("ui.style", "fill-color: rgb(0,216,0); size: 20;");

        drawGraph(graph);
    }

    private Graph addElement(Graph graph, Element element) {
        DependencyStrategy dependencyStrategy = (DependencyStrategy) element.getStrategy();
        String name = element.getName();

        Map<? extends Element, ? extends Set<? extends Element>> dependencies = dependencyStrategy.getDependencies();

        String rootName = name;
        graph.addNode(rootName);
        dependencies.entrySet().stream()
                .filter(entry -> entry.getValue().size() == 0)
                .forEach(entry -> graph.addEdge(entry.getKey() + rootName, entry.getKey().getName(), rootName, true));
        dependencies.entrySet()
                .forEach(entry -> entry.getValue()
                        .forEach(dep -> graph.addEdge(
                                entry.getKey().getName() + dep.getName(),
                                entry.getKey().getName(),
                                dep.getName(),
                                true)));

        diameter(graph);

        return graph;

    }

    private void drawGraph(Graph graph) {
        graph.addAttribute("ui.quality");
        graph.addAttribute("ui.antialias");
        graph.display();

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCustomPlanFromServiceSpecDoesntThrow() throws Block.InvalidException {
        DefaultBlockFactory blockFactory = new DefaultBlockFactory(stateStore);
        DefaultPhaseFactory phaseFactory = new DefaultPhaseFactory(blockFactory);
        Iterator<TaskSet> taskSetIterator = serviceSpecification.getTaskSets().iterator();

        Phase parallelPhase = phaseFactory.getPhase(
                taskSetIterator.next(),
                new ParallelStrategy());

        Phase serialPhase = phaseFactory.getPhase(
                taskSetIterator.next(),
                new SerialStrategy());

        TaskSet taskSet = taskSetIterator.next();
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        List<Block> blocks = blockFactory.getBlocks(taskSet.getTaskSpecifications());

        phaseBuilder.addDependency(blocks.get(3), blocks.get(1));
        phaseBuilder.addDependency(blocks.get(3), blocks.get(2));
        phaseBuilder.addDependency(blocks.get(1), blocks.get(0));
        phaseBuilder.addDependency(blocks.get(2), blocks.get(0));
        Phase diamondPhase = phaseBuilder.build();

        new DefaultPlan(
                "plan",
                Arrays.asList(parallelPhase, serialPhase, diamondPhase),
                new SerialStrategy<>());
    }

    private Phase getParallelPhase() throws DependencyStrategyHelper.InvalidDependencyException {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("parallel");
        phaseBuilder.addAll(blocks);
        return phaseBuilder.build();
    }

    private Phase getDiamondPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        phaseBuilder.addDependency(diamondBlock3, diamondBlock1);
        phaseBuilder.addDependency(diamondBlock3, diamondBlock2);
        phaseBuilder.addDependency(diamondBlock1, diamondBlock0);
        phaseBuilder.addDependency(diamondBlock2, diamondBlock0);

        return phaseBuilder.build();
    }

    private Phase getSerialPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("serial");
        phaseBuilder.addDependency(serialBlock3, serialBlock2);
        phaseBuilder.addDependency(serialBlock2, serialBlock1);
        phaseBuilder.addDependency(serialBlock1, serialBlock0);

        return phaseBuilder.build();
    }
}
