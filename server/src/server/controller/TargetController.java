package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.target.HumanTarget;
import server.model.target.Target;
import server.model.task.Task;

import java.util.HashMap;
import java.util.Map;

public class TargetController extends AbstractController {

    private static Map<String, Integer> uniqueTargetNumbers = new HashMap<>();

    public TargetController(Simulator simulator) {
        super(simulator, TargetController.class.getName());
    }

    private String generateUID(String typeName) {
        String uid = "Target-" + typeName + "-";
        int idNum = 1;
        if(uniqueTargetNumbers.containsKey(typeName))
            idNum = uniqueTargetNumbers.get(typeName);
        uid += idNum++;
        uniqueTargetNumbers.put(typeName, idNum);
        return uid;
    }

    public synchronized Target addTarget(double lat, double lng, int type) {
        Target target;
        switch(type) {
            case Target.HUMAN:
                target = new HumanTarget(generateUID("Human"), new Coordinate(lat, lng));
                break;
            default:
                throw new RuntimeException("Unrecognized target type - " + type);
        }
        simulator.getState().add(target);
        return target;
    }

    public synchronized void setTargetVisibility(String targetId, boolean visible) {
        Target target = simulator.getState().getTarget(targetId);
        target.setVisible(visible);
    }

    public synchronized boolean deleteTarget(String id) {
        Target target = simulator.getState().getTarget(id);
        if(target == null) {
            LOGGER.warning("Attempted to remove missing target " + id);
            return false;
        }
        simulator.getState().remove(target);
        LOGGER.info(String.format("%s; DELTRG; Deleted target (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, target.getCoordinate().getLatitude(), target.getCoordinate().getLongitude()));
        return true;
    }

    public synchronized void resetTargetNumbers() {
        this.uniqueTargetNumbers = new HashMap<>();
    }
    /**
     * Searches for any target with the given coordinate
     * @param coordinate The coordinate to check
     * @return The Target with that coordinate
     */
    public Target findTargetByCoord(Coordinate coordinate) {
        return simulator.getState().getTargetByCoordinate(coordinate);
    }


}
