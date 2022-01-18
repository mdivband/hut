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

    private final int SHALLOW_SCAN_TIME = 60;  // In-game seconds, so use 6*real-life seconds
    private final int DEEP_SCAN_TIME = 60;

    private Simulator simulator;

    private final List<String> deepScannedTargets = new ArrayList<>(16);
    private final List<String> shallowScannedTargets = new ArrayList<>(16);
    private final Map<String, Boolean> decisions = new HashMap<>(16);
    private HashMap<Double, ScheduledImage> scheduledImages = new HashMap<>(16);

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
            synchronized (simulator.getState().getStoredImages()) {
                if (at.isReal()) {
                    // TODO filepath
                    if (isDeep) {
                        System.out.println("Adding image for agent: " + at.getId() + ", using " + tempHighResTP + " (it's real)");
                        //addImage(at.getId(), tempHighResTP, true);
                        double timeToAdd = simulator.getState().getTime() + DEEP_SCAN_TIME;
                        scheduledImages.put(timeToAdd, new ScheduledImage(at.getId(), tempHighResTP, true));
                    } else {
                        System.out.println("Adding image for agent: " + at.getId() + ", using " + tempLowResTP + " (it's real)");
                        //addImage(at.getId(), tempLowResTP, false);
                        double timeToAdd = simulator.getState().getTime() + DEEP_SCAN_TIME;
                        scheduledImages.put(timeToAdd, new ScheduledImage(at.getId(), tempLowResTP, false));
                    }

                } else {
                    // TODO filepath
                    if (isDeep) {
                        System.out.println("Adding image for agent: " +at.getId() + ", using " + tempHighResFP + " (it's real)");
                        //addImage(at.getId(), tempHighResFP, true);
                        double timeToAdd = simulator.getState().getTime() + SHALLOW_SCAN_TIME;
                        scheduledImages.put(timeToAdd, new ScheduledImage(at.getId(), tempHighResFP, true));
                    } else {
                        System.out.println("Adding image for agent: " +at.getId() + ", using " + tempLowResFP + " (it's real)");
                        //addImage(at.getId(), tempLowResFP, false);
                        double timeToAdd = simulator.getState().getTime() + SHALLOW_SCAN_TIME;
                        scheduledImages.put(timeToAdd, new ScheduledImage(at.getId(), tempLowResFP, false));
                    }
                }
            }
        }
    }

    /**
     * Adds the given image to the required maps by target id, filename, and tagged as deep or not
     * @param id
     * @param fileName
     * @param isDeep
     */
    private void addImage(String id, String fileName, boolean isDeep) {
        if (isDeep && !deepScannedTargets.contains(id)) {
            deepScannedTargets.add(id);
            simulator.getState().addToStoredImages(id, fileName);
        } else if (!isDeep && !shallowScannedTargets.contains(id) && !deepScannedTargets.contains(id)) {
            shallowScannedTargets.add(id);
            simulator.getState().addToStoredImages(id, fileName);
        }
    }

    /**
     * Refers to the addImage method using a ScheduleImage object
     * @param scheduledImage
     */
    private void addImage(ScheduledImage scheduledImage) {
        addImage(scheduledImage.getId(), scheduledImage.fileName, scheduledImage.isDeep);
    }

    /**
     * Searches, triggers, and removes the first image that is due to be shown
     * Only returns the first, but as it is called at every main loop iteration we can assume this won't be an issue,
     *  and it makes this a more efficient search
     */
    public void checkForImages(){
        double currentTime = simulator.getState().getTime();
        double keyToRemove = -1;
        for (var entry : scheduledImages.entrySet()) {
            if (currentTime > entry.getKey()) {
                keyToRemove = entry.getKey();
                break;
            }
        }

        if (keyToRemove != -1) {
            // System.out.println("Time = " + currentTime + ", triggering image of time = " + keyToRemove);
            addImage(scheduledImages.get(keyToRemove));
            scheduledImages.remove(keyToRemove);
        }
    }

    /**
     * Unused, but would allow us to request a given image if implemented. Is attached to the handler "/requestImage/"
     * @param id
     */
    public void requestImage(String id) {

    }

    /**
     * Called when an image is classified. Handles the addition of the record of this image and removes its target
     * @param ref File reference
     * @param status Whether it was classified P or N
     */
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

    private class ScheduledImage {
        private String id;
        private String fileName;
        private boolean isDeep;

        public ScheduledImage(String id, String fileName, boolean isDeep) {
            this.id = id;
            this.fileName = fileName;
            this.isDeep = isDeep;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public boolean isDeep() {
            return isDeep;
        }

        public void setDeep(boolean deep) {
            isDeep = deep;
        }
    }


}
