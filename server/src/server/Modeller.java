package server;

import server.model.agents.Agent;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.Map;

public class Modeller {
    private Simulator simulator;
    private ArrayList<ModellerRecord> pendingRecords = new ArrayList<>();
    private ArrayList<ModellerRecord> loggedRecords = new ArrayList<>();
    private boolean started = false;

    public Modeller(Simulator simulator) {
        this.simulator = simulator;
    }

    public double calculateAll() {
        //OptionalDouble average = simulator.getState().getAgents().stream().filter(a -> a.getAllocatedTaskId() != null).mapToDouble(this::calculate).average();
        double result = simulator.getState().getAgents().stream().filter(a -> a.getAllocatedTaskId() != null).mapToDouble(this::calculate).reduce(1, (a, b) -> a * b);
        if (!started && pendingRecords.isEmpty()) {
            pendingRecords.add(new ModellerRecord(simulator.getState().getTime(), result, simulator.getState().getAllocation()));
        } else if (started) {
            //System.out.println("Adding a new record : ");
            //simulator.getState().getTempAllocation().forEach((k, v) -> System.out.println(k + " -> " + v));
            //System.out.println();
            pendingRecords.add(new ModellerRecord(simulator.getState().getTime(), result, simulator.getState().getAllocation()));
        }
        return 100 * result;
    }

    public double calculate(Agent agent) {
        //double distanceFromHub = agent.getCoordinate().getDistance(simulator.getState().getHubLocation());
        double distanceFromHub = agent.getTask().getCoordinate().getDistance(simulator.getState().getHubLocation());
        int batteryLevel;
        if (agent.getBattery() > 0.4) {
            batteryLevel = 2;
        } else if (agent.getBattery() > 0.15) {
            batteryLevel = 1;
        } else {
            batteryLevel = 0;
        }

        // Clumsy conditional approach. Otherwise we will create an object to check the relevant info and hashmap it
        if (distanceFromHub > 800) {
            return switch (batteryLevel) {
                case 2 -> 0.820;
                case 1 -> 0.505;
                default -> 0.133;
            };
        } else if (distanceFromHub > 700) {
            return switch (batteryLevel) {
                case 2 -> 0.861;
                case 1 -> 0.589;
                default -> 0.187;
            };
        } else if (distanceFromHub > 600) {
            return switch (batteryLevel) {
                case 2 -> 0.853;
                case 1 -> 0.644;
                default -> 0.247;
            };
        } else if (distanceFromHub > 500) {
            return switch (batteryLevel) {
                case 2 -> 0.865;
                case 1 -> 0.674;
                default -> 0.279;
            };
        } else if (distanceFromHub > 400) {
            return switch (batteryLevel) {
                case 2 -> 0.914;
                case 1 -> 0.747;
                default -> 0.346;
            };
        } else if (distanceFromHub > 300) {
            return switch (batteryLevel) {
                case 2 -> 0.958;
                case 1 -> 0.854;
                default -> 0.500;
            };
        } else {
            return switch (batteryLevel) {
                case 2 -> 0.977;
                case 1 -> 0.953;
                default -> 0.780;
            };
        }
    }

    public void failRecord(String allocatedTaskId) {
        ArrayList<ModellerRecord> recordsToFail = new ArrayList<>();
        for (ModellerRecord m : pendingRecords) {
            if (m.allocation.containsValue(allocatedTaskId)) {
                //System.out.println("An allocation containing " + allocatedTaskId + " failed");
                recordsToFail.add(m);
            }
        }

        recordsToFail.forEach(m -> {
            m.success = false;
            loggedRecords.add(m);
            pendingRecords.remove(m);
        });

        //printRecords();
    }

    public void passRecords() {
        ArrayList<ModellerRecord> recordsToPass = new ArrayList<>();
        for (ModellerRecord m : pendingRecords) {
            boolean done = true;
            for (String taskId : m.allocation.values()) {
                if (simulator.getState().getCompletedTasks().stream().noneMatch(t -> t.getId().equals(taskId))) {
                    // This allocation contains a task that is not yet completed. It's not done yet
                    done = false;
                    break;
                }
                // Else System.out.println("Allocation has " + taskId + ", which is in " + simulator.getState().getCompletedTasks());
            }

            if (done) {
                recordsToPass.add(m);
            }
        }

        recordsToPass.forEach(m -> {
            m.success = true;
            loggedRecords.add(m);
            pendingRecords.remove(m);
        });

        //printRecords();
    }

    private void printRecords() {
        System.out.println("Pending:");
        pendingRecords.forEach(System.out::println);
        System.out.println("Completed:");
        loggedRecords.forEach(System.out::println);
        System.out.println();

    }

    public void start() {
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    public void outputResults() {
        printRecords();
        // TODO compute precision/recall or something from these predictions and their success rate
        double totalSuccessPct = 0;
        double totalFailurePct = 0;
        int numSuccesses = 0;
        int numFailures = 0;
        for (ModellerRecord r : loggedRecords) {
            if (r.success) {
                totalSuccessPct += r.totalProbability;
                numSuccesses++;
                // Passed
            } else {
                totalFailurePct += r.totalProbability;
                numFailures++;
                // Failed
            }
        }

        double averageSuccessPrediction = totalSuccessPct / numSuccesses;
        double averageFailurePrediction = totalFailurePct / numFailures;

        System.out.println("In " + numSuccesses + " successful allocations, the model predicted, on average, a " + averageSuccessPrediction * 100 + "% success change");
        System.out.println("In " + numFailures + " failed allocations, the model predicted, on average, a " + averageFailurePrediction * 100 + "% success change");

    }

    private class ModellerRecord {
        private double timeStamp;
        private double totalProbability;
        private Map<String, String> allocation;
        private boolean success;

        public ModellerRecord(double timeStamp, double totalProbability, Map<String, String> allocation) {
            this.timeStamp = timeStamp;
            this.totalProbability = totalProbability;
            this.allocation = allocation;
        }

        @Override
        public String toString() {
            return "ModellerRecord{" +
                    "timeStamp=" + timeStamp +
                    ", totalProbability=" + totalProbability +
                    ", success=" + success +
                    '}';
        }

        public void printAlloc() {
            allocation.forEach((k, v) -> System.out.println(k + " -> " + v));
        }
    }


}
