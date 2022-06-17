package server.model.agents;

import deepnetts.data.MLDataItem;
import deepnetts.data.TabularDataSet;
import deepnetts.net.ConvolutionalNetwork;
import deepnetts.net.layers.AbstractLayer;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;
import deepnetts.util.Tensor;
import server.Simulator;
import server.model.Coordinate;

import javax.visrec.ml.data.BasicDataSet;
import javax.visrec.ml.data.DataSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LearningAllocator {
    private static final int TRAIN_FREQUENCY = 1;
    private static final float GAMMA = 0.9f;  // TODO trying gamma=1 might help?
    private static final int SAMPLE_SIZE = 50;
    private static final float LEARNING_RATE = 0.0001f;
    private static final int BUFFER_SIZE = 1000;
    private final int xSteps = 64;
    private final  int ySteps = 64;
    private double xSpan;
    private double ySpan;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;

    private List<AgentProgrammed> subordinates;

    private float MAX_REWARD;
    private Coordinate botLeft;
    private Coordinate topRight;
    private ConvolutionalNetwork qNetwork;
    private ConvolutionalNetwork targetNetwork;
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

    private ConvolutionalNetwork createNetwork() {
        ConvolutionalNetwork net = ConvolutionalNetwork.builder()
                .addInputLayer(64, 64, 4)
                .addConvolutionalLayer(16, 16, 4,  4, ActivationType.LINEAR)
                .addConvolutionalLayer(8, 8, 4,  4, ActivationType.LINEAR)
                .addConvolutionalLayer(4, 4, 4,  4, ActivationType.LINEAR)
                .addFullyConnectedLayer(256, ActivationType.LINEAR)
                .addOutputLayer(5, ActivationType.LINEAR)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(6400)
                .build();

        net.getTrainer()
                .setOptimizer(OptimizerType.SGD)
                .setBatchSize(99999)
                .setBatchMode(true)
                .setLearningRate(LEARNING_RATE);

        return net;
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
        Tensor inputTensor = getState();
        float[][] outputs = new float[4][5];
        int[] moves = new int[4];

        float negReward = 0;

        for (int j = 0; j < subordinates.size(); j++) {
            AgentProgrammed a = subordinates.get(j);
            //Tensor inputTensor = getStateForThisAgent(a);
            float[] output = compute(inputTensor);
            outputs[j] = output;
            int move;

            if (Simulator.instance.getRandom().nextDouble() < 0.9) {
                float maxVal = 4;
                move = -1;
                for (int i = 0; i < 5; i++) {
                    if (output[i] > maxVal) {
                        maxVal = output[i];
                        move = i;
                    }
                }
            } else {
                // EPSILON EXPLORATION
                move = Simulator.instance.getRandom().nextInt(5);
            }
            moves[j] = move;
            if (!a.programmerHandler.gridMove(move)) {
                negReward = -1f;
            }

        }
        Tensor result = getState();
        // Balance to max reward and subtract up to 1 from this
        float jointReward = (calculateReward() / MAX_REWARD) - (negReward / 4f);
        for (int i=0; i<4; i++) {
            buffer[pointer] = new MissionProgrammer.ExperienceRecord(inputTensor, outputs[i], moves[i], jointReward, result);
            pointer++;
            if (pointer >= BUFFER_SIZE) {
                pointer = 0;
                bufferFull = true;
            }
        }
        if (counter % TRAIN_FREQUENCY == 0) {
            train();
        }

        counter++;
        if (counter >= 20) {
            copyNets();
            counter = 0;
        }
    }

    private void train() {
        // TODO we have to manually check due to this boolean unusually returning true whilst false (probably threading
        //  or branch prediction caused?)
        synchronized (this) {
            if (bufferFull && Arrays.stream(buffer).noneMatch(Objects::isNull)) {
                qTrain(sample());
            }
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
            float[] targetQ = targetCompute(e.resultantState);
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
            MLDataItem item = new TabularDataSet.Item(e.originState, new Tensor(pred));
            dataItems.add(item);
        }
        DataSet<MLDataItem> dataSet = new BasicDataSet<>(dataItems);
    }

    /**
     * Takes state without the agent we want, then produces the best single state to match this (agent's action)
     * @param input
     * @return
     */
    private float[] compute(Tensor input) {
        qNetwork.setInput(input);
        return qNetwork.getOutput();
    }

    private float[] targetCompute(Tensor input) {
        targetNetwork.setInput(input);
        return targetNetwork.getOutput();
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

    private Tensor getStateForThisAgent(AgentProgrammed agent) {
        float[][][] stateArray = new float[4][xSteps][ySteps];
        List<AgentProgrammed> orderedAgents = new ArrayList<>();
        orderedAgents.add(agent);
        subordinates.forEach(a -> {
            if (!a.equals(agent)) {
                orderedAgents.add(a);
            }
        });

        for (int d=0; d<orderedAgents.size(); d++) {
            Coordinate refCoord = orderedAgents.get(d).getCoordinate();
            for (int i=0; i<xSteps; i++) {
                for (int j=0; j<ySteps; j++) {
                    // TODO unsure if best to use binary classifier for coverage or some kind of radiated heatmap variant
                    if (refCoord.getDistance(calculateEquivalentCoordinate(i, j)) < 250) {
                        stateArray[d][i][j] = 1;
                    } else {
                        stateArray[d][i][j] = 0;
                    }
                }
            }
        }
        return new Tensor(stateArray);
    }

    private Tensor getState() {
        float[][][] stateArray = new float[4][xSteps][ySteps];
        for (int d=0; d<subordinates.size(); d++) {
            Coordinate refCoord = subordinates.get(d).getCoordinate();
            for (int i=0; i<xSteps; i++) {
                for (int j=0; j<ySteps; j++) {
                    // TODO unsure if best to use binary classifier for coverage or some kind of radiated heatmap variant
                    if (refCoord.getDistance(calculateEquivalentCoordinate(i, j)) < 250) {
                        stateArray[d][i][j] = 1;
                    } else {
                        stateArray[d][i][j] = 0;
                    }
                }
            }
        }
        return new Tensor(stateArray);
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
