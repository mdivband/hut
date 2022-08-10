package server.model.agents;


import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.ConvolutionalNetwork;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.ConvolutionalBackpropagation;
import org.neuroph.util.Neuroph;
import server.Simulator;
import server.model.Coordinate;

import javax.visrec.ml.data.BasicDataSet;
import javax.visrec.ml.data.DataSet;
import java.awt.*;
import java.util.*;


import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

public class TensorRLearner extends LearningAllocator {
    private static final float GAMMA = 0.9f;  // TODO trying gamma=1 might help?
    private static final int SAMPLE_SIZE = 10;
    //private static final float LEARNING_RATE = 0.000001f;
    private static final float LEARNING_RATE = 0.0001f;

    private static final int BUFFER_SIZE = 1000;
    private ConvolutionalNetwork qNetwork;
    private ShortExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int stepCount = 0;
    private int xCell = 8;
    private int yCell = 8;

    private HashMap<Integer, Integer> levelMemory = new HashMap<>();

    private ArrayList<ShortExperienceRecord> miniBuffer = new ArrayList<>();
    private boolean[] excluded;
    private ShortExperienceRecord lastRec;

    public TensorRLearner(AgentProgrammed agent) {
        super(agent);
    }

    public void incrementMemory() {
        if (levelMemory.containsKey(getLevel())) {
            levelMemory.put(getLevel(), levelMemory.get(getLevel()) + 1);
        } else {
            levelMemory.put(getLevel(), 1);
        }
    }

    public HashMap<Integer, Integer> getLevelMemory() {
        return levelMemory;
    }

    public void setup() {
        super.setup();
        xSteps = 16;
        ySteps = 16;
        maxReward = 16 * 16;
        qNetwork = createNetwork();
        buffer = new ShortExperienceRecord[BUFFER_SIZE];
        pointer = 0;
        counter = 0;
    }

    public void reset() {
        super.reset();
        xCell = xSteps / 2;
        yCell = ySteps / 2;
    }

    @Override
    public void complete() {

    }

    @Override
    public void step() {
        step(-1);
    }

