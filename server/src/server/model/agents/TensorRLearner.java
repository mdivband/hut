package server.model.agents;


import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.SupervisedLearning;
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
    private static final int SAMPLE_SIZE = 1;
    //private static final float LEARNING_RATE = 0.000001f;
    private static final float LEARNING_RATE = 0.00001f;

    private static final int BUFFER_SIZE = 1;
    private ConvolutionalNetwork qNetwork;
    private ShortExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int stepCount = 0;
    private int xCell = 1;
    private int yCell = 1;

    private HashMap<Integer, Integer> levelMemory = new HashMap<>();

    private ArrayList<ShortExperienceRecord> miniBuffer = new ArrayList<>();
    private boolean[] excluded;
    private ShortExperienceRecord lastRec;
    private boolean debug = false;

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
        xSteps = 2;
        ySteps = 2;
        qNetwork = createNetwork();
        buffer = new ShortExperienceRecord[BUFFER_SIZE];
        pointer = 0;
        counter = 0;
        xCell = Simulator.instance.getRandom().nextInt(2);
        yCell = Simulator.instance.getRandom().nextInt(2);
    }

    public void reset() {
        super.reset();
        xCell = Simulator.instance.getRandom().nextInt(2);
        yCell = Simulator.instance.getRandom().nextInt(2);
        //xCell = xSteps / 2;
        //yCell = ySteps / 2;
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
            /*
            if (!miniBuffer.isEmpty()) {
                for (ShortExperienceRecord e : miniBuffer) {
                    if (pointer >= BUFFER_SIZE) {
                        pointer = 0;
                        bufferFull = true;
                    }
                    float[] state = new float[4];
                    for (int i = 0; i < 4; i++) {
                        state[i] = 0;
                    }
                    subordinates.forEach(a -> {
                        int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                        cell[0] = Math.max(0, Math.min(cell[0], 1));
                        cell[1] = Math.max(0, Math.min(cell[1], 1));
                        state[(cell[1] * 2) + cell[0]] = 1;
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

             */
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
            float[] state = new float[4];
            for (int i = 0; i < 4; i++) {
                state[i] = 0;
            }
            AtomicBoolean fail = new AtomicBoolean(false);
            subordinates.forEach(a -> {
                if (a != hero) {
                    int[] cell = new int[] {((TensorRLearner) a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).xCell, ((TensorRLearner) a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).yCell};
                    if (cell[0] < 0 || cell[0] > 1 || cell[1] < 0 || cell[1] > 1) {
                        System.out.println(agent.getId() + " -> " + a.getId() + " = " + Arrays.toString(cell));
                    }

                    Coordinate c = calculateEquivalentCoordinate(cell[0], cell[1]);
                    int[] remapped = calculateEquivalentGridCell(c);
                    if (!Arrays.equals(remapped, cell)) {
                        System.out.println("ERROR1: " +Arrays.toString(cell) + " -> " + c + " -> " + Arrays.toString(remapped));
                    }

                    //System.out.println(Arrays.toString(cell) + " -> " + c + " -> " + Arrays.toString(remapped));
                    /*
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] < 0 || cell[0] > 1 || cell[1] < 0 || cell[1] > 1) {
                        System.out.println(agent.getId() + " -> " + a.getId() + " = " + Arrays.toString(cell));
                    }

                     */

                    //cell[0] = Math.max(0, Math.min(cell[0], 1));
                    //cell[1] = Math.max(0, Math.min(cell[1], 1));
                    state[(cell[1] * 2) + cell[0]] = 1;
                }
            });
            if (fail.get()) {
                return;
            }
            if (lastRec != null) {
                lastRec.jointReward = jointReward;
                lastRec.inputState = state;
                train();
            }

            // 1. Populate moves in a list
            int[] heroCell = new int[] {((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).xCell,
                    ((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).yCell};
            Coordinate c = calculateEquivalentCoordinate(heroCell[0], heroCell[1]);
            int[] remapped = calculateEquivalentGridCell(c);
            if (!Arrays.equals(remapped, heroCell)) {
                System.out.println("ERROR: " + Arrays.toString(heroCell) + " -> " + c + " -> " + Arrays.toString(remapped));
            }

            //calculateEquivalentGridCell(hero.getCoordinate());
            float[][] states = ennumerateStates(state, heroCell);
            int best;
            float bestVal;
            boolean eps;
            if (epsilon == -1) {
                eps = false;
            } else {
                eps = (Simulator.instance.getRandom().nextInt(epsilon) == 0);
            }

/*
            float[] actualState = state.clone();
            actualState[(heroCell[1] * 2) + heroCell[0]] = 1;

            System.out.println(hero.getId() + " (" + Arrays.toString(actualState) + "), this at [" + ((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell() + ", "
                    + ((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell() + "] :");


 */

            if (!eps) {
                bestVal = -1000000;
                best = 0;

                if (debug) {
                    System.out.println(agent.getId() + " -> " + hero.getId() + " " + Arrays.toString(states[0]));
                }
                for (int i = 0; i < 5; i++) {
                    if (!excluded[i]) {
                        double[] thisState = new double[4];
                        for (int p = 0; p < 4; p++) {
                            thisState[p] = states[i][p];
                        }

                        float val = compute(thisState);
                        if (debug) {
                            System.out.println(i + " -> " + Arrays.toString(thisState) + " -> " + val);
                        }
                        if (val >= bestVal) {
                            bestVal = val;
                            best = i;
                        }
                    }
                }
            } else {
                best = Simulator.instance.getRandom().nextInt(5);
                bestVal = -1;
            }
            if (debug) {
                System.out.println("bv = " + bestVal + ", best = " + best + " for herocell = " + Arrays.toString(heroCell));
            }



            if (best == 0) {
                // No move
                if (heroCell[0] > 1 || heroCell[0] < 0 || heroCell[1] > 1 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 1) {
                if (heroCell[0] > 1 || heroCell[0] < 0 || heroCell[1] + 1 > 1 || heroCell[1] + 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] + 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] + 1);
            } else if (best == 2) {
                if (heroCell[0] > 1 || heroCell[0] < 0 || heroCell[1] - 1 > 1 || heroCell[1] - 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] - 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] - 1);
            } else if (best == 3) {
                if (heroCell[0] + 1 > 1 || heroCell[0] + 1 < 0 || heroCell[1] > 1 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] + 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] + 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 4) {
                if (heroCell[0] - 1 > 1 || heroCell[0] - 1 < 0 || heroCell[1] > 1 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] - 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] - 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            }

            ShortExperienceRecord e = new ShortExperienceRecord(state, bestVal, -1);
            lastRec = e;
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

    public void debugOut() {
        System.out.println("============================= " + agent.getId() + "=============================");
        System.out.println("Current config: "
                + "[" + ((TensorRLearner) subordinates.get(0).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell() + ", "
                + ((TensorRLearner) subordinates.get(0).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell() + "]"

                + "[" + ((TensorRLearner) subordinates.get(1).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell() + ", "
                + ((TensorRLearner) subordinates.get(1).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell() + "]"

                + "[" + ((TensorRLearner) subordinates.get(2).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell() + ", "
                + ((TensorRLearner) subordinates.get(2).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell() + "]"

                + "[" + ((TensorRLearner) subordinates.get(3).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell() + ", "
                + ((TensorRLearner) subordinates.get(3).getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell() + "]"
        );




        List<Double> deltas = new ArrayList<>();
        for (int r = 0; r < 1000; r++) {
            float[] state = new float[4];
            for (int i = 0; i < 4; i++) {
                state[i] = 0;
            }
            state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;
            state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;
            state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;
            state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;

            float[][] thisState = new float[2][2];
            for (int j = 0; j < 2; j++) {
                float[] row = new float[2];
                System.arraycopy(state, j * 2, row, 0, 2);
                thisState[j] = row;
            }

            double[] inSt = new double[4];
            for (int i = 0; i < 4; i++) {
                inSt[i] = state[i];
            }

            qNetwork.setInput(inSt);
            qNetwork.calculate();
            double res = qNetwork.getOutput()[0];
            double actual = calculateActualRewardFromArray(thisState) / 4d;
            double delta = res - actual;
            deltas.add(delta);
        }
        System.out.println("Max delta -> " + (deltas.stream().mapToDouble(Double::doubleValue).max().getAsDouble()));
        System.out.println("Average delta -> " + (deltas.stream().mapToDouble(Double::doubleValue).average().getAsDouble()));


        System.out.println("SAMPLE STEP:");
        debug = true;
        step(0.25f, -1);
        debug = false;
        System.out.println("done");


    }

    private float[][] ennumerateStates(float[] state, int[] heroCell) {
        // Create a state for each action (N,S,E,W,St)
        //float[][][] futureStates = new float[5][16][16];
        float[][] futureStates = new float[5][4];
        for (int i = 0; i < 5; i++) {
            futureStates[i] = state.clone();
        }

        excluded = new boolean[]{false, false, false, false, false};
        try {
            //futureStates[0][heroCell[0]][heroCell[1]] = 1;  // 0 = St
            futureStates[0][((heroCell[1] * 2) + heroCell[0])] = 1;  // 0 = St
        } catch (Exception e) {
            excluded[0] = true;
        }
        try {
            //futureStates[2][((heroCell[1] * 2) + heroCell[0] - 1)] = 1;  // 1 = N
            if (heroCell[1] + 1 < 0) {
                throw new Exception();
            }
            futureStates[1][(((heroCell[1] + 1) * 2) + heroCell[0])] = 1;  // 1 = N
        } catch (Exception e) {
            excluded[1] = true;
        }
        try {
            //futureStates[1][((heroCell[1] * 2) + heroCell[0] + 1)] = 1;  // 2 = S
            if (heroCell[1] - 1 > 1) {
                throw new Exception();
            }
            futureStates[2][(((heroCell[1] - 1) * 2) + heroCell[0])] = 1;  // 2 = S
        } catch (Exception e) {
            excluded[2] = true;
        }
        try {
            //futureStates[3][(((heroCell[1] + 1) * 2) + heroCell[0])] = 1;  // 3 = E
            if (heroCell[0] + 1 > 1) {
                throw new Exception();
            }
            futureStates[3][((heroCell[1] * 2) + heroCell[0] + 1)] = 1;  // 3 = E
        } catch (Exception e) {
            excluded[3] = true;
        }
        try {
            //futureStates[4][(((heroCell[1] - 1) * 2) + heroCell[0])] = 1;  // 4 = W
            if (heroCell[0] - 1 < 0) {
                throw new Exception();
            }
            futureStates[4][((heroCell[1] * 2) + heroCell[0] - 1)] = 1;  // 4 = W
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
            x = Math.max(0, Math.min(x, 1));
            y = Math.max(0, Math.min(y, 1));
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

    public float calculateGridReward() {
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                for (AgentProgrammed a : subordinates) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] == i && cell[1] == j) {
                        numPointsCovered++;
                        break;
                    }
                }
            }
        }
        return numPointsCovered;
    }

    private List<ShortExperienceRecord> sample() {
        List<ShortExperienceRecord> sample = new ArrayList<>();
        while (sample.size() < SAMPLE_SIZE) {
            ShortExperienceRecord e = buffer[Simulator.instance.getRandom().nextInt(BUFFER_SIZE)];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        System.out.println("Sample: " + sample);
        return sample;
    }

    private void train() {
        //List<ShortExperienceRecord> sample = sample();
        org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(4, 1);
        //List<DataSetRow> dataItems = new ArrayList<>();

        /*
        float[] state = new float[4];
        for (int i = 0; i < 4; i++) {
            state[i] = 0;
        }
        state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;
        state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;
        state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;
        state[(Simulator.instance.getRandom().nextInt(2) * 2) + Simulator.instance.getRandom().nextInt(2)] = 1;

        float[][] thisState = new float[2][2];
        for (int j = 0; j < 2; j++) {
            float[] row = new float[2];
            System.arraycopy(state, j * 2, row, 0, 2);
            thisState[j] = row;
        }

        double[] inSt = new double[4];
        for (int i = 0; i < 4; i++) {
            inSt[i] = state[i];
        }

        qNetwork.setInput(inSt);
        qNetwork.calculate();
        double res = qNetwork.getOutput()[0];
        float actual = (calculateActualRewardFromArray(thisState) / 4f);
        System.out.println(Arrays.toString(inSt) + " -> A:" + (actual) + " -> " + res + " DELTA = " + (actual - res) + ", t= " + counter);

        DataSetRow item = new DataSetRow(inSt, new double[] {actual});
        ds.add(item);
        qNetwork.learn(ds);
        counter++;

         */


        //for (ShortExperienceRecord e : sample) {
            double[] inSt = new double[4];
            for (int i=0; i<4; i++) {
                inSt[i] = lastRec.inputState[i];
            }

            //double scaledReward = (Math.pow(e.jointReward, 2) / (xSteps*ySteps*xSteps*ySteps));
            //double scaledReward = e.jointReward / 4f;
            double scaledReward = calculateActualRewardFromArray(new float[][]{{lastRec.inputState[0], lastRec.inputState[1]}, {lastRec.inputState[2], lastRec.inputState[3]}}) / 4f;
            DataSetRow item = new DataSetRow(inSt, new double[] {scaledReward});
            //DataSetRow item = new DataSetRow(inSt, new double[] {(e.jointReward / (xSteps*ySteps))});
            ds.add(item);
            //System.out.println(Arrays.toString(inSt) + " -> A:" + (scaledReward) + " -> " + lastRec.value + " DELTA = " + (scaledReward - lastRec.value) + ", t= " + counter);
            counter++;
            //System.out.println("Expected = " + e.value + ", actual = " + (e.jointReward / (xSteps*ySteps)) + " diff = " + ((e.jointReward - e.value)/ (xSteps*ySteps)));
        //}

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

    private float compute(double[] input) {
        qNetwork.setInput(input);
        qNetwork.calculate();
        double[] networkOutputOne = qNetwork.getOutput();
        //System.out.println(Arrays.toString(input) + " -> A:" + (calculateActualRewardFromArray(new float[][]{{(float) input[0], (float) input[1]}, {(float) input[2], (float) input[3]}}) / 4f) + " -> " + networkOutputOne[0] + " DELTA = " + ((calculateActualRewardFromArray(new float[][]{{(float) input[0], (float) input[1]}, {(float) input[2], (float) input[3]}}) / 4f) - networkOutputOne[0]) + ", t= " + counter);
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
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                if (state[i][j] == 1) {
                    numPointsCovered++;
                }
            }
        }
        //System.out.println(Arrays.deepToString(state) + " -> " + numPointsCovered);
        /*
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
                    if (cell[0] == i && cell[1] == j) {
                        numPointsCovered++;
                        break;
                    }
                }

            }
        }

         */

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
                .withInputLayer(2, 2, 1)
                .withConvolutionLayer(1, 1, 1)
                .withFullConnectedLayer(1)
                .build();


        convolutionNetwork.getLearningRule().setMaxIterations(1);
        convolutionNetwork.getLearningRule().setLearningRate(0.1d);
        /*
        ConvolutionalBackpropagation backPropagation = new ConvolutionalBackpropagation();
        backPropagation.setLearningRate(LEARNING_RATE);
        backPropagation.setBatchMode(false);
        backPropagation.setMaxIterations(1);
        //backPropagation.addListener(new LearningListener(convolutionNetwork, testSet));
        backPropagation.setErrorFunction(new MeanSquaredError());

        //convolutionNetwork.setLearningRule(backPropagation);

         */

        return convolutionNetwork;
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
