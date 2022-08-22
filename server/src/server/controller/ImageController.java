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
import java.util.logging.FileHandler;

public class ImageController extends AbstractController {

    private final int SHALLOW_SCAN_TIME = 18;  // In-game seconds, so use 6*real-life seconds

    private final List<String> shallowScannedTargets = new ArrayList<>(16);
    private final Map<String, Boolean> decisions = new HashMap<>(16);
    private HashMap<Double, ScheduledImage> scheduledImages = new HashMap<>(16);


    public ImageController(Simulator simulator) {
        super(simulator, ImageController.class.getName());
    }

    public void reset() {
        shallowScannedTargets.clear();
        decisions.clear();
        scheduledImages.clear();
    }

    public void takeImageById(String id) {
        boolean match =  false;
        for (var entry : scheduledImages.entrySet()) {
            if (entry.getValue().id.equals(id)) {
                match = true;
                break;
            }
        }
        if (!match) {
            //simulator.getState().getPendingIds().add(id);
            takeImage(simulator.getState().getTarget(id).getCoordinate());
        }
    }

    /**
     * This finds the target, checks if it's a TP or FP, "takes an image", and stores it in the hashmap for inspection
     */
    public void takeImage(Coordinate coordinate) {
        Target t = simulator.getTargetController().getTargetAt(coordinate);
        if (t instanceof AdjustableTarget at) {  // This also asserts that t is not null
            synchronized (simulator.getState().getTargetData()) {
                List<String> dataToAdd = at.getData();
                double timeToAdd = simulator.getState().getTime() + SHALLOW_SCAN_TIME;

                // TODO this is where we will decide how much info to share
                scheduledImages.put(timeToAdd, new ScheduledImage(at.getId(), dataToAdd));

                LOGGER.info(String.format("%s; TKIMG; Taking image for target of deep/shallow type with actual classification (id, filename, isReal); %s; %s; %s", Simulator.instance.getState().getTime(), at.getId(), dataToAdd, at.isReal()));

            }
        }
    }

    /**
     * Adds the given image to the required maps by target id, filename, and tagged as deep or not
     * @param id
     */
    private void addImage(String id, List<String> data) {
        simulator.getState().addToTargetData(id, data);
    }

    /**
     * Refers to the addImage method using a ScheduleImage object
     * @param scheduledImage
     */
    private void addImage(ScheduledImage scheduledImage) {
        addImage(scheduledImage.getId(), scheduledImage.data);
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
            addImage(scheduledImages.get(keyToRemove));
            simulator.getState().getPendingIds().remove(scheduledImages.get(keyToRemove).id);
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
     * @param id NOT the file ref; this is now the target ID
     * @param status Whether it was classified P or N
     */
    public void classify(String id, boolean status) {
        try {
            // id is the id of the target we want (the above line searches the Map by value, and assumes 1:1 mapping)
            decisions.put(id, status);

            boolean isReal = ((AdjustableTarget) simulator.getState().getTarget(id)).isReal();

            shallowScannedTargets.remove(id);
            Simulator.instance.getState().getTargetData().remove(id);

            // Foreach loop automatically handles the null case (no tasks found) by not entering
            for (Task t : simulator.getTaskController().getAllTasksAt(simulator.getState().getTarget(id).getCoordinate())) {
                t.complete();
            }
            simulator.getTargetController().deleteTarget(id);

            LOGGER.info(String.format("%s; CLIMG; Classifying target from deep/shallow scan as this, it is actually (id, classifiedStatus, ActualStatus); %s; %s; %s;", Simulator.instance.getState().getTime(), id, status, isReal));
        } catch (Exception ignored) {}



    }

    public Map<String, Boolean> getDecisions() {
        return decisions;
    }

    private class ScheduledImage {
        private String id;
        private List<String> data;

        public ScheduledImage(String id, List<String> data) {
            this.id = id;
            this.data = data;
        }

        @Override
        public String toString() {
            return "ScheduledImage{" +
                    "id='" + id + '\'' +
                    ", fileName='" + data + '\'' +
                    '}';
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getData() {
            return data;
        }

        public void setData(List<String> data) {
            this.data = data;
        }
    }


}
