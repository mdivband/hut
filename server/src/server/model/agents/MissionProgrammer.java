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
    private final float MAX_REWARD;
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
    private boolean assigned;
    private int runCounter = 0;
    private FeedForwardNetwork qNetwork;
    private FeedForwardNetwork targetNetwork;
    private ExperienceRecord[] buffer = new ExperienceRecord[BUFFER_SIZE];
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

    private long epochStartTime;

    public MissionProgrammer(AgentHubProgrammed ahp, ProgrammerHandler progHandler) {
        hub = ahp;
        programmerHandler = progHandler;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {agents.add(ap);}});
        MAX_REWARD = xSteps * ySteps;
        stateSize = 2*agents.size();
    }

    public void step() {
        if (!assigned) {
            //randomAssignAll();
            qLearningSetup();
        } else {
            if (stepCounter < NUM_STEPS_PER_EPOCH) {
                qLearningStep();
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
                                + ", " + epochDuration + " \n");
                        fw.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    Simulator.instance.softReset(this);
                    agents = new ArrayList<>();
                    Simulator.instance.getState().getAgents().forEach(a -> {
                        if (a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {
                            agents.add(ap);
                        }
                    });
                    runCounter++;
                    stepCounter = 0;
                    pointer = 0;
                    bufferFull = false;
                    buffer = new ExperienceRecord[BUFFER_SIZE];
                    Simulator.instance.startSimulation();
                }
            }
        }
    }

    private FeedForwardNetwork createNetwork() {
        return FeedForwardNetwork.builder()
                .addInputLayer(8)
                .addLayer(new ConvolutionalLayer(2, 1, 1))
                //.addFullyConnectedLayer(128, ActivationType.LINEAR)
                //.addFullyConnectedLayer(128, ActivationType.LINEAR)
                .addFullyConnectedLayer(64, ActivationType.LINEAR)
                .addFullyConnectedLayer(64, ActivationType.LINEAR)
                .addFullyConnectedLayer(128, ActivationType.LINEAR)
                .addFullyConnectedLayer(128, ActivationType.LINEAR)
                .addOutputLayer(5, ActivationType.LINEAR)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(6400)
                .build();
    }

    private void qLearningSetup() {
        qNetwork = createNetwork();

        // SOMETHING IN MAX EPOCHS CASUSES PROBLEM
        qNetwork.getTrainer()
                .setBatchMode(false)
                .setLearningRate(LEARNING_RATE)
                .setOptimizer(OptimizerType.SGD)
                .setMaxEpochs(SAMPLE_SIZE);

        targetNetwork = createNetwork();

        targetNetwork.getTrainer()
                .setBatchMode(false)
                .setLearningRate(LEARNING_RATE)
                .setOptimizer(OptimizerType.SGD)
                .setMaxEpochs(SAMPLE_SIZE);;

        assigned = true;
        counter = 0;
        epochStartTime = System.currentTimeMillis();
    }

    private void copyNets() {
        targetNetwork = createNetwork();

        for (AbstractLayer l : targetNetwork.getLayers()) {
            l.setWeights(qNetwork.getLayers().get(targetNetwork.getLayers().indexOf(l)).getWeights());
            l.setBiases(qNetwork.getLayers().get(targetNetwork.getLayers().indexOf(l)).getBiases());
            l.setOptimizerType(OptimizerType.SGD);
            l.setActivationType(ActivationType.LINEAR);
            l.setLearningRate(LEARNING_RATE);
        }
    }

    private void qLearningStep() {
        for (Agent a : agents) {
            if (a instanceof AgentProgrammed ap) {
                float[] input = getStateForThisAgent(a);
                for (int i=0; i<input.length - 1;i+=2) {
                    input[i] /= xSteps;
                    input[i + 1] /= ySteps;
                }
                float[] output = compute(input);
                int move;
                float reward;


                if (Simulator.instance.getRandom().nextDouble() < 0.9) {
                    float maxVal = -1;
                    move = -1;
                    for (int i=0; i<5; i++) {
                        if (output[i] > maxVal) {
                            maxVal = output[i];
                            move = i;
                        }
                    }
                } else {
                    // EPSILON EXPLORATION
                    move = Simulator.instance.getRandom().nextInt(5);
                }

                float rewardBefore = calculateReward() / MAX_REWARD;
                if (ap.programmerHandler.gridMove(move)) {
                    reward = (calculateReward() / MAX_REWARD) - rewardBefore;

                } else {
                    reward = -1f;
                }

                float[] result = getStateForThisAgent(a);

                for (int i=0; i<result.length - 1;i+=2) {
                    result[i] /= xSteps;
                    result[i + 1] /= ySteps;
                }

                train(new ExperienceRecord(input, output, move, reward, result));

            }
        }
        counter++;
        if (counter >= 100) {
            copyNets();
            /*
            System.out.println("============RESET================");
            System.out.println("pred: " + Arrays.toString(qNetwork.predict(new float[]{.39f, .56f, .34f, .59f, .36f, .60f, .37f, .60f})));
            System.out.println("targ: " + Arrays.toString(targetNetwork.predict(new float[]{.39f, .56f, .34f, .59f, .36f, .60f, .37f, .60f})));
            System.out.println();
             */
            counter = 0;
            //buffer = new ExperienceRecord[BUFFER_SIZE];
            //pointer = 0;
        }
    }

    private void train(ExperienceRecord e) {
        // TODO we have to manually check due to this boolean unusually returning true whilst false (probably threading
        //  or branch prediction caused?)
        synchronized (this) {
            if (bufferFull && Arrays.stream(buffer).noneMatch(Objects::isNull)) {
                qTrain(sample());
            }
        }
        buffer[pointer] = e;
        pointer++;
        if (pointer >= BUFFER_SIZE) {
            pointer = 0;
            bufferFull = true;
        }
    }

    private List<ExperienceRecord> sample() {
        List<ExperienceRecord> sample = new ArrayList<>();
        while (sample.size() < SAMPLE_SIZE) {
            ExperienceRecord e = buffer[Simulator.instance.getRandom().nextInt(BUFFER_SIZE)];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        return sample;
    }

    private void qTrain(List<ExperienceRecord> sample) {
        List<MLDataItem> dataItems = new ArrayList<>();
        for (ExperienceRecord e : sample) {
            float[] targetQ = targetNetwork.predict(e.resultantState);
            float maxVal = -100000;
            int bestI = 4;
            for (int i = 0; i < 5; i++) {
                if (targetQ[i] > maxVal) {
                    maxVal = targetQ[i];
                    bestI = i;
                }
            }
            float y = e.jointReward + (GAMMA * maxVal);
            float[] pred = e.actionValues.clone();
            pred[bestI] = y;
            MLDataItem item = new TabularDataSet.Item(e.originState, pred);
            dataItems.add(item);
        }
        DataSet<MLDataItem> dataSet = new BasicDataSet<>(dataItems);
        qNetwork.train(dataSet);
    }

    /**
     * Takes state without the agent we want, then produces the best single state to match this (agent's action)
     * @param input
     * @return
     */
    private float[] compute(float[] input) {
        return qNetwork.predict(input);

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
                        if (equiv.getDistance(coord) < programmerHandler.getSenseRange()) {
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

    public int[] calculateEquivalentGridCell(Coordinate c) {
        return new int[]{
                            (int) Math.floor(((c.getLongitude() - botLeft.getLongitude()) / (xSpan)) * xSteps),
                            (int) Math.floor(((c.getLatitude() - botLeft.getLatitude()) / (ySpan)) * ySteps)
                        };
    }

    public float[] getState() {
        float[] state = new float[stateSize];
        int i = 0;
        for (Agent a : agents) {
            int[] pair = calculateEquivalentGridCell(a.getCoordinate());
            state[i] = (float) pair[0];
            state[i + 1] = (float) pair[1];
            i += 2;
        }
        return state;
    }

    private float[] getStateForThisAgent(Agent agent) {
        //synchronized (this) {
            float[] state = new float[stateSize];
            int[] agentPair = calculateEquivalentGridCell(agent.getCoordinate());
            state[0] = (float) agentPair[0];
            state[1] = (float) agentPair[1];
            int i = 2;
            for (Agent a : agents) {
                if (!(a.equals(agent))) {
                    int[] pair = calculateEquivalentGridCell(a.getCoordinate());
                    state[i] = (float) pair[0];
                    state[i + 1] = (float) pair[1];
                    i += 2;
                }
            }
            return state;
        //}
    }

    public boolean checkInGrid(int[] cell) {
        return cell[0] >= 0 && cell[0] <= xSteps && cell[1] >= 0 && cell[1] <= ySteps;
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
