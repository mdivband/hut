package server.model.agents;

import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.SupervisedLearning;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.ConvolutionalNetwork;
import org.neuroph.nnet.learning.ConvolutionalBackpropagation;

import java.util.*;

public class NewNetTester {


    public static void main(String[] args) {
        ConvolutionalNetwork convolutionNetwork = new ConvolutionalNetwork.Builder()
                .withInputLayer(2, 2, 1)
                .withConvolutionLayer(1, 1, 4)
                .withFullConnectedLayer(1)
                .build();

        convolutionNetwork.getLearningRule().setMaxIterations(1);
        //convolutionNetwork.setLearningRule(backPropagation);


        NewNetTester ntt = new NewNetTester();
        Random random = new Random();
        int counter = 0;
        while (true) {
            float[] state = new float[4];
            for (int i = 0; i < 4; i++) {
                state[i] = 0;
            }
            state[(random.nextInt(2) * 2) + random.nextInt(2)] = 1;
            state[(random.nextInt(2) * 2) + random.nextInt(2)] = 1;
            state[(random.nextInt(2) * 2) + random.nextInt(2)] = 1;
            state[(random.nextInt(2) * 2) + random.nextInt(2)] = 1;

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

            convolutionNetwork.setInput(inSt);
            convolutionNetwork.calculate();
            double res = convolutionNetwork.getOutput()[0];
            float actual = (ntt.calculateActualRewardFromArray(thisState) / 4f);
            System.out.println(Arrays.toString(inSt) + " -> A:" + (actual) + " -> " + res + " DELTA = " + (actual - res) + ", t= " + counter);

            org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(4, 1);
            DataSetRow item = new DataSetRow(inSt, new double[] {actual});
            ds.add(item);
            convolutionNetwork.learn(ds);
            counter++;

        }

    }

    public float calculateActualRewardFromArray(float[][] state) {
        List<int[]> agents = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                if (state[i][j] == 1) {
                    agents.add(new int[]{i, j});
                }
            }
        }

        int numPointsCovered = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int[] cell : agents) {
                    if (cell[0] == i && cell[1] == j) {
                        numPointsCovered++;
                        break;
                    }
                }

            }
        }

        return numPointsCovered;
    }
}
