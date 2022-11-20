package server.model.agents;

import server.Simulator;
import tool.CircularQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class LearningAllocator {
    protected AgentProgrammed agent;

    private int HISTORY_SIZE = 5;

    protected int xSteps;
    protected int ySteps;
    protected int xCell;
    protected int yCell;

    protected int counter;
    private int level; // level 0 is a bottom level; raising as we move up
    private int bestRwd = 0;
    private CircularQueue<float[][]> history;

    protected HashMap<String, CommFrame> frameMap = new HashMap<>();

    public LearningAllocator(AgentProgrammed agent) {
        this.agent = agent;
        history = new CircularQueue<>(HISTORY_SIZE);
    }


    public void setup() {

    }

    public void setup(int xSteps, int ySteps) {
        this.xSteps = xSteps;
        this.ySteps = ySteps;
    }

    public void reset() {

    }

    public abstract void complete();

    public abstract void step();

    public void step(float jointReward) {
        System.out.println("NOT IMPLEMENTED");
    }

    public void randStep() {

    }

    public boolean checkInGrid(int[] cell) {
        return cell[0] >= 0 && cell[0] <= xSteps && cell[1] >= 0 && cell[1] <= ySteps;
    }

    public void setLevel(int l) {
        this.level = l;
        if (l == 0) {
            agent.getProgrammerHandler().setVisual("ghost");
        } else if (l == 1) {
            agent.getProgrammerHandler().setVisual("standard");
        } else if (l == 2) {
            agent.getProgrammerHandler().setVisual("leader");
        }
    }

    public int getLevel() {
        return level;
    }

    public void setCell(int x, int y) {
        xCell = x;
        yCell = y;
    }

    public void decideRandomMove() {
        // St, N, S, E, W
        boolean[] possibles = new boolean[]{true, true, true, true, true}; // Preset as true for all (covers stop too)
        if (yCell == ySteps - 1) { // Can't move upwards
            possibles[1] = false;
        } else if (yCell == 0) { // Can't move downwards
            possibles[2] = false;
        } else if (xCell == xSteps - 1) { // Can't move right
            possibles[3] = false;
        } else if (xCell == 0) { // Can't move left
            possibles[4] = false;
        }

        int move = -1;
        while (move == -1 || !possibles[move]) {
            move = Simulator.instance.getRandom().nextInt(5);
        }

        if (move == 1) {
            // Move up
            yCell++;
            agent.setHeading(0);
        } else if (move == 2) {
            // Move down
            yCell--;
            agent.setHeading(180);
        } else if (move == 3) {
            // Move right
            xCell++;
            agent.setHeading(90);
        } else if (move == 4) {
            // Move left
            xCell--;
            agent.setHeading(270);
        }

        if (xCell < 0 || yCell < 0 || xCell >= xSteps || yCell >= ySteps) {
            System.out.println("HERE!!!");
            System.out.println("move = " + move);
            System.out.println("(After move:)");
            System.out.println("x = " + xCell);
            System.out.println("y = " + yCell);
            System.out.println();
        }
    }

    public void receiveFrame(CommFrame frame) {
        //System.out.println(agent.getId() + " Receiving " + frame);
        // If this is the first one, we can have our own map, that's fine
        if (frameMap.isEmpty()) {
            frameMap.put(frame.agentID, frame);
        } else if (frame.agentID.equals(agent.getId())) {
            frameMap.get(agent.getId()).ttl--;
        } else {
            if (frameMap.containsKey(frame.agentID)) {
                frameMap.replace(frame.agentID, frame);
            } else {
                frameMap.put(frame.agentID, frame);
            }
        }
        //System.out.println("======");
        //frameMap.forEach((k,v) -> System.out.println(k + " -> " + v));
        //System.out.println();
    }

    public ArrayList<CommFrame> getFrameMapAsList() {
        return new ArrayList<>(frameMap.values());
    }

    public void printFrames() {
        frameMap.forEach((k,v) -> System.out.println(v));
    }

    public void clearFrames() {
        frameMap = new HashMap<>();
    }

    public boolean checkFramesResolved() {
        AtomicBoolean complete = new AtomicBoolean(true);
        frameMap.forEach((k,v) -> {
            if (!k.equals(agent.getId()) && v.ttl > 1) {
                complete.set(false);
            }
        });
        return complete.get();
    }

    public void addGlobalMap(float[][] constructedMap) {
        history.enqueue(constructedMap);
    }

}
