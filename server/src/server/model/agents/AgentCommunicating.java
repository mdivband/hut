package server.model.agents;

import server.model.Coordinate;
import server.model.Sensor;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Acts like the AgentVirtual, but also communicates in the same way as the AgentProgrammed. This means it can be used
 *  manually together with programmed agents.
 *
 *  This does contain some redundancy, probably should be replaced with a static library or more inheritance in future
 */
public class AgentCommunicating extends AgentVirtual {
    private String networkID = "";
    private final transient Random random;

    private final transient CommunicationHandler communicationHandler;

    public AgentCommunicating(String id, Coordinate position, Sensor sensor, Random random) {
        super(id, position, sensor);
        communicationHandler = new CommunicationHandler(this);
        this.random = random;
        type = "communicating";
    }

    /**
     * We need a different step function that acts as a virtual agent but also communicates int
     * @param flockingEnabled
     */
    @Override
    public void step(Boolean flockingEnabled) {
        communicationHandler.step();  // Perform the required communication processes to support the network
        super.step(flockingEnabled);  // Defer movement orders to the normal process
    }

    /***
     * Generates a random tag for a network ID. We use a global random object, but in real life these may be based on
     * unique information such as serial numbers PUFs.
     *
     * Collision chance (approx):
     * 26 letters = 4 bits (plus another for remaining 8, but we'll assume the worst)
     * 4*16 = 64, a 64 bit hash for e.g. 100 agents has collision probability
     * 2.22e-16
     * According to https://everydayinternetstuff.com/2015/04/hash-collision-probability-calculator/
     * -WH
     * @return a 16-char alphanumeric ID
     */
    protected String generateRandomTag(){
        // from https://www.baeldung.com/java-random-string
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 16;

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    @Override
    protected void moveTowardsDestination() {
        //Move agents
        if (!getRoute().isEmpty() && !isCurrentDestinationReached()) {
            if (!getSearching()) {

                //Align agent, if aligned then moved towards target
                if(!isStopped() && super.adjustHeadingTowardsGoal())
                    super.moveAlongHeading(1);

                incrementTimeInAir();
            }
            if (isCurrentDestinationReached() && this.getRoute().size() > 1)
                this.getRoute().remove(0);
        }

        if (isFinalDestinationReached() && !isStopped()) {
            stop();
            communicationHandler.completeTask();
        }
    }

    public void setNetworkID(String id) {
        networkID = id;
    }

    public String getNetworkId(){
        return networkID;
    }

    protected List<Agent> senseNeighbours(double sensingRadius){
        return this.sensor.senseNeighbours(this, sensingRadius);
    }

    protected void receiveMessage(String message){
        communicationHandler.receiveMessage(message);
    }

    public void setCommunicationRange(double range) {
        communicationHandler.setCommunicationRange(range);
    }

    public Coordinate getHubLocation() {
        return communicationHandler.getHome();
    }

    public double getSenseRange() {
        return communicationHandler.getSenseRange();
    }

    public void setCurrentTask(List<Coordinate> coords) {
        communicationHandler.setCurrentTask(coords);
    }

    public void registerCompleteTask(Coordinate coordinate) {
        communicationHandler.completeTask(coordinate);
    }

}
