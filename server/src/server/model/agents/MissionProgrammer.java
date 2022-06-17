package server.model.agents;

import deepnetts.data.MLDataItem;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.AbstractLayer;
import deepnetts.net.layers.ConvolutionalLayer;
import deepnetts.net.layers.MaxPoolingLayer;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;
import server.Simulator;
import server.model.Coordinate;

import javax.visrec.ml.data.BasicDataSet;
import javax.visrec.ml.data.DataSet;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * This is essentially a hub-specific programmer. Use this to setup the mission and rewards etc.
 * We are using it hard-coded as a coverage task for now. In future we should maybe generify a bit more
 */
public class MissionProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());

    private final float GAMMA = 0.9f;
    private final int SAMPLE_SIZE = 10;
    private final float LEARNING_RATE = 0.001f;
    private final int BUFFER_SIZE = 40;
    private final int NUM_STEPS_PER_EPOCH = 500;

    private AgentHubProgrammed hub;
    private ProgrammerHandler programmerHandler;
    private List<AgentProgrammed> agents;

    //TODO these values can be passed through the AgentHubProgrammed and therefore can be scenario file defined
    private Coordinate botLeft = new Coordinate(50.918934561834035, -1.415377448133106);
    private Coordinate topRight = new Coordinate(50.937665618776656, -1.3991319762570154);
    private int xSteps = 100;
    private int ySteps = 100;
    private int runCounter = 0;
    private boolean bufferFull = false;
    private int pointer = 0;
    private int counter;
    private int stepCounter = 0;
    private ArrayList<Double> scores = new ArrayList<>();
    private ArrayList<Long> times = new ArrayList<>();

    private double xSpan = topRight.getLongitude() - botLeft.getLongitude();
    private double ySpan = topRight.getLatitude() - botLeft.getLatitude();
    private double xSquareSpan = xSpan / xSteps;
    private double ySquareSpan = ySpan / ySteps;
    private int stateSize;
    private boolean ready = false;

    private long epochStartTime;
    private boolean set = false;

    public MissionProgrammer(AgentHubProgrammed ahp) {
        hub = ahp;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {agents.add(ap);}});
    }

    public void step() {
        if (!ready) {
            groupSetup();
        } else {
            if (stepCounter < NUM_STEPS_PER_EPOCH) {
                groupStep();
                if (stepCounter % (NUM_STEPS_PER_EPOCH / 10) == 0) {
                    System.out.print((stepCounter / (NUM_STEPS_PER_EPOCH / 100)) + ">");
                }
                stepCounter++;
            } else {
                // SOFT RESET
                double r = calculateReward();
                scores.add(r);
                synchronized (this) {
                    long epochDuration = System.currentTimeMillis() - epochStartTime;
                    epochStartTime = System.currentTimeMillis();
                    times.add(epochDuration);
                    double sum = 0;
                    for (int i = Math.max(0, scores.size() - 50); i < scores.size(); i++) {
                        sum += scores.get(i);
                    }
                    double mvAv = sum / Math.min(scores.size(), 50);

                    DecimalFormat f = new DecimalFormat("##.00");
                    System.out.println(
                        "run = " + runCounter
                        + ", steps = " + Simulator.instance.getStepCount()
                        + ", reward = " + r
                        + ", total average = " + f.format(scores.stream().mapToDouble(Double::doubleValue).average().getAsDouble())
                        + ", moving average = " + f.format(mvAv)
                        + ", epoch time = " + (epochDuration) + "ms"
                    );

                    File csvOutputFile = new File("results.csv");
                    try {
                        FileWriter fw = new FileWriter(csvOutputFile, true);
                        fw.write(runCounter
                                + ", " + r
                                + ", " + f.format(scores.stream().mapToDouble(Double::doubleValue).average().getAsDouble())
                                + ", " + f.format(mvAv)
                                + ", " + epochDuration
                                + " \n");
                        fw.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    Simulator.instance.softReset(this);  // This soft resets all agents
                    agents.clear();
                    Simulator.instance.getState().getAgents().forEach(a -> {
                        if (a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {
                            agents.add(ap);
                            if (ap.programmerHandler.getAgentProgrammer().getLevel() == 1) {
                                ap.programmerHandler.getAgentProgrammer().getLearningAllocator().reset();
                            }
                        }
                    });
                    runCounter++;
                    stepCounter = 0;
                    Simulator.instance.startSimulation();
                }
            }
        }
    }

    private void groupSetup() {
        // TODO Clumsy 1-level for now, in future bin these out to groups of 5 and allocate in groups
        int level = 1;
        AgentProgrammed leader = null;
        for (AgentProgrammed ap : agents) {
            if (level == 1) {
                ap.programmerHandler.getAgentProgrammer().setLevel(1);
                ap.programmerHandler.getAgentProgrammer().setup();
                ap.programmerHandler.getAgentProgrammer().getLearningAllocator().setBounds(
                        new Coordinate(50.918934561834035, -1.415377448133106),
                        new Coordinate(50.937665618776656, -1.3991319762570154));
                leader = ap;
                level = 0;
            } else {
                ap.programmerHandler.getAgentProgrammer().setLevel(0);
            }
        }

        for (AgentProgrammed ap : agents) {
            if (ap.getProgrammerHandler().getAgentProgrammer().getLevel() == 0) {
                leader.getProgrammerHandler().getAgentProgrammer().addSubordinate(ap);
            }
        }
        ready = true;
    }

    private void groupStep() {
        for (AgentProgrammed ap : agents) {
            ap.programmerHandler.getAgentProgrammer().step();
        }
    }

    /**
     * Reward function. Uses actual sim-side data to make this more efficient and generally easier to program
     */
    public float calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(i, j);
                for (Agent a : agents) {
                    //if (!(a instanceof Hub)) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < 250) {
                            // This square's centre is in range of an agent
                            numPointsCovered++;
                            break;
                        }
                    //}
                }
            }
        }
        return numPointsCovered;

    }

    private Coordinate calculateEquivalentCoordinate(int x, int y) {
        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan), botLeft.getLongitude() + (x * xSquareSpan));
    }

    public void complete() {
        System.out.println("COMPLETE");
    }

    public static class ExperienceRecord {
        // Buffer: <state, action, reward, state'>
        float[] originState;
        float[] actionValues;
        int actionTaken;
        float jointReward;
        float[] resultantState;

        public ExperienceRecord(float[] originState, float[] actionValues, int actionTaken, float jointReward, float[] resultantState) {
            this.originState = originState;
            this.actionValues = actionValues;
            this.actionTaken = actionTaken;
            this.jointReward = jointReward;
            this.resultantState = resultantState;
        }

        @Override
        public String toString() {
            return "ExperienceRecord{" +
                    "originState=" + Arrays.toString(originState) +
                    ", actionValues=" + Arrays.toString(actionValues) +
                    ", actionTaken=" + actionTaken +
                    ", jointReward=" + jointReward +
                    ", resultantState=" + Arrays.toString(resultantState) +
                    '}';
        }
    }

}
