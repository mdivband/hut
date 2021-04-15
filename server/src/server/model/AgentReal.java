package server.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import server.QueueManager.MessagePublisher;

public class AgentReal extends Agent {

    private transient MessagePublisher messagePublisher;
    private long lastPublish = System.currentTimeMillis();

    public AgentReal(String id, Coordinate coordinate, MessagePublisher messagePublisher) {
        super(id, coordinate, false);
        this.messagePublisher = messagePublisher;
    }

    @Override
    public void moveTowardsDestination() {
        if(isStopped()) {
            //TODO send stopped message to client
        } else {
            //TODO make more efficient - is it necessary to continually publish the route if it hasn't changed?
            long now = System.currentTimeMillis();
            if (now - lastPublish > 1000) {
                this.messagePublisher.publishMessage("UAV_TaskQueue_" + this.getId(), this.getRouteTaskJson(true).toString());
                lastPublish = now;
            }
        }
    }

    @Override
    void performFlocking() {
        //TODO implement real agent flocking
    }

    private JsonObject getRouteTaskJson(boolean prescan) {
        JsonObject tbr = new JsonObject();

        tbr.addProperty("Content", "Mission");
        tbr.addProperty("ID", getAllocatedTaskId());
        tbr.addProperty("IsRegion", false);
        tbr.addProperty("Step", (prescan) ? "Prescan" : "Scan");

        JsonArray coords = new JsonArray();
        for (Coordinate coord : this.getRoute()) {
            coords.add(coord.getJSON());
        }
        tbr.add("Coordinates", coords);

        return tbr;
    }
}
