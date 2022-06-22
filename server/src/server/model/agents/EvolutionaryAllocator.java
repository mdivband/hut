package server.model.agents;

import deepnetts.data.MLDataItem;
import deepnetts.data.TabularDataSet;
import deepnetts.net.ConvolutionalNetwork;
import deepnetts.net.NetworkType;
import deepnetts.net.layers.AbstractLayer;
import deepnetts.net.layers.ConvolutionalLayer;
import deepnetts.net.layers.FullyConnectedLayer;
import deepnetts.net.layers.OutputLayer;
import deepnetts.net.layers.activation.ActivationType;
import deepnetts.net.loss.LossType;
import deepnetts.net.train.opt.OptimizerType;
import deepnetts.util.Tensor;
import server.Simulator;
import server.model.Coordinate;

import javax.visrec.ml.data.BasicDataSet;
import javax.visrec.ml.data.DataSet;
import java.util.*;

public class EvolutionaryAllocator extends LearningAllocator {
    private final ArrayList<Individual> population = new ArrayList<>();;

    private float LEARNING_RATE = 0.001f;
    private final int BUFFER_SIZE = 100;
    private final int SAMPLE_SIZE = 20;
    private int populationSize = 100;

    private ArrayList<Integer> indexHistory = new ArrayList<>();

    private EvoExpRecord[] buffer;
    private boolean bufferFull = false;
    private int pointer;

    public void setup() {
        super.setup();
        xSteps = 8;
        ySteps = 8;
        maxReward = xSteps * ySteps;
        counter = 0;
        buffer = new EvoExpRecord[BUFFER_SIZE];
        initialisePopulation();
    }

    public void complete() {
        System.out.println("COMPLETE - mean fitness = " + population.stream().mapToDouble(p -> p.fitness).average().getAsDouble());

        ArrayList<Float> rewards = new ArrayList<>();
        System.out.println("top100: ");
        for (Individual p : population) {
            List<Coordinate> config = compute(p);
            for (int i = 0; i < subordinates.size(); i++) {
                AgentProgrammed ap = subordinates.get(i);
                ap.programmerHandler.manualSetTask(config.get(i));
                ap.step(true);
            }

            // RECORD REWARD AND STORE
            float reward = calculateReward();
            rewards.add(reward);
            System.out.print(reward+",");
        }
        System.out.println("mean reward of top 100 =  " + rewards.stream().mapToDouble(d -> d).average().getAsDouble());
    }

    public void performBest() {
        Individual best = population.get(0);
        List<Coordinate> config = compute(best);

        // PERFORM ACTION
        for (int i = 0; i < subordinates.size(); i++) {
            AgentProgrammed ap = subordinates.get(i);
            ap.programmerHandler.manualSetTask(config.get(i));
            ap.step(true);
        }

    }

    private void initialisePopulation() {
        float fitnessCounter = 1;
        for (int i=0; i<populationSize;i++) {
            ConvolutionalNetwork net = createNetwork();
            population.add(new Individual(net, fitnessCounter));
            fitnessCounter-=0.01f;
        }
    }

    public void step() {
        // SELECT 2 AND X-OVER
        int index1 = rankOrderSelection();
        Individual parent1 = population.get(index1);
        int index2 = rankOrderSelection();
        Individual parent2 = population.get(index2);

        Individual child = crossover(parent1, parent2);
        mutate(child);
        // MAYBE TRAIN ON MINI-BATCH
        train(child.net);

        // SELECT ACTION USING THIS INDIVIDUAL
        List<Coordinate> config = compute(child);

        // PERFORM ACTION
        for (int i = 0; i < subordinates.size(); i++) {
            AgentProgrammed ap = subordinates.get(i);
            ap.programmerHandler.manualSetTask(config.get(i));
            ap.step(true);
        }

        // RECORD REWARD AND STORE
        float reward = calculateReward();
        child.fitness = reward;
        // Store if better than current buffer average
        // TODO convert to inductive mean
        if (bufferFull) {
            if (reward > Arrays.stream(buffer).mapToDouble(evoExpRecord -> (double) evoExpRecord.reward).average().getAsDouble()) {
                buffer[pointer] = new EvoExpRecord(getState(), reward);
                pointer++;
                if (pointer >= BUFFER_SIZE) {
                    pointer = 0;
                    bufferFull = true;
                }
            }
        } else {
            buffer[pointer] = new EvoExpRecord(getState(), reward);
            pointer++;
            if (pointer >= BUFFER_SIZE) {
                pointer = 0;
                bufferFull = true;
            }
        }

        int indexToReplace = populationSize - rankOrderSelection() - 1;
        synchronized (population) {
            population.remove(indexToReplace);
            population.add(child);
            // Use an inverse comparator for descending sort (so first elems are best)
            population.sort(new Comparator<>() {
                @Override
                public int compare(Individual i1, Individual i2) {
                    if (i1.fitness.equals(i2.fitness)) {
                        return 0;
                    } else if (i1.fitness > i2.fitness) {
                        return -1;
                    } else {
                        return 1;
                    }
                }
            });
        }


    }

