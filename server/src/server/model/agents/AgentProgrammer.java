package server.model.agents;
import server.model.Coordinate;
import server.model.target.PackageTarget;

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


    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            LOGGER.severe("Sv: HUB " + a.agent.getId() + " assigned leadership and hub status");
            a.setLeader(true);
        } else if (a.getNextRandomDouble() > 0.5) { //0.65) {
            LOGGER.severe("Sv: Agent with GLOBAL ID " + a.agent.getId() + " randomly assigned leadership");
            a.setLeader(true);
            a.setVisual("leader");
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
        // Leader => Scout
        if (a.isLeader()) {
            if (a.isStopped()) {
                a.planSearchRoute();
            } else {
                a.followRoute();
            }

        // !Leader => Shuttle
        } else {
            if (a.isGoingHome()) {
                if (a.isAtHome()) {
                    a.deliverPack();
                } else {
                    a.followRoute();
                }
            } else if (a.isStopped()) {
                a.planPackageCollection();
                /*
                if (dupeCounter > dupeLimit) {
                    a.planPackageCollection();
                    dupeCounter = 0;
                } else {
                    dupeCounter++;
                }

                 */
            } else if (a.getPack() != null) {
                // We have a package, keep going home
                a.followRoute();
            } else {
                PackageTarget p = a.getNearbyPackage();
                if (p != null) {
                    a.pickupPackage(p);
                    a.goHome();
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
