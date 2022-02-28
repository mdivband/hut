package server;

import server.model.agents.Agent;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Modeller {
    private Simulator simulator;
    private ArrayList<ModellerRecord> pendingRecords = new ArrayList<>();
    private ArrayList<ModellerRecord> loggedRecords = new ArrayList<>();

    private ArrayList<ModellerAgentRecord> pendingAgentRecords = new ArrayList<>();
    private ArrayList<ModellerAgentRecord> loggedAgentRecords = new ArrayList<>();
    private boolean started = false;

    public Modeller(Simulator simulator) {
        this.simulator = simulator;
    }

    public double calculateAll(Agent agent) {
        //OptionalDouble average = simulator.getState().getAgents().stream().filter(a -> a.getAllocatedTaskId() != null).mapToDouble(this::calculate).average();
        pendingAgentRecords.add(new ModellerAgentRecord(simulator.getState().getTime(), calculate(agent), agent.getId(), agent.getAllocatedTaskId()));

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
        //double distanceFromHub = agent.getTask().getCoordinate().getDistance(simulator.getState().getHubLocation());
        // Misnomer currently. I think distance from agent is better
        // With distance from hub:
        //      In 19 successful allocations, the model predicted, on average, a 57.57303733694737% success change
        //      In 13 failed allocations, the model predicted, on average, a 17.015643008425663% success change
        // Without:
        //      In 22 successful allocations, the model predicted, on average, a 72.01247531930365% success change
        //      In 11 failed allocations, the model predicted, on average, a 12.691680880011091% success change

        double distanceFromHub = agent.getTask().getCoordinate().getDistance(agent.getCoordinate());
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

    public void failRecord(String agent, String allocatedTaskId) {
        ArrayList<ModellerRecord> recordsToFail = new ArrayList<>();
        for (ModellerRecord m : pendingRecords) {
            if (m.allocation.containsValue(allocatedTaskId)) {
                //System.out.println("An allocation containing " + allocatedTaskId + " failed");
                recordsToFail.add(m);
            }
        }

        recordsToFail.forEach(r -> {
            r.success = false;
            loggedRecords.add(r);
            pendingRecords.remove(r);
        });

        ArrayList<ModellerAgentRecord> agentRecordsToFail = new ArrayList<>();
        pendingAgentRecords.forEach(r -> {
            if (r.agentId.equals(agent)) {
                agentRecordsToFail.add(r);
            }
        });

        agentRecordsToFail.forEach(r -> {
            r.success = false;
            loggedAgentRecords.add(r);
            pendingAgentRecords.remove(r);
        });

        //printRecords();
    }

    public void passRecords(String task) {
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

        recordsToPass.forEach(r -> {
            r.success = true;
            loggedRecords.add(r);
            pendingRecords.remove(r);
        });

        ArrayList<ModellerAgentRecord> agentRecordsToPass = new ArrayList<>();
        pendingAgentRecords.forEach(r -> {
            if (r.taskId.equals(task)) {
                agentRecordsToPass.add(r);
            }
        });

        agentRecordsToPass.forEach(r -> {
            r.success = true;
            loggedAgentRecords.add(r);
            pendingAgentRecords.remove(r);
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

    private void printAgentRecords() {
        System.out.println("Agent Pending:");
        pendingAgentRecords.forEach(System.out::println);
        System.out.println("Agent Completed:");
        loggedAgentRecords.forEach(System.out::println);
        System.out.println();

    }

    public void start() {
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    public void outputResults() {
        System.out.println("=============================");
        System.out.println();
        printRecords();

        System.out.println();
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

        System.out.println("BY TOTAL: ");
        System.out.println("In " + numSuccesses + " successful allocations, the model predicted, on average, a " + averageSuccessPrediction * 100 + "% success change");
        System.out.println("In " + numFailures + " failed allocations, the model predicted, on average, a " + averageFailurePrediction * 100 + "% success change");

        printAgentRecords();
        System.out.println();
        totalSuccessPct = 0;
        totalFailurePct = 0;
        numSuccesses = 0;
        numFailures = 0;
        for (ModellerAgentRecord r : loggedAgentRecords) {
            if (r.success) {
                totalSuccessPct += r.probability;
                numSuccesses++;
                // Passed
            } else {
                totalFailurePct += r.probability;
                numFailures++;
                // Failed
            }
        }

        averageSuccessPrediction = totalSuccessPct / numSuccesses;
        averageFailurePrediction = totalFailurePct / numFailures;

        System.out.println("BY AGENT: ");
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

    private class ModellerAgentRecord {
        private double timeStamp;
        private double probability;
        private String agentId;
        private String taskId;
        private boolean success;

        public ModellerAgentRecord(double timeStamp, double probability, String agentId, String taskId) {
            this.timeStamp = timeStamp;
            this.probability = probability;
            this.agentId = agentId;
            this.taskId = taskId;
        }

        @Override
        public String toString() {
            return "ModellerAgentRecord{" +
                    "timeStamp=" + timeStamp +
                    ", probability=" + probability +
                    ", agentId='" + agentId + '\'' +
                    ", taskId='" + taskId + '\'' +
                    ", success=" + success +
                    '}';
        }
    }


}
