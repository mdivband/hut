package server.model.agents;

import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.SupervisedLearning;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.ConvolutionalNetwork;
import org.neuroph.nnet.learning.ConvolutionalBackpropagation;

import java.util.*;

public class NewNetTester {
    public static final double LEARNING_RATE = 0.05d;


    public static void main(String[] args) {
        ConvolutionalNetwork convolutionNetwork = new ConvolutionalNetwork.Builder()
                .withInputLayer(16, 16, 1)
                .withConvolutionLayer(5, 5, 121)
                .withPoolingLayer(5,5)
                .withFullConnectedLayer(1)
                .build();

        convolutionNetwork.getLearningRule().setMaxIterations(1);
        convolutionNetwork.getLearningRule().setLearningRate(LEARNING_RATE);
        //convolutionNetwork.setLearningRule(backPropagation);

        System.out.println("0 layer LR = " + LEARNING_RATE);

        NewNetTester ntt = new NewNetTester();
        Random random = new Random();
        int counter = 0;
        List<Double> scores = null;
        while (true) {
            if (counter % 10_000 == 0) {
                scores = new ArrayList<>();
            }
            float[] state = new float[256];
            for (int i = 0; i < 256; i++) {
                state[i] = 0;
            }
            state[(random.nextInt(16) * 16) + random.nextInt(16)] = 1;
            state[(random.nextInt(16) * 16) + random.nextInt(16)] = 1;
            state[(random.nextInt(16) * 16) + random.nextInt(16)] = 1;
            state[(random.nextInt(16) * 16) + random.nextInt(16)] = 1;

            float[][] thisState = new float[16][16];
            for (int j = 0; j < 16; j++) {
                float[] row = new float[16];
                System.arraycopy(state, j * 16, row, 0, 16);
                thisState[j] = row;
            }

            double[] inSt = new double[256];
            for (int i = 0; i < 256; i++) {
                inSt[i] = state[i];
            }

            convolutionNetwork.setInput(inSt);
            convolutionNetwork.calculate();
            double res = convolutionNetwork.getOutput()[0];
            float actual = (ntt.calculateActualRewardFromArray(thisState) / 256f);
           // System.out.println(" -> A:" + (actual) + " -> " + res + " DELTA (scaled) = " + (256 * (actual - res)) + ", t= " + counter);
            if (counter % 10_000 < 100) {
                scores.add(Math.abs(256 * (actual - res)));
            }
            if (counter % 10_000 == 100) {
                System.out.println(counter + ": av delta = " + (scores.stream().mapToDouble(Double::doubleValue).average().getAsDouble()));
            }
            org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(256, 1);
            DataSetRow item = new DataSetRow(inSt, new double[] {actual});
            ds.add(item);
            convolutionNetwork.learn(ds);
            counter++;

        }

    }

    public float calculateActualRewardFromArray(float[][] state) {
        int numPointsCovered = 0;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                boolean agentHere = false;
                for (int y = Math.max(0, i-4); y < Math.min(i+4, 16); y++) {
                    for (int x = Math.max(0, j-4); x < Math.min(j+4, 16); x++) {
                        if (state[y][x] == 1) {
                            agentHere = true;
                            break;
                        }
                    }
                    if (agentHere) {
                        break;
                    }
                }
                if (agentHere) {
                    numPointsCovered++;
                }
            }

        }

        return numPointsCovered;
    }
}
