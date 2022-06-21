package server.model.agents;

import deepnetts.net.ConvolutionalNetwork;
import deepnetts.net.NetworkType;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;

public class EvolutionaryAllocator extends LearningAllocator {
    private ConvolutionalNetwork network;

    private float LEARNING_RATE = 0.01f;

    public void setup() {
        xSteps = 64;
        ySteps = 64;
        maxReward = xSteps * ySteps;
        counter = 0;
        network = createNetwork();
        network.setInput(getState());
        System.out.println(network.getLayers().get(network.getLayers().size() - 1).getOutputs());
    }

    public void reset() {

    }

    public void step() {

    }

    private ConvolutionalNetwork createNetwork() {
        ConvolutionalNetwork net = ConvolutionalNetwork.builder()
                .addInputLayer(64, 64, 4)
                .addConvolutionalLayer(16, 16, 4)
                .addConvolutionalLayer(8, 8, 4)
                .addConvolutionalLayer(4, 4, 4)
                .addConvolutionalLayer(2, 2, 4)
                .addFullyConnectedLayer(128, ActivationType.LINEAR)
                .addConvolutionalLayer(64, 64, 4)
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

    private class Individual {
        private ConvolutionalNetwork network;






    }


}
