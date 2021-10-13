package server.model;

import java.util.ArrayList;
import java.util.List;

public class AgentProgrammer {
    ProgrammerHandler a;

    int flag = 0;

    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    public void setup() {
        // Initial setup function
        a.declareSelf(100);

    }

    public void step() {
        // Called at every time step (currently 200ms)
        if (a.agent.getId().contains("UAV-1") && flag == 0) {
            double lat = 50.931;
            double lng = -1.408;

            List<Coordinate> thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);

            lat = 50.932;
            lng = -1.410;

            thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);

            lat = 50.929;
            lng = -1.409;
            thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);

            lat = 50.920;
            lng = -1.408;
            thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);


            flag = 1;

        } else
            if(a.agent.getId().contains("UAV-3") && flag == 0) {
            double lat = 50.936;
            double lng = -1.4105;
            List<Coordinate> thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);

            lat = 50.9347;
            lng = -1.403;
            thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);

            lat = 50.93412;
            lng = -1.40687;
            thisTask = new ArrayList<>();
            thisTask.add(new Coordinate(lat, lng));
            a.tempPlaceNewTask("waypoint", thisTask);

            flag = 1;

        } else if (a.isStopped() && (a.agent.getId().contains("1") || a.agent.getId().contains("2") || a.agent.getId().contains("3"))) {
                
            //List<Coordinate> nearestTask = a.getNearestTask();
            List<Coordinate> nearestTask = a.getNearestEmptyTask();
            if (!nearestTask.isEmpty()) {
                if (a.getAgentsAssigned(nearestTask).isEmpty()) {
                    a.setTask(nearestTask);
                    a.resume();
                }
            }
        } else if (a.agent.getId().contains("1") || a.agent.getId().contains("2") || a.agent.getId().contains("3")) {
            a.followRoute();
        } else if (a.hasNeighbours()) {
            flock();
        }

    }

    public void flock(){
        // TODO this is a little bugged, as it continues to average across agents who have left its detection radius
        a.setHeading(a.calculateAverageNeighbourHeading());
        a.moveAlongHeading(1);
    }

}
