package server;

import server.controller.*;
import server.model.Agent;
import server.model.Coordinate;
import server.model.Sensor;
import server.model.State;
import server.model.target.Target;
import server.model.task.Task;
import tool.GsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class MultiSimulator {

    private final Logger LOGGER = Logger.getLogger(MultiSimulator.class.getName());

    private int index = 0;
    private int maxUsers = 4;
    private final int startPort = 8000;

    private Simulator[] sims;

    public MultiSimulator() {
        sims = new Simulator[maxUsers];
    }

    // Temporary solution: Create several servers on different ports
    public static void main(String[] args) {
        // TODO At present, running separate Simulator objects in parallel on different ports does work, but this method
        // doesn't, I'm not sure why not.

        MultiSimulator multiSimulator = new MultiSimulator();
        for (int i=0; i< multiSimulator.getMaxUsers(); i++) {
            multiSimulator.createSim();
        }
    }

    private void createSim() {
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./logging.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default logging.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        //Setup GSON
        GsonUtils.registerTypeAdapter(Task.class, Task.taskSerializer);
        GsonUtils.registerTypeAdapter(State.HazardHitCollection.class, State.hazardHitsSerializer);
        GsonUtils.create();

        //Simulator simulator = new Simulator();
        //simulator.start(startPort + index, index);
        //sims[index] = simulator;
        sims[index] = new Simulator(this);
        sims[index].start(startPort + index, index);
        index++;
    }

    private Simulator[] getSims() {
        return sims;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

}
