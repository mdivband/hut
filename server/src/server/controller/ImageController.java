package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ImageController {

    private Simulator simulator;
    private final List<String> deepScannedTargets = new ArrayList<>(16);
    private final List<String> shallowScannedTargets = new ArrayList<>(16);

    // Filenames of high and low resolution true and false positives
    private ArrayList<String> highResTrue;
    private ArrayList<String> lowResTrue;
    private ArrayList<String> highResFalse;
    private ArrayList<String> LowResFalse;

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
                        addImage(at.getId(), tempHighResTP, true);
                    } else {
                        addImage(at.getId(), tempLowResTP, false);
                    }

                } else {
                    // TODO filepath
                    if (isDeep) {
                        addImage(at.getId(), tempHighResFP, true);
                    } else {
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


}
