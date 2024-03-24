package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.HumanTarget;
import server.model.target.Target;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for targets that the user must find
 */
/* Edited by Will */
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
            case Target.ADJUSTABLE:
                target = new AdjustableTarget(generateUID("Unknown"), new Coordinate(lat, lng), true);
                break;
            default:
                throw new RuntimeException("Unrecognized target type - " + type);
        }
        simulator.getState().add(target);
        return target;
    }

    public synchronized Target addTarget(double lat, double lng, int type, boolean isReal) {
        Target target;
        switch(type) {
            case Target.HUMAN:
                target = new HumanTarget(generateUID("Human"), new Coordinate(lat, lng));
                break;
            case Target.ADJUSTABLE:
                target = new AdjustableTarget(generateUID("Unknown"), new Coordinate(lat, lng), isReal);
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
        simulator.incrementCompletedTargets();

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

    /**
     * Set the task at this location to this type
     * @param taskType
     * @param lat
     * @param lng
     */
    public void adjustForTask(int taskType, double lat, double lng) {
        for (Target t : Simulator.instance.getState().getTargets()) {
            try {
                if (t.getCoordinate().getLatitude() == lat && t.getCoordinate().getLongitude() == lng) {
                    t.setType(taskType);
                }
            } catch (Exception e) {
                System.out.println("Error changing sprite. Probably due to casting error");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns the target at this coordinate (within 5m)
     * @param c
     * @return
     */
    public Target getTargetAt(Coordinate c) {
        return getTargetAt(c, 5);
    }

    /**
     * Returns the target at this coordinate (within epsilon m)
     * @param c
     * @return
     */
    public Target getTargetAt(Coordinate c, double epsilon) {
        for (Target t : Simulator.instance.getState().getTargets()) {
            if(t.getCoordinate().getDistance(c) < epsilon) {
                return t;
            }
        }
        return null;
    }

    public void requestImage(String id) {
        simulator.getImageController().requestImage(id);
    }

    /**
     * Removes target if it has this id
     * @param id
     */
    public void removeTarget(String id) {
        simulator.getState().getTargets().removeIf(tgt -> tgt.getId().equals(id));
    }

}
