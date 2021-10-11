package server.model;

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

        if (a.agent.getId().contains("UAV-1")) {
            if (flag==0) {
                double lat = 50.931;
                double lng = -1.408;

                a.tempPlaceNewTask(new Coordinate(lat, lng));

                flag = 1;
            } else {
                double lat = 50.931;
                double lng = -1.408;
                a.broadcast("TASK_WAYPOINT;" + lat + "," + lng, 100);
                a.setHeading(70);
                a.moveAlongHeading(1);
            }
        } else if (a.isStopped()) {
            String nearestTask = a.getNearestTask();
            if (!nearestTask.equals("")) {
                a.setTask(a.getTaskById(nearestTask));
                //a.addToRoute(a.getTaskById(nearestTask));
                a.resume();
            }
            //a.followRoute();
        } else {
            List<Coordinate> c = a.getRoute();
            a.followRoute();
            //TODO must end the task when we get there
        }


        /*
        if (a.getId().contains("1")) {
            //if (Math.random() > 0.99)
                //a.setHeading(Math.random() * 360);
            a.performTask();
            //a.moveAlongHeading(1);
        } else {
            List<Agent> ns = a.getNeighboursAsAgentObjects(200);
            if (!ns.isEmpty()){
                double heading = ns.get(0).getHeading();
                a.setHeading(heading);
                if (ns.get(0).getSpeed()!=0) {
                    a.moveAlongHeading(1);
                }

            }
        }
    */



    }
}
