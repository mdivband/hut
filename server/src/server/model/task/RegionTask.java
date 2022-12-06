package server.model.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;

import java.util.*;
import java.util.stream.Collectors;

public class RegionTask extends PatrolTask {
    private static final double baseStep = 40;
    private Coordinate nw, ne, se, sw;

    private HashSet<Integer> coveredPoints = new HashSet<>();

    public RegionTask(String id, List<Coordinate> route, Coordinate centrePoint, Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        super(id, Task.TASK_REGION, route, centrePoint);
        this.nw = nw;
        this.ne = ne;
        this.se = se;
        this.sw = sw;
    }

    public static RegionTask createTask(String id, Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        List<Coordinate> route = createRoute(nw, ne, se, sw);
        return new RegionTask(id, route, getCentre(Arrays.asList(nw, ne, se, sw)), nw, ne, se, sw);
    }

    // TODO here we need to make new agents factor in the covered points (HashSet provided) so they continue where the last one left off
    public void addAgent(Agent agent) {
        super.addAgent(agent);
        List<Coordinate> toRemove = coveredPoints.stream().map(points::get).toList();
        points.removeAll(toRemove);
        coveredPoints.clear();
        lastPointMap.clear();
        workingAgents.clear();
    }

    private static Coordinate getCentre(List<Coordinate> corners) {
        return Coordinate.findCentre(corners);
    }

    boolean perform() {
        boolean testSet = false;
        synchronized (getAgents()) {
            // TODO Occasionally misses a small area (I think due to readjusting after agent adding)
            if (getAgents().stream().anyMatch(a -> (a.isWorking() && !workingAgents.contains(a)))) {
                testSet = true;
                lastPointMap.put(getAgents().get(0).getId(), points.indexOf(getPreviousPoint(getAgents().get(0))));
                workingAgents.add(getAgents().get(0));
                if (getAgents().size() > 1) {
                    for (int a = 1; a < getAgents().size(); a++) {
                        int relPos = (lastPointMap.get(getAgents().get(0).getId()) + (a * (points.size() / getAgents().size()))) % points.size();
                        workingAgents.add(getAgents().get(a));
                        lastPointMap.put(getAgents().get(a).getId(), relPos);
                        getAgents().get(a).setWorking(true);
                        getAgents().get(a).resume();
                    }
                }
            }

            for (Agent agent : getAgents()) {
                if (agent.isWorking() && !workingAgents.contains(agent)) {
                    lastPointMap.put(agent.getId(), points.indexOf(getPreviousPoint(agent)));
                    workingAgents.add(agent);
                }
                if (agent.isWorking()) {
                    updateAgentRoute(agent);
                    if (agent.isCurrentDestinationReached()) {
                        if (!testSet) {
                            coveredPoints.add(lastPointMap.get(agent.getId()));
                        }
                        lastPointMap.put(agent.getId(), lastPointMap.get(agent.getId()) < points.size() - 1 ? lastPointMap.get(agent.getId()) + 1 : 0);
                        synchronized (getAgents()) {
                            while (agent.getRoute().size() > points.size() - coveredPoints.size()) {
                                agent.getRoute().remove(agent.getRoute().size() - 1);
                            }
                        }
                    }
                } else
                    workingAgents.remove(agent);
            }
            Agent agentToRemove = null;
            for (Agent w : workingAgents) {
                if (!(w.getAllocatedTaskId().equals(getId()))) {
                    agentToRemove = w;
                    break;
                }
            }
            if (agentToRemove != null) {
                synchronized (workingAgents) {
                    workingAgents.remove(agentToRemove);
                }
            }

            // UNCOMMENT THIS IF YOU WANT COMPLETABLE REGION TASKS
            // TODO make this an argument of RegionTasks contained in scenario file
            if (coveredPoints.size() == points.size()) {
                // By definition, this means we're done
                return true;
            }
            return false;
        }
    }

    @Override
    public void updatePoints(List<Coordinate> points) {
        super.updatePoints(points);
        this.setCoordinate(getCentre(points));
    }

    private static List<Coordinate> createRoute(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        double width = nw.getDistance(ne);
        double height = nw.getDistance(sw);

        List<Coordinate> points = new ArrayList<>();
        int r = (int) Math.floor((width - baseStep)/(2*baseStep));
        if(width <= baseStep || height <= baseStep || r == 0) {
            points.add(nw);
            points.add(ne);
            points.add(se);
            points.add(sw);
            points.add(nw);
        }
        else {
            double dx = (width - baseStep)/(2*r);
            double dy = height - baseStep;

            double NORTH = Math.PI/180.0D * 90.0F;
            double EAST = 0;
            double SOUTH = Math.PI/180.0D * 270.0F;

            points.add(nw);
            Coordinate currentPoint = nw;
            for(int i = 0; i < r; i++) {
                currentPoint = currentPoint.getCoordinate(dx, EAST);
                points.add(currentPoint);
                currentPoint = currentPoint.getCoordinate(dy, SOUTH);
                points.add(currentPoint);
                currentPoint = currentPoint.getCoordinate(dx, EAST);
                points.add(currentPoint);
                currentPoint = currentPoint.getCoordinate(dy, NORTH);
                points.add(currentPoint);
            }
            currentPoint = currentPoint.getCoordinate(baseStep, EAST);
            points.add(currentPoint);
            points.add(se);
            points.add(sw);
            points.add(nw);
        }
        return points;
    }

    public synchronized void updateCorners(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        this.nw = nw;
        this.ne = ne;
        this.se = se;
        this.sw = sw;
        this.updatePoints(createRoute(nw, ne, se, sw));
    }

    @Override
    public JsonObject serialize(JsonSerializationContext context) {
        List<Coordinate> corners = Arrays.asList(nw, ne, se, sw);
        JsonObject jsonObj = super.serialize(context);
        jsonObj.add("corners", context.serialize(corners));
        return jsonObj;
    }

}
