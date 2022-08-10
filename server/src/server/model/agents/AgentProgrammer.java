package server.model.agents;
import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Here is where the user should program agent behaviour to be run on each programmed agent
 */
public class AgentProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    ProgrammerHandler a;

    // TODO subordinates here, with a method to handle them as reqd
    private Coordinate myTask;

    private LearningAllocator learningAllocator = null;    // Importantly if we are level 1 then we use this for learning and
    private boolean ready = false;
    // assignment of subordinates; if level 0, we refer to it as our env

    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            a.setLeader(true);
        }
    }

    public void setupAllocator() {
        learningAllocator = new TensorRLearner(a.agent);
        learningAllocator.setup();
    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step() {
        if (myTask == null) {
            a.stop();
        } else {
            a.teleport(myTask);
            myTask = null;
            //if (a.moveTowards(myTask)) {
            //    myTask = null;
            //}
        }
    }

    public void learningStep(float jointReward) {
        if (!getSubordinates().isEmpty()) {
            learningAllocator.step(jointReward);
        }
    }

    public void learningStep(float jointReward, int epsilon) {
        if (!getSubordinates().isEmpty()) {
            ((TensorRLearner) learningAllocator).step(jointReward, epsilon);
        }
    }

    public void learningStep() {
        learningAllocator.step();
    }

    public boolean gridMove(int i) {
        switch (i) {
            case 0 -> {
                if (learningAllocator.checkCellValid(a.getPosition().getCoordinate(getLearningAllocator().getCellWidth(), 0))) {
                    a.setHeading(0);
                    a.moveAlongHeading(getLearningAllocator().getCellWidth());
                } else {
                    return false;
                }
            } case 1 -> {
                if (learningAllocator.checkCellValid(a.getPosition().getCoordinate(getLearningAllocator().getCellWidth(), Math.PI))) {
                    a.setHeading(180);
                    a.moveAlongHeading(getLearningAllocator().getCellWidth());
                } else {
                    return false;
                }
            } case 2 -> {
                if (learningAllocator.checkCellValid(a.getPosition().getCoordinate(getLearningAllocator().getCellWidth(), Math.PI / 2))) {
                    a.setHeading(90);
                    a.moveAlongHeading(getLearningAllocator().getCellWidth());
                } else {
                    return false;
                }
            } case 3 -> {
                if (learningAllocator.checkCellValid(a.getPosition().getCoordinate(getLearningAllocator().getCellWidth(), 3 * Math.PI / 2))) {
                    a.setHeading(270);
                    a.moveAlongHeading(getLearningAllocator().getCellWidth());
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public void manualSetTask(Coordinate myTask) {
        a.resume();
        learningAllocator.updateBounds(myTask);
        this.myTask = myTask;
        ((TensorRLearner) learningAllocator).moveSubordinates();
    }

    public LearningAllocator getLearningAllocator() {
        return learningAllocator;
    }

    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {

    }

    public void setAllocator(LearningAllocator learningAllocator) {
        this.learningAllocator = learningAllocator;
    }

    public List<AgentProgrammed> getSubordinates() {
        return learningAllocator.getSubordinates();
    }

    public void setSubordinates(List<AgentProgrammed> subs) {
        learningAllocator.setSubordinates(subs);
    }
}