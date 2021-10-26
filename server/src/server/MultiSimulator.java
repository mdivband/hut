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

    private Logger LOGGER = Logger.getLogger(MultiSimulator.class.getName());

    private int index = 0;
    private int maxUsers = 4;
    private int startPort = 8000;

    private Simulator[] sims;

    public MultiSimulator() {
        sims = new Simulator[maxUsers];
    }

    public static void main(String[] args) {
        // Temporary (bad) solution: Create several servers on different ports
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

        Simulator simulator = new Simulator();
        simulator.start(startPort + index, index);
        sims[index] = simulator;
        index++;
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