    private void train(ConvolutionalNetwork network) {
        // TODO we have to manually check due to this boolean unusually returning true whilst false (probably threading
        //  or branch prediction caused?)
        synchronized (this) {
            if (bufferFull && Arrays.stream(buffer).noneMatch(Objects::isNull)) {
                qTrain(network, sample());
            }
        }
    }

    private List<EvoExpRecord> sample() {
        List<EvoExpRecord> sample = new ArrayList<>();
        while (sample.size() < SAMPLE_SIZE) {
            EvoExpRecord e = buffer[Simulator.instance.getRandom().nextInt(BUFFER_SIZE)];
            if (!sample.contains(e)) {
                sample.add(e);
            }
        }
        return sample;
    }

    private void qTrain(ConvolutionalNetwork net, List<EvoExpRecord> sample) {
        List<MLDataItem> dataItems = new ArrayList<>();
        Tensor exampleSeed = computeNoise();
        for (EvoExpRecord e : sample) {
            Tensor n = computeNoise();
            Tensor s = flatten(e.state);
            MLDataItem item = new TabularDataSet.Item(n, s);
            dataItems.add(item);
        }
        DataSet<MLDataItem> dataSet = new BasicDataSet<>(dataItems);
        net.train(dataSet);
        net.applyWeightChanges();
    }

    private Individual crossover(Individual parent1, Individual parent2) {
        ConvolutionalNetwork child = createNetwork();
        for (int i=0; i<parent1.net.getLayers().size(); i++) {
            AbstractLayer layer1 = parent1.net.getLayers().get(i);
            AbstractLayer layer2 = parent2.net.getLayers().get(i);
            // Ensure not a convolutional layer (no weights)
            if (layer1 instanceof FullyConnectedLayer || layer1 instanceof OutputLayer) {
                float[] w1 = layer1.getWeights().getValues();
                float[] b1 = layer1.getBiases();
                float[] w2 = layer2.getWeights().getValues();
                float[] b2 = layer2.getBiases();
                float[] w3 = new float[w1.length];
                float[] b3 = new float[b1.length];
                int index1 = Simulator.instance.getRandom().nextInt(w1.length);
                int index2 = Simulator.instance.getRandom().nextInt(b1.length);
                System.arraycopy(w1, 0, w3, 0, index1);
                if (w2.length - index1 >= 0) System.arraycopy(w2, index1, w3, index1, w2.length - index1);
                System.arraycopy(b1, 0, b3, 0, index2);
                if (b2.length - index2 >= 0) System.arraycopy(b2, index2, b3, index2, b2.length - index2);
                child.getLayers().get(i).getWeights().setValues(w3);
                child.getLayers().get(i).setBiases(b3);

            } else if (layer1 instanceof ConvolutionalLayer) {
                Tensor[] filters1 = ((ConvolutionalLayer) layer1).getFilters();
                Tensor[] filters2 = ((ConvolutionalLayer) layer2).getFilters();
                Tensor[] filters3 = new Tensor[filters1.length];
                int index = Simulator.instance.getRandom().nextInt(filters1.length);
                for (int j=0; j<index; j++) {
                    filters3[j] = filters1[j].copy();
                }
                for (int j=index; j< filters2.length; j++) {
                    filters3[j] = filters2[j].copy();
                }
                ((ConvolutionalLayer) child.getLayers().get(i)).setFilters(filters3);
            }
        }

        return new Individual(child, -1f);
    }


    private void mutate(Individual child) {
        for (int i=0; i<child.net.getLayers().size(); i++) {
            AbstractLayer layer = child.net.getLayers().get(i);
            if (layer instanceof FullyConnectedLayer || layer instanceof OutputLayer) {
                float[] values = layer.getWeights().getValues();
                for (int j = 0; j < values.length; j++) {
                    if (Simulator.instance.getRandom().nextInt(100) == 0) {
                        layer.getWeights().getValues()[j] = (Simulator.instance.getRandom().nextFloat() / 10);
                    }
                }
                float[] biases = layer.getBiases();
                for (int j = 0; j < biases.length; j++) {
                    if (Simulator.instance.getRandom().nextInt(100) == 0) {
                        layer.getBiases()[j] = Simulator.instance.getRandom().nextFloat();
                    }
                }
            } else if (layer instanceof ConvolutionalLayer cl) {
                for(Tensor t : cl.getFilters()) {
                    if (Simulator.instance.getRandom().nextInt(100) == 0) {
                        t.randomize();
                    }
                }
            }
        }
    }

