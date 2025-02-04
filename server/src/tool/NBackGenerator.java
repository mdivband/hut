package tool;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/** Automatically generates n-back episodes.
 * Note that the result isn't guaranteed to be good so check it.
 */
public class NBackGenerator {
    private List<Episode> episodes;
    private Random random;
    private static final String[] POSITIONS = {"BL", "TL", "TR", "BR"}; // "Bottom Left", "Top Left", "Top Right", "Bottom Right, Top, Bottom, Left, Right"

    private int numEpisodes = 60;
    private int episodeLength = 5;
    private int episodeCooldown = 5;
    private int reviewPeriod = 5; // New field for review period
    private int minAgents = 6;
    private int maxAgents = 10;
    private double matchProbability = 0.2;
    private int nValue = 2;
    private MatchCode diffType = MatchCode.NUMBER;
    private MatchCode matchType = MatchCode.NUMBER;

    public enum MatchCode {
        NUMBER,
        POSITIONS,
        BOTH,
        NOT_DEFINED
    }

    private static final int DEFAULT_NUM = 5;
    private static final String DEFAULT_START_POSITION = "L";
    private static final String DEFAULT_END_POSITION = "R";


    public NBackGenerator() {
        this.episodes = new ArrayList<>();
        this.random = new Random();
    }

    private void configure(int numEpisodes, int length, int cooldown, int reviewPeriod, int minAgents, int maxAgents, double matchProbability, int nValue, MatchCode diffType, MatchCode matchType) {
        this.numEpisodes = numEpisodes;
        this.episodeLength = length;
        this.episodeCooldown = cooldown;
        this.reviewPeriod = reviewPeriod; // Set the review period
        this.minAgents = minAgents;
        this.maxAgents = maxAgents;
        this.matchProbability = matchProbability;
        this.nValue = nValue;
        this.diffType = diffType;
        this.matchType = matchType;
    }

    public void generateEpisodesBalanced() {
        // TODO the new algorithm doesn't properly support position variation at present

        // Num conditions is the number of possible permutations based on what is changeable. e.g. poss num agents x poss positions
        int numConditions;
        List<String[]> positionCombinations = new ArrayList<>();
        if (matchType == MatchCode.NUMBER) {
            numConditions = maxAgents - minAgents + 1;
        } else if (matchType == MatchCode.POSITIONS) {
            // Generate all possible combinations of start and end positions
            for (String startPos : POSITIONS) {
                for (String endPos : POSITIONS) {
                    if (!startPos.equals(endPos)) { // Avoid the same start and end position
                        positionCombinations.add(new String[]{startPos, endPos});
                    }
                }
            }
            numConditions = positionCombinations.size();
        } else {
            // Generate all possible combinations of agent numbers and start-end positions
            for (int agents = minAgents; agents <= maxAgents; agents++) {
                for (String startPos : POSITIONS) {
                    for (String endPos : POSITIONS) {
                        if (!startPos.equals(endPos)) { // Avoid the same start and end position
                            positionCombinations.add(new String[]{String.valueOf(agents), startPos, endPos});
                        }
                    }
                }
            }
            numConditions = positionCombinations.size();
        }
        List<Integer> conditions = newNback.generateNBackConditions(numEpisodes, numConditions, matchProbability);
        System.out.println(conditions);
        System.out.println();

        conditions.forEach(c -> {
            // Reverse the mapping from earlier
            int numAgents;
            String agentPos;
            String targetPos;

            if (diffType == MatchCode.NUMBER) {
                numAgents = c + minAgents - 1;
                agentPos = DEFAULT_START_POSITION;
                targetPos = DEFAULT_END_POSITION;
            } else if (diffType == MatchCode.POSITIONS) {
                // Mapping the condition index back to start and end positions
                String[] positions = positionCombinations.get(c - 1);
                agentPos = positions[0];
                targetPos = positions[1];
                numAgents = DEFAULT_NUM;
            } else {
                // Mapping the condition index back to the combination of agents and positions
                int positionIndex = c % positionCombinations.size();
                numAgents = Integer.parseInt(positionCombinations.get(positionIndex)[0]);
                String[] positions = {positionCombinations.get(positionIndex)[1], positionCombinations.get(positionIndex)[2]};
                agentPos = positions[0];
                targetPos = positions[1];
            }

            System.out.println("Num Agents: " + numAgents + ", Position: " + agentPos + ", Target: " + targetPos);

            char agentChar = (char) ('a' + numAgents - 1);
            String episodeCode = String.valueOf(agentChar);

            boolean nBackMatch = false; // Initialize as false

            // Check if there is a match with the episode nValue steps back
            if (episodes.size() >= nValue) {
                Episode nBackEpisode = episodes.get(episodes.size() - nValue);
                switch (matchType) {
                    case NUMBER -> nBackMatch = numAgents == nBackEpisode.numAgents();
                    case POSITIONS ->
                            nBackMatch = agentPos.equals(nBackEpisode.agentPos()) && targetPos.equals(nBackEpisode.targetPos());
                    case BOTH ->
                            nBackMatch = numAgents == nBackEpisode.numAgents() && agentPos.equals(nBackEpisode.agentPos()) && targetPos.equals(nBackEpisode.targetPos());
                }
            }

            episodes.add(new Episode(episodeLength, episodeCooldown, agentPos, targetPos, numAgents, nBackMatch, episodeCode, reviewPeriod)); // Include reviewPeriod in Episode
        });
    }

