package tool;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/** Automatically generates degradation episodes.
 * Note that the result isn't guaranteed to be good so check it.
 */
public class DegradationGenerator {
    private List<DegradationEpisode> episodes;
    private Random random;
    private static final String[] POSITIONS = {"BL", "TL", "TR", "BR"}; // "Bottom Left", "Top Left", "Top Right", "Bottom Right, Top, Bottom, Left, Right"

    private int numEpisodes = 64;
    private int episodeLength = 7;
    private int episodeCooldown = 5;
    private int reviewPeriod = 3;
    private int minAgents = 4;
    private int maxAgents = 22;
    private int minDegradationTime = 3;
    private int maxDegradationTime = 5;

    private double degradationProbability = 0.5;

    private static final int DEFAULT_NUM = 5;
    private static final String DEFAULT_START_POSITION = "L";
    private static final String DEFAULT_END_POSITION = "R";


    public DegradationGenerator() {
        this.episodes = new ArrayList<>();
        this.random = new Random();
    }

    private void configure(int numEpisodes, int length, int cooldown, int reviewPeriod, int minAgents, int maxAgents, double matchProbability, int minDegradationTime, int maxDegradationTime) {
        this.numEpisodes = numEpisodes;
        this.episodeLength = length;
        this.episodeCooldown = cooldown;
        this.reviewPeriod = reviewPeriod;
        this.minAgents = minAgents;
        this.maxAgents = maxAgents;
        this.degradationProbability = matchProbability;
        this.minDegradationTime = minDegradationTime;
        this.maxDegradationTime = maxDegradationTime;
    }

    // Add a new field to hold the discrete list of agent numbers.
    private List<Integer> allowedAgents = Arrays.asList(6,8,10,12,14,16,18,20);

    private DegradationEpisode generateSingleEpisode(int numAgents, boolean degradationMatch) {
        String agentPos = getRandomPosition();
        String targetPos = getRandomPosition();
        // Ensure agent and target positions differ
        while (agentPos.equals(targetPos)) {
            targetPos = getRandomPosition();
        }
        int degradationTime = degradationMatch ? getRandomDegradationTime() : -1;
        // Build an episode code: e.g., if numAgents=3 then agentChar is 'C' (since 'A'+2='C')
        char agentChar = (char) ('A' + (numAgents - 1));
        char matchChar = degradationMatch ? 'T' : 'F';
        String episodeCode = "EP" + agentChar + matchChar;

        return new DegradationEpisode(
                episodeLength,
                random.nextInt(3, 5),  // Random cooldown between 3 and 5
                agentPos,
                targetPos,
                numAgents,
                degradationMatch,
                episodeCode,
                reviewPeriod,
                degradationTime
        );
    }

    public void generateBalancedEpisodes() {
        episodes.clear();

        // The total number of allowed agent counts
        int numAgentValues = allowedAgents.size();
        // For each agent count we want two types (degradationMatch true and false)
        int totalCombinations = 2 * numAgentValues;
        // Determine how many episodes per combination we can generate
        int k = numEpisodes / totalCombinations;
        int adjustedTotal = k * totalCombinations;

        if (adjustedTotal != numEpisodes) {
            System.out.println("Warning: numEpisodes (" + numEpisodes + ") is not a multiple of "
                    + totalCombinations + ". Adjusting total episodes to " + adjustedTotal + " for balance.");
        }

        // For each allowed agent count, generate k episodes for each degradation type.
        for (int agentCount : allowedAgents) {
            for (boolean degradationMatch : new boolean[]{true, false}) {
                for (int i = 0; i < k; i++) {
                    episodes.add(generateSingleEpisode(agentCount, degradationMatch));
                }
            }
        }

        // Shuffle the deck so that the order is randomized
        Collections.shuffle(episodes, random);
    }


    private String getRandomPosition() {
        return POSITIONS[random.nextInt(POSITIONS.length)];
    }

    private int getRandomNumAgents() {
        return random.nextInt(maxAgents - minAgents + 1) + minAgents;
    }

    public List<DegradationEpisode> getEpisodes() {
        return episodes;
    }

    private int getRandomDegradationTime() {
        return random.nextInt(maxDegradationTime - minDegradationTime + 1) + minDegradationTime;
    }


    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            DegradationGenerator degradationGenerator = new DegradationGenerator();
            int numEpisodes = degradationGenerator.numEpisodes;
            if (numEpisodes == -1) {
                System.out.println("Enter the number of episodes to generate:");
                numEpisodes = scanner.nextInt();
            }

            int length = degradationGenerator.episodeLength;
            if (length == -1) {
                System.out.println("Enter the length of each episode:");
                length = scanner.nextInt();
            }

            int cooldown = degradationGenerator.episodeCooldown;
            if (cooldown == -1) {
                System.out.println("Enter the cooldown time between each episode:");
                cooldown = scanner.nextInt();
            }

            int reviewPeriod = degradationGenerator.reviewPeriod;
            if (reviewPeriod == -1) {
                System.out.println("Enter the review period between each episode:");
                reviewPeriod = scanner.nextInt();
            }

            int minAgents = degradationGenerator.minAgents;
            if (minAgents == -1) {
                System.out.println("Enter the minimum number of agents:");
                minAgents = scanner.nextInt();
            }

            int maxAgents = degradationGenerator.maxAgents;
            if (maxAgents == -1) {
                System.out.println("Enter the maximum number of agents:");
                maxAgents = scanner.nextInt();
            }

            double degradationProbability = degradationGenerator.degradationProbability;
            if (degradationProbability == -1.0) {
                System.out.println("Enter the probability of an degradation:");
                degradationProbability = scanner.nextDouble();
            }

