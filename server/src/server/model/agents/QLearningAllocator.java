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

public class QLearningAllocator extends LearningAllocator {
    private static final int TRAIN_FREQUENCY = 1;
    private static final float GAMMA = 0.9f;  // TODO trying gamma=1 might help?
    private static final int SAMPLE_SIZE = 50;
    private static final float LEARNING_RATE = 0.0001f;
    private static final int BUFFER_SIZE = 1000;
    private ConvolutionalNetwork qNetwork;
    private ConvolutionalNetwork targetNetwork;
    private MissionProgrammer.ExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int stateSize;

    public void setup() {
        super.setup();
        xSteps = 64;
        ySteps = 64;
        maxReward = xSteps * ySteps;
        buffer = new MissionProgrammer.ExperienceRecord[BUFFER_SIZE];
        qNetwork = createNetwork();
        copyNets();  // Sets target network
        pointer = 0;
        counter = 0;
    }

    public void reset() {
        copyNets();
        pointer = 0;
        counter = 0;
        buffer = new MissionProgrammer.ExperienceRecord[BUFFER_SIZE];
    }

    @Override
    public void complete() {

    }

    public void step() {
        qLearningStep();
    }

    private ConvolutionalNetwork createNetwork() {

        ConvolutionalNetwork net = ConvolutionalNetwork.builder()
                .addInputLayer(64, 64, 4)
                .addConvolutionalLayer(16, 16, 4,  4, ActivationType.LINEAR)
                .addConvolutionalLayer(8, 8, 4,  4, ActivationType.LINEAR)
                .addConvolutionalLayer(4, 4, 4,  4, ActivationType.LINEAR)
                .addFullyConnectedLayer(256, ActivationType.LINEAR)
                .addOutputLayer(8, ActivationType.LINEAR)
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
        float negReward = 0;
        float prevReward = calculateReward();
        Tensor inputTensor = getState();
        float[] output = compute(inputTensor);
        int move;

        float epsBound = 0.1f;
        if (((AgentHubProgrammed) (Simulator.instance.getState().getHub())).missionProgrammer.getRunCounter() > 5) {
            epsBound = 0.2f;
        }
        if (((AgentHubProgrammed) (Simulator.instance.getState().getHub())).missionProgrammer.getRunCounter() > 10) {
            epsBound = 0.4f;
        }
        if (((AgentHubProgrammed) (Simulator.instance.getState().getHub())).missionProgrammer.getRunCounter() > 15) {
            epsBound = 0.6f;
        }
        if (((AgentHubProgrammed) (Simulator.instance.getState().getHub())).missionProgrammer.getRunCounter() > 20) {
            epsBound = 0.8f;
        }
        if (((AgentHubProgrammed) (Simulator.instance.getState().getHub())).missionProgrammer.getRunCounter() > 30) {
            epsBound = 0.9f;
        }

        if (Simulator.instance.getRandom().nextDouble() < epsBound) {
            float maxVal = -999999;
            move = 4;
            for (int i = 0; i < output.length; i++) {
                if (output[i] > maxVal) {
                    maxVal = output[i];
                    move = i;
                }
            }
        } else {
            // EPSILON EXPLORATION
            move = Simulator.instance.getRandom().nextInt(output.length);
        }

        int[] moves = decodeMove(move);
        for (int i = 0; i < subordinates.size(); i++) {
            AgentProgrammed ap = subordinates.get(i);
            if (!ap.programmerHandler.gridMove(moves[i])) {
                negReward = -1000f;
            }
        }

        Tensor result = getState();

        //}
        // Balance to max reward and subtract up to 1 from this
        float newReward = calculateReward();
        float jointReward = ((newReward - prevReward) / maxReward) - (negReward / 4f);
        //for (int i=0; i<4; i++) {
            //buffer[pointer] = new MissionProgrammer.ExperienceRecord(inputs[i], outputs[i], moves[i], jointReward, results[i]);
        buffer[pointer] = new MissionProgrammer.ExperienceRecord(inputTensor, output, move, jointReward, result);
        pointer++;
        if (pointer >= BUFFER_SIZE) {
            pointer = 0;
            bufferFull = true;
        }
        //}
        if (counter % TRAIN_FREQUENCY == 0) {
            train();
        }

        counter++;
        if (counter >= 20) {
            copyNets();
            counter = 0;
        }
    }

    private int[] decodeMove(int move) {
        int[] moves = new int[4];
        String code = (Integer.toString(Integer.parseInt(String.valueOf(move), 10), 5));

        int index = 0;
        for (char c : code.toCharArray()) {
            int thisMove = Integer.parseInt(String.valueOf(c));
            moves[index] = thisMove;
            index++;
        }
        return moves;
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
            for (int i = 0; i < e.actionValues.length; i++) {
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
        qNetwork.train(dataSet);
        qNetwork.applyWeightChanges();
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

}
