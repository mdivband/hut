package server.model;
import java.util.ArrayList;
import java.util.HashMap;
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
        if (Math.random() > 0.65 || a.isReceiver()) {
            LOGGER.severe("Sv: Agent with GLOBAL ID " + a.agent.getId() + " randomly assigned leadership");
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
                if (task != null) {
                    if (a.getPosition().getDistance(task.get(0)) < 550) {
                        // perform this task
                        a.setTask(task);
                        a.resume();
                    } else {
                        String randId = a.getRandomNeighbour();
                        LOGGER.severe("No nearby task, attempting to pass to leadership");
                        leader = false;
                        a.sendCustomMessage("ELECT", randId);

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

        if (a.checkForNeighbourMovement()) {
            a.flockWithAttractionRepulsion();
            a.moveAlongHeading(1);
        }



        /*
        if (a.checkForNeighbourMovement()) {
            // I lifted this straight out of the AgentVirtual
            double xSum = 0.0;
            double ySum = 0.0;
            double xRepulse = 0.0;
            double yRepulse = 0.0;
            double xAttract = 0.0;
            double yAttract = 0.0;
            double targetHeading = Math.toRadians(a.getHeading());

            HashMap<String, ProgrammerHandler.Position> neighboursFull = a.getNeighbours();
            HashMap<String, ProgrammerHandler.Position> neighbours = new HashMap<>();

            for (var entry : neighboursFull.entrySet()) {
                if (entry.getValue().getLocation().getDistance(a.getPosition()) < 50) {
                    neighbours.put(entry.getKey(), entry.getValue());
                }
            }

            if (neighbours.size() > 0) {
                for (var entry : neighbours.entrySet()) {
                    String id = entry.getKey();
                    ProgrammerHandler.Position pos = entry.getValue();
                    double multiplier = 1;
                    if (a.checkNeighbourHasTask(id)) {
                        multiplier = 100;
                    }
                    double neighbourHeading = Math.toRadians(pos.getHeading());
                    xSum += Math.cos(neighbourHeading) * multiplier;
                    ySum += Math.sin(neighbourHeading) * multiplier;
                }
                double magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                double xAlign = xSum/magnitude;
                double yAlign = ySum/magnitude;

                List<ProgrammerHandler.Position> tooCloseNeighbours = new ArrayList<>();
                List<ProgrammerHandler.Position> notTooClose = new ArrayList<>();
                for (var entry : neighbours.entrySet()) {
                    if (entry.getValue().getLocation().getDistance(a.getPosition()) < 15) {
                        tooCloseNeighbours.add(entry.getValue());
                    } else {
                        notTooClose.add(entry.getValue());
                    }
                }

                if (tooCloseNeighbours.size() > 0) {
                    xSum = 0.0;
                    ySum = 0.0;

                    for(ProgrammerHandler.Position n : tooCloseNeighbours) {
                        double lat1 = Math.toRadians(a.getPosition().getLatitude());
                        double lng1 = Math.toRadians(a.getPosition().getLongitude());
                        double lat2 = Math.toRadians(n.getLocation().getLatitude());
                        double lng2 = Math.toRadians(n.getLocation().getLongitude());
                        double dLng = (lng2 - lng1);
                        ySum -= Math.sin(dLng) * Math.cos(lat2);
                        xSum -= Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                                * Math.cos(lat2) * Math.cos(dLng);
                        magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                        xRepulse = xSum/magnitude;
                        yRepulse = ySum/magnitude;
                    }
                }
                xSum = 0.0; ySum = 0.0;
                for(ProgrammerHandler.Position n : notTooClose) {
                    double lat1 = Math.toRadians(a.getPosition().getLatitude());
                    double lng1 = Math.toRadians(a.getPosition().getLongitude());
                    double lat2 = Math.toRadians(n.getLocation().getLatitude());
                    double lng2 = Math.toRadians(n.getLocation().getLongitude());
                    double dLng = (lng2 - lng1);
                    ySum += Math.sin(dLng) * Math.cos(lat2);
                    xSum += Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                            * Math.cos(lat2) * Math.cos(dLng);
                    magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                    xAttract = xSum/magnitude;
                    yAttract = ySum/magnitude;
                }

                targetHeading = Math.atan2(
                        yAlign + 0.5 * yAttract + yRepulse,
                        xAlign + 0.5 * xAttract + xRepulse
                );
            }
            a.setHeading(targetHeading);
            a.moveAlongHeading(1);
        } else {

        }
        
         */


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