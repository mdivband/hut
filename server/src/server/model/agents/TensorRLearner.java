package server.model.agents;


import org.neuroph.contrib.rnn.util.Activation;
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
    private static final int SAMPLE_SIZE = 10;
    //private static final float LEARNING_RATE = 0.000001f;
    private static final float LEARNING_RATE = 0.1f;
    float TEMP = 0.01f;

    private static final int BUFFER_SIZE = 100;
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
        xSteps = 6;
        ySteps = 6;
        qNetwork = createNetwork();
        buffer = new ShortExperienceRecord[BUFFER_SIZE];
        pointer = 0;
        counter = 0;
        xCell = Simulator.instance.getRandom().nextInt(6);
        yCell = Simulator.instance.getRandom().nextInt(6);
    }

    public void reset() {
        super.reset();
        xCell = Simulator.instance.getRandom().nextInt(6);
        yCell = Simulator.instance.getRandom().nextInt(6);
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

            //if (!miniBuffer.isEmpty()) {

            if (lastRec != null) {
                float[] state = new float[36];
                for (int i = 0; i < 36; i++) {
                    state[i] = 0;
                }
                subordinates.forEach(a -> {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    cell[0] = Math.max(0, Math.min(cell[0], 6));
                    cell[1] = Math.max(0, Math.min(cell[1], 6));
                    state[(cell[1] * 6) + cell[0]] = 1;
                });

                //if (Arrays.stream(buffer).noneMatch(b -> Arrays.equals(b.inputState, state))) {
                int matchedI = -1;
                for (ShortExperienceRecord b : buffer) {
                    if (b != null && Arrays.equals(b.inputState, state)) {
                        matchedI = Arrays.stream(buffer).toList().indexOf(b);
                        break;
                    }
                }

                //System.out.println(Arrays.toString(state) + " -> " + lastRec.value + "("+jointReward+")");
                if (matchedI == -1) {
                    if (pointer >= BUFFER_SIZE) {
                        pointer = 0;
                        bufferFull = true;
                    }
                    lastRec.inputState = state;
                    lastRec.jointReward = jointReward;
                    buffer[pointer] = new ShortExperienceRecord(lastRec);
                    pointer++;
                } else {
                    lastRec.inputState = state;
                    lastRec.jointReward = jointReward;
                    buffer[matchedI] = new ShortExperienceRecord(lastRec);
                }


                //miniBuffer.clear();
            }

            //if (bufferFull && Arrays.stream(buffer).noneMatch(Objects::isNull)) {
                train();
            //}


            /*else if (lastRec != null) {
                trainOnIncompleteBuffer();
                //trainOnThis(lastRec);
            }

             */

            // Choose a hero
            int rnd = Simulator.instance.getRandom().nextInt(subordinates.size());

            AgentProgrammed hero = subordinates.get(rnd);
            // Create state rep
            //float[][] state = allZeros();
            float[] state = new float[36];
            for (int i = 0; i < 36; i++) {
                state[i] = 0;
            }
            AtomicBoolean fail = new AtomicBoolean(false);
            subordinates.forEach(a -> {
                if (a != hero) {
                    int[] cell = new int[] {((TensorRLearner) a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).xCell, ((TensorRLearner) a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).yCell};

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

            /*
            if (lastRec != null && lastRec.value != 0) {
                lastRec.jointReward = jointReward;
                lastRec.inputState = state;
                train();
            }

             */

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

/*
            float[] actualState = state.clone();
            actualState[(heroCell[1] * 2) + heroCell[0]] = 1;

            System.out.println(hero.getId() + " (" + Arrays.toString(actualState) + "), this at [" + ((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell() + ", "
                    + ((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell() + "] :");


 */

            if (debug  && stepCount % 10_000 == 0) {
                System.out.println(agent.getId() + " -> " + hero.getId() + " " + Arrays.toString(states[0]));
            }

            if (debug && stepCount % 10_000 == 1) {
                System.out.println("With reward = " + jointReward);
                System.out.println();
                System.out.println();
                System.out.println();
            }

            bestVal = -1000000;
            best = 0;
/*
            float[] vals = new float[5];
            boolean eps;
            if (epsilon == -1) {
                eps = false;
            } else {
                eps = (Simulator.instance.getRandom().nextInt(epsilon) == 0);
            }

            if (debug && stepCount % 10_000 == 0) {
                System.out.println("==============");
                System.out.println(agent.getId() + " -> " + hero.getId() + ",  " + Arrays.toString(states[0]));
            }
            for (int i = 4; i >= 0; i--) {
                if (!excluded[i]) {
                    double[] thisState = new double[4];
                    for (int p = 0; p < 4; p++) {
                        thisState[p] = states[i][p];
                    }

                    float val = compute(thisState);
                    vals[i] = val;
                    if (debug && stepCount % 10_000 == 0) {
                        System.out.println(i + " -> " + Arrays.toString(thisState) + " -> " + val);
                    }
                    if (val >= bestVal) {
                        bestVal = val;
                        best = i;
                    }
                }
            }
            if (eps) {
                best = Simulator.instance.getRandom().nextInt(5);
                bestVal = vals[best];
            }

             */

            // SOFTMAX


            double[] vals = new double[5];
            for (int i = 4; i >= 0; i--) {
                if (!excluded[i]) {
                    double[] thisState = new double[36];
                    for (int p = 0; p < 36; p++) {
                        thisState[p] = states[i][p];
                    }

                    float val = compute(thisState);
                    vals[i] = val;
                    if (debug && stepCount % 10_000 == 0) {
                        System.out.println(i + " -> " + Arrays.toString(thisState) + " -> " + val);
                    }
                    if (val >= bestVal) {
                        bestVal = val;
                        best = i;
                    }
                }
            }
            //System.out.println(hero.getId() + " -> " + Arrays.toString(vals));
            double total = Arrays.stream(vals).map(v -> Math.exp(v / TEMP)).sum();
            float[] cumulativeProbs = new float[5];
            float[] probs = new float[5];
            float sum = 0;
            for (int i = 4; i >= 0; i--) {
                float thisProb = (float) (Math.exp(vals[i] / TEMP) / total);
                //float thisProb = (float) (1 / (1 + Math.exp(-vals[i])));
                sum += thisProb;
                probs[i] = thisProb;
                cumulativeProbs[i] = sum;
            }
            float trg = Simulator.instance.getRandom().nextFloat(sum);

            if (debug && stepCount % 10_000 == 0) {
                //System.out.println(Arrays.toString(cumulativeProbs));
                System.out.println(Arrays.toString(probs));
            }

            for (int i = 4; i >= 0; i--) {
                if (cumulativeProbs[i] > trg) {
                    best = i;
                    bestVal = cumulativeProbs[i];
                    break;
                }
            }


            if (debug && stepCount % 10_000 == 0) {
                System.out.println("bv = " + bestVal + ", best = " + best + " for herocell = " + Arrays.toString(heroCell));
            }

            if (best == 0) {
                // No move
                if (heroCell[0] > 5 || heroCell[0] < 0 || heroCell[1] > 5 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 1) {
                if (heroCell[0] > 5 || heroCell[0] < 0 || heroCell[1] + 1 > 5 || heroCell[1] + 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] + 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] + 1);
            } else if (best == 2) {
                if (heroCell[0] > 5 || heroCell[0] < 0 || heroCell[1] - 1 > 5 || heroCell[1] - 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] - 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] - 1);
            } else if (best == 3) {
                if (heroCell[0] + 1 > 5 || heroCell[0] + 1 < 0 || heroCell[1] > 5 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] + 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] + 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 4) {
                if (heroCell[0] - 1 > 5 || heroCell[0] - 1 < 0 || heroCell[1] > 5 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] - 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] - 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            }

            ShortExperienceRecord e = new ShortExperienceRecord(state, bestVal, -1);
            lastRec = e;
            //miniBuffer.add(e);
            /*
            if (stepCount % 5 == 0) {
                miniBuffer.add(e);
            } else {
                lastRec = e;
            }

             */

            if (debug && stepCount % 10_000 == 0) {
                evaluateNet();
            }


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
        float[][] futureStates = new float[5][36];
        for (int i = 0; i < 5; i++) {
            futureStates[i] = state.clone();
        }

        excluded = new boolean[]{false, false, false, false, false};
        try {
            //futureStates[0][heroCell[0]][heroCell[1]] = 1;  // 0 = St
            futureStates[0][((heroCell[1] * 6) + heroCell[0])] = 1;  // 0 = St
        } catch (Exception e) {
            excluded[0] = true;
        }
        try {
            //futureStates[2][((heroCell[1] * 2) + heroCell[0] - 1)] = 1;  // 1 = N
            if (heroCell[1] + 1 < 0) {
                throw new Exception();
            }
            futureStates[1][(((heroCell[1] + 1) * 6) + heroCell[0])] = 1;  // 1 = N
        } catch (Exception e) {
            excluded[1] = true;
        }
        try {
            //futureStates[1][((heroCell[1] * 2) + heroCell[0] + 1)] = 1;  // 2 = S
            if (heroCell[1] - 1 > 5) {
                throw new Exception();
            }
            futureStates[2][(((heroCell[1] - 1) * 6) + heroCell[0])] = 1;  // 2 = S
        } catch (Exception e) {
            excluded[2] = true;
        }
        try {
            //futureStates[3][(((heroCell[1] + 1) * 2) + heroCell[0])] = 1;  // 3 = E
            if (heroCell[0] + 1 > 5) {
                throw new Exception();
            }
            futureStates[3][((heroCell[1] * 6) + heroCell[0] + 1)] = 1;  // 3 = E
        } catch (Exception e) {
            excluded[3] = true;
        }
        try {
            //futureStates[4][(((heroCell[1] - 1) * 2) + heroCell[0])] = 1;  // 4 = W
            if (heroCell[0] - 1 < 0) {
                throw new Exception();
            }
            futureStates[4][((heroCell[1] * 6) + heroCell[0] - 1)] = 1;  // 4 = W
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
            x = Math.max(0, Math.min(x, 5));
            y = Math.max(0, Math.min(y, 5));
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
        int sz = !bufferFull ? pointer : SAMPLE_SIZE;
        while (sample.size() < sz) {
            ShortExperienceRecord e = buffer[Simulator.instance.getRandom().nextInt(sz)];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        return sample;
    }

    private void train() {
        List<ShortExperienceRecord> sample = sample();
        org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(36, 1);
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


        for (ShortExperienceRecord e : sample) {
            double[] inSt = new double[36];
            for (int i=0; i<36; i++) {
                inSt[i] = e.inputState[i];
            }

            //double scaledReward = (Math.pow(e.jointReward, 2) / (xSteps*ySteps*xSteps*ySteps));
            //double scaledReward = e.jointReward / 4f;
            //double scaledReward = calculateActualRewardFromArray(new float[][]{{lastRec.inputState[0], lastRec.inputState[1]}, {lastRec.inputState[2], lastRec.inputState[3]}}) / 4f;
            DataSetRow item = new DataSetRow(inSt, new double[] {e.jointReward});
            //DataSetRow item = new DataSetRow(inSt, new double[] {(e.jointReward / (xSteps*ySteps))});
            ds.add(item);
            //System.out.println(Arrays.toString(inSt) + " -> A:" + (scaledReward) + " -> " + lastRec.value + " DELTA = " + (scaledReward - lastRec.value) + ", t= " + counter);

            //System.out.println("Expected = " + e.value + ", actual = " + (e.jointReward / (xSteps*ySteps)) + " diff = " + ((e.jointReward - e.value)/ (xSteps*ySteps)));
        }

        counter++;
        // Add buffer
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
                .withInputLayer(6, 6, 1)
                .withConvolutionLayer(1, 1,1)
                .withFullConnectedLayer(1)
                .build();


        convolutionNetwork.getLearningRule().setMaxIterations(1);
        //double LR = 0.0000000005d;
        convolutionNetwork.getLearningRule().setLearningRate(LEARNING_RATE);
        if (debug) {
            System.out.println("AG = " + agent.getId());
            System.out.println("LR = " + LEARNING_RATE);
            System.out.println("TMP = " + TEMP);
        }
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


        public ShortExperienceRecord(ShortExperienceRecord lastRec) {
            this.inputState = lastRec.inputState;
            this.value = lastRec.value;
            this.jointReward = lastRec.jointReward;
        }
    }


}
