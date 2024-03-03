package server.model.agents;
import com.mysql.jdbc.log.Log;
import server.Simulator;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;
import server.model.task.Task;

import java.util.List;
import java.util.logging.Logger;

/**
 * Here is where the user should program agent behaviour to be run on each programmed agent
 * @author William Hunt
 */
public class AgentProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    ProgrammerHandler a;

    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    // These are example member variables
    private int strandedCounter = 0;
    private int dupeCounter = 0;
    private int dupeLimit;
    private boolean hasNearbyLeader = false;
    private boolean returner = false;
    protected String targetName = "";


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
            returner = true;
            a.setVisual("leader");
        }


            /*
            if (a.getNextRandomDouble() > 0.65) {
            LOGGER.severe("Sv: Agent with GLOBAL ID " + a.agent.getId() + " randomly assigned leadership");
            a.setLeader(true);
            a.setVisual("leader");
            if (a.getNextRandomDouble() > 0.5) {
                LOGGER.severe("---- and set as a returning agent");
                returner = true;
            } else {
                LOGGER.severe("---- and set as a non-returning agent");
            }
        } else {
            a.setLeader(false);
        }

             */

        // Sets a random wait interval from 5-15
        dupeLimit = (int) Math.floor((a.getNextRandomDouble() * 10) + 5);
        LOGGER.severe("---- and set timeout to " + dupeLimit);

    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step(){
        if (a.isGoingHome()) {
            a.followRoute();
        } else if (a.getTasks().size() == 0 && a.getCompletedTasks().size() == 0) {
            // WAIT; Only begin executing if we have tasks added now (so they don't fly off at the start)
        } else {
            if (a.isStopped()) {
                if (a.getTasks().size() > 0) {
                    List<Coordinate> task = a.getHighestPriorityNearestTask();//a.getNearestEmptyTask();

                    if (task != null) {
                        a.setTask(task);
                        a.resume();
                    }
                    // ELSE wait
                }
                // ELSE wait

            } else {
                if (dupeCounter < dupeLimit) {
                    a.followRoute();
                    dupeCounter += 1;
                } else {
                    dupeCounter = 0;
                    if (a.checkForDuplicateAssignment()) {
                        a.cancel();
                        a.stop();
                    }
                }
            }
        }
    }

    public void onComplete(Coordinate c) {
        double prio = Simulator.instance.getTaskController().getAllTasksAt(c).get(0).getPriority();
        LOGGER.info(String.format("%s; APCMP; Ground agent completed pickup at (x, y, priority); %s; %s; %s",
                Simulator.instance.getState().getTime(), c.getLatitude(), c.getLongitude(), prio));

        String id = Simulator.instance.getState().getPendingMap().get(c);
        Simulator.instance.getState().addToHandledTargets(id);

        a.goHome();
    }

    private void pingLeaders() {
        hasNearbyLeader = false;
        a.sendCustomMessage("PING_LEADERS_SEND", a.getId());  // Use id as return address
    }


    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {
        if (opCode.equals("PING_LEADERS_SEND")) {
            if (a.isLeader() && !a.isHub()) {  // Check that we are a leader, and are not the hub
                a.sendCustomMessage("PING_LEADERS_RECEIVE", payload);
            }
        } else if (opCode.equals("PING_LEADERS_RECEIVE") && payload.equals(a.getId())) {
            // If our message (with our networkID) is returned, we know there are leaders
            hasNearbyLeader = true;
        }

    }
}
