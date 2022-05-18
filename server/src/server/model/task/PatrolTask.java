package server.model.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;

import java.util.*;

public class PatrolTask extends Task {

    private final List<Coordinate> points;
    //Map of the last point that each agent visited
    private final Map<String, Integer> lastPointMap;
    private final List<Agent> workingAgents;
    private double totalPathDistance;

    public PatrolTask(String id, int type, List<Coordinate> points, Coordinate centrePoint, Boolean ignored) {
        super(id, type, centrePoint);
        this.points = points;
        this.workingAgents = new ArrayList<>();
        this.lastPointMap = new HashMap<>();
        this.totalPathDistance = calcualteRouteLength();
        this.setIgnored(ignored);
    }

    public static PatrolTask createTask(String id, List<Coordinate> points, Boolean ignored) {
        return new PatrolTask(id, Task.TASK_PATROL, points, getCentre(points), ignored);
    }

    private static Coordinate getCentre(List<Coordinate> points) {
        return Coordinate.findCentre(points.subList(0, points.size() - 1));
    }

    @Override
    boolean perform() {
        synchronized (this.getAgents()) {
            for (Agent agent : getAgents()) {
                if(agent.isWorking() && !workingAgents.contains(agent)) {
                    lastPointMap.put(agent.getId(), points.indexOf(getPreviousPoint(agent)));
                    workingAgents.add(agent);
                }
                if(agent.isWorking()) {
                    updateAgentRoute(agent);
                    if(agent.isCurrentDestinationReached())
                        lastPointMap.put(agent.getId(), lastPointMap.get(agent.getId()) < points.size() - 1 ? lastPointMap.get(agent.getId()) + 1 : 0);
                }
                else
                    workingAgents.remove(agent);
            }
        }
        Agent agentToRemove = null;
        for (Agent w : workingAgents) {
            if (w.getAllocatedTaskId() != null && !(w.getAllocatedTaskId().equals(getId()))) {
                agentToRemove = w;
                break;
            }
        }
        if (agentToRemove != null) {
            workingAgents.remove(agentToRemove);
        }
        return false;
    }

    /**
     * Make sure that the working agents are evenly spaced on the route.
     * Keeps the first agent moving and stops others that are behind it
     * until the space is big enough.
     */
    private void sortSpacing() {
        double tolerance = 0.05;

        if(workingAgents.size() > 1) {
            double spacing = 1D/workingAgents.size();
            List<Agent> sortedAgents = new ArrayList<>(workingAgents);
            Map<String, Double> agentPositions = new HashMap<>();
            for(Agent agent : workingAgents)
                agentPositions.put(agent.getId(), getAgentRelativePosition(agent));

            //Sort agents by their progress around the patrol
            Collections.sort(sortedAgents, new Comparator<Agent>() {
                @Override
                public int compare(Agent o1, Agent o2) {
                    return agentPositions.get(o1.getId()).compareTo(agentPositions.get(o2.getId()));
                }
            });

            for (int i = 0; i < sortedAgents.size(); i++) {
                //Keep first agent moving
                if(i == sortedAgents.size() - 1) {
                    Agent agent = sortedAgents.get(i);
                    if(agent.isStopped() && Simulator.instance.getState().getEditMode() == 1)
                        agent.resume();
                }
                else {
                    //Start/stop other agents if they are too close to the agent in front
                    Agent agent = sortedAgents.get(i);
                    Agent next = sortedAgents.get(i + 1);

                    double dBetween = agentPositions.get(next.getId()) - agentPositions.get(agent.getId());

                    if(dBetween < spacing - tolerance) {
                        if(!agent.isStopped()) {
                            System.out.println(123);
                            agent.stop();
                        }
                    }
                    else if(agent.isStopped() && Simulator.instance.getState().getEditMode() == 1)
                        agent.resume();
                }
            }
        }
    }

    /**
     * Get the progress of an agent around the patrol.
     * @param agent - Should be a working agent.
     * @return double between 0 and 1 where 0 is the start of the patrol and 1 is the end.
     */
    private double getAgentRelativePosition(Agent agent) {
        int lastPointIndex = lastPointMap.get(agent.getId());
        double absDistance = 0;
        for (int i = 0; i < points.size(); i++) {
            Coordinate point = points.get(i);
            if(i == lastPointIndex || i == points.size() - 1) {
                absDistance += point.getDistance(agent.getCoordinate());
                break;
            }
            absDistance += point.getDistance(points.get(i + 1));
        }
        return absDistance/totalPathDistance;
    }

    private synchronized void updateAgentRoute(Agent agent) {
        List<Coordinate> route = new ArrayList<>();
        if(lastPointMap.get(agent.getId()) < points.size() - 1)
            route.addAll(points.subList(lastPointMap.get(agent.getId()) + 1, points.size()));
        route.addAll(points.subList(0, lastPointMap.get(agent.getId()) + 1));
        agent.setRoute(route);
    }

    public List<Coordinate> getPoints() {
        return points;
    }