            int minDegradationTime = degradationGenerator.minDegradationTime;
            if (minDegradationTime == -1) {
                System.out.println("Enter the minimum degradation time:");
                minDegradationTime = scanner.nextInt();
            }

            int maxDegradationTime = degradationGenerator.maxDegradationTime;
            if (maxDegradationTime == -1) {
                System.out.println("Enter the maximum degradation time:");
                maxDegradationTime = scanner.nextInt();
            }

            degradationGenerator.configure(numEpisodes, length, cooldown, reviewPeriod, minAgents, maxAgents, degradationProbability, minDegradationTime, maxDegradationTime);
            degradationGenerator.generateBalancedEpisodes();
            System.out.println("\"episodes\": [");
            for (int i = 0; i < degradationGenerator.getEpisodes().size(); i++) {
                DegradationEpisode episode = degradationGenerator.getEpisodes().get(i);
                System.out.print(episode);
                if (i < degradationGenerator.getEpisodes().size() - 1) {
                    System.out.println(",");
                } else {
                    System.out.println();
                }
            }
            System.out.println("]");

            List<String> jsonFiles = new ArrayList<>();
            File directory = new File("server/web/scenarios");

            if (!directory.exists() || !directory.isDirectory() || directory.listFiles() == null || directory.listFiles().length == 0) {
                System.out.println("Directory is missing, empty, or invalid. Attempting to locate it dynamically...");
                Thread.sleep(1000);
                for (int i = 0; i < 3; i++) {
                    System.out.print(".");
                    Thread.sleep(500); // Pause for 500 milliseconds
                }
                System.out.println(); // Move to the next line

                System.out.println("[Pausing for dramatic effect]");
                Thread.sleep(1000);

                for (int i = 0; i < 3; i++) {
                    System.out.print(".");
                    Thread.sleep(500); // Pause for 500 milliseconds
                }
                System.out.println(); // Move to the next line

                directory = FolderLocator.findScenariosFolder(new File(System.getProperty("user.dir")));
            }

            if (directory != null && directory.isDirectory()) {

                File[] files = directory.listFiles();
                if (files != null && files.length > 0) {
                    System.out.println("Found!");
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".json")) {
                            jsonFiles.add(file.getAbsolutePath());
                        }
                    }

                    if (!jsonFiles.isEmpty()) {
                        System.out.println("JSON files found:");
                        for (int i = 0; i < jsonFiles.size(); i++) {
                            System.out.println((i + 1) + ": " + jsonFiles.get(i));
                        }
                    } else {
                        System.out.println("No JSON files found in the directory: " + directory.getAbsolutePath());
                    }
                } else {
                    System.out.println("The directory is empty: " + directory.getAbsolutePath());
                }
            } else {
                System.out.println("Unable to locate the scenarios folder.");
            }

            Thread.sleep(1000);

            System.out.println("Would you like to update one of the files with the generated episodes? ([Y]/n)");
            // Read the user's input
            String userInput = scanner.nextLine().trim();

            // Default to 'y' if the user presses Enter
            if (userInput.isEmpty()) {
                userInput = "y";
            }

            // Check the user's choice
            if (userInput.equalsIgnoreCase("y")) {
                // Now prompt the user to enter the number of the file they want to overwrite
                System.out.println("Enter the number of the file you want to overwrite:");
                int fileNumber = scanner.nextInt();
                String selectedFileName = jsonFiles.get(fileNumber - 1);
                System.out.println("You selected: " + selectedFileName);

                try {
                    degradationGenerator.injectJsonFile(selectedFileName, degradationGenerator.getEpisodes());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                System.out.println("Exiting...");
            }

            scanner.close();

        } catch (InterruptedException e) {
            System.out.println("Interrupted during time sleep");
        }
    }

    public void injectJsonFile(String selectedFileName, List<DegradationEpisode> episodes) throws IOException {
        GsonUtils.create();

        System.out.println("Reading JSON from file: " + selectedFileName);
        String jsonContent = GsonUtils.readFile(selectedFileName);

        Object jsonObj = GsonUtils.fromJson(jsonContent);

        if (GsonUtils.hasKey(jsonObj, "episodes")) {
            ((Map<String, Object>) jsonObj).put("episodes", episodes);
        } else {
            System.out.println("'episodes' key not found in the JSON file.");
            throw new IOException("Key not found");
        }

        String modifiedJson = GsonUtils.toJson(jsonObj);

        System.out.println("Saving modified JSON back to file: " + selectedFileName);
        try (FileWriter fileWriter = new FileWriter(selectedFileName)) {
            fileWriter.write(modifiedJson);
        }

        System.out.println("JSON file updated successfully.");
    }

}

record DegradationEpisode(int episodeLength, int episodeCooldown, String agentPos, String targetPos, int numAgents, boolean degradationMatch, String episodeCode, int reviewPeriod, int degradationTime) {
    @Override
    public String toString() {
        return "{\n"
                + "\t\"episodeLength\": " + episodeLength + ",\n"
                + "\t\"episodeCooldown\": " + episodeCooldown + ",\n"
                + "\t\"reviewPeriod\": " + reviewPeriod + ",\n" // Include reviewPeriod in JSON
                + "\t\"agentPos\": \"" + agentPos + "\",\n"
                + "\t\"targetPos\": \"" + targetPos + "\",\n"
                + "\t\"numAgents\": " + numAgents + ",\n"
                + "\t\"degradationMatch\": " + degradationMatch + ",\n"
                + "\t\"degradationTime\": " + degradationTime + ",\n"
                + "\t\"episodeCode\": \"" + episodeCode + "\"\n"
                + "}";
    }
}

