package tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
public class NBackGenerator {
    private List<Episode> episodes;
    private Random random;
    private static final String[] POSITIONS = {"BL", "TL", "TR", "BR", "T", "B", "L", "R"}; // "Bottom Left", "Top Left", "Top Right", "Bottom Right, Top, Bottom, Left, Right"

    private int numEpisodes = -1;
    private int episodeLength = -1;
    private int minAgents = -1;
    private int maxAgents = -1;
    private double matchProbability = -1.0;
    private int nValue = -1;
    private MatchType matchType = MatchType.NOT_DEFINED;

    public enum MatchType {
        AGENTS,
        POSITIONS,
        BOTH,
        NOT_DEFINED
    }

    public NBackGenerator() {
        this.episodes = new ArrayList<>();
        this.random = new Random();
    }

    private void configure(int numEpisodes, int length, int minAgents, int maxAgents, double matchProbability, int nValue, MatchType matchType) {
        this.numEpisodes = numEpisodes;
        this.episodeLength = length;
        this.minAgents = minAgents;
        this.maxAgents = maxAgents;
        this.matchProbability = matchProbability;
        this.nValue = nValue;
        this.matchType = matchType;
    }

    public void generateEpisodes() {
        for (int i = 0; i < numEpisodes; i++) {
            String agentPos = null;
            String targetPos = null;
            int numAgents = getRandomNumAgents();
            boolean nBackMatch = false;

            // Apply n-back condition based on the user-defined probability
            if (i >= nValue && random.nextDouble() < matchProbability) {
                Episode previousEpisode = episodes.get(i - nValue);
                switch (matchType) {
                    case AGENTS -> numAgents = previousEpisode.numAgents();
                    case POSITIONS -> {
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
                Episode previousEpisode = (i >= nValue) ? episodes.get(i - nValue) : new Episode(-1, "", "", -1, false);
                do {
                    agentPos = getRandomPosition();
                    targetPos = getRandomPosition();
                    numAgents = getRandomNumAgents();
                    // Ensure agent and target positions are not the same
                    while (agentPos.equals(targetPos)) {
                        targetPos = getRandomPosition();
                    }
                } while (
                        (matchType == MatchType.BOTH && agentPos.equals(previousEpisode.agentPos()) && targetPos.equals(previousEpisode.targetPos()) && numAgents == previousEpisode.numAgents()) ||
                                (matchType == MatchType.POSITIONS && agentPos.equals(previousEpisode.agentPos()) && targetPos.equals(previousEpisode.targetPos())) ||
                                (matchType == MatchType.AGENTS && numAgents == previousEpisode.numAgents())
                );
            }

            episodes.add(new Episode(episodeLength, agentPos, targetPos, numAgents, nBackMatch));
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

        int nValue = nBackGenerator.maxAgents;
        if (nValue == -1) {
            System.out.println("Enter the value for n (match distance):");
            nValue = scanner.nextInt();
        }

        // If match type is not defined, prompt the user to enter it
        MatchType matchType = nBackGenerator.matchType;
        if (nBackGenerator.matchType == MatchType.NOT_DEFINED) {
            System.out.println("Enter the match type (1 - only numAgents, 2 - only positions, 3 - both):");
            int matchTypeOrdinal = scanner.nextInt();
             matchType = (matchTypeOrdinal == -1) ? nBackGenerator.matchType : MatchType.values()[matchTypeOrdinal];
        }

        nBackGenerator.configure(numEpisodes, length, minAgents, maxAgents, matchProbability, nValue, matchType);
        nBackGenerator.generateEpisodes();
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
    }

}

record Episode(int episodeLength, String agentPos, String targetPos, int numAgents, boolean nBackMatch) {
    @Override
    public String toString() {
        return "{\n"
                + "\t\"episodeLength\": " + episodeLength + ",\n"
                + "\t\"agentPos\": \"" + agentPos + "\",\n"
                + "\t\"targetPos\": \"" + targetPos + "\",\n"
                + "\t\"numAgents\": " + numAgents + ",\n"
                + "\t\"nBackMatch\": " + nBackMatch + "\n"
                + "}";
    }
}
