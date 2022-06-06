package server.model.agents;

import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * This is essentially a hub-specific programmer. Use this to setup the mission and rewards etc.
 * We are using it hard-coded as a coverage task for now. In future we should maybe generify a bit more
 */
public class MissionProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    private AgentHubProgrammed hub;
    private ProgrammerHandler programmerHandler;
    private List<AgentProgrammed> agents;

    //TODO these values can be passed through the AgentHubProgrammed and therefore can be scenario file defined
    private Coordinate botLeft = new Coordinate(50.918934561834035, -1.415377448133106);
    private Coordinate topRight = new Coordinate(50.937665618776656, -1.3991319762570154);
    private int xSteps = 100;
    private int ySteps = 100;
    private boolean assigned;
    private int runCounter = 0;
    private FeedForwardNetwork qNetwork;

    public MissionProgrammer(AgentHubProgrammed ahp, ProgrammerHandler progHandler) {
        hub = ahp;
        programmerHandler = progHandler;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap) {agents.add(ap);}});

    }

    public void step() {
        if (Simulator.instance.getState().isInProgress() && !assigned) {
            //randomAssignAll();
            qLearningSetup();
        } else {
            qLearningStep();
        }
    }

    private void qLearningSetup() {
        // We use:
        //      a state code (1-100 representing flattened grid 10x10)
        //      an action code (0-5 representing N-S-E-W-STOP)
        //

        int[] layerWidths = new int[8];

        for (int i=0; i<8; i++) {
            layerWidths[i] = 8;
        }

        qNetwork = FeedForwardNetwork.builder()
                .addInputLayer(8)
                .addFullyConnectedLayers(layerWidths)
                .addOutputLayer(40, ActivationType.LINEAR)
                .lossFunction(LossType.CROSS_ENTROPY)
                .build();

        System.out.println(qNetwork);

        assigned = true;
    }

    private void qLearningStep() {
        // Feed state variables into qNet
        // Run qNet and record results
        // Take the best of every batch of 5 as joint action
        // Put into environment. Record reward
        // SOMEHOW?? train using reward and previous action


    }

    public void complete() {
        System.out.println("Run " + runCounter++ + " completed, reward = " + calculateReward());

        assigned = false;

        // We must reset the network IDs of the agents too, as this is the only non-handler variable that is used (it
        //  signifies that it hasn't been setup yet
        hub.setNetworkID("");
        Simulator.instance.getState().getAgents().forEach(a -> {if (a instanceof AgentProgrammed ap) {ap.setNetworkID("");}});
    }

    public void randomAssignAll() {
        for (Agent a : agents) {
            if (!programmerHandler.agentHasTask(a.getId())) {
                int x = (int) Math.floor(programmerHandler.getNextRandomDouble() * xSteps);
                int y = (int) Math.floor(programmerHandler.getNextRandomDouble() * ySteps);

                Coordinate c = calculateEquivalentCoordinate(x, y);
                programmerHandler.issueOrder(a.getId(), c);
            }
        }

        // Stop trying to assign if every agent has a route
        synchronized (Simulator.instance.getState().getAgents()) {
            if (Simulator.instance.getState().getAgents().stream().anyMatch(a -> !(a instanceof Hub) && a.getRoute().isEmpty())) {
                assigned = true;
            }
        }
    }

    /**
     * Reward function. Uses actual sim-side data to make this more efficient and generally easier to program
     */
    private int calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        double detectionRange = programmerHandler.getSenseRange();
        int numPointsCovered = 0;

        synchronized (Simulator.instance.getState().getAgents()) {
            for (int i = 0; i < xSteps; i++) {
                for (int j = 0; j < ySteps; j++) {
                    //System.out.println("for (" + i + ", " + j + ")");
                    Coordinate equiv = calculateEquivalentCoordinate(i, j);
                    for (Agent a : Simulator.instance.getState().getAgents()) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < detectionRange) {
                            // This square's centre is in range of an agent
                            numPointsCovered++;
                            break;
                        }
                    }
                }
            }
        }

        return numPointsCovered;

    }

    private Coordinate calculateEquivalentCoordinate(int x, int y) {
        double xSquareSpan = (topRight.getLongitude() - botLeft.getLongitude()) / xSteps;
        double ySquareSpan = (topRight.getLatitude() - botLeft.getLatitude()) / ySteps;

        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan), botLeft.getLongitude() + (x * xSquareSpan));
    }

    /**
     * Lifted whole from https://stackoverflow.com/questions/714108/cartesian-product-of-an-arbitrary-number-of-sets
     * @return
     */
    public static List<List<Object>> cartesianProduct(List<?>... sets) {
        if (sets.length < 2)
            throw new IllegalArgumentException(
                    "Can't have a product of fewer than two sets (got " +
                            sets.length + ")");

        return _cartesianProduct(0, sets);
    }

    private static List<List<Object>> _cartesianProduct(int index, List<?>... sets) {
        List<List<Object>> ret = new ArrayList<List<Object>>();
        if (index == sets.length) {
            ret.add(new ArrayList<Object>());
        } else {
            for (Object obj : sets[index]) {
                for (List<Object> set : _cartesianProduct(index+1, sets)) {
                    set.add(obj);
                    ret.add(set);
                }
            }
        }
        return ret;
    }


}
