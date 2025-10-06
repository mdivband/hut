package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.fire.Fire;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for fire objects.
 */
public class FireController extends AbstractController {

    private static int uniqueFireNumber = 1;

    public FireController(Simulator simulator) {
        super(simulator, FireController.class.getName());
    }

    public synchronized Fire addFire(String id, double lat, double lng, String image) {
        Fire fire = new Fire(id, new Coordinate(lat, lng));
        fire.setImage(image);
        simulator.getState().add(fire);
        LOGGER.info(String.format("Created new fire with ID: %s at (%.6f, %.6f)", id, lat, lng));
        return fire;
    }

    public String generateUID(int ddsId) {
        return "FIRE-" + ddsId;
    }

    public synchronized void reset() {
        uniqueFireNumber = 1;
    }
}