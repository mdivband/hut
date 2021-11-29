package server.controller;

import server.Simulator;

import java.util.ArrayList;

public class ImageController {

    private Simulator simulator;

    // Filenames of high and low resolution true and false positives
    private ArrayList<String> highResTrue;
    private ArrayList<String> lowResTrue;
    private ArrayList<String> highResFalse;
    private ArrayList<String> LowResFalse;

    public ImageController(Simulator simulator) {
        this.simulator = simulator;
    }


    /**
     * Not yet sure how to define what image is taken
     */
    public String getImageName(int arg1, int arg2, int arg3) {
        return "exampleImg.png";
    }
}