    /**
     * Based on the agent's current position, get the last point it would have passed through on the patrol route.
     * Gets the edge that the agent is on (or closest to) and returns the start point of that edge.
     */
    private Coordinate getPreviousPoint(Agent agent) {
        Coordinate nearest = null;
        double nearestDist = 0;
        double lat0 = this.getCoordinate().getLatitude();
        for(int i = 0; i < points.size() - 1; i++) {
            Coordinate p1 = this.points.get(i);
            Coordinate p2 = this.points.get(i+1);
            double[] res = getNearestPointOnEdge(p1, p2, agent.getCoordinate(), lat0);
            double dist = res[2];
            if(nearest == null || dist < nearestDist) {
                nearest = p1;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    /**
     * Get the nearest absolute point that is closest to the agent - i.e. any point on the patrol route not just
     * the vertices.
     * Uses the final point in the agent's route (or temp route if in edit mode) or position as a fallback.
     */
    public Coordinate getNearestPointAbsolute(Agent agent) {
        Coordinate nearest = null;
        double nearestDist = 0;
        double lat0 = this.getCoordinate().getLatitude();
        Coordinate agentPos = agent.getCoordinate();
        if(Simulator.instance.getState().getEditMode() == 2 && agent.getTempRoute().size() > 1)
            agentPos = agent.getTempRoute().get(agent.getTempRoute().size() - 2);
        else if(Simulator.instance.getState().getEditMode() == 1 && agent.getRoute().size() > 1)
            agentPos = agent.getRoute().get(agent.getRoute().size() - 2);
        for(int i = 0; i < points.size() - 1; i++) {
            Coordinate p1 = this.points.get(i);
            Coordinate p2 = this.points.get(i+1);
            double[] res = getNearestPointOnEdge(p1, p2, agentPos, lat0);
            double lat = res[0];
            double lng = res[1];
            double dist = res[2];
            if(nearest == null || dist < nearestDist) {
                nearest = new Coordinate(lat, lng);
                nearestDist = dist;
            }
        }
        return nearest;
    }

    public Coordinate getRandomPoint() {
        Random rand = new Random();
        Coordinate randomPoint = this.points.get(rand.nextInt(this.points.size()));
        return randomPoint;
    }

    /**
     * Get the point on a line between two points p1 and p2 that is closest to another point p.
     * @param p1 - Start of line
     * @param p2 - End of line
     * @param p - Reference point
     * @param lat0 - Reference latitude for cartesian conversion
     * @return nearest point (lat, lng) and distance to that point from p
     */
    private double[] getNearestPointOnEdge(Coordinate p1, Coordinate p2, Coordinate p, double lat0) {
        double[] c0 = p.toCartesian(lat0);
        double x0 = c0[0];
        double y0 = c0[1];

        double[] c1 = p1.toCartesian(lat0);
        double[] c2 = p2.toCartesian(lat0);
        double x1 = c1[0];
        double y1 = c1[1];
        double x2 = c2[0];
        double y2 = c2[1];
        double a = y1 - y2;
        double b = x2 - x1;
        double c = x1*y2 - x2*y1;
        double xNearest = (b*(b*x0 - a*y0) - a*c)/(a*a + b*b);
        double yNearest = (a*(-b*x0 + a*y0) - b*c)/(a*a + b*b);
        double r = getPositionOnLine(x1, y1, x2, y2, xNearest, yNearest);
        if(r < 0) {
            xNearest = x1;
            yNearest = y1;
        }
        else if (r > 1) {
            xNearest = x2;
            yNearest = y2;
        }
        Coordinate nearest = Coordinate.fromCartesian(xNearest, yNearest, lat0);
        double dist = nearest.getDistance(p);
        return new double[]{nearest.getLatitude(), nearest.getLongitude(), dist};
    }

    /**
     * Get the position of a point p (x,y) relative to two other points p1 (x1, y1) and p2 (x2, y2).
     * p, p1 and p2 are assumed to be co-linear.
     * @return   < 0 if p is beyond p1 e.g. p-p1---p2
     *         0 < 1 if p is between   e.g.   p1-p-p2
     *           > 1 if p is beyond p2 e.g.   p1---p2-p
     */
    private double getPositionOnLine(double x1, double y1, double x2, double y2, double x, double y) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dxy = Math.sqrt(dx*dx + dy*dy);

        // Distance from p to p1
        double dxp1 = x - x1;
        double dyp1 = y - y1;
        double dxyp1 = Math.sqrt(dxp1*dxp1 + dyp1*dyp1);
        if(dxyp1 > dxy)
            return dxyp1/dxy;

        // Distance from p to p2
        double dxp2 = x - x2;
        double dyp2 = y - y2;
        double dxyp2 = Math.sqrt(dxp2*dxp2 + dyp2*dyp2);
        if(dxyp2 > dxy)
            return -(dxyp2/dxy - 1);

        return dxyp1/dxy;
    }

    private double calcualteRouteLength() {
        double distance = 0;
        for(int i = 0; i < points.size() - 1; i++)
            distance += points.get(i).getDistance(points.get(i + 1));
        return distance;
    }

    public void updatePoints(List<Coordinate> points) {
        synchronized (this) {
            this.points.clear();
            this.points.addAll(points);
            this.setCoordinate(getCentre(points));
            this.totalPathDistance = calcualteRouteLength();
            this.resetAllocatedAgentRoutes();
            perform();
        }
    }

    private synchronized void resetAllocatedAgentRoutes() {
        for (Agent agent : this.getAgents()) {
            agent.stop();
            agent.setTempRoute(Collections.singletonList(this.getRandomPoint()));
        }
    }

    @Override
    public JsonObject serialize(JsonSerializationContext context) {
        JsonObject jsonObj = super.serialize(context);
        jsonObj.add("points", context.serialize(points));
        return jsonObj;
    }

}
