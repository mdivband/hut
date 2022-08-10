package server.model.agents;

import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.ConvolutionalNetwork;
import org.neuroph.nnet.learning.ConvolutionalBackpropagation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NetTester {
    private ConvolutionalNetwork convolutionNetwork;

    public NetTester(ConvolutionalNetwork convolutionNetwork) {
        this.convolutionNetwork = convolutionNetwork;
    }

    private float compute(double[] input) {
        convolutionNetwork.setInput(input);
        convolutionNetwork.calculate();
        double[] networkOutputOne = convolutionNetwork.getOutput();
        return (float) networkOutputOne[0];
    }

    private float calculateActualRewardFromArray(float[][] state) {
        List<int[]> agents = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (state[i][j] == 1) {
                    agents.add(new int[]{i, j});
                }
            }
        }

        int numPointsCovered = 0;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
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

    private void train(double[] inSt, double actual) {
        org.neuroph.core.data.DataSet ds = new org.neuroph.core.data.DataSet(256, 1);
        DataSetRow item = new DataSetRow(inSt, new double[] {actual});
        ds.add(item);
        convolutionNetwork.learn(ds);
    }

    public double test(int runNumber, int numTests) {
        List<Double> deltas = new ArrayList<>();
        Random random = new Random();
        for (int run=0; run<numTests; run++) {
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
            float res = compute(inSt);
            float actual = (((calculateActualRewardFromArray(thisState) / 256f)));
            double delta = actual - res;
            deltas.add(delta);
        }
        System.out.println("Run " + runNumber + " -> Average delta: " + (deltas.stream().mapToDouble(Double::doubleValue).average().getAsDouble() * 256f));
        return (deltas.stream().mapToDouble(Double::doubleValue).average().getAsDouble() * 256f);
        //System.out.println("Run " + runNumber + " -> Average delta: " + (deltas.stream().mapToDouble(Double::doubleValue).average().getAsDouble() * 255f));
    }

    public static void main(String[] args) {
        /*
        NetTester netTester = new NetTester();
        Random random = new Random();
        int run=0;
        while (true) {
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
            float res = netTester.compute(inSt);
            float actual = (netTester.calculateActualRewardFromArray(thisState) / 256f);

            netTester.train(inSt, actual);

            if (run == 1000000) {
                netTester.test(run, 10000);
                break;
            } else if (run % 10000 == 0) {
                netTester.test(run, 100);
                //System.out.println("Expected = " + res);
                //System.out.println("Actual   = " + actual);
                //System.out.println("Delta    = " + (actual - res));
            }




            run++;

        }

         */
    }




}
