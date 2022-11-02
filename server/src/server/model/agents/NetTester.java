package server.model.agents;

import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.ConvolutionalNetwork;
import org.neuroph.nnet.learning.ConvolutionalBackpropagation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class NetTester {
    private ConvolutionalNetwork convolutionNetwork;

    public NetTester(ConvolutionalNetwork convolutionNetwork) {
        this.convolutionNetwork = convolutionNetwork;
    }

    public NetTester() {
        System.out.println("Building...");
        this.convolutionNetwork = new ConvolutionalNetwork.Builder()
                .withInputLayer(16, 16, 1)
                .withConvolutionLayer(1, 1,1)
                .withFullConnectedLayer(1)
                .build();


        convolutionNetwork.getLearningRule().setMaxIterations(1);
        //double LR = 0.0000000005d;
        convolutionNetwork.getLearningRule().setLearningRate(0.1f);;
        System.out.println("Net built");
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
                    if (cell[0] == i && cell[1] == j) {
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
        return test(runNumber, numTests, false);
    }

    public double test(int runNumber, int numTests, boolean debug) {
        List<Double> deltas = new ArrayList<>();
        Random random = new Random();
        for (int run=0; run<numTests; run++) {
            /*
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
             */
            float[] state = new float[256];
            for (int i = 0; i < 256; i++) {
                state[i] = 0;
            }

            for (int i = 0; i < 64; i++) {
                // For now, we pick a square and do the other three above, beside, and (positive) diagonally from them
                int x = random.nextInt(16);
                int y = random.nextInt(16);
                try {
                    state[(x * 16) + y] = 1;
                } catch (Exception ignored) {}
                try {
                    state[((x+1) * 16) + y] = 1;
                } catch (Exception ignored) {}
                try {
                    state[(x * 16) + y + 1] = 1;
                } catch (Exception ignored) {}
                try {
                    state[((x+1) * 16) + y + 1] = 1;
                } catch (Exception ignored) {}
            }

            float[][] thisState = new float[16][16];
            for (int j = 0; j < 16; j++) {
                float[] row = new float[256];
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
            // TODO print each of these. The results we get from the sample are near perfect but the test disagrees here?
            //System.out.println("---Run " + runNumber + "." + run + ": " + res + " (" + actual + ") " + " d = " + delta);
        }
        double av = (deltas.stream().mapToDouble(Double::doubleValue).average().getAsDouble());
        if (debug) {
            System.out.println("Run " + runNumber + " -> Average delta: " + av + " (scaled for 255 => " + (av * 255f) + ")");
        }
        return av;
        //System.out.println("Run " + runNumber + " -> Average delta: " + (deltas.stream().mapToDouble(Double::doubleValue).average().getAsDouble() * 255f));
    }

    public void staticTest() {
        Random random = new Random();
        int run=1;
        while (true) {
            float[] state = new float[256];
            for (int i = 0; i < 256; i++) {
                state[i] = 0;
            }
            for (int i = 0; i < 64; i++) {
                // For now, we pick a square and do the other three above, beside, and (positive) diagonally from them
                int x = random.nextInt(16);
                int y = random.nextInt(16);
                try {
                    state[(x * 16) + y] = 1;
                } catch (Exception ignored) {}
                try {
                    state[((x+1) * 16) + y] = 1;
                } catch (Exception ignored) {}
                try {
                    state[(x * 16) + y + 1] = 1;
                } catch (Exception ignored) {}
                try {
                    state[((x+1) * 16) + y + 1] = 1;
                } catch (Exception ignored) {}
            }


            float[][] thisState = new float[16][16];
            for (int j = 0; j < 16; j++) {
                float[] row = new float[256];
                System.arraycopy(state, j * 16, row, 0, 16);
                thisState[j] = row;
            }

            double[] inSt = new double[256];
            for (int i = 0; i < 256; i++) {
                inSt[i] = state[i];
            }
            float res = compute(inSt);
            float actual = (((calculateActualRewardFromArray(thisState) / 256f)));

            train(inSt, actual);

            if (run == 1_000_000) {//1_000_000) {
                System.out.println("TESTING");
                test(run, 100_000, true);
                break;
            } else if (run % 100_000 == 0) {
                test(run, 10_000, true);
                //System.out.println("Expected = " + res);
                //System.out.println("Actual   = " + actual);
                //System.out.println("Training Delta    = " + (actual - res));
            }
            run++;

        }
    }

    public static void main(String[] args) {
        NetTester nt = new NetTester();
        nt.staticTest();

    }




}
