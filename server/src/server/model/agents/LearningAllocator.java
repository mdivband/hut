package server.model.agents;

import org.neuroph.core.data.DataSetRow;
import org.neuroph.nnet.ConvolutionalNetwork;
import org.neuroph.nnet.learning.ConvolutionalBackpropagation;
import server.Simulator;
import tool.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class LearningAllocator {
    protected AgentProgrammed agent;

    private int HISTORY_SIZE = 3;

    protected int xSteps;
    protected int ySteps;
    protected int xCell;
    protected int yCell;

    protected int counter;
    private float LEARNING_RATE = 0.01f;
    private int level; // level 0 is a bottom level; raising as we move up
    private int bestRwd = 0;
    private ConvolutionalNetwork valueNet;
    private tool.history history;

    protected HashMap<String, CommFrame> frameMap = new HashMap<>();
    private double[][] currentBelief = null; // This is what we believe the actual value is
    private double[][] predictedNextBelief = null;
    private double[][] backVal; // This is the last popped element. We need to include this when training

    public LearningAllocator(AgentProgrammed agent) {
        this.agent = agent;
    }


    public void setup() {

    }

    public void setup(int xSteps, int ySteps) {
        this.xSteps = xSteps;
        this.ySteps = ySteps;
        history = new history(HISTORY_SIZE, xSteps, ySteps);
        valueNet = createNetwork();
    }

    public void reset() {

    }

    public void step(float jointReward) {
        System.out.println("NOT IMPLEMENTED");
    }
    public void step(float jointReward, float epsilon) {
        System.out.println("NOT IMPLEMENTED");
    }

    public void randStep() {

    }

    public boolean checkInGrid(int[] cell) {
        return cell[0] >= 0 && cell[0] <= xSteps && cell[1] >= 0 && cell[1] <= ySteps;
    }

    public void setLevel(int l) {
        this.level = l;
        if (l == 0) {
            agent.getProgrammerHandler().setVisual("ghost");
        } else if (l == 1) {
            agent.getProgrammerHandler().setVisual("standard");
        } else if (l == 2) {
            agent.getProgrammerHandler().setVisual("leader");
        }
    }

    public int getLevel() {
        return level;
    }

    public void setCell(int x, int y) {
        xCell = x;
        yCell = y;
    }

    public void decideRandomMove() {
        int tempOldX = xCell;
        int tempOldY = yCell;

        // St, N, S, E, W
        boolean[] possibles = new boolean[]{true, true, true, true, true}; // Preset as true for all (covers stop too)
        if (yCell >= ySteps - 1) { // Can't move upwards
            possibles[1] = false;
        } else if (yCell <= 0) { // Can't move downwards
            possibles[2] = false;
        }
        // Highly important that these ifs are separated
        if (xCell >= xSteps - 1) { // Can't move right
            possibles[3] = false;
        } else if (xCell <= 0) { // Can't move left
            possibles[4] = false;
        }

        int move = -1;
        while (move == -1 || !possibles[move]) {
            move = Simulator.instance.getRandom().nextInt(5);
        }

        if (move == 1) {
            // Move up
            yCell++;
            agent.setHeading(0);
        } else if (move == 2) {
            // Move down
            yCell--;
            agent.setHeading(180);
        } else if (move == 3) {
            // Move right
            xCell++;
            agent.setHeading(90);
        } else if (move == 4) {
            // Move left
            xCell--;
            agent.setHeading(270);
        }

        if (xCell < 0 || yCell < 0 || xCell >= xSteps || yCell >= ySteps) {


            System.out.println("HERE!!!");
            System.out.println("before x = " + tempOldX);
            System.out.println("before y = " + tempOldY);
            System.out.println("poss: " + Arrays.toString(possibles));
            System.out.println("move = " + move);
            System.out.println("(After move:)");
            System.out.println("x = " + xCell);
            System.out.println("y = " + yCell);
            System.out.println();
        }
    }

    public void receiveFrame(CommFrame frame) {
        //System.out.println(agent.getId() + " Receiving " + frame);
        // If this is the first one, we can have our own map, that's fine
        if (frameMap.isEmpty()) {
            frameMap.put(frame.agentID, frame);
        } else if (frame.agentID.equals(agent.getId())) {
            frameMap.get(agent.getId()).ttl--;
        } else {
            if (frameMap.containsKey(frame.agentID)) {
                frameMap.replace(frame.agentID, frame);
            } else {
                frameMap.put(frame.agentID, frame);
            }
        }
        //System.out.println("======");
        //frameMap.forEach((k,v) -> System.out.println(k + " -> " + v));
        //System.out.println();
    }

    public ArrayList<CommFrame> getFrameMapAsList() {
        return new ArrayList<>(frameMap.values());
    }

    public void printFrames() {
        frameMap.forEach((k,v) -> System.out.println(v));
    }

    public void clearFrames() {
        frameMap = new HashMap<>();
    }

    public boolean checkFramesResolved() {
        AtomicBoolean complete = new AtomicBoolean(true);
        frameMap.forEach((k,v) -> {
            if (!k.equals(agent.getId()) && v.ttl > 1) {
                complete.set(false);
            }
        });
        return complete.get();
    }

    public void addGlobalMap(double[][] constructedMap) {
        if (currentBelief != null) {
            backVal = history.enqueue(currentBelief);
        }
        currentBelief = constructedMap;
    }

    public void train() {
        if (history.isFull()) {
            // Use [backVal + history (minus head)] as input, and [currentBelief] as target
            double[][][] historyThen = history.getOrderedElementsAsTensor();
            historyThen[history.getFront()] = currentBelief;
            double[] input = flatten(historyThen);
            double[] actual = flatten(currentBelief);

            org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(HISTORY_SIZE * xSteps * ySteps, xSteps * ySteps);
            DataSetRow item = new DataSetRow(input, actual);
            ds.add(item);
            valueNet.learn(ds);
        } // ELSE wait until full
    }

    /**
     * Helper function to flatten 2d array
     * @param array2d
     * @return
     */
    public static double[] flatten(double[][] array2d) {
        int rows = array2d.length;
        int cols = array2d[0].length;

        double[] flattenedInput = new double[rows*cols];
        for (int j=0; j<rows; j++) {
            for (int i=0; i<cols; i++) {
                flattenedInput[j*cols + i] = array2d[j][i];
            }
        }

        return flattenedInput;
    }

    /**
     * Helper function to flatten 3d array
     * @param array3d
     * @return
     */
    public static double[] flatten(double[][][] array3d) {
        int depth = array3d.length;
        int rows = array3d[0].length;
        int cols = array3d[0][0].length;

        double[] flattenedInput = new double[depth*rows*cols];
        for (int d=0; d<depth; d++) {
            for (int j=0; j<rows; j++) {
                for (int i=0; i<cols; i++) {
                    flattenedInput[d*(rows*cols) + j*cols + i] = array3d[d][j][i];
                }
            }
        }

        return flattenedInput;
    }

    private double[][] unwrap(double[] flatArray) {
        double[][] unwrappedOutput = new double[ySteps][xSteps];

        for (int j=0; j<ySteps; j++) {
            if (xSteps >= 0) System.arraycopy(flatArray, j * xSteps, unwrappedOutput[j], 0, xSteps);
        }
        return unwrappedOutput;
    }

    public void predict() {
        // Use the entire history to predict next belief
        if (history.isFull()) {
            double[][][] input = history.getOrderedElementsAsTensor();
            double[] flattenedInput = flatten(input);

            valueNet.setInput(flattenedInput);
            valueNet.calculate();
            double[] output = valueNet.getOutput();

            predictedNextBelief = unwrap(output);
        } // ELSE wait until it's full
    }

    private ConvolutionalNetwork createNetwork() {
        ConvolutionalNetwork convolutionNetwork = new ConvolutionalNetwork.Builder()
                .withInputLayer(27, 27, 3)
                .withFullConnectedLayer(27*27)
                .build();

        convolutionNetwork.getLearningRule().setMaxIterations(1);
        convolutionNetwork.getLearningRule().setLearningRate(LEARNING_RATE);

        return convolutionNetwork;
    }

    public double[][] getPrediction() {
        return predictedNextBelief;
    }
}