    public void step(float jointReward, int epsilon) {
        boolean perform = true;
        if (perform) {
            if (!miniBuffer.isEmpty()) {
                for (ShortExperienceRecord e : miniBuffer) {
                    if (pointer >= BUFFER_SIZE) {
                        pointer = 0;
                        bufferFull = true;
                    }
                    float[] state = new float[256];
                    for (int i = 0; i < 256; i++) {
                        state[i] = 0;
                    }
                    subordinates.forEach(a -> {
                        int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                        cell[0] = Math.max(0, Math.min(cell[0], 15));
                        cell[1] = Math.max(0, Math.min(cell[1], 15));
                        state[(cell[1] * 16) + cell[0]] = 1;
                    });
                    e.inputState = state;
                    e.jointReward = jointReward;
                    buffer[pointer] = e;
                    pointer++;
                }
                miniBuffer.clear();
            }

            if (bufferFull && Arrays.stream(buffer).noneMatch(Objects::isNull)) {
                train();
            }
            /*else if (lastRec != null) {
                trainOnIncompleteBuffer();
                //trainOnThis(lastRec);
            }

             */

            // Choose a hero
            int rnd = Simulator.instance.getRandom().nextInt(4);

            AgentProgrammed hero = subordinates.get(rnd);
            // Create state rep
            //float[][] state = allZeros();
            float[] state = new float[256];
            for (int i = 0; i < 256; i++) {
                state[i] = 0;
            }
            AtomicBoolean fail = new AtomicBoolean(false);
            subordinates.forEach(a -> {
                if (a != hero) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    cell[0] = Math.max(0, Math.min(cell[0], 15));
                    cell[1] = Math.max(0, Math.min(cell[1], 15));
                    state[(cell[1] * 16) + cell[0]] = 1;
                }
            });
            if (fail.get()) {
                return;
            }

            // 1. Populate moves in a list
            int[] heroCell = calculateEquivalentGridCell(hero.getCoordinate());
            float[][] states = ennumerateStates(state, heroCell);
            HashMap<Integer, Float> startValues = new HashMap<>();
            for (int i=0; i<5; i++) {
                float[] modelledState = states[i];
                int[] modelledHeroPos = heroCell.clone();
                if (i == 0) {

                } else if (i == 1) {
                    modelledHeroPos[1] += 1;
                } else if (i == 2) {
                    modelledHeroPos[1] -= 1;
                } else if (i == 3) {
                    modelledHeroPos[0] += 1;
                } else if (i == 4) {
                    modelledHeroPos[0] -= 1;
                }
                float bestVal = -1;
                float totalReward = 0;
                for (int d = 0; d < 1; d++) {
                    // For each step forward in time:
                    float[][] modelledFutureStates = ennumerateStates(modelledState, modelledHeroPos);
                    float[] values = new float[5];
                    int best = -1;
                    bestVal = -1000;

                    // Compute best move
                    for (int m = 0; m < 5; m++) {
                        if (!excluded[m]) {
                            float[][] thisState = new float[16][16];
                            for (int j = 0; j < 16; j++) {
                                float[] row = new float[16];
                                System.arraycopy(modelledFutureStates[m], j * 16, row, 0, 16);
                                thisState[j] = row;
                            }

                            double[] stateAsDoubleArray = new double[256];
                            for (int e=0; e<256; e++) {
                                stateAsDoubleArray[e] = modelledFutureStates[m][e];
                            }

                            /*
                            if (subordinates.get(0).getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getSubordinates().isEmpty()) {
                                values[i] = calculateActualRewardFromArray(thisState);
                            } else {
                                values[i] = compute(stateAsDoubleArray);
                            }
                             */

                            values[i] = compute(stateAsDoubleArray);
                            //values[i] = calculateActualRewardFromArray(thisState);

                            if (values[i] > bestVal) {
                                bestVal = values[i];
                                best = i;
                            }
                        }
                    }

                    // Take the best and model it
                    if (best == 0) {

                    } else if (best == 1) {
                        modelledHeroPos[1] += 1;
                    } else if (best == 2) {
                        modelledHeroPos[1] -= 1;
                    } else if (best == 3) {
                        modelledHeroPos[0] += 1;
                    } else if (best == 4) {
                        modelledHeroPos[0] -= 1;
                    }
                    //totalReward += (bestVal * (Math.pow(GAMMA, d-1)));

                }

                startValues.put(i, bestVal);
                //startValues.put(i, totalReward);

            }

            int best = -1;
            float bestVal = -1000;
            for (Map.Entry<Integer, Float> entry : startValues.entrySet()) {
                Integer k = entry.getKey();
                Float v = entry.getValue();
                if (v > bestVal) {
                    best = k;
                    bestVal = v;
                }
            }

            boolean eps = false;
            if (epsilon != -1) {
                eps = Simulator.instance.getRandom().nextInt(epsilon) == 0;
            }
            //boolean eps = Simulator.instance.getRandom().nextInt(epsilon) == 0;

            if (eps) {
                best = -1;
                if (excluded[0] && excluded[1] && excluded[2] && excluded[3] && excluded[4]) {
                    // All wrong
                    best = Simulator.instance.getRandom().nextInt(5);
                } else {
                    while (best == -1 || excluded[best]) {
                        best = Simulator.instance.getRandom().nextInt(5);
                    }
                }
                bestVal = startValues.get(best);
            }


            if (best == 0) {
                // No move
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 1) {
                if (heroCell[0] > 15 || heroCell[0] < 0 || heroCell[1] + 1 > 15 || heroCell[1] + 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] + 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] + 1);
            } else if (best == 2) {
                if (heroCell[0] > 15 || heroCell[0] < 0 || heroCell[1] - 1 > 15 || heroCell[1] - 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] - 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] - 1);
            } else if (best == 3) {
                if (heroCell[0] + 1 > 15 || heroCell[0] + 1 < 0 || heroCell[1] > 15 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] + 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] + 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 4) {
                if (heroCell[0] - 1 > 15 || heroCell[0] - 1 < 0 || heroCell[1] > 15 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] - 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] - 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            }

            ShortExperienceRecord e = new ShortExperienceRecord(state, bestVal, -1);
            miniBuffer.add(e);
            /*
            if (stepCount % 5 == 0) {
                miniBuffer.add(e);
            } else {
                lastRec = e;
            }

             */
            stepCount++;
        }
    }

    private float[][] ennumerateStates(float[] state, int[] heroCell) {
        // Create a state for each action (N,S,E,W,St)
        //float[][][] futureStates = new float[5][16][16];
        float[][] futureStates = new float[5][256];
        for (int i = 0; i < 5; i++) {
            futureStates[i] = state.clone();
        }

        excluded = new boolean[]{false, false, false, false, false};
        try {
            //futureStates[0][heroCell[0]][heroCell[1]] = 1;  // 0 = St
            futureStates[0][((heroCell[1] * 16) + heroCell[0])] = 1;  // 0 = St
        } catch (Exception e) {
            excluded[0] = true;
        }
        try {
            futureStates[1][((heroCell[1] * 16) + heroCell[0] + 1)] = 1;  // 1 = N
        } catch (Exception e) {
            excluded[1] = true;
        }
        try {
            futureStates[2][((heroCell[1] * 16) + heroCell[0] - 1)] = 1;  // 2 = S
        } catch (Exception e) {
            excluded[2] = true;
        }
        try {
            futureStates[3][(((heroCell[1] + 1) * 16) + heroCell[0])] = 1;  // 3 = E
        } catch (Exception e) {
            excluded[3] = true;
        }
        try {
            futureStates[4][(((heroCell[1] - 1) * 16) + heroCell[0])] = 1;  // 4 = W
        } catch (Exception e) {
            excluded[4] = true;
        }

        // We now have our futureStates populated
        return futureStates;
    }

    public void moveSubordinates() {
        for (AgentProgrammed sub : subordinates) {
            int x = ((TensorRLearner) sub.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell();
            int y = ((TensorRLearner) sub.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell();
            x = Math.max(0, Math.min(x, 16));
            y = Math.max(0, Math.min(y, 16));
            sub.manualSetTask(calculateEquivalentCoordinate(x, y));
        }
    }

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        BufferedImage resizedImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(img, 0, 0, newW, newH, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private void writeImage(int d, float[] result) {
        float[][] matrix = new float[xSteps][ySteps];
        for (int i=0;i<xSteps;i++) {
            if (ySteps >= 0) System.arraycopy(result, (i * xSteps), matrix[i], 0, ySteps);
        }

        try {
            BufferedImage image = new BufferedImage(xSteps, ySteps, BufferedImage.TYPE_INT_RGB);
            for(int i=0; i<matrix.length; i++) {
                for(int j=0; j<matrix.length; j++) {
                    float a = matrix[i][j];
                    int intensity = (int) Math.floor((0.5 + a/2) * 255);
                    intensity = Math.min(intensity, 255);
                    intensity = Math.max(0, intensity);
                    //System.out.println(a);
                    Color newColor = new Color(intensity,intensity,intensity);
                    image.setRGB(j,i,newColor.getRGB());
                }
            }
            BufferedImage scaled = resize(image, 256, 256);
            File output = new File("decision"+d+".jpg");
            ImageIO.write(scaled, "jpg", output);
        } catch(Exception ignored) {
            System.out.println(ignored);
        }

    }

    private List<ShortExperienceRecord> sample() {
        List<ShortExperienceRecord> sample = new ArrayList<>();
        while (sample.size() < SAMPLE_SIZE) {
            ShortExperienceRecord e = buffer[Simulator.instance.getRandom().nextInt(BUFFER_SIZE)];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        return sample;
    }

    private void trainOnIncompleteBuffer() {
        try {
            List<ShortExperienceRecord> sample = new ArrayList<>();
            int head = 0;
            while ((buffer[head] != null) && (head < BUFFER_SIZE)) {
                head++;
            }

            while (sample.size() < Math.min(head, SAMPLE_SIZE)) {
                ShortExperienceRecord e = buffer[Simulator.instance.getRandom().nextInt(head)];
                if (!sample.contains(e)) {
                    sample.add(e);
                }
            }

            org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(256, 1);
            //List<DataSetRow> dataItems = new ArrayList<>();
            for (ShortExperienceRecord e : sample) {
                double[] inSt = new double[256];
                for (int i = 0; i < 256; i++) {
                    inSt[i] = e.inputState[i];
                }

                //double scaledReward = (Math.pow(e.jointReward, 2) / (xSteps*ySteps*xSteps*ySteps));
                //double scaledReward = e.jointReward / (xSteps*ySteps);
                double scaledReward = e.jointReward / (64f);
                DataSetRow item = new DataSetRow(inSt, new double[]{scaledReward});
                //DataSetRow item = new DataSetRow(inSt, new double[] {(e.jointReward / (xSteps*ySteps))});
                ds.add(item);
                //System.out.println("Expected = " + e.value + ", actual = " + (e.jointReward / (xSteps*ySteps)) + " diff = " + ((e.jointReward - e.value)/ (xSteps*ySteps)));
            }
        } catch (Exception e) {
            bufferFull = true;
        }
    }

    private void trainOnThis(ShortExperienceRecord e) {
        org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(256, 1);
        double[] inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = e.inputState[i];
        }

        //double scaledReward = (Math.pow(e.jointReward, 2) / (xSteps*ySteps*xSteps*ySteps));
        //double scaledReward = e.jointReward / (xSteps*ySteps);
        double scaledReward = e.jointReward / (64f);
        DataSetRow item = new DataSetRow(inSt, new double[] {scaledReward});
        //DataSetRow item = new DataSetRow(inSt, new double[] {(e.jointReward / (xSteps*ySteps))});
        ds.add(item);
    }

    private void train() {
        List<ShortExperienceRecord> sample = sample();
        org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(256, 1);
        //List<DataSetRow> dataItems = new ArrayList<>();

        for (ShortExperienceRecord e : sample) {
            double[] inSt = new double[256];
            for (int i=0; i<256; i++) {
                inSt[i] = e.inputState[i];
            }

            //double scaledReward = (Math.pow(e.jointReward, 2) / (xSteps*ySteps*xSteps*ySteps));
            //double scaledReward = e.jointReward / (xSteps*ySteps);
            double scaledReward = e.jointReward / (64f);
            DataSetRow item = new DataSetRow(inSt, new double[] {scaledReward});
            //DataSetRow item = new DataSetRow(inSt, new double[] {(e.jointReward / (xSteps*ySteps))});
            ds.add(item);
            //System.out.println("Expected = " + e.value + ", actual = " + (e.jointReward / (xSteps*ySteps)) + " diff = " + ((e.jointReward - e.value)/ (xSteps*ySteps)));
        }
/*

        float[] state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(4 * 16) + 4] = 1;
        state[(4 * 16) + 12] = 1;
        state[(12 * 16) + 4] = 1;
        state[(12 * 16) + 12] = 1;

        float[][] thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        double[] inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        DataSetRow item = new DataSetRow(inSt, new double[] {1d});
        ds.add(item);

        state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(6 * 16) + 6] = 1;
        state[(6 * 16) + 10] = 1;
        state[(10 * 16) + 6] = 1;
        state[(10 * 16) + 10] = 1;

        thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }
        DataSetRow item2 = new DataSetRow(inSt, new double[] {169f / 256f});
        ds.add(item2);


        state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(1 * 16) + 1] = 1;
        state[(3 * 16) + 2] = 1;

        thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        DataSetRow item3 = new DataSetRow(inSt, new double[] {144f / 256f});
        ds.add(item3);

        state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(4 * 16) + 4] = 1;
        state[(12 * 16) + 12] = 1;

        thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        DataSetRow item4 = new DataSetRow(inSt, new double[] {56f / 256f});
        ds.add(item4);

 */
        qNetwork.learn(ds);
    }


    private int[] calculateBestCoord(float[] result) {
        float bestValue = -1f;
        int[] crd = new int[2];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int equivalentFlatIndex = i*16 + j;
                float value = result[equivalentFlatIndex];
                if (value > bestValue) {
                    bestValue = value;
                    crd = new int[]{i, j};
                }
            }
        }
        //System.out.println("bv = " +bestValue + ", crd = " + Arrays.toString(crd));
        return crd;
    }

    private float compute(double[] input) {
        qNetwork.setInput(input);
        qNetwork.calculate();
        double[] networkOutputOne = qNetwork.getOutput();
        return (float) networkOutputOne[0];
    }

    public void checkTestRewards() {
        float[] state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(4 * 16) + 4] = 1;
        state[(4 * 16) + 12] = 1;
        state[(12 * 16) + 4] = 1;
        state[(12 * 16) + 12] = 1;

        float[][] thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        double[] inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        float res = compute(inSt);

        System.out.println("PERFECT = " + res);
        System.out.println("--Actual = " + calculateActualRewardFromArray(thisState));


        state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(6 * 16) + 6] = 1;
        state[(6 * 16) + 10] = 1;
        state[(10 * 16) + 6] = 1;
        state[(10 * 16) + 10] = 1;

        thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        res = compute(inSt);

        System.out.println("MEDIUM= = " + res);
        System.out.println("--Actual = " + calculateActualRewardFromArray(thisState));

        state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(4 * 16) + 4] = 1;
        state[(12 * 16) + 12] = 1;

        thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        res = compute(inSt);

        System.out.println("OVERLAP CORNERS = " + res);
        System.out.println("--Actual = " + calculateActualRewardFromArray(thisState));

        state = new float[256];
        for (int i = 0; i < 256; i++) {
            state[i] = 0;
        }
        state[(1 * 16) + 1] = 1;
        state[(3 * 16) + 2] = 1;

        thisState = new float[16][16];
        for (int j = 0; j < 16; j++) {
            float[] row = new float[16];
            System.arraycopy(state, j * 16, row, 0, 16);
            thisState[j] = row;
        }

        inSt = new double[256];
        for (int i=0; i<256; i++) {
            inSt[i] = state[i];
        }
        res = compute(inSt);

        System.out.println("BAD = " + res);
        System.out.println("--Actual = " + calculateActualRewardFromArray(thisState));


    }

    private float calculateActualRewardFromArray(float[][] state) {
        List<int[]> agents = new ArrayList<>();
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                if (state[i][j] == 1) {
                    agents.add(new int[]{i, j});
                }
            }
        }

        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                for (int[] cell : agents) {
                    if (cell[0] - 4 <= i && cell[0] + 4 >= i && cell[1] - 4 <= j && cell[1] + 4 >= j) {
                        numPointsCovered++;
                        break;
                    }
                }

            }
        }

        return numPointsCovered;
    }

    private float[][] allZeros() {
        float[][] res = new float[16][16];
        for (int i=0; i<16; i++) {
            for (int j=0; j<16; j++) {
                res[i][j] = 0;
            }
        }
        return res;
    }

    private ConvolutionalNetwork createNetwork() {

        // Passable:
        /*
        ConvolutionalNetwork convolutionNetwork = new ConvolutionalNetwork.Builder()
                .withInputLayer(16, 16, 1)
                .withConvolutionLayer(1, 1, 1)
                .withFullConnectedLayer(256)
                .withFullConnectedLayer(1)
                .build();
         */
        ConvolutionalNetwork convolutionNetwork = new ConvolutionalNetwork.Builder()
                .withInputLayer(16, 16, 1)
                .withConvolutionLayer(5, 5, 1)
                .withPoolingLayer(5,5)
                .withFullConnectedLayer(1)
                .build();


        ConvolutionalBackpropagation backPropagation = new ConvolutionalBackpropagation();
        backPropagation.setLearningRate(LEARNING_RATE);
        backPropagation.setBatchMode(false);
        backPropagation.setMaxIterations(1);
        //backPropagation.addListener(new LearningListener(convolutionNetwork, testSet));
        backPropagation.setErrorFunction(new MeanSquaredError());

        convolutionNetwork.setLearningRule(backPropagation);

        return convolutionNetwork;



/*
        ConvolutionalNetwork net = ConvolutionalNetwork.builder()
                .addInputLayer(16, 16, 1)
                .addConvolutionalLayer(4, 1)
                .addMaxPoolingLayer(4, 1)
                .addConvolutionalLayer(2, 1)
                .addMaxPoolingLayer(2, 1)
                .addFullyConnectedLayer(128)
                .addOutputLayer(1, ActivationType.LINEAR)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(Simulator.instance.getRandom().nextInt(9999))
                .build();

        net.getTrainer()
                .setOptimizer(OptimizerType.SGD)
                .setBatchSize(SAMPLE_SIZE)
                .setBatchMode(true)
                .setMaxError(999999)
                .setLearningRate(LEARNING_RATE);

        return net;

 */
    }

    public int getxCell() {
        return xCell;
    }

    public int getyCell() {
        return yCell;
    }

    public void setxCell(int xCell) {
        this.xCell = xCell;
    }

    public void setyCell(int yCell) {
        this.yCell = yCell;
    }

    public double evaluateNet() {
        NetTester netTester = new NetTester(qNetwork);
        return netTester.test(stepCount, 1000);
        //System.out.println(agent.getId() + " -> Average delta = " + netTester.test(stepCount, 1000));
    }

    private class ExperienceRecord {
        float[][][] inputState;
        float[] actionValues;
        int indexTaken;
        float jointReward;

        public ExperienceRecord(float[][][] inputState, float[] actionValues, int indexTaken, float jointReward) {
            this.inputState = inputState;
            this.actionValues = actionValues;
            this.indexTaken = indexTaken;
            this.jointReward = jointReward;
        }
    }

    private class ShortExperienceRecord {
        float[] inputState;
        float value;
        float jointReward;

        public ShortExperienceRecord(float[] inputState, float value, float jointReward) {
            this.inputState = inputState;
            this.value = value;
            this.jointReward = jointReward;
        }
    }


}
