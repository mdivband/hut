package server.model.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RegionTask extends PatrolTask {

    private static final double baseStep = 40;
    private Coordinate nw, ne, se, sw;
    private int currentRotation;

    public RegionTask(String id, List<Coordinate> route, Coordinate centrePoint, Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw, Boolean ignored) {
        super(id, Task.TASK_REGION, route, centrePoint, ignored);
        this.nw = nw;
        this.ne = ne;
        this.se = se;
        this.sw = sw;
        this.group = 10; // hard coding 10 agents to complete region task
        this.setIgnored(ignored);
        this.currentRotation = 0;
    }

    public static RegionTask createTask(String id, Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw, Boolean ignored) {
        List<Coordinate> route = createRoute(nw, ne, se, sw);
        return new RegionTask(id, route, getCentre(Arrays.asList(nw, ne, se, sw)), nw, ne, se, sw, ignored);
    }

    private static Coordinate getCentre(List<Coordinate> corners) {
        return Coordinate.findCentre(corners);
    }

    @Override
    public void updatePoints(List<Coordinate> points) {
        super.updatePoints(points);
        this.setCoordinate(getCentre(points));
    }

    private static List<Coordinate> createRoute(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        return createRoute(nw, ne, se, sw, 0);
    }

    private static List<Coordinate> createRoute(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw, int currentRotation) {
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
            double WEST = Math.PI;
            double OLD_NORTH;
            double OLD_EAST;
            double OLD_SOUTH;
            double OLD_WEST;
            for (int i = 0; i < currentRotation; i++) {
                OLD_NORTH = NORTH;
                OLD_EAST = EAST;
                OLD_SOUTH = SOUTH;
                OLD_WEST = WEST;
                EAST = OLD_NORTH;
                SOUTH = OLD_EAST;
                WEST = OLD_SOUTH;
                NORTH = OLD_WEST;
            }

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

    public synchronized void rotateRoute() {
        this.currentRotation++;
        if (this.currentRotation == 4) {
            currentRotation = 0;
        }
        Coordinate old_nw;
        Coordinate old_ne;
        Coordinate old_se;
        Coordinate old_sw;
        Coordinate new_nw = this.nw;
        Coordinate new_ne = this.ne;
        Coordinate new_se = this.se;
        Coordinate new_sw = this.sw;
        for (int i = 0; i < this.currentRotation; i++) {
            old_nw = new_nw;
            old_ne = new_ne;
            old_se = new_se;
            old_sw = new_sw;
            new_nw = old_sw;
            new_ne = old_nw;
            new_se = old_ne;
            new_sw = old_se;
        }
        List<Coordinate> points = this.createRoute(new_nw, new_ne, new_se, new_sw, currentRotation);
        this.updatePoints(points);
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
