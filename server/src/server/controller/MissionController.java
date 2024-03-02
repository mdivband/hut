package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.agents.Agent;
import server.model.target.Target;
import server.model.task.Task;

import java.util.*;

public class MissionController extends AbstractController {
    private int taskSpawnRate = 0;
    private double lastSpawn = -10000000;
    private double spawnRadius = 0;
    private double batchSize = 3;
    private double batchRadius = 200;
    private List<Integer[]> spawnPairs = new ArrayList<>();

    public MissionController(Simulator simulator) {
        super(simulator, ScoreController.class.getName());
    }

    public void spawnIfRequired(double time) {
        // Weird interaction around the timescale. For now I'm using a manual hacky workaround
        if (spawnPairs != null && (spawnPairs.get(0)[0] * (5 * simulator.getGameSpeed() / 10)) <= time) {
            taskSpawnRate = spawnPairs.get(0)[1];
            System.out.println("Updating to " + taskSpawnRate);
            if (spawnPairs.size() > 1) {
                spawnPairs.remove(0);
            } else {
                spawnPairs = null;
            }

        }

        if (lastSpawn + ((60d / (taskSpawnRate / batchSize)) * 5) < time) {
            lastSpawn = time;
            boolean acceptableSpawn = false;
            Coordinate newPos = null;
            while (!acceptableSpawn) {
                double theta = simulator.getRandom().nextDouble(2 * Math.PI);
                double r = simulator.getRandom().nextDouble(spawnRadius);
                //System.out.println("Spawning; r = " + r + ", th = " + theta);
                //Coordinate newPos = simulator.getState().getGameCentre().getCoordinate(r, theta);
                newPos = simulator.getState().getGameCentre().getCoordinateElliptical(r, theta, 1.5, 1);
                if (isAcceptable(newPos)) {
                    acceptableSpawn = true;
                }
            }
            simulator.getTaskController().createTask(0, newPos.getLatitude(), newPos.getLongitude());
            for (int i=1; i<batchSize; i++) {
                acceptableSpawn = false;
                Coordinate relPos = null;
                while (!acceptableSpawn) {
                    double theta = simulator.getRandom().nextDouble(2 * Math.PI);
                    double r = simulator.getRandom().nextDouble(batchRadius);
                    //System.out.println("Spawning; r = " + r + ", th = " + theta);
                    //Coordinate newPos = simulator.getState().getGameCentre().getCoordinate(r, theta);
                    relPos = newPos.getCoordinateElliptical(r, theta, 1.5, 1);
                    if (isAcceptable(relPos, 0.30)) {
                        acceptableSpawn = true;
                    }
                }
                simulator.getTaskController().createTask(0, relPos.getLatitude(), relPos.getLongitude());
            }
        }
    }

    public void setSpawnRadius(double spawnRadius) {
        this.spawnRadius = spawnRadius;
    }

    public void addPair(Integer spawnRate, Integer spawnTime) {
        spawnPairs.add(new Integer[]{spawnRate, spawnTime});
    }

    public void orderPairs() {
        spawnPairs.sort(Comparator.comparing(a -> a[0]));
    }

    private boolean isAcceptable(Coordinate position) {
        return isAcceptable(position, 1d);

    }

    private boolean isAcceptable(Coordinate position, double scale) {
        // Immediately return false if any condition is not met
        return simulator.getTaskController().getAllTasksAt(position, scale * 150).isEmpty() &&
                //simulator.getTargetController().getTargetAt(position, scale * 200) == null &&
                simulator.getAgentController().getAgentAt(position, scale * 400) == null;

    }

}
