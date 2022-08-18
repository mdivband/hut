package server.model.agents;

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
    private final int NUM_STEPS_PER_EPOCH = 1_000_000_000;
    public static final int WIDTH = 4;

    float bestReward = 0f;

    private AgentHubProgrammed hub;
    private ProgrammerHandler programmerHandler;
    private List<AgentProgrammed> agents;

    //TODO these values can be passed through the AgentHubProgrammed and therefore can be scenario file defined
    private Coordinate botLeft;
    private Coordinate topRight;
    protected int xSteps = 16;
    protected int ySteps = 16;
    private double X_SPAN = 0.015;
    private double Y_SPAN = 0.009;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;
    private int runCounter = 0;
    private boolean bufferFull = false;
    private int pointer = 0;
    private int counter;
    private int stepCounter = 0;
    private ArrayList<Double> scores = new ArrayList<>();
    private List<List<Float>> subScores = new ArrayList<>();
    private ArrayList<Long> times = new ArrayList<>();

    private int stateSize;
    private boolean ready = false;
    private long epochStartTime;
    private boolean set = false;
    private AgentHierarchy hierarchy = null;
    private double lastDeltaAverage = 0;
    private int waitCounter = 0;

    public MissionProgrammer(AgentHubProgrammed ahp) {
        hub = ahp;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {agents.add(ap);}});
        subScores.add(new ArrayList<>());
        subScores.add(new ArrayList<>());
        subScores.add(new ArrayList<>());
        subScores.add(new ArrayList<>());
        subScores.add(new ArrayList<>());

    }

    public void step() {
        if (!ready) {
            groupSetup();
        } else {
            //if (stepCounter < (NUM_STEPS_PER_EPOCH * (hierarchy.layers.size() - 1))) {
            if (stepCounter < NUM_STEPS_PER_EPOCH) {
                groupStep();
                if (agents.stream().allMatch(Agent::isStopped)) {
                    groupLearningStep();

                    /*
                    if (stepCounter % (NUM_STEPS_PER_EPOCH / 10) == 0) {
                        System.out.print((stepCounter / (NUM_STEPS_PER_EPOCH / 100)) + ">");
                    }

                     */
                    boolean needsToExpand = false;
                    boolean needsToShrink = false;
                    boolean needsToWrite = false;
                    for (AgentProgrammed a : agents) {
                        if ((a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getLevel() > 0) {
                            stepCounter++;
                            waitCounter++;
                            if (stepCounter % 10_000 == 0) {
                                needsToWrite = true;
                            }
                            //if (stepCounter == 200_000 || stepCounter == 2_000_000  || stepCounter == 5_000_000) {
                            // 200,000 * 1 agent = 200,000
                            // 200,000 * 5 agents = 1,000,000 -> 1,200,000
                            // 200,000 * 21 agents = 4,200,000 -> 5,400,000
                            // 200,000 * 85 agents = 17,000,000 -> 22,400,000
                            if (stepCounter == 150_000 || stepCounter == 3_500_000 || stepCounter == 10_000_000) {
                                needsToExpand = true;
                                waitCounter = 0;
                            }
                        }
                    }
                    //stepCounter ++;
                    //if (stepCounter % ((NUM_STEPS_PER_EPOCH * (hierarchy.layers.size() - 1) / 10)) == 0) {
                    if (stepCounter > 25_000_000) {
                        System.exit(1);
                    } else if (needsToExpand) {
                        addAgentIfRequired();
                    } else if (needsToShrink) {
                        removeAgentsIfRequired();
                    }
                    if (needsToWrite) {
                        //double r = calculateGlobalGridRewardOfLevelOne();

                        //double r = calculateSubGridReward(hierarchy.getRoot().getProgrammerHandler().getAgentProgrammer().getSubordinates());
                        //double r = hierarchy.getRoot().getProgrammerHandler().getAgentProgrammer().getLearningAllocator().calculateGridReward();
                        double r = calculateTotalRewardFrom(hierarchy.getRoot(), hierarchy.getRoot().getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getLevel(), hierarchy.getRoot().getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getSubordinates());

                        double sum1000 = 0;
                        for (int i = Math.max(0, scores.size() - 1000); i < scores.size(); i++) {
                            sum1000 += scores.get(i);
                        }
                        double mvAv1000 = sum1000 / Math.min(scores.size(), 1000);

                        double sumL1 = 0;
                        for (int i = Math.max(0, subScores.get(0).size() - 1000); i < subScores.get(0).size(); i++) {
                            sumL1 += subScores.get(0).get(i);
                        }
                        double mvAvL1 = sumL1 / Math.min(subScores.get(0).size(), 1000);

                        double sumL2 = 0;
                        for (int i = Math.max(0, subScores.get(1).size() - 1000); i < subScores.get(1).size(); i++) {
                            sumL2 += subScores.get(1).get(i);
                        }
                        double mvAvL2 = sumL2 / Math.min(subScores.get(1).size(), 1000);

                        double sumL3 = 0;
                        for (int i = Math.max(0, subScores.get(2).size() - 1000); i < subScores.get(2).size(); i++) {
                            sumL3 += subScores.get(2).get(i);
                        }
                        double mvAvL3 = sumL3 / Math.min(subScores.get(2).size(), 1000);

                        double sumL4 = 0;
                        for (int i = Math.max(0, subScores.get(3).size() - 1000); i < subScores.get(3).size(); i++) {
                            sumL4 += subScores.get(3).get(i);
                        }
                        double mvAvL4 = sumL4 / Math.min(subScores.get(3).size(), 1000);



                        System.out.println("run = " + stepCounter + ", reward at end = " + r + " mvAv1000 = " + mvAv1000 + ", hierarchy dims = " + Arrays.toString(hierarchy.getDims()));
                        scores = new ArrayList<>();
                        subScores.forEach(List::clear);
                        /*
                        agents.forEach(a -> {
                            if (!a.programmerHandler.getAgentProgrammer().getSubordinates().isEmpty()) {
                                ((TensorRLearner) a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).debugOut();
                            }
                        });

                         */

                        DecimalFormat f = new DecimalFormat("##.00");
                        File csvOutputFile = new File("IncTopLaterR3.csv");
                        try {
                            FileWriter fw = new FileWriter(csvOutputFile, true);
                            fw.write(r
                                    + ", " + f.format(mvAv1000)
                                    + ", " + f.format(mvAvL1)
                                    + ", " + f.format(mvAvL2)
                                    + ", " + f.format(mvAvL3)
                                    + ", " + f.format(mvAvL4)
                                    + "\n");
                            fw.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
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

    private void regenerateHierarchyMaximizingFull() {
        List<List<AgentProgrammed>> layers = hierarchy.layers;
        List<AgentProgrammed> top = layers.get(layers.size() - 1); // This is necessarily a singleton in current config
        top.forEach(a -> a.programmerHandler.getAgentProgrammer().getLearningAllocator().setLevel(layers.size() - 1));
        for (int l = layers.size() - 2; l >= 0; l--) {
            int c = 0;
            for (AgentProgrammed a : layers.get(l)) {
                while (top.get(c).programmerHandler.getAgentProgrammer().getLearningAllocator().getSubordinates().size() == 4) {
                    c++;
                }
                top.get(c).programmerHandler.getAgentProgrammer().getLearningAllocator().addSubordinate(a);
                a.programmerHandler.getAgentProgrammer().getLearningAllocator().setSupervisor(top.get(c));
                a.setCoordinate(new Coordinate(50.9289, -1.409));
                a.programmerHandler.getAgentProgrammer().getLearningAllocator().setLevel(l);

            }
            top = layers.get(l);
        }
        updateBounds();
    }

    private void reorderHierarchy() {
        hierarchy.layers.forEach(List::clear);
        agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());

        ArrayList<Integer> ascendingWidths = new ArrayList<>();
        int layer = 0;
        while (ascendingWidths.stream().mapToInt(Integer::intValue).sum() < agents.size()) {
            ascendingWidths.add((int) Math.pow(MissionProgrammer.WIDTH, layer));
            layer++;
        }

        ArrayList<Integer> widths = new ArrayList<>();
        for (int i = ascendingWidths.size() - 1; i > -1 ; i--) {
            widths.add(ascendingWidths.get(i));
        }

        List<AgentProgrammed> toAssign = new ArrayList<>(agents);
        HashMap<AgentProgrammed, HashMap<Integer, Integer>> memoryMap = new HashMap<>();
        toAssign.forEach(a -> {
            HashMap<Integer, Integer> mems = ((TensorRLearner) a.programmerHandler.getAgentProgrammer().getLearningAllocator()).getLevelMemory();
            for (int i=0; i<hierarchy.layers.size(); i++) {
                if (!mems.containsKey(i)) {
                    mems.put(i, hierarchy.layers.size() - i);
                }
            }
            memoryMap.put(a, mems);
        });


        while (!toAssign.isEmpty()) {
            List<AgentProgrammed> toRemove = new ArrayList<>();

            for (AgentProgrammed agent : toAssign) {
                HashMap<Integer, Integer> mems = memoryMap.get(agent);
                Map.Entry<Integer, Integer> bestEntry = Collections.max(mems.entrySet(), new Comparator<Map.Entry<Integer, Integer>>() {
                    public int compare(Map.Entry<Integer, Integer> e1, Map.Entry<Integer, Integer> e2) {
                        return e1.getValue().compareTo(e2.getValue());
                    }
                });

                int level = bestEntry.getKey();
                if (hierarchy.layers.get(level).size() < widths.get(level)) {
                    // This means there is room
                    hierarchy.layers.get(level).add(agent);
                    agent.setCoordinate(new Coordinate(50.9289, -1.409));
                    toRemove.add(agent);
                } else {
                    memoryMap.get(agent).remove(level);
                }
            }
            toRemove.forEach(toAssign::remove);
        }

        // Now there is a chance that we don't have a workable hierarchy (e.g. 2 parents to 10 children => one needs to be promoted)

        for (int i=hierarchy.layers.size()-1; i>0; i++) {
            while (hierarchy.layers.get(i).size() * 4 < hierarchy.layers.get(i-1).size()) {
                // SELECT BEST OPTION FROM THE LAYERS BELOW
                
            }
        }

    }

    private void removeAgentsIfRequired() {
        if (stepCounter < 16_000_000) {
            // Drop layer
            hierarchy.layers.get(0).forEach(a -> {
                Simulator.instance.getState().getAgents().remove(a);
                agents.remove(a);
            });
            hierarchy.layers.remove(0);
            agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
            regenerateHierarchy();
        } else {
            List<AgentProgrammed> toRemove = new ArrayList<>();
            agents.forEach(a -> {
                if (Integer.parseInt(a.getId().split("-")[1]) % 4 == 0) {
                    toRemove.add(a);
                }
            });
            toRemove.forEach(a -> {
                hierarchy.layers.forEach(l -> {
                    l.remove(a);
                });
                agents.remove(a);
            });
            regenerateHierarchy();
        }
    }

    private void addAgentIfRequired() {
        // Inc  -> R0=5; R1=21; R2=85; R3=341; ... ; R6(5)=85; ... ; R9(8)=341 ; ... ; R11(10)=85
        // Full ->                     R0=341; ... ; R3(2)=85; ... ; R6(5)=341 ; ... ; R8(7)=85

        /*
        if (runCounter == 15 || runCounter == 25) {
        //if (runCounter == 20 || runCounter == 30) {
            // Drop layer
            hierarchy.layers.get(0).forEach(a -> {
                Simulator.instance.getState().getAgents().remove(a);
                agents.remove(a);
            });
            hierarchy.layers.remove(0);
            agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
            regenerateHierarchy();
        } else if ((runCounter == 5 || runCounter == 10 || runCounter == 20  || runCounter == 30) && agents.size() < 85) {
        //} else if ((runCounter == 25 || runCounter == 35) && agents.size() < 85) {
            int numToAdd = (int) Math.pow(4, hierarchy.layers.size());
            for (int i = 0; i < numToAdd; i++) {
                if (agents.size() < 85) {
                    AgentProgrammed ap = (AgentProgrammed) Simulator.instance.getAgentController().addProgrammedAgent(
                            50.9289,
                            -1.409,
                            0);

                    agents.add(ap);
                    ap.programmerHandler.getAgentProgrammer().setupAllocator();
                    agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                    hierarchy.addAgent(ap);
                    //hierarchy.addAgentWithoutPromotion(ap);
                    //hierarchy.addAgentFromTop(ap);
                } else {
                    agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                }
            }
            regenerateHierarchy();
        } else {

         */
            if (agents.size() < 341) {
                int numToAdd = (int) Math.pow(4, hierarchy.layers.size());
                for (int i = 0; i < numToAdd; i++) {
                    if (agents.size() < 341) {
                        AgentProgrammed ap = (AgentProgrammed) Simulator.instance.getAgentController().addProgrammedAgent(
                                50.9289,
                                -1.409,
                                0);

                        agents.add(ap);
                        ap.programmerHandler.getAgentProgrammer().setupAllocator();
                        agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                        //hierarchy.addAgent(ap);
                        //hierarchy.addAgentWithoutPromotion(ap);
                        hierarchy.addAgentFromTop(ap);
                    } else {
                        agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
                    }
                }
                regenerateHierarchyMaximizingFull();
            }
      /*
            hierarchy = null;
            Collections.shuffle(agents);
            agents.forEach(a -> {
                if (hierarchy == null) {
                    hierarchy = new AgentHierarchy(a);
                } else {
                    hierarchy.addAgent(a);
                    a.setCoordinate(new Coordinate(50.9289, -1.409));
                }
            });
            agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().clearAssociations());
            agents.forEach(a -> a.setCoordinate(new Coordinate(50.9289, -1.409)));
            regenerateHierarchy();
          */

        //}

    }

    private void updateBounds() {
        double topBound = hierarchy.getRoot().getCoordinate().getLatitude() + ((4 * Y_SPAN) / 2);
        double botBound = hierarchy.getRoot().getCoordinate().getLatitude() - ((4 * Y_SPAN) / 2);
        double rightBound = hierarchy.getRoot().getCoordinate().getLongitude() + ((4 * X_SPAN) / 2);
        double leftBound = hierarchy.getRoot().getCoordinate().getLongitude() - ((4 * X_SPAN) / 2);

        botLeft = new Coordinate(botBound, leftBound);
        topRight = new Coordinate(topBound, rightBound);

        xSquareSpan = (4 * X_SPAN) / xSteps;
        ySquareSpan = (4 * Y_SPAN) / ySteps;
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

    private float calculateRecursiveReward(AgentProgrammed ap, float subTreeReward) {
        if (ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getLevel() == 1) {
            // Base case
            return ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().calculateGridReward();
        } else {
            // Iteration over subordinates, which each make a recursive call to this
            for (AgentProgrammed s : ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getSubordinates()) {
                subTreeReward += calculateRecursiveReward(s, 0);
            }
            return subTreeReward;
        }
    }

    private float calculateTotalRewardFrom(AgentProgrammed root, int lvl, List<AgentProgrammed> subs) {
        List<AgentProgrammed> agentsToConsider = new ArrayList<>();

        List<AgentProgrammed> toAdd = new ArrayList<>(subs);
        boolean changed = true;
        while (changed) {
            changed = false;
            List<AgentProgrammed> nextBatch = new ArrayList<>();
            for (AgentProgrammed agent : toAdd) {
                if (!agent.getProgrammerHandler().getAgentProgrammer().getSubordinates().isEmpty()) {
                    changed = true;
                    nextBatch.addAll(agent.getProgrammerHandler().getAgentProgrammer().getSubordinates());
                } else {
                    agentsToConsider.add(agent);
                }
            }
            toAdd = nextBatch;
        }

        //System.out.println("For " + root.getId() + " -> " + agentsToConsider.size());

        int steps = (int) Math.pow(2, lvl);
        double topBound = root.getCoordinate().getLatitude() + ((Math.pow(2, lvl-1) * Y_SPAN) / 2);
        double botBound = root.getCoordinate().getLatitude() - ((Math.pow(2, lvl-1) * Y_SPAN) / 2);
        double rightBound = root.getCoordinate().getLongitude() + ((Math.pow(2, lvl-1) * X_SPAN) / 2);
        double leftBound = root.getCoordinate().getLongitude() - ((Math.pow(2, lvl-1) * X_SPAN) / 2);

        Coordinate bl = new Coordinate(botBound, leftBound);
        Coordinate tr = new Coordinate(topBound, rightBound);

        double xss = (Math.pow(2, lvl-1) * X_SPAN) / steps;
        double yss = (Math.pow(2, lvl-1) * Y_SPAN) / steps;
        float cellWidth = (float) ((xss * 111111));

        //System.out.println("==================");
        int numPoints = 0;
        for (int i=0; i<steps; i++) {
            for (int j=0; j<steps; j++){

                Coordinate pos = new Coordinate( bl.getLatitude() + (j * yss) + (yss / 2), bl.getLongitude() + (i * xss) + (xss / 2));
                List<AgentProgrammed> toRemove = new ArrayList<>();
                for (AgentProgrammed ap : agentsToConsider) {
                    //System.out.println("Checking " + i + ", " + j + " ( crd = " + pos + ") and agent at " + ap.getCoordinate() + " dist = " + pos.getDistance(ap.getCoordinate()) + " compared to " + (cellWidth / 2));
                    if (pos.getDistance(ap.getCoordinate()) < (cellWidth / 2)) {
                        //System.out.println(ap.getId() + " is at (" + i + ", " + j + ") relative to " + root.getId());
                        toRemove.add(ap);
                        numPoints++;
                        break;
                    }
                }
                agentsToConsider.removeAll(toRemove);

            }
        }

        return numPoints;



    }

    private void groupLearningStep() {
        // TODO try the reward being from the whole subtree (or the region below this)
        for (AgentProgrammed ap : agents) {
            int lvl = ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getLevel();
            //if (lvl > 0) {
            if (!ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getSubordinates().isEmpty()) {

                //float subTreeReward = ((TensorRLearner) ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).calculateGridReward() / 4f;
                float subTreeReward = calculateTotalRewardFrom(ap, lvl, ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getSubordinates()) / (float) (Math.pow(4, lvl));
                //TODO balance reward

                //subScores.get(lvl - 1).add(subTreeReward);
                subScores.get(lvl - 1).add((ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).calculateGridReward() / 4f);


                if (ap.equals(hierarchy.getRoot())) {
                    scores.add((double) subTreeReward * (float) (Math.pow(4, lvl)));
                    //scores.add((double) calculateTotalRewardFrom(ap, lvl, ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getSubordinates()));
                }







                // We must scale this down depending on level, otherwise they get increasing reward when promoted and it nullifies old learning
                // e.g. level 1 -> (reward for 4 agents below)                       -> 4 * (average reward per agent)
                //      level 2 -> (reward for 16 combined agents below) / 4         -> 4 * (average reward per agent)
                //      level 3 -> (reward for 64 combined agents below) / 16        -> 4 * (average reward per agent)
                //float subTreeReward = (float) (calculateSubGridReward(ap.getProgrammerHandler().getAgentProgrammer().getSubordinates()) / Math.pow(4, lvl-1));



                //if (stepCounter < 4000) {
                // After 80% time we stop exploring
               // if (stepCounter < Math.floor((NUM_STEPS_PER_EPOCH * (hierarchy.layers.size() - 1)) * 0.25)) {
                //if (stepCounter < (Math.floor(NUM_STEPS_PER_EPOCH) * 0.75)) {

                ap.programmerHandler.getAgentProgrammer().learningStep(subTreeReward, 10);
/*
                if (stepCounter < 1000000) {
                    ap.programmerHandler.getAgentProgrammer().learningStep(subTreeReward, 10);
                } else {
                    ap.programmerHandler.getAgentProgrammer().learningStep(subTreeReward, 100);
                }


 */


            }
        }
    }

    private float calculateGlobalGridReward() {
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                for (AgentProgrammed a : agents) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] - 4 <= i && cell[0] + 4 >= i && cell[1] - 4 <= j && cell[1] + 4 >= j) {
                        numPointsCovered++;
                        break;
                    }
                }
            }
        }
        return numPointsCovered;
    }

    private float calculateGlobalGridRewardOfLevelOne() {
        List<AgentProgrammed> agentsToConsider = new ArrayList<>();
        for (AgentProgrammed agent : agents) {
            if (agent.getProgrammerHandler().getAgentProgrammer().getSubordinates().isEmpty()) {
                agentsToConsider.add(agent);
            }
        }

        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                for (AgentProgrammed a : agentsToConsider) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] - 4 <= i && cell[0] + 4 >= i && cell[1] - 4 <= j && cell[1] + 4 >= j) {
                        numPointsCovered++;
                        break;
                    }
                }
            }
        }
        return numPointsCovered;
    }

    private float calculateSubGridReward(List<AgentProgrammed> subordinates) {
        List<AgentProgrammed> agentsToConsider = new ArrayList<>();
        List<AgentProgrammed> toAdd = new ArrayList<>(subordinates);
        boolean changed = true;
        while (changed) {
            changed = false;
            List<AgentProgrammed> nextBatch = new ArrayList<>();
            for (AgentProgrammed agent : toAdd) {
                if (!agent.getProgrammerHandler().getAgentProgrammer().getSubordinates().isEmpty()) {
                    changed = true;
                    nextBatch.addAll(agent.getProgrammerHandler().getAgentProgrammer().getSubordinates());
                } else {
                    agentsToConsider.add(agent);
                }
            }
            toAdd = nextBatch;
        }

        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                for (AgentProgrammed a : agentsToConsider) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] - 1 <= i && cell[0] + 1 >= i && cell[1] - 1 <= j && cell[1] + 1 >= j) {
                        numPointsCovered++;
                        break;
                    }
                }
            }
        }
        return numPointsCovered;
    }

    public int[] calculateEquivalentGridCell(Coordinate c) {
        return new int[]{
                (int) Math.round(((c.getLongitude() - botLeft.getLongitude()) / (4 * X_SPAN)) * xSteps),
                (int) Math.round(((c.getLatitude() - botLeft.getLatitude()) / (4 * Y_SPAN)) * ySteps)
        };
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

    public float calculateSimulatedReward(Coordinate centre, int multiplier, List<AgentProgrammed> subordinates) {
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(centre, i, j, multiplier);
                //for (Agent a : agents) {
                for (Agent a : subordinates) {
                    if (!(a instanceof Hub)) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < 250 * (multiplier - 1)) {
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

    public float calculateSubTreeReward(Coordinate centre, int multiplier, List<AgentProgrammed> subordinates) {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH

        List<AgentProgrammed> agentsToConsider = new ArrayList<>();
        List<AgentProgrammed> toAdd = new ArrayList<>(subordinates);
        boolean changed = true;
        while (changed) {
            changed = false;
            agentsToConsider.addAll(toAdd);
            List<AgentProgrammed> nextBatch = new ArrayList<>();
            for (AgentProgrammed agent : toAdd) {
                if (!agent.getProgrammerHandler().getAgentProgrammer().getSubordinates().isEmpty()) {
                    changed = true;
                    nextBatch.addAll(agent.getProgrammerHandler().getAgentProgrammer().getSubordinates());
                }
            }
            toAdd = nextBatch;
        }
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(centre, i, j, multiplier);
                //for (Agent a : agents) {
                for (Agent a : agentsToConsider) {
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

        public void addAgentWithoutPromotion(AgentProgrammed ap) {
            for (int i=0; i<layers.size() - 1; i++) {
                int layerTargetSize = MissionProgrammer.WIDTH * layers.get(i + 1).size();
                if (layers.get(i).size() < layerTargetSize) {
                    layers.get(i).add(ap);
                    return;
                }
            }
            // If full:
            layers.add(0, new ArrayList<>());
            layers.get(0).add(ap);

            maximizeUpperLayers();
        }

        public void addAgentFromTop(AgentProgrammed toAdd) {
            addAgentFromTop(toAdd, layers.size() - 1);
        }

        public void addAgentFromTop(AgentProgrammed toAdd, int layerIndex) {
            // Check this layer for saturation
            // IF this layer is too full:
            //      Place agent in this layer;
            //      Promote one to the next layer, using the same process
            // ELSE:
            //      Place in this layer

            if (layerIndex == -1) {
                // Bottom layer is full; make a new one
                ArrayList<AgentProgrammed> newLayer = new ArrayList<>();
                newLayer.add(toAdd);
                layers.add(0, newLayer);
                return;
            }

            int layerTargetSize = (int) Math.pow(MissionProgrammer.WIDTH, (layers.size() - 1 - layerIndex));  // 1 if top, otherwise the required width
            if (layers.get(layerIndex).size() < layerTargetSize) {
                // This layer has free space
                layers.get(layerIndex).add(toAdd);
            } else {
                // This layer is full: place, and demote
                AgentProgrammed toDemote = layers.get(layerIndex).get(Simulator.instance.getRandom().nextInt(layers.get(layerIndex).size()));
                layers.get(layerIndex).remove(toDemote);
                layers.get(layerIndex).add(toAdd);
                addAgentFromTop(toDemote, layerIndex-1);
            }
        }

        public void addAgent(AgentProgrammed toAdd) {
            addAgent(toAdd, 0);
            maximizeUpperLayers();
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

        public void maximizeUpperLayers() {

            //System.out.println("START: " + Arrays.toString(getDims()));
            for (int i=layers.size()-1; i>0; i--) {
                int layerTargetSize = (int) Math.pow(MissionProgrammer.WIDTH, (layers.size() - 1 - i));  // 1 if top, otherwise the required width
                //System.out.println("Target for layer " + i + " is " + layerTargetSize);
                while (layers.get(i).size() < layerTargetSize && !layers.get(i-1).isEmpty()) {
                    AgentProgrammed toPromote = layers.get(i-1).get(0);
                    layers.get(i-1).remove(toPromote);
                    layers.get(i).add(toPromote);
                    //System.out.println(Arrays.toString(getDims()));
                }
            }


        }

        public AgentProgrammed getRoot() {
            return layers.get(layers.size() - 1).get(0);
        }

        public int[] getDims() {
            int[] dims = new int[layers.size()];
            for (int i = 0; i < layers.size(); i++) {
                dims[i] = layers.get(i).size();
            }
            return dims;
        }
    }

}
