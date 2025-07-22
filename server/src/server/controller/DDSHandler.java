package server.controller;

import server.model.State;

public class DDSHandler {
    private State state;

    public DDSHandler(State state) {
        // At each timestep this is called. We should update everything that has changed


        double heading = 0.0;  // Would actually be retrieved from DDS
        state.getAgent("UAV-1").setHeading(heading);
    }

    public void update() {
        System.out.println("Here we pull from DDS");
    }
}
