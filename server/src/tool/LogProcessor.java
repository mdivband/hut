package tool;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class LogProcessor {
    // Date formatter for the log timestamp.
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Inner class to hold episode-level ratings.
    static class Episode {
        String episodeCode;
        String workload = "";
        String subjectivePerformance = "";

        public Episode(String episodeCode) {
            this.episodeCode = episodeCode;
        }
    }

    public static void main(String[] args) {
        processLogFile("logs/NewSetupWill-DegP1.log");
    }

    public static void processLogFile(String logFile) {
        String outputCsv = "processedLogs/" + logFile.split("/")[1].replaceFirst("\\.log$", ".csv");
        processLogFile(logFile, outputCsv);
    }

    public static void processLogFile(String logFile, String outputCsv) {
        List<String> lines;
        try {
            lines = Files.lines(Paths.get(logFile))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            return;
        }

        List<String[]> outputData = new ArrayList<>();

        // Classification metrics counters.
        int tp = 0, fp = 0, tn = 0, fn = 0;

        // Reaction time and workload collections for additional metrics.
        List<Double> reactionTimes = new ArrayList<>(); // For DGCLK events.
        Map<String, List<Double>> episodeReactionTimes = new HashMap<>();
        List<Long> eventTimestamps = new ArrayList<>(); // Store event timestamps in epoch seconds.
        List<Double> workloadValues = new ArrayList<>();  // All workload values (if numeric).

        // New: Subjective performance collections.
        List<Double> subjectivePerformanceValues = new ArrayList<>();

        // For correlation: pairs of (reaction time, workload) for DGCLK events.
        List<Double> corrReactionTimes = new ArrayList<>();
        List<Double> corrWorkloads = new ArrayList<>();
        // New: For subjective performance correlation.
        List<Double> corrSubjectivePerformances = new ArrayList<>();

        // Count degradation events per episode.
        Map<String, Integer> eventCountPerEpisode = new HashMap<>();

        // We'll use a currentEpisode object to track ratings,
        // and store each finished episode in a map.
        Episode currentEpisode = null;
        Map<String, Episode> finalEpisodeMap = new HashMap<>();

        // Process each line.
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(";");
            if (parts.length < 3) continue;  // Skip malformed lines.

            // Parse timestamp from the first column.
            String timestampStr = parts[0].trim();
            long epochSeconds = 0;
            try {
                LocalDateTime ldt = LocalDateTime.parse(timestampStr, formatter);
                epochSeconds = ldt.toEpochSecond(ZoneOffset.UTC);
            } catch (Exception e) {
                continue;
            }

            String event = parts[2].trim();

            // NEWEP defines a new episode.
            if ("NEWEP".equals(event) && parts.length >= 5) {
                // If a current episode is open, store it in the final map.
                if (currentEpisode != null) {
                    finalEpisodeMap.put(currentEpisode.episodeCode, currentEpisode);
                }
                currentEpisode = new Episode(parts[4].trim());
            }
            // Update current episode fields.
            else if ("WKLD".equals(event) && currentEpisode != null && parts.length >= 5) {
                currentEpisode.workload = parts[4].trim();
            }
            else if ("PRCP".equals(event) && currentEpisode != null && parts.length >= 5) {
                currentEpisode.subjectivePerformance = parts[4].trim();
            }

            // Process degradation events.
            if (("DGCLK".equals(event) || "DGNOC".equals(event)) && currentEpisode != null) {
                if (parts.length < 9) continue;

                eventTimestamps.add(epochSeconds);

                // Update event count per episode.
                eventCountPerEpisode.put(currentEpisode.episodeCode,
                        eventCountPerEpisode.getOrDefault(currentEpisode.episodeCode, 0) + 1);

                boolean userHasClicked = "DGCLK".equals(event);

                // Parse the "match" field (assumed at index 4) to indicate ground truth.
                boolean match;
                try {
                    match = Boolean.parseBoolean(parts[4].trim());
                } catch (Exception ex) {
                    continue;
                }

                if (userHasClicked) {
                    if (match) tp++; else fp++;
                } else {
                    if (match) fn++; else tn++;
                }

                boolean degradation = Boolean.parseBoolean(parts[4].trim());

                int numberOfAgents;
                try {
                    numberOfAgents = Integer.parseInt(parts[7].trim());
                } catch (NumberFormatException ex) {
                    continue;
                }

                // Reaction time: only for DGCLK events.
                String reactionTimeStr = "";
                double reactionTime = 0.0;
                if (userHasClicked) {
                    try {
                        reactionTime = Double.parseDouble(parts[8].trim());
                        reactionTimeStr = String.format("%.2f", reactionTime);
                        reactionTimes.add(reactionTime);
                        episodeReactionTimes.computeIfAbsent(currentEpisode.episodeCode, k -> new ArrayList<>())
                                .add(reactionTime);
                    } catch (NumberFormatException ex) {
                        reactionTimeStr = "0.00";
                    }
                }

                // At the time of processing a degradation event the current episode’s ratings
                // might not have been updated yet. So we initially use what we have.
                String workload = currentEpisode.workload;
                String subjectivePerformance = currentEpisode.subjectivePerformance;

                // Record numeric values if possible.
                try {
                    double wl = Double.parseDouble(workload);
                    workloadValues.add(wl);
                    if (userHasClicked) {
                        corrReactionTimes.add(reactionTime);
                        corrWorkloads.add(wl);
                    }
                } catch (NumberFormatException ex) {
                    // Ignore non-numeric workload.
                }
                try {
                    double sp = Double.parseDouble(subjectivePerformance);
                    subjectivePerformanceValues.add(sp);
                    if (userHasClicked) {
                        corrSubjectivePerformances.add(sp);
                    }
                } catch (NumberFormatException ex) {
                    // Ignore non-numeric subjective performance.
                }

                outputData.add(new String[]{
                        currentEpisode.episodeCode,
                        String.valueOf(degradation),
                        String.valueOf(numberOfAgents),
                        String.valueOf(userHasClicked),
                        reactionTimeStr,
                        workload,
                        subjectivePerformance
                });
            }
        }
        // Store the last episode.
        if (currentEpisode != null) {
            finalEpisodeMap.put(currentEpisode.episodeCode, currentEpisode);
        }

        // Now update every degradation event row with the final ratings for its episode.
        for (String[] row : outputData) {
            String epCode = row[0];
            if (finalEpisodeMap.containsKey(epCode)) {
                Episode ep = finalEpisodeMap.get(epCode);
                row[5] = ep.workload;
                row[6] = ep.subjectivePerformance;
            }
        }

        // Ensure the output directory exists.
        try {
            Path outputPath = Paths.get(outputCsv).getParent();
            if (outputPath != null) {
                Files.createDirectories(outputPath);
            }
        } catch (IOException e) {
            System.err.println("Error creating output directory: " + e.getMessage());
        }

        // Write the degradation events CSV.
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsv))) {
            writer.println("EpisodeCode,Degradation,NumberOfAgents,UserHasClicked,Reaction_Time,Workload,SubjectivePerformance");
            for (String[] row : outputData) {
                writer.println(String.join(",", row));
            }
            System.out.println("CSV output saved to " + outputCsv);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }

        // --- (Metrics computations remain unchanged) ---
        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double accuracy = (tp + tn) > 0 ? (double) (tp + tn) / (tp + tn + fp + fn) : 0;
        double specificity = (tn + fp) > 0 ? (double) tn / (tn + fp) : 0;
        double fpr = (tn + fp) > 0 ? (double) fp / (tn + fp) : 0;
        double npv = (tn + fn) > 0 ? (double) tn / (tn + fn) : 0;
        double balancedAccuracy = (recall + specificity) / 2;

        double avgReactionTime = reactionTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double medianReactionTime = computeMedian(reactionTimes);
        double stdReactionTime = computeStdDev(reactionTimes, avgReactionTime);

        Map<String, Double> avgReactionTimePerEpisode = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : episodeReactionTimes.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            avgReactionTimePerEpisode.put(entry.getKey(), avg);
        }

        double avgTimeBetweenEvents = computeAverageTimeDifference(eventTimestamps);
        double avgWorkload = workloadValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgSubjectivePerformance = subjectivePerformanceValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double correlation = computePearsonCorrelation(corrReactionTimes, corrWorkloads);
        double correlationSubjective = computePearsonCorrelation(corrReactionTimes, corrSubjectivePerformances);

        System.out.println("Classification Metrics:");
        System.out.println("TP: " + tp);
        System.out.println("FP: " + fp);
        System.out.println("TN: " + tn);
        System.out.println("FN: " + fn);
        System.out.println(String.format("Precision: %.2f", precision));
        System.out.println(String.format("Recall (Sensitivity): %.2f", recall));
        System.out.println(String.format("F1 Score: %.2f", f1));
        System.out.println(String.format("Accuracy: %.2f", accuracy));
        System.out.println(String.format("Specificity: %.2f", specificity));
        System.out.println(String.format("False Positive Rate: %.2f", fpr));
        System.out.println(String.format("Negative Predictive Value: %.2f", npv));
        System.out.println(String.format("Balanced Accuracy: %.2f", balancedAccuracy));

        System.out.println("\nReaction Time Metrics:");
        System.out.println(String.format("Average Reaction Time: %.2f", avgReactionTime));
        System.out.println(String.format("Median Reaction Time: %.2f", medianReactionTime));
        System.out.println(String.format("Std Dev Reaction Time: %.2f", stdReactionTime));
        System.out.println("Average Reaction Time per Episode: " + avgReactionTimePerEpisode);

        System.out.println("\nTemporal Metrics:");
        System.out.println(String.format("Average Time Between Degradation Events: %.2f seconds", avgTimeBetweenEvents));
        System.out.println("Degradation Event Count per Episode: " + eventCountPerEpisode);

        System.out.println("\nWorkload Metrics:");
        System.out.println(String.format("Average Workload: %.2f", avgWorkload));
        System.out.println(String.format("Pearson Correlation (Reaction Time vs Workload): %.2f", correlation));

        System.out.println("\nSubjective Performance Metrics:");
        System.out.println(String.format("Average Subjective Performance: %.2f", avgSubjectivePerformance));
        System.out.println(String.format("Pearson Correlation (Reaction Time vs Subjective Performance): %.2f", correlationSubjective));

        String metricsCsv = outputCsv.replace(".csv", "_metrics.csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(metricsCsv))) {
            writer.println("Metric,Value");
            writer.println("TP," + tp);
            writer.println("FP," + fp);
            writer.println("TN," + tn);
            writer.println("FN," + fn);
            writer.println(String.format("Precision,%.2f", precision));
            writer.println(String.format("Recall,%.2f", recall));
            writer.println(String.format("F1 Score,%.2f", f1));
            writer.println(String.format("Accuracy,%.2f", accuracy));
            writer.println(String.format("Specificity,%.2f", specificity));
            writer.println(String.format("False Positive Rate,%.2f", fpr));
            writer.println(String.format("Negative Predictive Value,%.2f", npv));
            writer.println(String.format("Balanced Accuracy,%.2f", balancedAccuracy));

            writer.println(String.format("Average Reaction Time,%.2f", avgReactionTime));
            writer.println(String.format("Median Reaction Time,%.2f", medianReactionTime));
            writer.println(String.format("Std Dev Reaction Time,%.2f", stdReactionTime));
            for (Map.Entry<String, Double> entry : avgReactionTimePerEpisode.entrySet()) {
                writer.println("Avg Reaction Time (" + entry.getKey() + ")," + String.format("%.2f", entry.getValue()));
            }

            writer.println(String.format("Average Time Between Events,%.2f", avgTimeBetweenEvents));
            writer.println("Event Count per Episode," + eventCountPerEpisode.toString());

            writer.println(String.format("Average Workload,%.2f", avgWorkload));
            writer.println(String.format("Pearson Correlation (Reaction Time vs Workload),%.2f", correlation));

            writer.println(String.format("Average Subjective Performance,%.2f", avgSubjectivePerformance));
            writer.println(String.format("Pearson Correlation (Reaction Time vs Subjective Performance),%.2f", correlationSubjective));

            System.out.println("Metrics CSV output saved to " + metricsCsv);
        } catch (IOException e) {
            System.err.println("Error writing metrics CSV file: " + e.getMessage());
        }
    }

    // Helper method to compute median.
    private static double computeMedian(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 0) {
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        } else {
            return sorted.get(n / 2);
        }
    }

    // Helper method to compute standard deviation.
    private static double computeStdDev(List<Double> values, double mean) {
        if (values == null || values.isEmpty()) return 0.0;
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / values.size());
    }

    // Helper method to compute average time difference between successive timestamps.
    private static double computeAverageTimeDifference(List<Long> timestamps) {
        if (timestamps == null || timestamps.size() < 2) return 0.0;
        Collections.sort(timestamps);
        long totalDiff = 0;
        for (int i = 1; i < timestamps.size(); i++) {
            totalDiff += (timestamps.get(i) - timestamps.get(i - 1));
        }
        return (double) totalDiff / (timestamps.size() - 1);
    }

    // Helper method to compute Pearson correlation.
    private static double computePearsonCorrelation(List<Double> xs, List<Double> ys) {
        if (xs == null || ys == null || xs.size() != ys.size() || xs.isEmpty()) return 0.0;
        int n = xs.size();
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0, sumY2 = 0.0;
        for (int i = 0; i < n; i++) {
            double x = xs.get(i);
            double y = ys.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return (denominator == 0) ? 0.0 : numerator / denominator;
    }
}