    private Tensor computeNoise() {
        float[][][] noise = new float[4][8][8];
        for (int d=0; d<4; d++) {
            for (int i=0; i<8; i++) {
                for (int j=0; j<8; j++) {
                    noise[d][i][j] = Simulator.instance.getRandom().nextFloat();
                }
            }
        }
        return new Tensor(noise);
    }

    private List<Coordinate> compute(ConvolutionalNetwork net, Tensor inputSeed) {
        net.setInput(inputSeed);
        float[] output = net.getOutput();
        int[][] bestCells = new int[4][2];
        float[] bestValues = {-1f, -1f, -1f, -1f};

        for (int d=0; d<4; d++) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    int equivalentFlatIndex = d*64 + i*8 + j;
                    float value = output[equivalentFlatIndex];
                    if (value > bestValues[d]) {
                        bestValues[d] = value;
                        bestCells[d] = new int[]{i, j};
                    }
                }
            }
        }

        List<Coordinate> coords = new ArrayList<>();
        for (int[] cell : bestCells) {
            coords.add(calculateEquivalentCoordinate(cell[0], cell[1]));
        }
        return coords;
    }

    private List<Coordinate> compute(Individual selected) {
        return compute(selected.net, computeNoise());
    }

    private Tensor flatten(Tensor state) {
        float[] output = new float[256];
        for (int d=0; d<4; d++) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    int equivalentFlatIndex = d*64 + i*8 + j;
                    float value = state.get(i, j, d);
                    output[equivalentFlatIndex] = value;
                }
            }
        }
        return new Tensor(output);
    }

    private int rankOrderSelection() {
        // TODO this isn't quite lined up right but approximately gives the right distribution
        // By Gauss, S = n⁄2 (a + L)
        // treat as 1 indexed -> 1-100
        int max = ((populationSize) / 2) * (populationSize + 1);
        int targetIndex = 1 + Simulator.instance.getRandom().nextInt(max);
        int i = 1;
        int counter = populationSize;
        int intervalWidth = populationSize;
        while (counter < targetIndex) {
            intervalWidth--;
            counter+=intervalWidth;
            i++;

        }
        return i-1;
    }

    private ConvolutionalNetwork createNetwork() {
        ConvolutionalNetwork net = ConvolutionalNetwork.builder()
                .addInputLayer(8, 8, 4)
                .addConvolutionalLayer(8, 8 , 1, 4)
                .addConvolutionalLayer(4, 4 , 1, 4)
                .addConvolutionalLayer(2, 2, 1, 4)
                .addFullyConnectedLayer(256, ActivationType.LINEAR)
                .addOutputLayer(256, ActivationType.LINEAR)
                .randomSeed(Simulator.instance.getRandom().nextInt(99999))
                .lossFunction(LossType.MEAN_SQUARED_ERROR)
                .build();

        net.getTrainer()
                .setOptimizer(OptimizerType.SGD)
                .setBatchSize(99999)
                .setBatchMode(false)
                .setMaxError(9999999)
                .setLearningRate(LEARNING_RATE);

        return net;
    }

    public float calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        int numPointsCovered = 0;
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(i, j);
                for (Agent a : subordinates) {
                    //if (!(a instanceof Hub)) {
                    Coordinate coord = a.getCoordinate();
                    if (equiv.getDistance(coord) < 250) {
                        // This square's centre is in range of an agent
                        numPointsCovered++;
                        break;
                    }
                    //}
                }
            }
        }
        return numPointsCovered;

    }

    private class Individual {
        protected ConvolutionalNetwork net;
        protected Float fitness;

        public Individual(ConvolutionalNetwork net, Float fitness) {
            this.net = net;
            this.fitness = fitness;
        }

        @Override
        public String toString() {
            return "i{f=" + fitness + '}';
        }
    }

    private class EvoExpRecord {
        protected Tensor state;
        protected float reward;

        public EvoExpRecord(Tensor state, float reward) {
            this.state = state;
            this.reward = reward;
        }
    }

}
