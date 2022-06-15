package server;

import server.model.Coordinate;
import server.model.State;
import server.model.agents.Agent;
import server.model.agents.MissionProgrammer;
import server.model.task.Task;
import tool.GsonUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class RLWrapper {
    private Coordinate hubPosition;
    private HashMap<Agent, Coordinate> agentPositions = new HashMap<>();
    private Simulator simulator;
    // Making missionProgrammer static just makes alot of things easier, and there should only ever be 1 anyway
    private static MissionProgrammer missionProgrammer;

    public static void main(String[] args) {
        RLWrapper rlWrapper = new RLWrapper();
        rlWrapper.runRL("coverageTest.json");
    }

    public void runRL(String scenarioName) {
        startRL(scenarioName);
        try {
            // I think this busy waiting is fine. This is only the wrapper thread so we aren't slowing anything down,
            //  probably better to change this in future, but for now it will do -WH
            while (true) {
                Thread.sleep(100000);
                restart();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private void restart() {
        missionProgrammer.complete();
        simulator.softReset(missionProgrammer);
        simulator.startSimulation();
    }

    public void startRL(String scenarioName) {
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        //Setup GSON
        GsonUtils.registerTypeAdapter(Task.class, Task.taskSerializer);
        GsonUtils.registerTypeAdapter(State.HazardHitCollection.class, State.hazardHitsSerializer);
        GsonUtils.create();

        int port = 44101;

        simulator = new Simulator();
        simulator.start(port);
        simulator.loadScenarioMode(scenarioName);
        simulator.startSimulation();
    }

    public HashMap<Agent, Coordinate> getAgentPositions() {
        return agentPositions;
    }

    public void addAgent(Agent agent) {
        agentPositions.put(agent, agent.getCoordinate());
    }

    public void setMissionProgrammer(MissionProgrammer missionProgrammer) {
        RLWrapper.missionProgrammer = missionProgrammer;
    }

    public MissionProgrammer getMissionProgrammer() {
        return missionProgrammer;
    }
}
