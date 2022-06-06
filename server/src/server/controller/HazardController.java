package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.hazard.DebrisHazard;
import server.model.hazard.FireHazard;
import server.model.hazard.Hazard;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class HazardController extends AbstractController {

    private static Map<String, Integer> uniqueHazardNumbers = new HashMap<>();

    public HazardController(Simulator simulator, Logger LOGGER) {
        super(simulator, HazardController.class.getName(), LOGGER);
    }

    private String generateUID(String typeName) {
        String uid = "Hazard-" + typeName + "-";
        int idNum = 1;
        if(uniqueHazardNumbers.containsKey(typeName))
            idNum = uniqueHazardNumbers.get(typeName);
        uid += idNum++;
        uniqueHazardNumbers.put(typeName, idNum);
        return uid;
    }

    public void addHazard(double lat, double lng, int type) {
        Hazard hazard;
        switch(type) {
            case Hazard.FIRE:
                hazard = new FireHazard(generateUID("Fire"), new Coordinate(lat, lng));
                break;
            case Hazard.DEBRIS:
                hazard = new DebrisHazard(generateUID("Debris"), new Coordinate(lat, lng));
                break;
            default:
                throw new RuntimeException("Unrecognized hazard type - " + type);
        }
        simulator.getState().add(hazard);
    }

    public void addHazard(double lat, double lng, int type, int size) {
        Hazard hazard;
        switch(type) {
            case Hazard.FIRE:
                hazard = new FireHazard(generateUID("Fire"), new Coordinate(lat, lng), size);
                break;
            case Hazard.DEBRIS:
                hazard = new DebrisHazard(generateUID("Debris"), new Coordinate(lat, lng), size);
                break;
            default:
                throw new RuntimeException("Unrecognized hazard type - " + type);
        }
        simulator.getState().add(hazard);
    }

    public synchronized void resetHazardNumbers() {
        this.uniqueHazardNumbers = new HashMap<>();
    }


}
