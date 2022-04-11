package server;

import server.model.Coordinate;
import server.model.State;
import server.model.agents.Agent;
import server.model.agents.AgentVirtual;
import server.model.agents.Hub;
import server.model.task.Task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ModelGenerator {

    public static boolean run(State state) {
        String droneRep = generateDroneRep(state);
        String taskRep = generateTaskRep(state);
        try {
            FileWriter myWriter = new FileWriter("drones.txt");
            myWriter.write(droneRep);
            myWriter.close();
            System.out.println("Wrote to drones.txt");
            myWriter.close();

            myWriter = new FileWriter("tasks.txt");
            myWriter.write(taskRep);
            myWriter.close();
            System.out.println("Wrote to tasks.txt");
            myWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static String generateDroneRep(State state) {
        StringBuilder sb = new StringBuilder();
        Coordinate hubloc = Simulator.instance.getState().getHubLocation();
        for (Agent a : state.getAgents()) {
            if (!(a instanceof Hub)) {
                double dLoc = hubloc.getDistance(a.getCoordinate());
                double taskLoc;
                if (a.getTask() != null) {
                    taskLoc = hubloc.getDistance(a.getTask().getCoordinate());
                } else {
                    taskLoc = -1;  // -1 if no task
                }

                if (!a.getType().equals("withpack")) {  // if delivered
                    taskLoc = 0;  // 0 if delivered
                }

                double bat = a.getBattery();
                int delivered = (!a.getType().equals("withpack")) ? 1 : 0;
                int recharge = (((AgentVirtual) a).isCharging()) ? 1 : 0;
                int returning = (((AgentVirtual) a).isGoingHome()) ? 1 : 0;
                int alive = (((AgentVirtual) a).isAlive()) ? 1 : 0;
                sb.append(dLoc).append(" ")
                        .append(taskLoc).append(" ")
                        .append(bat).append(" ")
                        .append(delivered).append(" ")
                        .append(recharge).append(" ")
                        .append(returning).append(" ")
                        .append(alive).append("\n");
            }
        }
        return sb.toString();

    }

    public static String generateTaskRep(State state) {
        StringBuilder sb = new StringBuilder();
        Coordinate hubloc = Simulator.instance.getState().getHubLocation();
        for (Task t : state.getTasks()) {
            //String tskId = t.getId();
            double tLoc = hubloc.getDistance(t.getCoordinate());
            //sb.append(tskId).append(",")
            sb.append(tLoc).append("\n");
        }
        return sb.toString();
    }

}
