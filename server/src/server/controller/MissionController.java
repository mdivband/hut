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
    private final int batchRand = 1;  // Could merge this and above to a range
    private double batchRadius = 60;
    private List<Integer[]> spawnPairs = new ArrayList<>();

    public MissionController(Simulator simulator) {
        super(simulator, ScoreController.class.getName());
    }

    public void spawnIfRequired(double time) {
        // Weird interaction around the timescale. For now I'm using a manual hacky workaround
        if (spawnPairs != null && spawnPairs.get(0)[0] <= time) {
            taskSpawnRate = spawnPairs.get(0)[1];
            System.out.println("Updating to " + taskSpawnRate);
            if (spawnPairs.size() > 1) {
                spawnPairs.remove(0);
            } else {
                spawnPairs = null;
            }

        }

        if (lastSpawn + (60 / (taskSpawnRate / batchSize)) < time) {
            List<Task> tasksOutsideThisBatch = new ArrayList<>(simulator.getState().getTasks());
            List<Task> tasksInThisBatch = new ArrayList<>((int) (batchSize + batchRand));
            lastSpawn = time;
            boolean acceptableSpawn = false;
            Coordinate newPos = null;
            while (!acceptableSpawn) {
                double theta = simulator.getRandom().nextDouble(2 * Math.PI);
                double r = simulator.getRandom().nextDouble(spawnRadius);
                //System.out.println("Spawning; r = " + r + ", th = " + theta);
                //Coordinate newPos = simulator.getState().getGameCentre().getCoordinate(r, theta);
                newPos = simulator.getState().getGameCentre().getCoordinateElliptical(r, theta, 1.5, 1);
                if (isAcceptable(newPos, 1, tasksOutsideThisBatch)) {
                    acceptableSpawn = true;
                }
            }
            Task createdTask = simulator.getTaskController().createTask(0, newPos.getLatitude(), newPos.getLongitude());
            tasksInThisBatch.add(createdTask);
            Coordinate relPos = newPos.clone();
            for (int i=1; i<(batchSize + simulator.getRandom().nextInt(0, batchRand + 1)); i++) {
                acceptableSpawn = false;
                while (!acceptableSpawn) {
                    double theta = simulator.getRandom().nextDouble(2 * Math.PI);
                    double r = simulator.getRandom().nextDouble(batchRadius);
                    //System.out.println("Spawning; r = " + r + ", th = " + theta);
                    //Coordinate newPos = simulator.getState().getGameCentre().getCoordinate(r, theta);
                    relPos = relPos.getCoordinateElliptical(r, theta, 1.5, 1);
                    if (isAcceptable(relPos, 1, tasksOutsideThisBatch) && isAcceptable(relPos, 0.3,  tasksInThisBatch)) {  // May need to exclude
                        acceptableSpawn = true;
                    }
                }
                createdTask = simulator.getTaskController().createTask(0, relPos.getLatitude(), relPos.getLongitude());
                tasksInThisBatch.add(createdTask);
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

    private boolean isAcceptable(Coordinate position, double scale, List<Task> tasks) {
        // Immediately return false if any condition is not met
        return simulator.getTaskController().getAllTasksAt(position,  scale * 300, tasks).isEmpty() &&
                (simulator.getAgentController().getAgentAt(position, scale * 400) == null);

        //simulator.getTargetController().getTargetAt(position, scale * 200) == null
    }

    public void reset() {
        taskSpawnRate = 0;
        lastSpawn = -10000000;
        spawnRadius = 0;
        batchSize = 3;
        batchRadius = 75;
        spawnPairs = new ArrayList<>();  // NOTE: Has to be reinstantiated instead of using .clear() as may be null
    }
}
