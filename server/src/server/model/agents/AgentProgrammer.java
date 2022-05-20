package server.model.agents;
import server.model.Coordinate;

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

    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            LOGGER.severe("Sv: HUB " + a.agent.getId() + " assigned leadership and hub status");
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
                    System.out.println("--Found my task! " + taskToDo);
                    myTask = taskToDo.get(0);
                }
            }
        } else {
            a.followRoute();
        }
    }

    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {

    }

}
