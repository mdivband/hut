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


    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (Math.random() > 0.75 || a.isReceiver()) {
            leader = true;
        }

    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step(){
        if (leader) {
            if (a.isStopped()) {
                List<Coordinate> task = a.getNearestEmptyTask();
                if (!task.isEmpty()) {
                    if (task.get(0).getDistance(a.getPosition()) > 350){
                        // Nearest task too far away. Pass leadership to a random neighbour
                        leader = false;
                        String targetId = a.getRandomNeighbour();
                        a.sendCustomMessage("ELECT", targetId);
                    } else {
                        // perform this task
                        a.setTask(task);
                        a.resume();
                    }
                }
            } else {
                a.followRoute();
            }
        } else {
            flock();
        }
    }

    private void flock() {
        if (a.checkForNeighbourMovement()) {
            a.flockWithAttractionRepulsion();
            a.moveAlongHeading(1);
        }
    }

    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {
        if (opCode.equals("ELECT")) {
            if (a.getId().equals(payload)) {
                leader = true;
                LOGGER.severe("This agent has been elected as a leader");
            }
        }
    }
}