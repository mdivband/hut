package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ImageController {

    private Simulator simulator;
    private final List<String> deepScannedTargets = new ArrayList<>(16);
    private final List<String> shallowScannedTargets = new ArrayList<>(16);

    private final Map<String, Boolean> decisions = new HashMap<>(16);

    // Filenames of high and low resolution true and false positives
    private ArrayList<String> highResTrue;
    private ArrayList<String> lowResTrue;
    private ArrayList<String> highResFalse;
    private ArrayList<String> LowResFalse;

    //private String tempLowResFP = "images/image_gmaps_no_globe_320x240.jpg";
    private String tempLowResFP = "images/lowFP.jpg";
    private String tempLowResTP = "images/lowTP.jpg";
    private String tempHighResFP = "images/highFP.jpg";
    private String tempHighResTP = "images/highTP.jpg";

    public ImageController(Simulator simulator) {
        this.simulator = simulator;
    }

    /**
     * This finds the target, checks if it's a TP or FP, "takes an image", and stores it in the hashmap for inspection
     */
    public void takeImage(Coordinate coordinate, boolean isDeep) {
        // TODO distinguish deep and shallow with above flag
        Target t = simulator.getTargetController().getTargetAt(coordinate);
        if (t instanceof AdjustableTarget at) {  // This also asserts that t is not null
            synchronized (Simulator.instance.getState().getStoredImages()) {
                if (at.isReal()) {
                    // TODO filepath
                    if (isDeep) {
                        System.out.println("Adding image for agent: " +at.getId() + ", using " +  tempHighResTP + "( it's real)");
                        addImage(at.getId(), tempHighResTP, true);
                    } else {
                        System.out.println("Adding image for agent: " +at.getId() + ", using " +  tempLowResTP + "( it's real)");
                        addImage(at.getId(), tempLowResTP, false);
                    }

                } else {
                    // TODO filepath
                    if (isDeep) {
                        System.out.println("Adding image for agent: " +at.getId() + ", using " +  tempHighResFP + "( it's false)");
                        addImage(at.getId(), tempHighResFP, true);
                    } else {
                        System.out.println("Adding image for agent: " +at.getId() + ", using " +  tempLowResFP + "( it's false)");
                        addImage(at.getId(), tempLowResFP, false);
                    }
                }
            }
        }
    }

    private void addImage(String id, String fileName, boolean isDeep) {
        if (isDeep && !deepScannedTargets.contains(id)) {
            deepScannedTargets.add(id);
            Simulator.instance.getState().addToStoredImages(id, fileName);
        } else if (!isDeep && !shallowScannedTargets.contains(id) && !deepScannedTargets.contains(id)) {
            shallowScannedTargets.add(id);
            Simulator.instance.getState().addToStoredImages(id, fileName);
        }

    }

    /**
     * Not yet sure how to define what image is taken
     */
    public String getImageName(int arg1, int arg2, int arg3) {
        return "exampleImg.png";
    }

    public void requestImage(String id) {

    }

    public void classify(String ref, boolean status) {
        Map<String, String> map = simulator.getState().getStoredImages();
        String id = map
                .entrySet()
                .stream().
                filter(entry -> ref.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .get();

        // id is the id of the target we want (the above line searches the Map by value, and assumes 1:1 mapping)
        decisions.put(id, status);
        deepScannedTargets.remove(id);
        shallowScannedTargets.remove(id);
        map.remove(id);

        // Foreach loop automatically handles the null case (no tasks found) by not entering
        for (Task t : simulator.getTaskController().getAllTasksAt(simulator.getState().getTarget(id).getCoordinate())) {
            t.complete();
        }
        simulator.getTargetController().deleteTarget(id);

        System.out.println("=====DIAG=====");
        decisions.entrySet().forEach(System.out::println);
        System.out.println();
    }

    public Map<String, Boolean> getDecisions() {
        return decisions;
    }
}
