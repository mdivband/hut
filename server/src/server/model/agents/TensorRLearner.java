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
    private static final int SAMPLE_SIZE = 10;
    private static final int BUFFER_SIZE = 100;
    private static final float LEARNING_RATE = 0.1f;
    private ConvolutionalNetwork qNetwork;
    private ShortExperienceRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;
    private int stepCount = 0;
    private boolean[] excluded;
    private ShortExperienceRecord lastRec;
    private boolean debug = false;

    public TensorRLearner(AgentProgrammed agent) {
        super(agent);
    }

    public void setup() {
        super.setup();
        qNetwork = createNetwork();
        buffer = new ShortExperienceRecord[BUFFER_SIZE];
        pointer = 0;
        counter = 0;
        xCell = 0;
        yCell = 0;
    }

    @Override
    public void complete() {

    }

    @Override
    public void step() {
        step(-1);
    }

    public void step(float jointReward, int epsilon) {

        stepCount++;
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
