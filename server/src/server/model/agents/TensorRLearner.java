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

public class TensorRLearner extends LearningAllocator {
    private static final float GAMMA = 0.9f;  // TODO trying gamma=1 might help?
    private static final int SAMPLE_SIZE = 4;
    private static final float LEARNING_RATE = 0.01f;
    private static final int BUFFER_SIZE = 4;
    private ConvolutionalNetwork qNetwork;
    private ExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int stepCount = 0;

    private ArrayList<ExperienceRecord> miniBuffer = new ArrayList<>();

    public TensorRLearner(AgentProgrammed agent) {
        super(agent);
    }

    public void setup() {
        super.setup();
        xSteps = 16;
        ySteps = 16;
        maxReward = 64 * 64;
        qNetwork = createNetwork();
        buffer = new ExperienceRecord[BUFFER_SIZE];
        pointer = 0;
        counter = 0;
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
        /*
        if (Simulator.instance.getStepCount() > 2000) {
            qNetwork.getTrainer().setLearningRate(0.001f);
            System.out.println("SWITCH");
        }

         */

        /*
        boolean perform = false;
        switch (getLevel()) {
            case 0 -> perform = false;
            case 1 -> perform = true;
            case 2 -> {
                if (stepCount % 3 == 0) {
                    perform = true;
                }
            }
            case 3 -> {
                if (stepCount % 8 == 0) {
                    perform = true;
                }
            } default -> {
                if (stepCount % 24 == 0) {
                    perform = true;
                }
            }
        }

         */
        boolean perform = true;
        if (perform) {
            if (!miniBuffer.isEmpty()) {
                for (ExperienceRecord e : miniBuffer) {
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

            boolean eps = Simulator.instance.getRandom().nextInt(5) == 0;


            // Map the tensor of layers (16x16x3) to a best response (256 -> 16x16)
            float[][][] config = new float[4][16][16];
            List<Coordinate> moves = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                float[][][] state = new float[3][16][16];
                System.arraycopy(config, 0, state, 0, i);
                for (int d = i; d < 3; d++) {
                    float[][] input = allZeros();
                    state[d] = input;
                }
                float[] result = compute(new Tensor(state));
                int[] best;
                if (!eps) {
                    best = calculateBestCoord(result);
                    config[i] = allZeros();
                    config[i][best[0]][best[1]] = 1f;
                    // ^ Placed a 1 in the cell used
                    moves.add(calculateEquivalentCoordinate(best[0], best[1]));
                } else {
                    best = new int[]{Simulator.instance.getRandom().nextInt(16), Simulator.instance.getRandom().nextInt(16)};
                    moves.add(calculateEquivalentCoordinate(best[0], best[1]));
                }
                miniBuffer.add(new ExperienceRecord(state, result, best[0] * 16 + best[1], -1));
            }

            for (int i = 0; i < subordinates.size(); i++) {
                AgentProgrammed ap = subordinates.get(i);
                ap.programmerHandler.manualSetTask(moves.get(i));
            }
        }


        stepCount++;
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

    private void train() {
        List<ExperienceRecord> sample = sample();
        List<MLDataItem> dataItems = new ArrayList<>();
        for (ExperienceRecord e : sample) {
            float[] results = e.actionValues.clone();
            results[e.indexTaken] = (e.jointReward / (64*64));
            MLDataItem item = new TabularDataSet.Item(new Tensor(e.inputState), new Tensor(results));
            dataItems.add(item);
        }
        DataSet<MLDataItem> dataSet = new BasicDataSet<>(dataItems);

        /*
        float[][][] state = new float[3][8][8];
        for (int d = 0; d < 3; d++) {
            float[][] input = allZeros();
            state[d] = input;
        }
         */
        //int[] before = calculateBestCoord(compute(new Tensor(state)));
        qNetwork.train(dataSet);
        qNetwork.applyWeightChanges();
        //int[] after = calculateBestCoord(compute(new Tensor(state)));
        /*
        if (!Arrays.equals(after, before)) {
            System.out.println("Before: " + Arrays.toString(before));
            System.out.println("After:  " + Arrays.toString(after));
        }
         */
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

    private float[] compute(Tensor input) {
        qNetwork.setInput(input);
        return qNetwork.getOutput();
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
        // TODO consider conv and max pooling if it works
        ConvolutionalNetwork net = ConvolutionalNetwork.builder()
                .addInputLayer(16, 16, 3)
                .addConvolutionalLayer(1, 1, 3)
                .addMaxPoolingLayer(1, 1, 3)
                .addFullyConnectedLayer(256, ActivationType.LINEAR)
                .addOutputLayer(256, ActivationType.LINEAR)
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .randomSeed(Simulator.instance.getRandom().nextInt(9999))
                .build();

        net.getTrainer()
                .setOptimizer(OptimizerType.SGD)
                .setBatchSize(50)
                .setBatchMode(true)
                .setMaxError(999999)
                .setLearningRate(LEARNING_RATE);

        return net;
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


}