    public void generateEpisodes() {
        for (int i = 0; i < numEpisodes; i++) {
            String agentPos;
            String targetPos;
            int numAgents;
            boolean nBackMatch = false;

            // Apply n-back condition based on the user-defined probability
            if (i >= nValue && random.nextDouble() < matchProbability) {
                Episode previousEpisode = episodes.get(i - nValue);
                switch (matchType) {
                    case NUMBER -> {
                        numAgents = previousEpisode.numAgents();

                        agentPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_START_POSITION;
                        targetPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_END_POSITION;
                        while (agentPos.equals(targetPos)) {
                            targetPos = getRandomPosition();
                        }
                    }
                    case POSITIONS -> {
                        numAgents = (diffType == MatchCode.NUMBER) ? getRandomNumAgents() : DEFAULT_NUM;
                        agentPos = previousEpisode.agentPos();
                        targetPos = previousEpisode.targetPos();
                    }
                    default -> {
                        agentPos = previousEpisode.agentPos();
                        targetPos = previousEpisode.targetPos();
                        numAgents = previousEpisode.numAgents();
                    }
                }
                nBackMatch = true;
            } else {
                // If it's not a match, ensure this episode does not match the one nValue steps back based on the match type
                Episode previousEpisode = (i >= nValue) ? episodes.get(i - nValue) : new Episode(-1, -1, "", "", -1, false, "NONE", -1);
                do {
                    numAgents = (diffType == MatchCode.NUMBER) ? getRandomNumAgents() : DEFAULT_NUM;
                    agentPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_START_POSITION;
                    targetPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_END_POSITION;
                    // Ensure agent and target positions are not the same
                    while (agentPos.equals(targetPos)) {
                        targetPos = getRandomPosition();
                    }
                } while (
                        (matchType == MatchCode.BOTH && agentPos.equals(previousEpisode.agentPos()) && targetPos.equals(previousEpisode.targetPos()) && numAgents == previousEpisode.numAgents()) ||
                                (matchType == MatchCode.POSITIONS && agentPos.equals(previousEpisode.agentPos()) && targetPos.equals(previousEpisode.targetPos())) ||
                                (matchType == MatchCode.NUMBER && numAgents == previousEpisode.numAgents())
                );
            }

            char agentChar = (char) ('a' + numAgents - 1);
            String episodeCode = String.valueOf(agentChar);

            episodes.add(new Episode(episodeLength, episodeCooldown, agentPos, targetPos, numAgents, nBackMatch, episodeCode, reviewPeriod)); // Include reviewPeriod in Episode
        }
    }


    private String getRandomPosition() {
        return POSITIONS[random.nextInt(POSITIONS.length)];
    }

