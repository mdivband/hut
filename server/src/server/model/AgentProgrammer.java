package server.model;
import java.util.List;
import java.util.logging.Logger;

public class AgentProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    ProgrammerHandler a;
    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    private boolean leader = false;
    private int strandedCounter = 0;
    private boolean hasNearbyLeader = false;


    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            LOGGER.severe("Sv: HUB " + a.agent.getId() + " assigned leadership and hub status");
            leader = true;
        } else if (Math.random() > 0.65) {
            LOGGER.severe("Sv: Agent with GLOBAL ID " + a.agent.getId() + " randomly assigned leadership");
            leader = true;
            a.setVisual("leader");
        }
    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step(){
        if (leader) {
            if (a.isStopped()) {
                if (Math.random() < 0.9) {
                    if (a.getCompletedTasks().size() > 0) {
                        // temp pass to add a delay. Flock 1 step, then stop again so this gets recalled (as a leader)
                        a.flockWithAttractionRepulsion();
                        a.moveAlongHeading(1);
                        a.stop();
                    }

                } else {
                    List<Coordinate> task = a.getNearestEmptyTask();
                    if (task != null) {
                        a.setTask(task);
                        a.resume();

                    } else {
                        if (a.getCompletedTasks().size() > 0) {
                            // Tasks have been completed,AND we can't get a new task. This means we're in session and
                            // have done everything known
                            a.goHome();
                        }

                        // Otherwise,probably the start of the session. Wait for something to happen
                    }
                }
            } else {
                a.followRoute();
            }
        } else {
            flock();
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
                    a.flockWithAttractionRepulsion();
                    a.moveAlongHeading(1);
                    strandedCounter++;
                }
            } else if (strandedCounter < 30) {
                pingLeaders();  // This updates the nearby leaders by use of custom messages
                if (a.checkForNeighbourMovement() && hasNearbyLeader) {
                    a.flockWithAttractionRepulsion();
                    a.moveAlongHeading(1);
                    strandedCounter = 1;
                } else {
                    strandedCounter++;
                }
            } else {
                strandedCounter = 1;
                a.goHome();
            }
        } else {
            if (a.getPosition().getDistance(a.getHome()) > 150) {
                //a.resume();   //TODO stopping here fixes the stranded problem but causes new ones
                a.followRoute();
            } else {
                //LOGGER.severe("GOT HOME");
                strandedCounter = 1;
                a.stopGoingHome();
            }

        }
    }

    private void pingLeaders() {
        //hasNearbyLeader = false;
        //a.sendCustomMessage("PING_LEADERS_SEND", a.getId());  // Use id as return address

        hasNearbyLeader = true;


        if (!hasNearbyLeader) {
            //LOGGER.severe("Failed to find a nearby leader for agent: " + a.getId());
        }
    }


    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {
        if (opCode.equals("PING_LEADERS_SEND")) {
            if (leader && !a.isHub()) {  // Check that we are a leader, and are not the hub
                //LOGGER.severe(a.getId() + " has been pinged, and is a leader, returning confirmation");
                a.sendCustomMessage("PING_LEADERS_RECEIVE", payload);
            }
        } else if (opCode.equals("PING_LEADERS_RECEIVE") && payload.equals(a.getId())) {
            // If our message (with our networkID) is returned, we know there are leaders
            hasNearbyLeader = true;
        }

    }
}