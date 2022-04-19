package server.model.agents;
import server.model.Coordinate;

import java.util.List;
import java.util.logging.Logger;

/**
 * Here is where the user should program agent behaviour to be run on each programmed agent
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


    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            LOGGER.severe("Sv: HUB " + a.agent.getId() + " assigned leadership and hub status");
            a.setLeader(true);
        } else if (a.getNextRandomDouble() > 0.65) {
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

        // Sets a random wait interval from 5-15
        dupeLimit = (int) Math.floor((a.getNextRandomDouble() * 10) + 5);
        LOGGER.severe("---- and set timeout to " + dupeLimit);

    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step(){
        if (a.getTasks().size() == 0 && a.getCompletedTasks().size() == 0) {
            // WAIT; Only begin executing if we have tasks added now (so they don't fly off at the start)
        } else {
            if (a.isLeader()) {
                if (a.isStopped()) {
                    if (a.getCompletedTasks().size() == 0) {
                        // Get first task
                        List<Coordinate> task = a.getNearestEmptyTask();
                        if (task != null) {
                            a.setTask(task);
                            a.resume();
                        }
                    }
                    if (a.getNextRandomDouble() < 0.9) {
                        if (a.getCompletedTasks().size() > 0) {
                            // WAIT - To make task clashes less likely
                        }
                    } else {
                        try {
                            if (returner || a.getNextRandomDouble() > 0.8) {
                                // If this agent has selected the returner strategy then it always heads home after a
                                // task completion
                                // Otherwise, there is still a 20% chance to return anyway
                                if (a.getCompletedTasks().size() > 0 && a.getPosition().getDistance(a.getHome()) < a.getSenseRange()) {
                                    // At home
                                    List<Coordinate> task = a.getNearestEmptyTask();
                                    if (task != null) {
                                        a.setTask(task);
                                        a.resume();
                                    } else {
                                        a.goHome();
                                    }
                                } else {
                                    a.goHome();
                                }
                            } else {
                                List<Coordinate> task = a.getNearestEmptyTask();
                                if (task != null) {
                                    a.setTask(task);
                                    a.resume();
                                } else {
                                    a.goHome();
                                }
                            }

                        } catch (Exception e) {
                            System.out.println("Excep: Should be due to start conditions");
                        }
                    }
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
            } else {
                flock();
            }
        }
    }

    /***
     * You don't need to use this, but I would suggest having a method to call that makes agents flock when they can't
     * find/select a task
     */
    public void flock() {
        if (!a.isGoingHome()) {
            if (strandedCounter == 0) {
                // wait and check
                if (a.checkForNeighbourMovement()) {
                    a.flockWithAttractionRepulsion(100, 20);
                    a.moveAlongHeading();
                    strandedCounter++;
                }
            } else if (strandedCounter < 30) {
                pingLeaders();  // This updates the nearby leaders by use of custom messages
                if (a.checkForNeighbourMovement() && hasNearbyLeader) {
                    a.flockWithAttractionRepulsion(100, 20);
                    a.moveAlongHeading();
                    strandedCounter = 1;
                } else {
                    strandedCounter++;
                }
            } else {
                strandedCounter = 1;
                a.goHome();
            }
        } else {
            if (a.getPosition().getDistance(a.getHome()) > a.getSenseRange()) {
                a.followRoute();
            } else {
                strandedCounter = 1;
                a.stopGoingHome();
            }

        }
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
