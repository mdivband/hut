package server.model.agents;

import deepnetts.data.MLDataItem;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.AbstractLayer;
import deepnetts.net.layers.ConvolutionalLayer;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;
import server.Simulator;
import server.model.Coordinate;

import javax.visrec.ml.data.BasicDataSet;
import javax.visrec.ml.data.DataSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LearningAllocator {
    private final float GAMMA = 0.9f;
    private final int SAMPLE_SIZE = 10;
    private final float LEARNING_RATE = 0.001f;
    private final int BUFFER_SIZE = 40;
    private final int NUM_STEPS_PER_EPOCH = 500;
    private final int xSteps = 100;
    private final  int ySteps = 100;
    private double xSpan;
    private double ySpan;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;

    private List<AgentProgrammed> subordinates;

    private float MAX_REWARD;
    private Coordinate botLeft;
    private Coordinate topRight;
    private FeedForwardNetwork qNetwork;
    private FeedForwardNetwork targetNetwork;
    private MissionProgrammer.ExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int counter;
    private int stateSize;

    public void setup() {
        MAX_REWARD = xSteps * ySteps;
        buffer = new MissionProgrammer.ExperienceRecord[BUFFER_SIZE];
        qNetwork = createNetwork();
        copyNets();  // Sets target network
        subordinates = new ArrayList<>();
        pointer = 0;
        counter = 0;
    }

    public void reset() {
        copyNets();
        pointer = 0;
        counter = 0;
        buffer = new MissionProgrammer.ExperienceRecord[BUFFER_SIZE];
    }

    public void step() {
        qLearningStep();
    }

    public void setBounds(Coordinate botLeft, Coordinate topRight) {
        this.botLeft = botLeft;
        this.topRight = topRight;
        xSpan = topRight.getLongitude() - botLeft.getLongitude();
        ySpan = topRight.getLatitude() - botLeft.getLatitude();
        xSquareSpan = xSpan / xSteps;
        ySquareSpan = ySpan / ySteps;
        cellWidth = (float) ((xSquareSpan * 111111));  //((0.00016245471 * 111111));
    }

    public void addSubordinate(AgentProgrammed ap) {
        subordinates.add(ap);
        stateSize = subordinates.size();
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
        for (AgentProgrammed a : subordinates) {
            float[] input = getStateForThisAgent(a);
            for (int i=0; i<input.length - 1;i+=2) {
                input[i] /= xSteps;
                input[i + 1] /= ySteps;
            }
            float[] output = compute(input);
            int move;
            float reward;

            if (Simulator.instance.getRandom().nextDouble() < 0.9) {
                float maxVal = 4;
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
            //System.out.println("Ordering " + a.getId() + " to go " + move);
            if (a.programmerHandler.gridMove(move)) {
                reward = (calculateReward() / MAX_REWARD) - rewardBefore;

            } else {
                reward = -1f;
            }

            float[] result = getStateForThisAgent(a);

            for (int i=0; i<result.length - 1;i+=2) {
                result[i] /= xSteps;
                result[i + 1] /= ySteps;
            }

            train(new MissionProgrammer.ExperienceRecord(input, output, move, reward, result));

        }
        counter++;
        if (counter >= 100) {
            copyNets();
            counter = 0;
        }
    }

    private void train(MissionProgrammer.ExperienceRecord e) {
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

    private List<MissionProgrammer.ExperienceRecord> sample() {
        List<MissionProgrammer.ExperienceRecord> sample = new ArrayList<>();
        while (sample.size() < SAMPLE_SIZE) {
            MissionProgrammer.ExperienceRecord e = buffer[Simulator.instance.getRandom().nextInt(BUFFER_SIZE)];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        return sample;
    }

    private void qTrain(List<MissionProgrammer.ExperienceRecord> sample) {
        List<MLDataItem> dataItems = new ArrayList<>();
        for (MissionProgrammer.ExperienceRecord e : sample) {
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
                for (Agent a : subordinates) {
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

    public int[] calculateEquivalentGridCell(Coordinate c) {
        return new int[]{
                (int) Math.floor(((c.getLongitude() - botLeft.getLongitude()) / (xSpan)) * xSteps),
                (int) Math.floor(((c.getLatitude() - botLeft.getLatitude()) / (ySpan)) * ySteps)
        };
    }

    public float getCellWidth() {
        return cellWidth;
    }

    private float[] getStateForThisAgent(Agent agent) {
        //synchronized (this) {
        float[] state = new float[stateSize * 2];
        int[] agentPair = calculateEquivalentGridCell(agent.getCoordinate());
        state[0] = (float) agentPair[0];
        state[1] = (float) agentPair[1];
        int i = 2;
        for (AgentProgrammed a : subordinates) {
            if (!(a.equals(agent))) {
                int[] pair = calculateEquivalentGridCell(a.getCoordinate());
                state[i] = (float) pair[0];
                state[i + 1] = (float) pair[1];
                i += 2;
            }
        }
        return state;
    }

    public boolean checkInGrid(int[] cell) {
        return cell[0] >= 0 && cell[0] <= xSteps && cell[1] >= 0 && cell[1] <= ySteps;
    }


    public boolean checkCellValid(Coordinate coordinate) {
        return checkInGrid(calculateEquivalentGridCell(coordinate));
    }

    public List<AgentProgrammed> getSubordinates() {
        return subordinates;
    }

    public void setSubordinates(List<AgentProgrammed> subordinates) {
        this.subordinates = subordinates;
    }

}
