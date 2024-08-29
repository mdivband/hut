package tool;

import java.util.*;

public class newNback {

    public static void main(String[] args) {
        int totalCards = 32;
        int nConditions = 4;
        double matchProbability = 0.125;

        List<Integer> sequence = generateNBackConditions(totalCards, nConditions, matchProbability);
        System.out.println("Generated Sequence: " + sequence);
        System.out.println("Condition Counts: " + Arrays.toString(countConditions(sequence, nConditions)));

        checkSequence(sequence);
    }

    public static List<Integer> generateNBackConditions(int totalCards, int nConditions, double matchProbability) {
        List<Integer> cardSequence;
        List<Integer> remainingConditions;
        do {
            cardSequence = new ArrayList<>(Collections.nCopies(totalCards, 0));
            remainingConditions = new ArrayList<>();

            int cardsPerCondition = totalCards / nConditions;
            int totalMatches = (int) (totalCards * matchProbability);
            int matchesPerCondition = totalMatches / nConditions;

            // Step 1: Generate match patterns
            List<List<Integer>> matchPatterns = new ArrayList<>();
            int[] usedConditions = new int[nConditions + 1]; // Tracks the number of times each condition is used in patterns

            for (int i = 1; i <= nConditions; i++) {
                for (int j = 0; j < matchesPerCondition; j++) {
                    List<Integer> pattern = new ArrayList<>();
                    pattern.add(i);
                    pattern.add(0);  // using 0 as a placeholder for "_"
                    pattern.add(i);
                    matchPatterns.add(pattern);
                    usedConditions[i] += 2; // Increment the count for this condition
                }
            }
            System.out.println("Match Patterns: " + matchPatterns);

            // Step 2: Initialize card sequence and available positions
            List<Integer> availablePositions = new ArrayList<>();
            for (int i = 0; i < totalCards; i++) {
                availablePositions.add(i);
            }
            System.out.println("Initial Card Sequence: " + cardSequence);
            System.out.println("Initial Available Positions: " + availablePositions);

            Random rand = new Random();

            // Step 3: Place match patterns into the card sequence
            for (List<Integer> pattern : matchPatterns) {
                boolean placed = false;
                while (!placed) {
                    int pos = availablePositions.get(rand.nextInt(availablePositions.size() - 2));
                    if (cardSequence.get(pos) == 0 && cardSequence.get(pos + 1) == 0 && cardSequence.get(pos + 2) == 0) {
                        cardSequence.set(pos, pattern.get(0));
                        cardSequence.set(pos + 2, pattern.get(2));
                        availablePositions.remove((Integer) pos);
                        availablePositions.remove((Integer) (pos + 2));
                        placed = true;
                    }
                }
            }
            System.out.println("Card Sequence after placing match patterns: " + cardSequence);
            System.out.println("Available Positions after placing match patterns: " + availablePositions);

            // Step 4: Prepare remaining conditions and adjust based on used conditions
            for (int i = 1; i <= nConditions; i++) {
                int remainingCount = cardsPerCondition - usedConditions[i];
                for (int j = 0; j < remainingCount; j++) {
                    remainingConditions.add(i);
                }
            }
            System.out.println("Remaining Conditions before shuffle: " + remainingConditions);
            Collections.shuffle(remainingConditions);
            System.out.println("Remaining Conditions after shuffle: " + remainingConditions);

            // Step 5: Systematic placement of remaining conditions, ensuring no accidental matches
            for (int i = 0; i < totalCards; i++) {
                if (cardSequence.get(i) == 0) {
                    for (int j = 0; j < remainingConditions.size(); j++) {
                        int condition = remainingConditions.get(j);
                        if (isValidPlacement(cardSequence, i, condition, nConditions)) {
                            cardSequence.set(i, condition);
                            remainingConditions.remove(j);
                            break;
                        }
                    }
                }
            }
        } while (countConditions(cardSequence, nConditions)[0] > 0);

        System.out.println("Final Card Sequence: " + cardSequence);
        System.out.println("Remaining Conditions after placement: " + remainingConditions);

        return cardSequence;
    }

    // Helper method to check if placing a condition will cause an accidental match
    public static boolean isValidPlacement(List<Integer> cardSequence, int index, int condition, int nConditions) {
        int totalCards = cardSequence.size();

        // Check the previous n-back positions to avoid creating unintended matches
        if (index >= 2 && cardSequence.get(index - 2) == condition) {
            return false;
        }

        // Check if placing this condition violates adjacent placement rules
        if ((index > 0 && cardSequence.get(index - 1) == condition) ||
                (index < totalCards - 1 && cardSequence.get(index + 1) == condition)) {
            return false;
        }

        return true;
    }

    // Method to check the sequence and tag matches/non-matches, printing each result with the condition
    public static void checkSequence(List<Integer> sequence) {
        for (int i = 0; i < sequence.size(); i++) {
            String result;
            if (i >= 2 && sequence.get(i).equals(sequence.get(i - 2))) {
                result = "Match";
            } else {
                result = "No Match";
            }
            System.out.println(sequence.get(i) + ": " + result);
        }
    }


    public static int[] countConditions(List<Integer> sequence, int nConditions) {
        int[] counts = new int[nConditions + 1];
        for (int condition : sequence) {
            counts[condition]++;
        }
        return counts;
    }
}
