//package tool;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.*;
//
///** Automatically generates n-back episodes.
// * Note that the result isn't guaranteed to be good so check it.
// */
//public class NBackGenerator {
//    private List<Episode> episodes;
//    private Random random;
//    private static final String[] POSITIONS = {"BL", "TL", "TR", "BR", "T", "B", "L", "R"}; // "Bottom Left", "Top Left", "Top Right", "Bottom Right, Top, Bottom, Left, Right"
//
//    private int numEpisodes = 10;
//    private int episodeLength = 5;
//    private int episodeCooldown = 5;
//    private int reviewPeriod = 5; // New field for review period
//    private int minAgents = 3;
//    private int maxAgents = 10;
//    private double matchProbability = 0.4;
//    private int nValue = 2;
//    private MatchCode diffType = MatchCode.NUMBER;
//    private MatchCode matchType = MatchCode.NUMBER;
//
//    public enum MatchCode {
//        NUMBER,
//        POSITIONS,
//        BOTH,
//        NOT_DEFINED
//    }
//
//    private static final int DEFAULT_NUM = 5;
//    private static final String DEFAULT_START_POSITION = "L";
//    private static final String DEFAULT_END_POSITION = "R";
//
//
//    public NBackGenerator() {
//        this.episodes = new ArrayList<>();
//        this.random = new Random();
//    }
//
//    private void configure(int numEpisodes, int length, int cooldown, int reviewPeriod, int minAgents, int maxAgents, double matchProbability, int nValue, MatchCode diffType, MatchCode matchType) {
//        this.numEpisodes = numEpisodes;
//        this.episodeLength = length;
//        this.episodeCooldown = cooldown;
//        this.reviewPeriod = reviewPeriod; // Set the review period
//        this.minAgents = minAgents;
//        this.maxAgents = maxAgents;
//        this.matchProbability = matchProbability;
//        this.nValue = nValue;
//        this.diffType = diffType;
//        this.matchType = matchType;
//    }
//
//    public void generateEpisodes() {
//        for (int i = 0; i < numEpisodes; i++) {
//            String agentPos;
//            String targetPos;
//            int numAgents;
//            boolean degradationMatch = false;
//
//            // Apply n-back condition based on the user-defined probability
//            if (i >= nValue && random.nextDouble() < matchProbability) {
//                Episode previousEpisode = episodes.get(i - nValue);
//                switch (matchType) {
//                    case NUMBER -> {
//                        numAgents = previousEpisode.numAgents();
//
//                        agentPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_START_POSITION;
//                        targetPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_END_POSITION;
//                        while (agentPos.equals(targetPos)) {
//                            targetPos = getRandomPosition();
//                        }
//                    }
//                    case POSITIONS -> {
//                        numAgents = (diffType == MatchCode.NUMBER) ? getRandomNumAgents() : DEFAULT_NUM;
//                        agentPos = previousEpisode.agentPos();
//                        targetPos = previousEpisode.targetPos();
//                    }
//                    default -> {
//                        agentPos = previousEpisode.agentPos();
//                        targetPos = previousEpisode.targetPos();
//                        numAgents = previousEpisode.numAgents();
//                    }
//                }
//                degradationMatch = true;
//            } else {
//                // If it's not a match, ensure this episode does not match the one nValue steps back based on the match type
//                Episode previousEpisode = (i >= nValue) ? episodes.get(i - nValue) : new Episode(-1, -1, "", "", -1, false, "NONE", -1);
//                do {
//                    numAgents = (diffType == MatchCode.NUMBER) ? getRandomNumAgents() : DEFAULT_NUM;
//                    agentPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_START_POSITION;
//                    targetPos = (diffType == MatchCode.POSITIONS) ? getRandomPosition() : DEFAULT_END_POSITION;
//                    // Ensure agent and target positions are not the same
//                    while (agentPos.equals(targetPos)) {
//                        targetPos = getRandomPosition();
//                    }
//                } while (
//                        (matchType == MatchCode.BOTH && agentPos.equals(previousEpisode.agentPos()) && targetPos.equals(previousEpisode.targetPos()) && numAgents == previousEpisode.numAgents()) ||
//                                (matchType == MatchCode.POSITIONS && agentPos.equals(previousEpisode.agentPos()) && targetPos.equals(previousEpisode.targetPos())) ||
//                                (matchType == MatchCode.NUMBER && numAgents == previousEpisode.numAgents())
//                );
//            }
//
//            char agentChar = (char) ('a' + numAgents - 1);
//            String episodeCode = String.valueOf(agentChar);
//
//            episodes.add(new Episode(episodeLength, episodeCooldown, agentPos, targetPos, numAgents, degradationMatch, episodeCode, reviewPeriod)); // Include reviewPeriod in Episode
//        }
//    }
//
//    private String getRandomPosition() {
//        return POSITIONS[random.nextInt(POSITIONS.length)];
//    }
//
//    private int getRandomNumAgents() {
//        return random.nextInt(maxAgents - minAgents + 1) + minAgents;
//    }
//
//    public List<Episode> getEpisodes() {
//        return episodes;
//    }
//
//    public static void main(String[] args) {
//        Scanner scanner = new Scanner(System.in);
//
//        NBackGenerator nBackGenerator = new NBackGenerator();
//        int numEpisodes = nBackGenerator.numEpisodes;
//        if (numEpisodes == -1) {
//            System.out.println("Enter the number of episodes to generate:");
//            numEpisodes = scanner.nextInt();
//        }
//
//        int length = nBackGenerator.episodeLength;
//        if (length == -1) {
//            System.out.println("Enter the length of each episode:");
//            length = scanner.nextInt();
//        }
//
//        int cooldown = nBackGenerator.episodeCooldown;
//        if (cooldown == -1) {
//            System.out.println("Enter the cooldown time between each episode:");
//            cooldown = scanner.nextInt();
//        }
//
//        int reviewPeriod = nBackGenerator.reviewPeriod;
//        if (reviewPeriod == -1) {
//            System.out.println("Enter the review period between each episode:");
//            reviewPeriod = scanner.nextInt();
//        }
//
//        int minAgents = nBackGenerator.minAgents;
//        if (minAgents == -1) {
//            System.out.println("Enter the minimum number of agents:");
//            minAgents = scanner.nextInt();
//        }
//
//        int maxAgents = nBackGenerator.maxAgents;
//        if (maxAgents == -1) {
//            System.out.println("Enter the maximum number of agents:");
//            maxAgents = scanner.nextInt();
//        }
//
//        double matchProbability = nBackGenerator.matchProbability;
//        if (matchProbability == -1.0) {
//            System.out.println("Enter the probability of an n-back match:");
//            matchProbability = scanner.nextDouble();
//        }
//
//        int nValue = nBackGenerator.nValue;
//        if (nValue == -1) {
//            System.out.println("Enter the value for n (match distance):");
//            nValue = scanner.nextInt();
//        }
//
//        MatchCode diffType = nBackGenerator.matchType;
//        if (nBackGenerator.matchType == MatchCode.NOT_DEFINED) {
//            System.out.println("Enter the difference type (1 - only numAgents changes between episodes, 2 - only positions, 3 - both):");
//            int matchTypeOrdinal = scanner.nextInt();
//            diffType = (matchTypeOrdinal == -1) ? nBackGenerator.matchType : MatchCode.values()[matchTypeOrdinal];
//        }
//
//        MatchCode matchType = nBackGenerator.matchType;
//        if (nBackGenerator.matchType == MatchCode.NOT_DEFINED) {
//            System.out.println("Enter the match type (1 - only numAgents matching constitutes a matching condition, 2 - only positions, 3 - both):");
//            int matchTypeOrdinal = scanner.nextInt();
//            matchType = (matchTypeOrdinal == -1) ? nBackGenerator.matchType : MatchCode.values()[matchTypeOrdinal];
//        }
//
//        nBackGenerator.configure(numEpisodes, length, cooldown, reviewPeriod, minAgents, maxAgents, matchProbability, nValue, diffType, matchType);
//        nBackGenerator.generateEpisodes();
//        System.out.println("\"episodes\": [");
//        for (int i = 0; i < nBackGenerator.getEpisodes().size(); i++) {
//            Episode episode = nBackGenerator.getEpisodes().get(i);
//            System.out.print(episode);
//            if (i < nBackGenerator.getEpisodes().size() - 1) {
//                System.out.println(",");
//            } else {
//                System.out.println();
//            }
//        }
//        System.out.println("]");
//
//        // Now it lists the json files in server/web/scenarios, and prompts the user to select one by entering the number (they will be listed 1: scenario1.json, 2: scenario2.json, etc.)
//        List<String> jsonFiles = new ArrayList<>();
//        File directory = new File("server/web/scenarios");
//
//        if (directory.isDirectory()) {
//            File[] files = directory.listFiles();
//            if (files != null) {
//                for (File file : files) {
//                    if (file.isFile() && file.getName().endsWith(".json")) {
//                        jsonFiles.add(file.getAbsolutePath());
//                    }
//                }
//            }
//        }
//
//        // Now list then for the user with numbers
//        for (int i = 0; i < jsonFiles.size(); i++) {
//            System.out.println((i + 1) + ": " + jsonFiles.get(i));
//        }
//
//        System.out.println("Would you like to update one of the files with the generated episodes? ([Y]/n)");
//        // Read the user's input
//        String userInput = scanner.nextLine().trim();
//
//        // Default to 'y' if the user presses Enter
//        if (userInput.isEmpty()) {
//            userInput = "y";
//        }
//
//        // Check the user's choice
//        if (userInput.equalsIgnoreCase("y")) {
//            // Now prompt the user to enter the number of the file they want to view
//            System.out.println("Enter the number of the file you want to view:");
//            int fileNumber = scanner.nextInt();
//            String selectedFileName = jsonFiles.get(fileNumber - 1);
//            System.out.println("You selected: " + selectedFileName);
//
//            try {
//                nBackGenerator.injectJsonFile(selectedFileName, nBackGenerator.getEpisodes());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        } else {
//            System.out.println("Exiting...");
//        }
//
//    }
//
//
//    public void injectJsonFile(String selectedFileName, List<Episode> episodes) throws IOException {
//        GsonUtils.create();
//
//        System.out.println("Reading JSON from file: " + selectedFileName);
//        String jsonContent = GsonUtils.readFile(selectedFileName);
//
//        Object jsonObj = GsonUtils.fromJson(jsonContent);
//
//        if (GsonUtils.hasKey(jsonObj, "episodes")) {
//            ((Map<String, Object>) jsonObj).put("episodes", episodes);
//        } else {
//            System.out.println("'episodes' key not found in the JSON file.");
//            throw new IOException("Key not found");
//        }
//
//        String modifiedJson = GsonUtils.toJson(jsonObj);
//
//        System.out.println("Saving modified JSON back to file: " + selectedFileName);
//        try (FileWriter fileWriter = new FileWriter(selectedFileName)) {
//            fileWriter.write(modifiedJson);
//        }
//
//        System.out.println("JSON file updated successfully.");
//    }
//}
//
//
//record Episode(int episodeLength, int episodeCooldown, String agentPos, String targetPos, int numAgents, boolean degradationMatch, String episodeCode, int reviewPeriod) {
//    @Override
//    public String toString() {
//        return "{\n"
//                + "\t\"episodeLength\": " + episodeLength + ",\n"
//                + "\t\"episodeCooldown\": " + episodeCooldown + ",\n"
//                + "\t\"reviewPeriod\": " + reviewPeriod + ",\n" // Include reviewPeriod in JSON
//                + "\t\"agentPos\": \"" + agentPos + "\",\n"
//                + "\t\"targetPos\": \"" + targetPos + "\",\n"
//                + "\t\"numAgents\": " + numAgents + ",\n"
//                + "\t\"degradationMatch\": " + degradationMatch + ",\n"
//                + "\t\"episodeCode\": \"" + episodeCode + "\"\n"
//                + "}";
//    }
//}
