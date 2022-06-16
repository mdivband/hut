package server.model.agents;
import server.Simulator;
import server.model.Coordinate;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    private float cellWidth = (float) ((0.00016245471 * 111111));

    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            //LOGGER.severe("Sv: HUB " + a.agent.getId() + " assigned leadership and hub status");
            a.setLeader(true);
        } else {
            a.setLeader(true);
            a.setVisual("leader");
        }
    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step(){
        if (a.isStopped()) {
            if (myTask != null) {
                a.setTask(Collections.singletonList(myTask));
                a.resume();
            } else {
                List<Coordinate> taskToDo = a.findOwnOrder();
                if (taskToDo != null) {
                    myTask = taskToDo.get(0);
                }
            }
        } else {
            a.followRoute();
        }
    }

    public boolean gridMove(int i) {
        switch (i) {
            case 0 -> {
                if (((AgentHubProgrammed) Simulator.instance.getState().getHub()).checkCellValid(a.getPosition().getCoordinate(cellWidth, 0))) {
                    a.setHeading(0);
                    a.moveAlongHeading(cellWidth);
                } else {
                    return false;
                }
            } case 1 -> {
                if (((AgentHubProgrammed) Simulator.instance.getState().getHub()).checkCellValid(a.getPosition().getCoordinate(cellWidth, Math.PI))) {
                    a.setHeading(180);
                    a.moveAlongHeading(cellWidth);
                } else {
                    return false;
                }
            } case 2 -> {
                if (((AgentHubProgrammed) Simulator.instance.getState().getHub()).checkCellValid(a.getPosition().getCoordinate(cellWidth, Math.PI / 2))) {
                    a.setHeading(90);
                    a.moveAlongHeading(cellWidth);
                } else {
                    return false;
                }
            } case 3 -> {
                if (((AgentHubProgrammed) Simulator.instance.getState().getHub()).checkCellValid(a.getPosition().getCoordinate(cellWidth, 3 * Math.PI / 2))) {
                    a.setHeading(270);
                    a.moveAlongHeading(cellWidth);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public Coordinate getMyTask() {
        return myTask;
    }

    public void manualSetTask(Coordinate myTask) {
        this.myTask = myTask;
    }

    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {

    }


}
