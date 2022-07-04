package server.model.agents;

import deepnetts.util.Tensor;
import server.Simulator;
import server.model.Coordinate;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * This is essentially a hub-specific programmer. Use this to setup the mission and rewards etc.
 * We are using it hard-coded as a coverage task for now. In future we should maybe generify a bit more
 */
public class MissionProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    private final int NUM_STEPS_PER_EPOCH = 300;
    public static final int WIDTH = 4;

    private AgentHubProgrammed hub;
    private ProgrammerHandler programmerHandler;
    private List<AgentProgrammed> agents;

    //TODO these values can be passed through the AgentHubProgrammed and therefore can be scenario file defined
    private Coordinate botLeft;
    private Coordinate topRight;
    protected int xSteps = 64;
    protected int ySteps = 64;
    private double X_SPAN = 0.01;
    private double Y_SPAN = 0.006;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;
    private int runCounter = 0;
    private boolean bufferFull = false;
    private int pointer = 0;
    private int counter;
    private int stepCounter = 0;
    private ArrayList<Double> scores = new ArrayList<>();
    private ArrayList<Long> times = new ArrayList<>();

    private int stateSize;
    private boolean ready = false;
    private long epochStartTime;
    private boolean set = false;
    private AgentHierarchy hierarchy = null;

    public MissionProgrammer(AgentHubProgrammed ahp) {
        hub = ahp;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {agents.add(ap);}});
    }

    public void step() {
        if (!ready) {
            groupSetup();
        } else {
            if (stepCounter < NUM_STEPS_PER_EPOCH) {
                groupStep();
                if (agents.stream().allMatch(Agent::isStopped)) {
                    groupLearningStep();
                    if (stepCounter % (NUM_STEPS_PER_EPOCH / 10) == 0) {
                        System.out.print((stepCounter / (NUM_STEPS_PER_EPOCH / 100)) + ">");
                    }

                    stepCounter++;
                    double r = calculateReward();
                    scores.add(r);
                    synchronized (this) {
                        //times.add(epochDuration);
                        double sum = 0;
                        for (int i = Math.max(0, scores.size() - 100); i < scores.size(); i++) {
                            sum += scores.get(i);
                        }
                        double mvAv = sum / Math.min(scores.size(), 100);
                        DecimalFormat f = new DecimalFormat("##.00");

                        File csvOutputFile = new File("IncNewArchl001.csv");
                        try {
                            FileWriter fw = new FileWriter(csvOutputFile, true);
                            fw.write(//runCounter
                                    r
                                    + ", " + f.format(mvAv)
                                    //+ ", " + epochDuration
                                    + " \n");
                            fw.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } else {
                // SOFT RESET
                /*
                agents.forEach(a -> {
                    if (!a.programmerHandler.getAgentProgrammer().getSubordinates().isEmpty()) {
                        ((EvolutionaryAllocator) a.programmerHandler.getAgentProgrammer().getLearningAllocator()).performBest();
                    }
                });

                 */

                long epochDuration = System.currentTimeMillis() - epochStartTime;
                epochStartTime = System.currentTimeMillis();

                DecimalFormat f = new DecimalFormat("##.00");
                double sum = 0;
                for (int i = Math.max(0, scores.size() - 100); i < scores.size(); i++) {
                    sum += scores.get(i);
                }
                double mvAv = sum / Math.min(scores.size(), 100);
                System.out.println(
                        //"run = " + runCounter
                        "steps = " + (stepCounter * runCounter)
                                + ", moving average = " + f.format(mvAv)
                                + ", epoch time = " + (epochDuration) + "ms"
                                + ", num agents = " + agents.size()
                );

                /*
                double r = calculateReward();
                scores.add(r);
                synchronized (this) {
                    long epochDuration = System.currentTimeMillis() - epochStartTime;
                    epochStartTime = System.currentTimeMillis();
                    times.add(epochDuration);
                    double sum = 0;
                    for (int i = Math.max(0, scores.size() - 10); i < scores.size(); i++) {
                        sum += scores.get(i);
                    }
                    double mvAv = sum / Math.min(scores.size(), 10);

                    DecimalFormat f = new DecimalFormat("##.00");
                    System.out.println(
                        "run = " + runCounter
                        + ", steps = " + (stepCounter*runCounter)
                        + ", reward = " + r
                        + ", total average = " + f.format(scores.stream().mapToDouble(Double::doubleValue).average().getAsDouble())
                        + ", moving average = " + f.format(mvAv)
                        + ", epoch time = " + (epochDuration) + "ms"
                        + ", num agents = " + agents.size()
                    );


                    agents.forEach(a -> {
                        if (a.programmerHandler.getAgentProgrammer().getLevel() == 1) {
                            a.programmerHandler.getAgentProgrammer().getLearningAllocator().complete();
                        }
                    });



                    File csvOutputFile = new File("noBatchl1.csv");
                    try {
                        FileWriter fw = new FileWriter(csvOutputFile, true);
                        fw.write(runCounter
                                + ", " + r
                                + ", " + f.format(scores.stream().mapToDouble(Double::doubleValue).average().getAsDouble())
                                + ", " + f.format(mvAv)
                                + ", " + epochDuration
                                + " \n");
                        fw.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }


                     */
                    Simulator.instance.softReset(this);  // This soft resets all agents
                    agents.clear();
                    Simulator.instance.getState().getAgents().forEach(a -> {
                        if (a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {
                            agents.add(ap);
                        }
                    });
                    agents.forEach(a -> {
                        if (!a.programmerHandler.getAgentProgrammer().getSubordinates().isEmpty()) {
                            a.setCoordinate(new Coordinate(50.9289, -1.409));
                            a.programmerHandler.getAgentProgrammer().getLearningAllocator().reset();
                        }
                    });

                    addAgentIfRequired();

                    runCounter++;
                    stepCounter = 0;

                    Simulator.instance.startSimulation();
                }

        }
    }


    private void regenerateHierarchy() {
        List<List<AgentProgrammed>> layers = hierarchy.layers;
        List<AgentProgrammed> top = layers.get(layers.size() - 1); // This is necessarily a singleton in current config
        top.forEach(a -> a.programmerHandler.getAgentProgrammer().getLearningAllocator().setLevel(layers.size() - 1));
        for (int l = layers.size() - 2; l >= 0; l--) {
            int c = 0;
            for (AgentProgrammed a : layers.get(l)) {
                top.get(c).programmerHandler.getAgentProgrammer().getLearningAllocator().addSubordinate(a);
                a.programmerHandler.getAgentProgrammer().getLearningAllocator().setSupervisor(top.get(c));
                a.setCoordinate(new Coordinate(50.9289, -1.409));
                a.programmerHandler.getAgentProgrammer().getLearningAllocator().setLevel(l);
                c++;
                if (c >= top.size()) {
                    c = 0;
                }
            }
            top = layers.get(l);
        }
        updateBounds();
    }

    private void addAgentIfRequired() {
        //if (runCounter > 1 && runCounter % 10 == 0 && agents.size() < 341) {
        if (agents.size() < 341) {
            int numToAdd = (int) Math.pow(4, hierarchy.layers.size());
            for (int i=0; i<numToAdd; i++) {
                if (agents.size() < 341) {
                    AgentProgrammed ap = (AgentProgrammed) Simulator.instance.getAgentController().addProgrammedAgent(
                            50.9289,
                            -1.409,
                            0);

                    agents.add(ap);
                    ap.programmerHandler.getAgentProgrammer().setupAllocator();
                    agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                    hierarchy.addAgent(ap);
                } else {
                    agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                }
            }
            regenerateHierarchy();
        } else {
            agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
            regenerateHierarchy();
        }

    }

    private void updateBounds() {
        double topBound = hierarchy.getRoot().getCoordinate().getLatitude() + ((5 * Y_SPAN) / 2);
        double botBound = hierarchy.getRoot().getCoordinate().getLatitude() - ((5 * Y_SPAN) / 2);
        double rightBound = hierarchy.getRoot().getCoordinate().getLongitude() + ((5 * X_SPAN) / 2);
        double leftBound = hierarchy.getRoot().getCoordinate().getLongitude() - ((5 * X_SPAN) / 2);

        botLeft = new Coordinate(botBound, leftBound);
        topRight = new Coordinate(topBound, rightBound);

        xSquareSpan = (5 * X_SPAN) / xSteps;
        ySquareSpan = (5 * Y_SPAN) / ySteps;
        cellWidth = (float) ((xSquareSpan * 111111));

    }

    private void initialiseLearningAllocators() {
        agents.forEach(a -> a.programmerHandler.getAgentProgrammer().setupAllocator());
    }

    private void groupSetup() {
/*
        while (Simulator.instance.getState().getAgents().size() < 341 + 1) {
            if (agents.size() < 341) {
                AgentProgrammed ap = (AgentProgrammed) Simulator.instance.getAgentController().addProgrammedAgent(
                        50.9289,
                        -1.409,
                        0);

                agents.add(ap);
            }
        }

 */





        initialiseLearningAllocators();
        for (AgentProgrammed ap : agents) {
            if (hierarchy == null) {
                hierarchy = new AgentHierarchy(ap);
            } else {
                agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                hierarchy.addAgent(ap);
                regenerateHierarchy();
            }
        }

        ready = true;
        epochStartTime = System.currentTimeMillis();
    }

    private void groupStep() {
        for (AgentProgrammed ap : agents) {
            ap.programmerHandler.getAgentProgrammer().step();
        }
    }

    private void groupLearningStep() {
        // TODO try the reward being from the whole subtree (or the region below this)
        for (AgentProgrammed ap : agents) {
            int lvl = ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getLevel();
            if (lvl > 0) {
                float subTreeReward = calculateSubTreeReward(ap.getCoordinate(), ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getLevel() + 1);
                //System.out.println("subrwd for " + ap.getId() + " mul="+(ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getLevel() + 1) + " rwd" + subTreeReward);
                ap.programmerHandler.getAgentProgrammer().learningStep(subTreeReward);
            }
        }
    }

    /**
     * Reward function. Uses actual sim-side data to make this more efficient and generally easier to program
     */
    public float calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(i, j);
                for (Agent a : agents) {
                    if (!(a instanceof Hub)) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < 250) {
                            // This square's centre is in range of an agent
                            numPointsCovered++;
                            break;
                        }
                    }
                }
            }
        }
        return numPointsCovered;

    }

    public float calculateSubTreeReward(Coordinate centre, int multiplier) {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(centre, i, j, multiplier);
                for (Agent a : agents) {
                    if (!(a instanceof Hub)) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < 250) {
                            // This square's centre is in range of an agent
                            numPointsCovered++;
                            break;
                        }
                    }
                }
            }
        }
        return numPointsCovered;

    }

    public static float calculateRewardForNonProg() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        int numPointsCovered = 0;
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                float span = (float) ((-1.3991319762570154 + 1.415377448133106) / 64);

                Coordinate equiv = new Coordinate( new Coordinate(50.918934561834035, -1.415377448133106).getLatitude() + (j * span),
                        new Coordinate(50.918934561834035, -1.415377448133106).getLongitude() + (i * span));

                for (Agent a : Simulator.instance.getState().getAgents()) {
                    if (!(a instanceof Hub)) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < 250) {
                            // This square's centre is in range of an agent
                            numPointsCovered++;
                            break;
                        }
                    }
                }
            }
        }
        return numPointsCovered;

    }

    public Coordinate calculateEquivalentCoordinate(int x, int y) {
        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan), botLeft.getLongitude() + (x * xSquareSpan));
    }

    public Coordinate calculateEquivalentCoordinate(Coordinate centre, int x, int y, int multiplier) {
        double botBound = centre.getLatitude() - ((multiplier * Y_SPAN) / 2);
        double leftBound = centre.getLongitude() - ((multiplier * X_SPAN) / 2);

        return new Coordinate( botBound + (y * (multiplier * Y_SPAN) / ySteps), leftBound + (x * (multiplier * X_SPAN) / xSteps));
    }

    public void complete() {
        System.out.println("COMPLETE");
    }

    public int getRunCounter() {
        return runCounter;
    }

    public static class AgentHierarchy {
        List<List<AgentProgrammed>> layers = new ArrayList<>();

        public AgentHierarchy(AgentProgrammed a1) {
            ArrayList<AgentProgrammed> l1 = new ArrayList<>();
            l1.add(a1);
            layers.add(l1);
        }

        public void addAgent(AgentProgrammed toAdd) {
            addAgent(toAdd, 0);
        }

        public void addAgent(AgentProgrammed toAdd, int layerIndex) {
            // Check this layer for saturation
            // IF this layer is too full:
            //      Place agent in this layer;
            //      Promote one to the next layer, using the same process
            // ELSE:
            //      Place in this layer

            if (layerIndex == layers.size()) {
                // Top layer is full; make a new one
                ArrayList<AgentProgrammed> newLayer = new ArrayList<>();
                newLayer.add(toAdd);
                layers.add(newLayer);
                return;
            }
            int layerTargetSize;
            if (layerIndex + 1 == layers.size()) {
                // This means there is no layer above. This is the frontier layer so is always max 4 (for allowing
                //  separate trees), or 1 (for requiring a single top-level agents)
                layerTargetSize =  1;
            } else {
                layerTargetSize = MissionProgrammer.WIDTH * layers.get(layerIndex + 1).size();
            }

            if (layers.get(layerIndex).size() < layerTargetSize) {
                // This layer has free space
                layers.get(layerIndex).add(toAdd);
            } else {
                // This layer is full: place, and promote
                AgentProgrammed toPromote = layers.get(layerIndex).get(0);
                layers.get(layerIndex).remove(0);
                layers.get(layerIndex).add(toAdd);
                addAgent(toPromote, layerIndex+1);
            }
        }

        public AgentProgrammed getRoot() {
            return layers.get(layers.size() - 1).get(0);
        }

    }

    public static class ExperienceRecord {
        // Buffer: <state, action, reward, state'>
        Tensor originState;
        float[] actionValues;
        int actionTaken;
        float jointReward;
        Tensor resultantState;

        public ExperienceRecord(Tensor originState, float[] actionValues, int actionTaken, float jointReward, Tensor resultantState) {
            this.originState = originState;
            this.actionValues = actionValues;
            this.actionTaken = actionTaken;
            this.jointReward = jointReward;
            this.resultantState = resultantState;
        }

        @Override
        public String toString() {
            return "ExperienceRecord{" +
                    "originState=" + originState +
                    ", actionValues=" + Arrays.toString(actionValues) +
                    ", actionTaken=" + actionTaken +
                    ", jointReward=" + jointReward +
                    ", resultantState=" + resultantState +
                    '}';
        }
    }

}
