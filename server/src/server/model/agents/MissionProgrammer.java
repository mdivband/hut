package server.model.agents;

import server.Simulator;
import server.model.Coordinate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
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
    private List<AgentProgrammed> agents;

    //TODO these values can be passed through the AgentHubProgrammed and therefore can be scenario file defined
    private Coordinate botLeft;
    private Coordinate topRight;
    private int xSteps = 27;
    private int ySteps = 27;
    private double[][] globalCoverageMap;
    private HashMap<AgentProgrammed, double[][]> localCoverageMaps;
    private double X_SPAN = 0.05;//0.09;
    private double Y_SPAN = 0.025;//0.054;
    private double xSquareSpan;
    private double ySquareSpan;
    private int stepCounter = 0;
    private ArrayList<Double> scores = new ArrayList<>();
    private List<List<Float>> subScores = new ArrayList<>();
    private ArrayList<Long> times = new ArrayList<>();
    private boolean ready = false;
    private float decayRate = 0.02f;
    private int commRange = 9;

    public MissionProgrammer(AgentHubProgrammed ahp) {
        botLeft = new Coordinate(50.9289 - (Y_SPAN / 2), -1.409 - (X_SPAN / 2));
        topRight = new Coordinate(50.9289 + (Y_SPAN / 2), -1.409 + (X_SPAN / 2));
        xSquareSpan = X_SPAN / xSteps;
        ySquareSpan = Y_SPAN / ySteps;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> {if(a instanceof AgentProgrammed ap && (!(a instanceof Hub))) {agents.add(ap);}});
        drawBounds();
        globalCoverageMap = new double[27][27];
        for (double[] row: globalCoverageMap) {
            Arrays.fill(row, 0f);
        }
        localCoverageMaps = new HashMap<>();
        agents.forEach(a -> {
            double[][] cov = new double[27][27];
            for (double[] row: cov) {
                Arrays.fill(row, 0f);
            }
            localCoverageMaps.put(a, cov);
        });

    }

    public void step() {
        if (!ready) {
            groupSetup();
        } else {
            if (stepCounter < NUM_STEPS_PER_EPOCH) {
                groupStep();
                if (agents.stream().allMatch(Agent::isStopped)) {
                    //groupLearningStep();
                    stepCounter++; // This stepcounter is per total swarm step, not per agent step
                    boolean needsToWrite = false;
                    if (stepCounter % 10 == 1) {
                        needsToWrite = true;
                    }
                    if (needsToWrite) {
                        //System.out.println("run = " + stepCounter + ", reward at end = " + r + " mvAv1000 = " + mvAv1000 + ", hierarchy dims = " + Arrays.toString(hierarchy.getDims()));
                        /*
                        DecimalFormat f = new DecimalFormat("##.00");
                        File csvOutputFile = new File("tmp.csv");
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
                         */
                    }
                }
            }
        }
    }

    private void groupSetup() {
        agents.forEach(a -> {
            int x = Simulator.instance.getRandom().nextInt(0, xSteps);
            int y = Simulator.instance.getRandom().nextInt(0, ySteps);
            //System.out.println(a.getId() + " starting at " + x + ", " + y);
            a.programmerHandler.getAgentProgrammer().setupAllocator(xSteps, ySteps);
            moveAgent(a, x, y);
        });
        /*
        while (Simulator.instance.getState().getAgents().size() < 4 + 1) {
            System.out.println(Simulator.instance.getState().getAgents().size());
            if (agents.size() < 5) {
                AgentProgrammed ap = (AgentProgrammed) Simulator.instance.getAgentController().addProgrammedAgent(
                        50.9289,
                        -1.409,
                        0);

                agents.add(ap);
                int x = Simulator.instance.getRandom().nextInt(0, xSteps);
                int y = Simulator.instance.getRandom().nextInt(0, ySteps);
                //System.out.println(ap.getId() + " starting at " + x + ", " + y);
                ap.programmerHandler.getAgentProgrammer().setupAllocator();
                moveAgent(ap, x, y);
            }
        }

         */
        //initialiseLearningAllocators();
        ready = true;
    }

    private void moveAgent(AgentProgrammed ap, int x, int y) {
        ap.programmerHandler.getAgentProgrammer().getLearningAllocator().setCell(x, y);
        Coordinate c = calculateEquivalentCoordinate(x, y);
        ap.programmerHandler.getAgentProgrammer().manualSetTask(c);
        ap.programmerHandler.getAgentProgrammer().step();
    }

    private void groupStep() {
        int TTL = 5; // TODO scale this
        int range = 99; // TODO scale this

        // 1. For each agent, compute the local map and create a packet to send (just store our own packet and the next
        //    step takes care of everything
        for (AgentProgrammed origin : agents) {
            origin.programmerHandler.getAgentProgrammer().getLearningAllocator().clearFrames();
            // a. Get local map for this agent
            // TODO We are currently using localMap; later we may use the "currentMap" variable in agent (NOT the
            //  predicted map, use the last constructed observation instead)
            //double[][] localMap = extractLocalMapFor(origin, origin.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell,
            //        origin.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell, range);
            //writeImage("Local-"+origin.getId(), localMap);
            double[][] localMap = extractGroundTruthLocalMapFor(origin, origin.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell,
                    origin.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell, range);
            writeImage("Local-"+origin.getId(), localMap);

            CommFrame frame = new CommFrame(origin.getId(), TTL,
                    origin.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell,
                    origin.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell,
                    localMap);
            origin.programmerHandler.getAgentProgrammer().getLearningAllocator().receiveFrame(frame);
        }

        /*
        System.out.println("Initial Assignment");
        for (AgentProgrammed ap : agents) {
            System.out.println(ap.getId());
            ap.programmerHandler.getAgentProgrammer().getLearningAllocator().printFrames();
        }
        System.out.println();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
         */

        // 2. Every agent now passes on frames until end
        int i = 0;
        boolean done = false;
        while (!done) {
            i++;
            //System.out.println("ROUND " + i);
            for (AgentProgrammed origin : agents) {
                for (AgentProgrammed target : agents) {
                    if (!(origin == target)) {
                        boolean inRange = (Math.abs(target.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell
                                - origin.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell) < commRange)
                                && (Math.abs(target.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell
                                - origin.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell) < commRange);
                        if (inRange) {
                            // TODO important to ensure that this doesn't pass duplicate frames and fail TTL consistency
                            ArrayList<CommFrame> framesToPass = origin.programmerHandler.getAgentProgrammer().getLearningAllocator().getFrameMapAsList();
                            //System.out.println(origin.getId() + " -> " + target.getId() + ": " + framesToPass);
                            if (!framesToPass.isEmpty()) {
                                for (CommFrame f : framesToPass) {
                                    CommFrame newF = f.duplicateAndDecrement();
                                    if (newF != null) {
                                        //System.out.println("Adding a new frame. From " + f + " to " + newF);
                                        target.programmerHandler.getAgentProgrammer().getLearningAllocator().receiveFrame(newF);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (agents.parallelStream().allMatch(a -> a.programmerHandler.getAgentProgrammer().getLearningAllocator().checkFramesResolved())) {
                done = true;
            }

            if (i > 15) {
                // Occasionally we get stuck in a loop. Not sure quite why, but the eye-test shows that the required
                //  info is still transferred. Not worth spending lots of time on this bug for now, so let's just skip
                //  over it -WH
                done = true;
            }

            /*
             for (AgentProgrammed ap : agents) {
                    System.out.println(ap.getId());
                    ap.programmerHandler.getAgentProgrammer().getLearningAllocator().printFrames();
                }
                System.out.println();
                System.out.println();
             */
        }

        /*
        System.out.println("===============+DONE+================");
        for (AgentProgrammed ap : agents) {
            System.out.println(ap.getId());
            ap.programmerHandler.getAgentProgrammer().getLearningAllocator().printFrames();
        }
        System.out.println();
        System.out.println();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
         */


        // 3. Use received maps to build a total map
        constructMaps();  // This also updates each agent's history of maps

        // 4. Each agent should now have a belief history. Let's train the model from the previous belief history
        // TODO we may need to do something about empty space (agents leaving range or needing to fill empty space)
        groupLearningStep();

        // 5. Let's now use this history as "ground truth"
        predictMaps();



        // 2. Make a random move (for now)
        for (AgentProgrammed ap : agents) {
            //ap.programmerHandler.getAgentProgrammer().getLearningAllocator().decideMove()
            ap.programmerHandler.getAgentProgrammer().getLearningAllocator().decideRandomMove();
            moveAgent(ap, ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().xCell, ap.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().yCell);
        }
        updateCoverageMap();
    }

    private void groupLearningStep() {
        //agents.parallelStream().forEach(a -> a.programmerHandler.getAgentProgrammer().getLearningAllocator().train());
        agents.forEach(a -> a.programmerHandler.getAgentProgrammer().getLearningAllocator().train());
    }

    /**
     * For each agent, use frames to construct the estimated global map, and then save them to learningAllocators
     */
    private void constructMaps() {
        for (AgentProgrammed ap : agents) {

            double[][] constructedMap = new double[ySteps][xSteps];
            for (double[] row: constructedMap) {
                Arrays.fill(row, 0f); // Filling with 0s is needed for agents at edge to be handled
            }

            for (CommFrame frame : ap.programmerHandler.getAgentProgrammer().getLearningAllocator().getFrameMapAsList()) {
                int reach = frame.report.length;
                int range = (reach - 1) / 2; // In theory there should never be a rounding error here?

                //System.out.println(ap.getId() + " -> x=" + frame.xCell + ", y=" + frame.yCell + ", reach=" + reach + ", range=" + range);

                for (int i = -range; i < range; i++) {
                    if ((frame.xCell + i) < xSteps && (frame.xCell + i) > -1) {
                        for (int j = -range; j < range; j++) {
                            if ((ySteps - (frame.yCell + j)) < ySteps && (ySteps - (frame.yCell + j)) > -1) {
                                constructedMap[ySteps - (frame.yCell + j)][frame.xCell + i] = frame.report[reach - 1 - (range + j)][range + i];
                                //System.out.println("adding " + frame.report[reach - 1 - (range + j)][range + i] + " to " + (reach - 1 - (range + j)) + ", " + (range + i));
                            }
                        }
                    }
                }
            }
            ap.programmerHandler.getAgentProgrammer().getLearningAllocator().addGlobalMap(constructedMap);
            writeImage("Global-"+ap.getId(), constructedMap);
        }
    }

    private void predictMaps() {
        agents.forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().predict());
        agents.forEach(a -> writeImage("Predicted-"+a.getId(), a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getPrediction()));
        //agents.parallelStream().forEach(a -> a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().predict());
        //agents.parallelStream().forEach(a -> writeImage("Predicted-"+a.getId(), a.getProgrammerHandler().getAgentProgrammer().getLearningAllocator().getPrediction()));
    }

    private double[][] extractLocalMapFor(AgentProgrammed agent, int xCell, int yCell, int range) {
        int reach = ((2*range) + 1);
        double[][] localMap = new double[reach][reach];
        for (double[] row: localMap) {
            Arrays.fill(row, 0f); // Filling with 0s is needed for agents at edge to be handled
        }

        for (int i = -range; i < range; i++) {
            if ((xCell + i) < xSteps && (xCell + i) > -1) {
                for (int j = -range; j < range; j++) {
                    if ((yCell + j) < ySteps && (yCell + j) > -1) {
                        localMap[reach - 1 - (range + j)][range + i] = localCoverageMaps.get(agent)[ySteps - 1 - (yCell + j)][xCell + i];
                    }
                }
            }
        }
        return localMap;
    }

    private double[][] extractGroundTruthLocalMapFor(AgentProgrammed agent, int xCell, int yCell, int range) {
        int reach = ((2*range) + 1);
        double[][] localMap = new double[reach][reach];
        for (double[] row: localMap) {
            Arrays.fill(row, 0f); // Filling with 0s is needed for agents at edge to be handled
        }

        for (int i = -range; i < range; i++) {
            if ((xCell + i) < xSteps && (xCell + i) > -1) {
                for (int j = -range; j < range; j++) {
                    if ((yCell + j) < ySteps && (yCell + j) > -1) {
                        localMap[reach - 1 - (range + j)][range + i] = globalCoverageMap[ySteps - 1 - (yCell + j)][xCell + i];
                    }
                }
            }
        }
        return localMap;
    }

    private void updateCoverageMap() {
        // Decay map. Doesn't matter what order we do this. There may be a matrix or functional way of doing this better
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                if (globalCoverageMap[i][j] > decayRate) {
                    globalCoverageMap[i][j] -= decayRate;
                }
                for (Map.Entry<AgentProgrammed, double[][]> entry : localCoverageMaps.entrySet()) {
                    if (entry.getValue()[i][j] > decayRate) {
                        entry.getValue()[i][j] -= decayRate;
                    }
                }
            }
        }

        // Update for agents. Remember that matrix isn't arranged same as cartesian coords, so x,y -> [ySteps-1-y][x]
        // FOR 1 cell coverage all around
        /*
        agents.forEach(a -> {
            for(int i = a.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell - 1;
                i < a.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell + 1;
                i++) {
                if (i < xSteps && i > -1) {
                    for (int j = a.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell - 1;
                         j < a.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell + 1;
                         j++) {
                        if (j < ySteps && j > -1) {
                            coverageMap[ySteps - 1 - j][i] = 1f;
                        }
                    }
                }
            }
        });
         */

        // FOR just the cell we occupy
        for (AgentProgrammed a : agents) {
            try {
                globalCoverageMap[ySteps - 1 - a.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell][a.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell] = 1f;
                localCoverageMaps.get(a)[ySteps - 1 - a.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell][a.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell] = 1f;
            } catch (Exception e) {
                System.out.println(a.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell + ", " + a.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell);
                System.out.println(globalCoverageMap[ySteps - 1 - a.programmerHandler.getAgentProgrammer().getLearningAllocator().yCell][a.programmerHandler.getAgentProgrammer().getLearningAllocator().xCell]);
            }
        }

        writeImage("Truth", globalCoverageMap);
    }

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        BufferedImage resizedImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(img, 0, 0, newW, newH, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private void writeImage(String prefix, double[][] matrix) {
        try {
            BufferedImage image = new BufferedImage(matrix[0].length, matrix.length, BufferedImage.TYPE_INT_RGB);
            for(int i=0; i<matrix.length; i++) {
                for(int j=0; j<matrix.length; j++) {
                    double a = matrix[i][j];
                    int intensity = (int) Math.floor((0.5 + a/2) * 255);
                    intensity = Math.min(intensity, 255);
                    intensity = Math.max(0, intensity);
                    //System.out.println(a);
                    Color newColor = new Color(intensity,intensity,intensity);
                    image.setRGB(j,i,newColor.getRGB());
                }
            }
            BufferedImage scaled = resize(image, 256, 256);
            File output = new File(prefix+"-decision.jpg");
            ImageIO.write(scaled, "jpg", output);
        } catch(Exception ignored) {
            //System.out.println(ignored);
        }

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

    public void drawBounds() {
        double bot = botLeft.getLatitude();
        double top = topRight.getLatitude();
        double left = botLeft.getLongitude();
        double right = topRight.getLongitude();
        String botLine = "Main"+"bot,"+",polyline,"
                +bot+","+left+","+bot+","+right;
        String rightLine = "Main"+"right,"+",polyline,"
                +bot+","+right+","+top+","+right;
        String topLine = "Main"+"top,"+",polyline,"
                +top+","+right+","+top+","+left;
        String leftLine = "Main"+"left,"+",polyline,"
                +top+","+left+","+bot+","+left;

        Simulator.instance.getState().getMarkers().add(botLine);
        Simulator.instance.getState().getMarkers().add(rightLine);
        Simulator.instance.getState().getMarkers().add(topLine);
        Simulator.instance.getState().getMarkers().add(leftLine);
    }

}
