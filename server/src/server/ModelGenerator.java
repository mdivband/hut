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
import java.util.Arrays;

public class ModelGenerator {

    public static boolean run(State state, String webRef) {
        String droneRep = generateDroneRep(state);
        String taskRep = generateTaskRep(state);
        return generate(droneRep, taskRep, webRef+"/ModelFiles/currentDrones.txt", webRef+"/ModelFiles/currentTasks.txt");
    }

    public static boolean runOver(State state, String webRef) {
        String droneRep = generateDroneRep(state) + "0.0 0.0 1.0 0 0 0 1 1 \n";
        String taskRep = generateTaskRep(state);
        return generate(droneRep, taskRep, webRef+"/ModelFiles/add1drone.txt", webRef+"/ModelFiles/add1tasks.txt");
    }

    public static boolean runUnder(State state, String webRef) {
        String[] rep = generateDroneRep(state).split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<rep.length-1; i++) {
            sb.append(rep[i]).append("\n");
        }
        String taskRep = generateTaskRep(state);
        return generate(sb.toString(), taskRep, webRef+"/ModelFiles/remove1drone.txt", webRef+"/ModelFiles/remove1tasks.txt");

    }

    public static boolean generate(String droneRep, String taskRep, String dronesFileName, String tasksFileName) {
        try {
            FileWriter myWriter = new FileWriter(dronesFileName);
            myWriter.write(droneRep);
            myWriter.close();
            System.out.println("Wrote to "+dronesFileName);

            myWriter = new FileWriter(tasksFileName);
            myWriter.write(taskRep);
            myWriter.close();
            System.out.println("Wrote to "+tasksFileName);
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
            if (!(a instanceof Hub) && ((AgentVirtual) a).isAlive()) {
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
                int delivered = !a.getType().equals("withpack") ? 1 : 0;
                int recharge = ((AgentVirtual) a).isCharging() ? 1 : 0;
                int returning = ((AgentVirtual) a).isGoingHome() ? 1 : 0;
                int alive = ((AgentVirtual) a).isAlive() ? 1 : 0;
                int needsToTurn;
                if ((a).getRoute().isEmpty() || ((AgentVirtual) a).isGoingHome()) {
                    needsToTurn = 1;
                    //System.out.println(a.getId() + " cond 1; taskid="+a.getAllocatedTaskId());
                } else {
                    needsToTurn = Math.abs(((AgentVirtual) a).calculateAngleToGoal() - Math.toRadians(a.getHeading())) > 0.3F ? 1 : 0;
                    //System.out.println(a.getId() + " cond 2; angle="+(((AgentVirtual) a).calculateAngleToGoal() - Math.toRadians(a.getHeading()))+" ntt="+needsToTurn);
                }

                //int needsToTurn = ((AgentVirtual) a).calculateAngleToGoal() > 0.1F ? 1 : 0;
                sb.append(dLoc).append(" ")
                        .append(taskLoc).append(" ")
                        .append(bat).append(" ")
                        .append(delivered).append(" ")
                        .append(recharge).append(" ")
                        .append(returning).append(" ")
                        .append(alive).append(" ")
                        .append(needsToTurn).append("\n");
            }
        }
        return sb.toString();

    }

    public static String generateTaskRep(State state) {
        StringBuilder sb = new StringBuilder();
        Coordinate hubloc = Simulator.instance.getState().getHubLocation();
        for (Task t : state.getTasks()) {
            double offset = Simulator.instance.getState().calculateRandomValueFor("locationNoise"); // Random [-25, 25]
            double tLoc = hubloc.getDistance(t.getCoordinate()) + offset;
            //double tLoc = hubloc.getDistance(t.getCoordinate());
            sb.append(tLoc).append("\n");
        }
        return sb.toString();
    }

}
