package server.model.agents;

import deepnetts.data.MLDataItem;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;
import deepnetts.util.Tensor;
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
    private static final float LEARNING_RATE = 0.000001f;

    private static final int BUFFER_SIZE = 100;
    private FeedForwardNetwork qNetwork;
    private ShortExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int stepCount = 0;
    private int xCell = 8;
    private int yCell = 8;

    private HashMap<Integer, Integer> levelMemory = new HashMap<>();

    private ArrayList<ShortExperienceRecord> miniBuffer = new ArrayList<>();

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

    @Override
    public void step(float jointReward) {
        boolean perform = true;
        if (perform) {
            if (!miniBuffer.isEmpty()) {
                for (ShortExperienceRecord e : miniBuffer) {
                    if (pointer >= BUFFER_SIZE) {
                        pointer = 0;
                        bufferFull = true;
                    }
                    e.jointReward = jointReward;
                    buffer[pointer] = e;
                    pointer++;
                }
                miniBuffer.clear();
            }
            if (bufferFull && Arrays.stream(buffer).noneMatch(Objects::isNull)) {
                train();
            }

            // Choose a hero
            int rnd = Simulator.instance.getRandom().nextInt(4);

            AgentProgrammed hero = subordinates.get(rnd);
            // Create state rep
            //float[][] state = allZeros();
            float[] state = new float[256];
            for (int i=0; i<256; i++) {
                state[i] = 0;
            }
            AtomicBoolean fail = new AtomicBoolean(false);
            subordinates.forEach(a -> {
                if (a != hero) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    //state[cell[0]][cell[1]] = 1;
                    if (cell[0] - 1 > 15 || cell[0] - 1 < 0 || cell[1] > 15 || cell[1] < 0) {
                        fail.set(true);
                        return;
                    }
                    state[(cell[1] * 8) + cell[0]] = 1;
                }
            });
            if (fail.get()) {
                return;
            }

            // Create a state for each action (N,S,E,W,St)
            //float[][][] futureStates = new float[5][16][16];
            float[][] futureStates = new float[5][256];
            for (int i = 0; i < 5; i++) {
                futureStates[i] = state.clone();
            }

            int[] heroCell = calculateEquivalentGridCell(hero.getCoordinate());
            boolean[] excluded = {false, false, false, false, false};
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

            float[] values = new float[5];
            int best = -1;
            float bestVal = -1000;
            for (int i = 0; i < 5; i++) {
                if (!excluded[i]) {
                    values[i] = compute(new Tensor(futureStates[i]));
                    if (values[i] > bestVal) {
                        bestVal = values[i];
                        best = i;
                    }
                }
            }

            // System we have our best value
            // TODO epsilon exploration

            // Record prediction

            // Make move


            boolean eps = Simulator.instance.getRandom().nextInt(5) == 0;
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
                bestVal = values[best];
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

            miniBuffer.add(new ShortExperienceRecord(state, bestVal, -1));

        }

        stepCount++;
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

    private void train() {
        List<ShortExperienceRecord> sample = sample();
        List<MLDataItem> dataItems = new ArrayList<>();
        for (ShortExperienceRecord e : sample) {
            MLDataItem item = new TabularDataSet.Item(new Tensor(e.inputState), new Tensor(e.jointReward / (xSteps*ySteps)));
            dataItems.add(item);
            //System.out.println("Expected = " + e.value + ", actual = " + (e.jointReward / (xSteps*ySteps)) + " diff = " + ((e.jointReward - e.value)/ (xSteps*ySteps)));
        }
        DataSet<MLDataItem> dataSet = new BasicDataSet<>(dataItems);
        qNetwork.train(dataSet);
        qNetwork.applyWeightChanges();
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

    private float compute(Tensor input) {
        qNetwork.setInput(input);
        return qNetwork.getOutput()[0];
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

    private FeedForwardNetwork createNetwork() {
        // TODO consider conv and max pooling if it works
        FeedForwardNetwork net = FeedForwardNetwork.builder()
                .addInputLayer(256)
                .addFullyConnectedLayer(256, ActivationType.LINEAR)
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
