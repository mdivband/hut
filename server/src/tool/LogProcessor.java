package tool;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class LogProcessor {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static class Episode {
        String episodeCode;
        String workload = "";
        String subjectivePerformance = "";

        public Episode(String episodeCode) {
            this.episodeCode = episodeCode;
        }
    }

    static class AgentMetrics {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        List<Integer> workloads = new ArrayList<>();
        List<Integer> performances = new ArrayList<>();
    }

    public static void main(String[] args) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("logs"), "*.log")) {
            for (Path entry : stream) {
                processLogFile(entry.toString());
            }
        } catch (IOException e) {
            System.err.println("Error reading log directory: " + e.getMessage());
        }
    }

    public static void processLogFile(String logFile) {
        String outputCsv = "processedLogs/" + Paths.get(logFile).getFileName().toString().replaceFirst("\\.log$", ".csv");
        processLogFile(logFile, outputCsv);
    }

    public static void processLogFile(String logFile, String outputCsv) {
        List<String> lines;
        try {
            lines = Files.lines(Paths.get(logFile)).map(String::trim).collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            return;
        }

        List<String[]> outputData = new ArrayList<>();
        Map<Integer, AgentMetrics> agentMetricsMap = new TreeMap<>();
        Episode currentEpisode = null;
        boolean episodeHasDegradation = false;
        boolean userHasClicked = false;
        boolean degradationMatched = false;
        int numberOfAgents = 0;
        double reactionTime = 0.0;

        int tp = 0, fp = 0, tn = 0, fn = 0;
        List<Double> reactionTimes = new ArrayList<>();

        for (String line : lines) {
            String[] parts = line.split(";");
            if (parts.length < 3) continue;

            String timestampStr = parts[0].trim();
            try {
                LocalDateTime.parse(timestampStr, formatter);
            } catch (Exception e) {
                continue;
            }

            String event = parts[2].trim();

            switch (event) {
                case "NEWEP":
                    if (currentEpisode != null) {
                        outputData.add(new String[]{
                                currentEpisode.episodeCode,
                                String.valueOf(episodeHasDegradation),
                                String.valueOf(numberOfAgents),
                                String.valueOf(userHasClicked),
                                userHasClicked ? String.format("%.2f", reactionTime) : "0.00",
                                currentEpisode.workload,
                                currentEpisode.subjectivePerformance
                        });
                    }
                    currentEpisode = new Episode(parts[4].trim());
                    episodeHasDegradation = false;
                    userHasClicked = false;
                    degradationMatched = false;
                    numberOfAgents = 0;
                    reactionTime = 0.0;
                    break;

                case "WKLD":
                    if (currentEpisode != null && parts.length >= 5)
                        currentEpisode.workload = parts[4].trim();
                    break;

                case "PRCP":
                    if (currentEpisode != null && parts.length >= 5)
                        currentEpisode.subjectivePerformance = parts[4].trim();
                    break;

                case "DEGTR":
                    episodeHasDegradation = true;
                    break;

                case "DGCLK":
                    if (parts.length >= 10) {
                        userHasClicked = true;
                        try {
                            degradationMatched = Boolean.parseBoolean(parts[4].trim());
                            numberOfAgents = Integer.parseInt(parts[7].trim());
                            reactionTime = Double.parseDouble(parts[8].trim());
                            reactionTimes.add(reactionTime);
                        } catch (Exception ignored) {}
                    }
                    break;

                case "EPEND":
                    if (currentEpisode != null) {
                        int agentsForThisEpisode = numberOfAgents;

                        // If numberOfAgents wasn't set (i.e. no DGCLK), decode from episode code
                        if (!userHasClicked && currentEpisode.episodeCode.length() >= 4) {
                            char agentChar = currentEpisode.episodeCode.charAt(2);
                            if (Character.isLetter(agentChar)) {
                                agentsForThisEpisode = Character.toUpperCase(agentChar) - 'A' + 1;
                            }
                        }

                        AgentMetrics metrics = agentMetricsMap.computeIfAbsent(agentsForThisEpisode, k -> new AgentMetrics());

                        // Confusion matrix logic
                        if (episodeHasDegradation) {
                            if (userHasClicked) {
                                if (degradationMatched) {
                                    tp++; metrics.tp++;
                                } else {
                                    fp++; metrics.fp++;
                                }
                            } else {
                                fn++; metrics.fn++;
                            }
                        } else {
                            if (userHasClicked) {
                                // Any click when there's no degradation should be considered a false positive.
                                fp++; metrics.fp++;
                            } else {
                                tn++; metrics.tn++;
                            }
                        }


                        // Record workload/performance
                        try {
                            int wkld = Integer.parseInt(currentEpisode.workload);
                            int perf = Integer.parseInt(currentEpisode.subjectivePerformance);
                            metrics.workloads.add(wkld);
                            metrics.performances.add(perf);
                        } catch (Exception ignored) {}

                        outputData.add(new String[]{
                                currentEpisode.episodeCode,
                                String.valueOf(episodeHasDegradation),
                                String.valueOf(agentsForThisEpisode),
                                String.valueOf(userHasClicked),
                                userHasClicked ? String.format("%.2f", reactionTime) : "0.00",
                                currentEpisode.workload,
                                currentEpisode.subjectivePerformance
                        });
                    }

                    currentEpisode = null;
                    episodeHasDegradation = false;
                    userHasClicked = false;
                    degradationMatched = false;
                    numberOfAgents = 0;
                    reactionTime = 0.0;
                    break;

            }
        }

        try {
            Files.createDirectories(Paths.get(outputCsv).getParent());
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsv))) {
                writer.println("EpisodeCode,Degradation,NumberOfAgents,UserHasClicked,Reaction_Time,Workload,SubjectivePerformance");
                outputData.forEach(row -> writer.println(String.join(",", row)));
            }
            System.out.println("CSV output saved to " + outputCsv);
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double avgReactionTime = reactionTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        String metricsCsv = outputCsv.replaceFirst("\\.csv$", "-metrics.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(metricsCsv))) {
            writer.println("TP,FP,TN,FN,Precision,Recall,F1,AverageReactionTime");
            writer.printf("%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f%n", tp, fp, tn, fn, precision, recall, f1, avgReactionTime);

            writer.println();
            writer.println("Confusion values by number of agents");
            writer.println("Agents,TP,TN,FP,FN,AvgWorkload,StdWorkload,AvgPerformance,StdPerformance");

            for (Map.Entry<Integer, AgentMetrics> entry : agentMetricsMap.entrySet()) {
                int numAgents = entry.getKey();
                AgentMetrics m = entry.getValue();
                double avgW = average(m.workloads);
                double stdW = stddev(m.workloads);
                double avgP = average(m.performances);
                double stdP = stddev(m.performances);
                writer.printf("%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f%n",
                        numAgents, m.tp, m.tn, m.fp, m.fn, avgW, stdW, avgP, stdP);
            }

            System.out.println("Metrics output (with confusion counts) saved to " + metricsCsv);
        } catch (IOException e) {
            System.err.println("Error writing metrics CSV: " + e.getMessage());
        }
    }

    private static double average(List<Integer> list) {
        return list.stream().mapToDouble(i -> i).average().orElse(0.0);
    }

    private static double stddev(List<Integer> list) {
        double avg = average(list);
        return Math.sqrt(list.stream().mapToDouble(i -> (i - avg) * (i - avg)).average().orElse(0.0));
    }
}