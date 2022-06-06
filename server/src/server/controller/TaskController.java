package server.controller;

import server.Simulator;
import server.model.agents.Agent;
import server.model.agents.AgentHub;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.task.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskController extends AbstractController {

    private static int uniqueTaskNumber = 1;

    public TaskController(Simulator simulator) {
        super(simulator, TaskController.class.getName());
    }

    private String generateUID() {
        return "Task-" + uniqueTaskNumber++;
    }

    public synchronized Task createTask(int taskType, double lat, double lng) {
        synchronized (simulator.getState().getTasks()) {
            String id = generateUID();
            Task task;
            switch (taskType) {
                case Task.TASK_WAYPOINT:
                    task = new WaypointTask(id, new Coordinate(lat, lng));
                    break;
                case Task.TASK_MONITOR:
                    task = new MonitorTask(id, new Coordinate(lat, lng));
                    break;
                case Task.TASK_DEEP_SCAN:
                    synchronized (simulator.getState().getTasks()) {
                        task = new DeepScanTask(id, new Coordinate(lat, lng));
                    }
                    simulator.getTargetController().adjustForTask(AdjustableTarget.ADJ_DEEP_SCAN, lat, lng);
                    break;
                case Task.TASK_SHALLOW_SCAN:
                    task = new ShallowScanTask(id, new Coordinate(lat, lng));
                    simulator.getTargetController().adjustForTask(AdjustableTarget.ADJ_SHALLOW_SCAN, lat, lng);
                    break;
                case Task.TASK_VISIT:
                    task = new VisitTask(id, new Coordinate(lat, lng));
                    break;
                default:
                    throw new IllegalArgumentException("Unable to create task of type " + taskType);
            }
            simulator.getState().add(task);

            if (task instanceof DeepScanTask) {
                task.setPriority(100);
                simulator.getAllocator().dynamicReassign(task);
                String trgId = simulator.getTargetController().getTargetAt(new Coordinate(lat, lng)).getId();
                LOGGER.info(String.format("%s; DPSCN; Creating a deep scan task of id and for target (id, targetId); %s; %s", Simulator.instance.getState().getTime(), id, trgId));

            }
            for (Agent a : simulator.getState().getAgents()) {
                if (!(a instanceof AgentHub) && a.getTask() != null) {
                    a.resume();
                }
            }
            LOGGER.info(String.format("%s; CRWP; Created new task (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, lat, lng));
            return task;
        }
    }

    public synchronized Task createPatrolTask(List<Coordinate> path) {
        String id = generateUID();
        Task task = PatrolTask.createTask(id, path);
        simulator.getState().add(task);
        StringBuilder sb = new StringBuilder();
        for (Coordinate c : path) {
            sb.append(c.getLatitude()).append(";").append(c.getLongitude()).append(";");
        }
        LOGGER.info(String.format("%s; CRPT; Created new patrol task with points (id, lat1, lng1, lat2, lng2, ...); %s; %s", Simulator.instance.getState().getTime(), id, sb));
        //LOGGER.info(String.format("%s; CRPT; Created new patrol task with centre (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
        return task;
    }

    public synchronized boolean updatePatrolPath(String id, List<Coordinate> path) {
        Task task = simulator.getState().getTask(id);
        if(task.getType() == Task.TASK_PATROL) {
            ((PatrolTask) task).updatePoints(path);
            return true;
        }
        return false;
    }

    public synchronized Task createRegionTask(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        String id = generateUID();
        Task task = RegionTask.createTask(id, nw, ne, se, sw);
        simulator.getState().add(task);
        StringBuilder sb = new StringBuilder();
        sb.append(nw.getLatitude()).append(";").append(nw.getLongitude()).append(";");
        sb.append(ne.getLatitude()).append(";").append(ne.getLongitude()).append(";");
        sb.append(se.getLatitude()).append(";").append(se.getLongitude()).append(";");
        sb.append(sw.getLatitude()).append(";").append(sw.getLongitude()).append(";");
        LOGGER.info(String.format("%s; CRRG; Created new region task with corners (id, nwlat, nwlng, nelat, nelng, selat, selng, swlat, swlng,); %s; %s", Simulator.instance.getState().getTime(), id, sb));


        //LOGGER.info(String.format("%s; CRRG; Created new region task with centre (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
        return task;
    }

    public synchronized boolean updateRegionCorners(String id, List<Coordinate> corners) {
        Task task = simulator.getState().getTask(id);
        if(task.getType() == Task.TASK_REGION) {
            ((RegionTask) task).updateCorners(corners.get(0), corners.get(1), corners.get(2), corners.get(3));
            return true;
        }
        return false;
    }

    public synchronized Task updateTaskPosition(String id, double lat, double lng) {
        Task task = simulator.getState().getTask(id);
        if (!task.getCoordinate().equals(new Coordinate(lat, lng))) {
            task.getCoordinate().set(lat, lng);
            LOGGER.info("Moved task " + id + " to " + lat + ", " + lng);
            LOGGER.info(String.format("%s; MVTSK; Moved task to (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
        }
        return task;
    }

    public synchronized boolean deleteTask(String id, boolean completed) {
        Task task = simulator.getState().getTask(id);
        if (task == null) {
            LOGGER.warning("Attempted to remove missing task " + id);
            return false;
        }

        removeTaskAllocations(id, simulator.getState().getAllocation());
        removeTaskAllocations(id, simulator.getState().getTempAllocation());

        for(Agent agent : task.getAgents()) {
            agent.setTempRoute(new ArrayList<>());
            agent.setRoute(new ArrayList<>());
            agent.setWorking(false);
            agent.setAllocatedTaskId("");
            agent.stop();
        }

        simulator.getState().remove(task);
        LOGGER.info(String.format("%s; DELTSK; Removed task (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));

        if(completed) {
            simulator.getState().addCompletedTask(task);
        }
        return true;
    }

    /**
     * Deletes the task with the given coordinates
     * @param coord Coordinate to check
     */
    public void deleteTaskByCoords(Coordinate coord) {
        try {
            findTaskByCoord(coord).setStatus(Task.STATUS_DONE);
        } catch (Exception e){
            // For now we leave this. It's rare, but when deleting an already completed task this throws an error
            // It doesn't matter that we let this through, because it was already gone
        }
    }

    /**
     * Searcges the nearby area for any task and returns the first match
     * @param position Coordinate around which to check
     * @param eps Epsilon value to search around
     * @return The first nearby task, or null if none found
     */
    public Coordinate checkIfNearbyTaskComplete(Coordinate position, double eps){
        for (Task t :simulator.getState().getTasks()) {
            Coordinate thisPos = t.getCoordinate();
            if(Math.abs(position.getLatitude() - thisPos.getLatitude()) < eps &&
               Math.abs(position.getLongitude() - thisPos.getLongitude()) < eps) {
                // The agent is close enough to this task, so we report it
                return thisPos;
            }
        }
        return null;
    }

    public synchronized void updateTaskGroup(String taskId, int group) {
        simulator.getState().getTask(taskId).setGroup(group);
    }

    public synchronized void updateTaskPriority(String taskId, double priority) {
        simulator.getState().getTask(taskId).setPriority(priority);
    }

    private void removeTaskAllocations(String taskId, Map<String, String> allocation) {
        List<String> keysToRemove = new ArrayList<>();
        for(Map.Entry<String, String> e : allocation.entrySet()) {
            if(e.getValue().equals(taskId))
                keysToRemove.add(e.getKey());
        }
        for(String key : keysToRemove)
            allocation.remove(key);
    }

    /**
     * Removes tasks matching this coordinate. It may be that we need to introduce an epsilon value later
     * @param coordinate
     */
    public void removeAllTasksAt(Coordinate coordinate) {
        simulator.getState().getTasks().removeIf(tsk -> tsk.getCoordinate().equals(coordinate));
    }

    public List<Task> getAllTasksAt(Coordinate coordinate) {
       List<Task> foundTasks = new ArrayList<>(2);

       for (Task t : simulator.getState().getTasks()) {
           if (t.getCoordinate().equals(coordinate)) {
               foundTasks.add(t);
           }
       }

       return foundTasks;
    }

    public synchronized void resetTaskNumbers() {
        this.uniqueTaskNumber = 1;
    }

    public Task findTaskByCoord(Coordinate coordinate) {
        return simulator.getState().getTaskByCoordinate(coordinate);
    }

    public boolean checkForFreeTasks() {
        return simulator.getState().getTasks().stream().anyMatch(a -> a.getAgents().isEmpty());
    }

    public void createGroundTask(Coordinate coordinate, int prio) {
        GroundTask groundTask = new GroundTask(generateUID(), coordinate);
        groundTask.setPriority(prio);
        simulator.getState().add(groundTask);

    }
}
