package server;

import server.model.agents.Agent;

import java.util.HashMap;
import java.util.OptionalDouble;

public class Modeller {
    private Simulator simulator;

    public Modeller(Simulator simulator) {
        this.simulator = simulator;
    }

    public double calculateAll() {
        //OptionalDouble average = simulator.getState().getAgents().stream().filter(a -> a.getAllocatedTaskId() != null).mapToDouble(this::calculate).average();
        OptionalDouble average = simulator.getState().getAgents().stream().filter(a -> a.getAllocatedTaskId() != null).mapToDouble(a -> Math.sqrt(calculate(a))).average();
        if (average.isPresent()) {
            return 100 * average.getAsDouble();
        }
        return 0;
    }

    public double calculate(Agent agent) {
        //double distanceFromHub = agent.getCoordinate().getDistance(simulator.getState().getHubLocation());
        double distanceFromHub = agent.getTask().getCoordinate().getDistance(simulator.getState().getHubLocation());
        int batteryLevel;
        if (agent.getBattery() > 0.75) {
            batteryLevel = 2;
        } else if (agent.getBattery() > 0.25) {
            batteryLevel = 1;
        } else {
            batteryLevel = 0;
        }

        // Clumsy conditional approach. Otherwise we will create an object to check the relevant info and hashmap it
        if (distanceFromHub > 800) {
            return switch (batteryLevel) {
                case 2 -> 0.112;
                case 1 -> 0.017;
                default -> 0.000;
            };
        } else if (distanceFromHub > 700) {
            return switch (batteryLevel) {
                case 2 -> 0.195;
                case 1 -> 0.041;
                default -> 0.002;
            };
        } else if (distanceFromHub > 600) {
            return switch (batteryLevel) {
                case 2 -> 0.321;
                case 1 -> 0.095;
                default -> 0.007;
            };
        } else if (distanceFromHub > 500) {
            return switch (batteryLevel) {
                case 2 -> 0.490;
                case 1 -> 0.204;
                default -> 0.026;
            };
        } else if (distanceFromHub > 400) {
            return switch (batteryLevel) {
                case 2 -> 0.890;
                case 1 -> 0.671;
                default -> 0.227;
            };
        } else if (distanceFromHub > 300) {
            return switch (batteryLevel) {
                case 2 -> 0.923;
                case 1 -> 0.793;
                default -> 0.384;
            };
        } else {
            return switch (batteryLevel) {
                case 2 -> 0.957;
                case 1 -> 0.911;
                default -> 0.659;
            };
        }
    }


}
