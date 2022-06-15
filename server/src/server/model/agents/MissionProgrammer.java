package server.model.agents;

import com.sun.source.doctree.ThrowsTree;
import deepnetts.data.MLDataItem;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.NeuralNetwork;
import deepnetts.net.layers.AbstractLayer;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;
import deepnetts.util.Tensor;
import server.Simulator;
import server.model.Coordinate;

import javax.visrec.ml.data.BasicDataSet;
import javax.visrec.ml.data.DataSet;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
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
    private FeedForwardNetwork targetNetwork;
    private ExperienceRecord[] buffer = new ExperienceRecord[40];
    private boolean bufferFull = false;
    private int pointer = 0;
    private int counter;
    private int stepCounter = 0;
    private final float GAMMA = 0.99f;
    private final float MAX_REWARD;
    private boolean epsBranch = false;
    private int SAMPLE_SIZE = 10;
    private float LEARNING_RATE = 0.0001f;

    public MissionProgrammer(AgentHubProgrammed ahp, ProgrammerHandler progHandler) {
        System.out.println("CONS");
        hub = ahp;
        programmerHandler = progHandler;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap) {agents.add(ap);}});
        MAX_REWARD = xSteps * ySteps;

    }

    public void step() {
        if (!assigned) {
            //randomAssignAll();
            qLearningSetup();
        } else {
            if (stepCounter < 60) {
                qLearningStep();
                stepCounter++;
            } else {
                // SOFT RESET
                Simulator.instance.softReset(this);
                Simulator.instance.startSimulation();
                agents = new ArrayList<>();
                Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap) {agents.add(ap);}});
                stepCounter = 0;
            }
        }
    }

    private void qLearningSetup() {
        // We use:
        //      a state code (1-100 representing flattened grid 10x10)
        //      an action code (0-5 representing N-S-E-W-STOP)
        //

        System.out.println("SETUP");
        int[] layerWidths = new int[2];

        for (int i=0; i<2; i++) {
            layerWidths[i] = 8;
        }

        qNetwork = FeedForwardNetwork.builder()
                .addInputLayer(8)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addOutputLayer(5, ActivationType.LINEAR)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(6400)
                .build();

        // SOMETHING IN MAX EPOCHS CASUSES PROBLEM
        qNetwork.getTrainer()
                .setBatchMode(false)
                .setLearningRate(LEARNING_RATE)
                .setOptimizer(OptimizerType.SGD)
                .setMaxEpochs(SAMPLE_SIZE);

        targetNetwork = FeedForwardNetwork.builder()
                .addInputLayer(8)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addFullyConnectedLayer(16, ActivationType.LINEAR)
                .addOutputLayer(5, ActivationType.LINEAR)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(6400)
                .build();

        targetNetwork.getTrainer()
                .setBatchMode(false)
                .setLearningRate(LEARNING_RATE)
                .setOptimizer(OptimizerType.SGD)
                .setMaxEpochs(SAMPLE_SIZE);;

        assigned = true;
        counter = 0;

    }

    private void qLearningStep() {
        // For each agent:
            // Run selection
        // Step
            // Reward and update each (record joint action - joint reward pairs)

        for (Agent a : Simulator.instance.getState().getAgents()) {
            if (a instanceof AgentProgrammed ap && !(a instanceof Hub)) {
                int xCoord;
                int yCoord;
                float[] input = getStateForThisAgent(a);
                for (int i=0; i<input.length - 1;i+=2) {
                    input[i] /= xSteps;
                    input[i + 1] /= ySteps;
                }

                if (Simulator.instance.getRandom().nextDouble() < 0.9) {

                    float[] output = compute(input);

                    float maxVal = -100000;
                    int bestMove = -1;
                    for (int i=0; i<5; i++) {
                        if (output[i] > maxVal) {
                            maxVal = output[i];
                            bestMove = i;
                        }
                    }

                    ap.programmerHandler.gridMove(bestMove);

                    /*
                    float reward;
                    if (bestMove == 1) {
                        reward = 0.9f;
                    } else if (bestMove == 2) {
                        reward = 0.2f;
                    } else {
                        reward = -0.1f;
                    }
                     */

                    float reward = calculateReward();
                    //System.out.println("REWARD: " + reward);

                    float[] result = getStateForThisAgent(a);
                    for (int i=0; i<result.length - 1;i+=2) {
                        result[i] /= xSteps;
                        result[i + 1] /= ySteps;
                    }

                    // Trying 1 step each, reward and record EVERY time. In future use a joint
                    //buffer[pointer] = new ExperienceRecord(input, new float[]{coord[0], coord[1]}, reward, getState());
                    train(new ExperienceRecord(input, output, bestMove, reward, result));
                } else {
                    // EPSILON EXPLORATION
                    int move = Simulator.instance.getRandom().nextInt(5);

                    float[] expectedVals = qNetwork.predict(input);

                    ap.programmerHandler.gridMove(move);
                    //System.out.println("Randmove");

                    /*
                    float reward;
                    if (move == 1) {
                        reward = 0.9f;
                    } else if (move == 2) {
                        reward = 0.2f;
                    } else {
                        reward = -0.1f;
                    }
                    */

                    float reward = calculateReward();

                    float[] result = getStateForThisAgent(a);
                    for (int i=0; i<result.length - 1;i+=2) {
                        result[i] /= xSteps;
                        result[i + 1] /= ySteps;
                    }

                    train(new ExperienceRecord(input, expectedVals, move, reward, result));
                }

            }
        }
        counter++;
        if (counter >= 10) {
            System.out.println("============RESET================");
            int[] layerWidths = new int[2];
            for (int i=0; i<2; i++) {
                layerWidths[i] = 8;
            }

            targetNetwork = FeedForwardNetwork.builder()
                    .addInputLayer(8)
                    .addFullyConnectedLayer(16, ActivationType.LINEAR)
                    .addFullyConnectedLayer(16, ActivationType.LINEAR)
                    .addFullyConnectedLayer(16, ActivationType.LINEAR)
                    .addFullyConnectedLayer(16, ActivationType.LINEAR)
                    .addOutputLayer(5, ActivationType.LINEAR)
                    .lossFunction(LossType.MEAN_SQUARED_ERROR)
                    .randomSeed(6400)
                    .build();

            for (AbstractLayer l : targetNetwork.getLayers()) {
                l.setWeights(qNetwork.getLayers().get(targetNetwork.getLayers().indexOf(l)).getWeights());
                l.setBiases(qNetwork.getLayers().get(targetNetwork.getLayers().indexOf(l)).getBiases());
                l.setOptimizerType(OptimizerType.SGD);
                l.setActivationType(ActivationType.LINEAR);
                l.setLearningRate(LEARNING_RATE);
            }
            System.out.println("pred: " + Arrays.toString(qNetwork.predict(new float[]{.39f, .56f, .34f, .59f, .36f, .60f, .37f, .60f})));
            System.out.println("targ: " + Arrays.toString(targetNetwork.predict(new float[]{.39f, .56f, .34f, .59f, .36f, .60f, .37f, .60f})));
            System.out.println();



            counter = 0;
        }
        /*

        if (!bufferFull) {
            List<ExperienceRecord> miniBatch = new ArrayList<>();

            // Batch of 3 x agents
            for (int i = 0; i < agents.size() * 3; i++) {
                int index = Simulator.instance.getRandom().nextInt(10 * agents.size());
                miniBatch.add(buffer[index]);  // Maybe check for duplicates
            }

            qTrain(miniBatch);

        }

        pointer += agents.size();
        if (pointer >= 10 * agents.size()) {
            bufferFull = true;
            pointer = 0;
        }

         */
    }

    private void train(ExperienceRecord e) {
        if (bufferFull) {
            qTrain(sample());
        }
        buffer[pointer] = e;
        pointer++;
        if (pointer >= 40) {
            pointer = 0;
            bufferFull = true;
        }
    }

    private List<ExperienceRecord> sample() {
        List<ExperienceRecord> sample = new ArrayList<>();
        while (sample.size() < SAMPLE_SIZE) {
            int index = Simulator.instance.getRandom().nextInt(buffer.length);
            ExperienceRecord e = buffer[index];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        return sample;
    }

    private void qTrain(List<ExperienceRecord> sample) {
        // TODO not sure how to batch these

        //DataSet<TabularDataSet.Item> dataSet = new BasicDataSet<TabularDataSet.Item>(List);
        List<MLDataItem> dataItems = new ArrayList<>();
        for (ExperienceRecord e : sample) {
            float[] targetQ = targetNetwork.predict(e.resultantState);
            float maxVal = -100000;
            int bestI = -1;
            for (int i = 0; i < 5; i++) {
                if (targetQ[i] > maxVal) {
                    maxVal = targetQ[i];
                    bestI = i;
                }
            }

            //System.out.println("BEF: " + Arrays.toString(e.actionValues));
            float y = e.jointReward + (GAMMA * maxVal);
            float[] pred = e.actionValues.clone();
            pred[bestI] = y;
            //targetQ[bestI] = y;
            float diff = y - e.actionValues[bestI];
            //System.out.println("AFT: " + Arrays.toString(pred) + " (y=" + y + " at " + bestI + ") diff=" + diff);
            //System.out.println();

            MLDataItem item = new TabularDataSet.Item(e.originState, pred);
            dataItems.add(item);
            /*
            qNetwork.train();

            qNetwork.setInput(e.originState);
            qNetwork.getOutputLayer().setOutputs(new Tensor(targetQ));
            qNetwork.backward();
            qNetwork.applyWeightChanges();

            System.out.println("AFT : " + Arrays.toString(qNetwork.predict(e.originState)));

             */

            /*
            float loss = (float) Math.pow(y - e.actionValues[e.actionTaken], 2);
            float[] lossSet = new float[]{0f, 0f, 0f, 0f, 0f};
            lossSet[e.actionTaken] = loss;

            //System.out.println("loss: " + Arrays.toString(lossSet));

            qNetwork.setInput(e.originState);
            qNetwork.setOutputError(lossSet);
            qNetwork.backward();
            qNetwork.applyWeightChanges();
             */
        }

        /*
        System.out.println();
        System.out.println();
        System.out.println("TRAINING");
        System.out.println("before: " + Arrays.toString(qNetwork.predict(new float[]{.3f, .84f, .11f, .09f, .54f, .83f, .22f, .42f})));
        System.out.println("SZ di" + dataItems.size());

         */
        DataSet<MLDataItem> dataSet = new BasicDataSet<>(dataItems);
        /*
        System.out.println("SZ ds" + dataSet.size());
        System.out.println("USING: ");
        dataSet.forEach(System.out::println);
        System.out.println();
         */

        qNetwork.train(dataSet);
        //System.out.println("after : " + Arrays.toString(qNetwork.predict(new float[]{.3f, .84f, .11f, .09f, .54f, .83f, .22f, .42f})));
    }



    /*
    private void qTrain(List<ExperienceRecord> buffer) {
        for (ExperienceRecord e : buffer) {

        }
    }

     */

    /**
     * Takes state without the agent we want, then produces the best single state to match this (agent's action)
     * @param input
     * @return
     */
    private float[] compute(float[] input) {
        //System.out.println("IN: " + Arrays.toString(input));
        float[] output = qNetwork.predict(input);
        //System.out.println("OUT: " + Arrays.toString(output));
        //System.out.println("TRG: " + Arrays.toString(targetNetwork.predict(input)));
        return output;
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
    private float calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        double detectionRange = programmerHandler.getSenseRange();
        int numPointsCovered = 0;

        /*
        float total = 0;
        synchronized (Simulator.instance.getState().getAgents()) {
            for (Agent a : Simulator.instance.getState().getAgents()) {
                int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                float val;
                if (cell[0] < 0 || cell[0] > 100 || cell[1] < 0 || cell[1] > 100) {
                    val = -10000;
                } else {
                    val = (float) (cell[0] * (100 - cell[1]));  // [0,10000]    Trying to go to BL
                }
                total += val;
            }
        }
        float score = (total / 4) / 10000;
        System.out.println("Reward: " + score);
        return score;

         */


            for (int i = 0; i < xSteps; i++) {
                for (int j = 0; j < ySteps; j++) {
                    //System.out.println("for (" + i + ", " + j + ")");
                    Coordinate equiv = calculateEquivalentCoordinate(i, j);
                    for (Agent a : Simulator.instance.getState().getAgents()) {
                        if (!(a instanceof Hub)) {
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
        
        return numPointsCovered / MAX_REWARD;

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

    private int[] calculateEquivalentGridCell(Coordinate c) {
        int x = (int) Math.floor(((c.getLongitude() - botLeft.getLongitude()) / (topRight.getLongitude() - botLeft.getLongitude())) * xSteps);
        int y = (int) Math.floor(((c.getLatitude() - botLeft.getLatitude()) / (topRight.getLatitude() - botLeft.getLatitude())) * ySteps);


        return new int[]{x, y};
    }

    public float[] getState() {
        float[] state = new float[2*agents.size()];
        int i = 0;
        for (Agent a : agents) {
            if (!(a instanceof Hub)) {
                int[] pair = calculateEquivalentGridCell(a.getCoordinate());
                state[i] = (float) pair[0];
                state[i + 1] = (float) pair[1];
                i += 2;
            }
        }
        return state;
    }

    private float[] getStateForThisAgent(Agent agent) {
        float[] state = new float[2*agents.size()];
        int[] agentPair = calculateEquivalentGridCell(agent.getCoordinate());
        state[0] = (float) agentPair[0];
        state[1] = (float) agentPair[1];
        int i = 2;
        for (Agent a : agents) {
            if (!(a instanceof Hub) && !(a.equals(agent))) {
                int[] pair = calculateEquivalentGridCell(a.getCoordinate());
                state[i] = (float) pair[0];
                state[i + 1] = (float) pair[1];
                i += 2;
            }
        }
        //System.out.println("s " + Arrays.toString(state));
        return state;

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

    }

}