    private int getRandomNumAgents() {
        return random.nextInt(maxAgents - minAgents + 1) + minAgents;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            NBackGenerator nBackGenerator = new NBackGenerator();
            int numEpisodes = nBackGenerator.numEpisodes;
            if (numEpisodes == -1) {
                System.out.println("Enter the number of episodes to generate:");
                numEpisodes = scanner.nextInt();
            }

            int length = nBackGenerator.episodeLength;
            if (length == -1) {
                System.out.println("Enter the length of each episode:");
                length = scanner.nextInt();
            }

            int cooldown = nBackGenerator.episodeCooldown;
            if (cooldown == -1) {
                System.out.println("Enter the cooldown time between each episode:");
                cooldown = scanner.nextInt();
            }

            int reviewPeriod = nBackGenerator.reviewPeriod;
            if (reviewPeriod == -1) {
                System.out.println("Enter the review period between each episode:");
                reviewPeriod = scanner.nextInt();
            }

            int minAgents = nBackGenerator.minAgents;
            if (minAgents == -1) {
                System.out.println("Enter the minimum number of agents:");
                minAgents = scanner.nextInt();
            }

            int maxAgents = nBackGenerator.maxAgents;
            if (maxAgents == -1) {
                System.out.println("Enter the maximum number of agents:");
                maxAgents = scanner.nextInt();
            }

            double matchProbability = nBackGenerator.matchProbability;
            if (matchProbability == -1.0) {
                System.out.println("Enter the probability of an n-back match:");
                matchProbability = scanner.nextDouble();
            }

            int nValue = nBackGenerator.nValue;
            if (nValue == -1) {
                System.out.println("Enter the value for n (match distance):");
                nValue = scanner.nextInt();
            }

            MatchCode diffType = nBackGenerator.matchType;
            if (nBackGenerator.matchType == MatchCode.NOT_DEFINED) {
                System.out.println("Enter the difference type (1 - only numAgents changes between episodes, 2 - only positions, 3 - both):");
                int matchTypeOrdinal = scanner.nextInt();
                diffType = (matchTypeOrdinal == -1) ? nBackGenerator.matchType : MatchCode.values()[matchTypeOrdinal];
            }

            MatchCode matchType = nBackGenerator.matchType;
            if (nBackGenerator.matchType == MatchCode.NOT_DEFINED) {
                System.out.println("Enter the match type (1 - only numAgents matching constitutes a matching condition, 2 - only positions, 3 - both):");
                int matchTypeOrdinal = scanner.nextInt();
                matchType = (matchTypeOrdinal == -1) ? nBackGenerator.matchType : MatchCode.values()[matchTypeOrdinal];
            }

            nBackGenerator.configure(numEpisodes, length, cooldown, reviewPeriod, minAgents, maxAgents, matchProbability, nValue, diffType, matchType);
            nBackGenerator.generateEpisodesBalanced();
            System.out.println("\"episodes\": [");
            for (int i = 0; i < nBackGenerator.getEpisodes().size(); i++) {
                Episode episode = nBackGenerator.getEpisodes().get(i);
                System.out.print(episode);
                if (i < nBackGenerator.getEpisodes().size() - 1) {
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
                // Now prompt the user to enter the number of the file they want to view
                System.out.println("Enter the number of the file you want to view:");
                int fileNumber = scanner.nextInt();
                String selectedFileName = jsonFiles.get(fileNumber - 1);
                System.out.println("You selected: " + selectedFileName);

                try {
                    nBackGenerator.injectJsonFile(selectedFileName, nBackGenerator.getEpisodes());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                System.out.println("Exiting...");
            }

            scanner.close();

            nBackGenerator.testNBack();
        } catch (InterruptedException e) {
            System.out.println("Interrupted during time sleep");
        }
    }

private void testNBack() {
    // Run through the episodes and manually check if the n-back match is correct
    for (int i = 0; i < episodes.size(); i++) {
        Episode episode = episodes.get(i);
        boolean isNBackMatch = false;

        if (i >= nValue) {
            Episode nBackEpisode = episodes.get(i - nValue);
            switch (matchType) {
                case NUMBER -> isNBackMatch = episode.numAgents() == nBackEpisode.numAgents();
                case POSITIONS ->
                        isNBackMatch = episode.agentPos().equals(nBackEpisode.agentPos()) && episode.targetPos().equals(nBackEpisode.targetPos());
                case BOTH ->
                        isNBackMatch = episode.numAgents() == nBackEpisode.numAgents() && episode.agentPos().equals(nBackEpisode.agentPos()) && episode.targetPos().equals(nBackEpisode.targetPos());
            }
        }

        // Print out the result, comparing the manually checked value to the stored value
        System.out.println("Episode " + (i + 1) + ": " + episode.numAgents() + ", Start Position: " + episode.agentPos() + ", End Position: " + episode.targetPos() + ", N-Back Match (Calculated): " + isNBackMatch + ", N-Back Match (Stored): " + episode.nBackMatch());
    }
}


    public void injectJsonFile(String selectedFileName, List<Episode> episodes) throws IOException {
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


record Episode(int episodeLength, int episodeCooldown, String agentPos, String targetPos, int numAgents, boolean nBackMatch, String episodeCode, int reviewPeriod) {
    @Override
    public String toString() {
        return "{\n"
                + "\t\"episodeLength\": " + episodeLength + ",\n"
                + "\t\"episodeCooldown\": " + episodeCooldown + ",\n"
                + "\t\"reviewPeriod\": " + reviewPeriod + ",\n" // Include reviewPeriod in JSON
                + "\t\"agentPos\": \"" + agentPos + "\",\n"
                + "\t\"targetPos\": \"" + targetPos + "\",\n"
                + "\t\"numAgents\": " + numAgents + ",\n"
                + "\t\"degradationMatch\": " + nBackMatch + ",\n"
                + "\t\"episodeCode\": \"" + episodeCode + "\"\n"
                + "}";
    }
}
