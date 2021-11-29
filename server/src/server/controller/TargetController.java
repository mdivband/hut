package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.HumanTarget;
import server.model.target.Target;

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
            case Target.ADJUSTABLE:
                target = new AdjustableTarget(generateUID("Unknown"), new Coordinate(lat, lng));
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
        LOGGER.info("Deleted agent " + id);
        return true;
    }


    public void adjustForTask(int taskType, double lat, double lng) {
        for (Target t : Simulator.instance.getState().getTargets()) {
            //System.out.println("Checking : ");
            //System.out.println("    lat: " + t.getCoordinate().getLatitude());
           // System.out.println("    lng: " + t.getCoordinate().getLongitude());
            try {
                if (t.getCoordinate().getLatitude() == lat && t.getCoordinate().getLongitude() == lng) {
                    System.out.println("Located target for this task");
                    t.setType(taskType);
                    //AdjustableTarget aT = (AdjustableTarget) t;
                    //aT.setStatus(taskType);
                }
            } catch (Exception e) {
                System.out.println("Error changing sprite. Probably due to casting error");
                e.printStackTrace();
            }
        }
    }
}
