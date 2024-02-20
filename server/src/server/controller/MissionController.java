package server.controller;

import server.Simulator;
import server.model.Coordinate;

public class MissionController extends AbstractController {
    private int taskSpawnRate = 0;
    private double lastSpawn = 0;
    private double spawnRadius = 0;

    public MissionController(Simulator simulator) {
        super(simulator, ScoreController.class.getName());
    }

    public void setTaskSpawnRate(int taskSpawnRate) {
        this.taskSpawnRate = taskSpawnRate;
    }

    public void spawnIfRequired(double time) {
        if (lastSpawn + ((60d / taskSpawnRate) * 6) < time) {
            lastSpawn = time;
            double theta = simulator.getRandom().nextDouble(2 * Math.PI);
            double r = simulator.getRandom().nextDouble(spawnRadius);
            System.out.println("Spawning; r = " + r + ", th = " + theta);
            Coordinate newPos = simulator.getState().getGameCentre().getCoordinate(r, theta);
            simulator.getTaskController().createTask(0, newPos.getLatitude(), newPos.getLongitude());
        }

    }

    public void setSpawnRadius(double spawnRadius) {
        this.spawnRadius = spawnRadius;
    }
}
