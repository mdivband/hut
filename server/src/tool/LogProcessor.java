package tool;

import java.io.*;
import java.util.*;

public class LogProcessor {
    public static void main(String[] args) {
        processLogFile("logs/anothergo-N-Back (short).log");
    }

    public static void processLogFile(String logFile) {
        processLogFile(logFile, "processedLogs/"+logFile.split("/")[1]);
    }

    public static void processLogFile(String logFile, String outputCsv) {
        List<String[]> outputData = new ArrayList<>();
        String participantId = null;

        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            List<String> lines = new ArrayList<>();
            String line;

            // Read all lines into a list
            while ((line = br.readLine()) != null) {
                lines.add(line.trim());
            }

            // Process each line
            for (int i = 0; i < lines.size(); i++) {
                String[] parts = lines.get(i).split(";");
                if (parts.length < 2) {
                    continue; // Skip malformed lines
                }

                // Extract elapsed time and event type
                double elapsed;
                try {
                    elapsed = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    continue; // Skip non-numeric elapsed values
                }

                String event = parts[2].trim();

                // Extract Participant ID from RGNAME
                if (event.equals("RGNAME")) {
                    participantId = parts[4].trim();
                }

                // Extract Participant ID from LGSTRT
                if (event.equals("LGSTRT")) {
                    participantId = parts[5].trim(); // Extract participant ID

                }

                // Handle NBCLK (NBack clicked) and NBNOC (NBack not clicked)
                if (event.equals("DGCLK") || event.equals("DGNOC")) {
                    if (parts.length >= 9) {
                        int match = parts[4].trim().equalsIgnoreCase("true") ? 1 : 0;
                        int accuracy = match; // Accuracy is equal to match
                        int swarmSize = Integer.parseInt(parts[7].trim()); // 'numAgents' field
                        double reactionTime = Double.parseDouble(parts[8].trim()); // 'reactiontime' field

                        // Determine workload
                        String workload = " "; // Default if no workload found
                        if (event.equals("DGNOC")) {
                            // Look one line back for NBNOC
                            if (i - 1 >= 0) {
                                String[] prevLine = lines.get(i - 1).split(";");
                                if (prevLine.length >= 5 && prevLine[2].trim().equals("WKLD")) {
                                    workload = prevLine[4].trim(); // Extract workload level
                                }
                            }
                        } else {
                            // Look forward up to 5 lines for DGCLK
                            for (int j = 1; j <= 5; j++) {
                                if (i + j < lines.size()) {
                                    String[] nextLine = lines.get(i + j).split(";");
                                    if (nextLine.length >= 5 && nextLine[2].trim().equals("WKLD")) {
                                        workload = nextLine[4].trim(); // Extract workload level
                                        break;
                                    }
                                }
                            }
                        }

                        // Add row to output
                        outputData.add(new String[]{participantId, String.valueOf(swarmSize), String.valueOf(accuracy), String.format("%.2f", reactionTime), String.valueOf(match), workload});
                    }
                }
            }

            // Write output CSV
            try (PrintWriter pw = new PrintWriter(new FileWriter(outputCsv))) {
                // Write header
                pw.println("Participant,SwarmSize,Accuracy,Reaction_Time,Match,Workload");

                // Write data rows
                for (String[] row : outputData) {
                    pw.println(String.join(",", row));
                }

                System.out.println("CSV output saved to " + outputCsv);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
