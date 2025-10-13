package tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * FastPI Episode Generator (single-file)
 *
 * - Generates C chained Eulerian cycles (directed complete graph over cfg.nodes).
 * - Each cycle's first few edges are "not boring" (not all ±1-step) if cfg.avoidBoringStart = true.
 * - Balanced colours per episode: one of each colour per |palette| agents, shuffled.
 * - degradationTime ∈ {3,4,5}.
 * - Writes episodes.json and prints a self-documenting summary + validation report.
 *
 * HOW TO RUN
 *   With Gradle: set application mainClass to fastpi.GenerateEpisodes then `./gradlew run`
 *   With javac:
 *     javac -cp gson-2.11.0.jar -d out src/main/java/fastpi/GenerateEpisodes.java
 *     java  -cp gson-2.11.0.jar:out fastpi.GenerateEpisodes   (Windows: use ';' instead of ':')
 */
public class GenerateEpisodes {

    /* ============================== CONFIG ============================== */

    /** First-start policy for the first cycle. */
    public enum FirstStartMode { RANDOM, FIXED, ROUND_ROBIN }

    /** User-tunable configuration. */
    public static class DeckConfig {
        // Graph / deck
        public List<Integer> nodes = Arrays.asList(4, 8, 12, 16, 20);
        public int repetitions = 3;                  // number of cycles to chain
        public Long seed = 20251013L;                // null => nondeterministic
        public boolean avoidBoringStart = true;      // first minMix edges not all ±1-step
        public int minMix = 4;                       // "few" edges at the start of each cycle

        // First-start control for the FIRST cycle only
        public FirstStartMode firstStartMode = FirstStartMode.RANDOM;
        public Integer firstStart = null;            // used when FIXED
        public Path rrStatePath = Path.of(".fastpi_round_robin_state.txt"); // used when ROUND_ROBIN

        // Episode content
        public double episodeLength = 7.0;           // seconds
        public int[] degradationChoices = new int[]{3, 4, 5}; // exact values
        public List<String> palette = Arrays.asList("red", "green", "blue", "yellow");

        // Output
        // TIP: set to your server path if desired, e.g. Path.of("/server/web/scenarios/eps/episodes.json")
        public Path outPath = Path.of("episodes.json");
    }

    /* ============================== DATA ================================ */

    /** One episode (matches your schema). */
    public static class Episode {
        public double episodeLength;
        public int numAgents;
        public int degradationTime;          // {3,4,5}
        public List<String> colours;         // balanced, shuffled
        public String degColour;             // one of colours

        public Episode(double episodeLength, int numAgents, int degradationTime,
                       List<String> colours, String degColour) {
            this.episodeLength = episodeLength;
            this.numAgents = numAgents;
            this.degradationTime = degradationTime;
            this.colours = colours;
            this.degColour = degColour;
        }
    }

    /** JSON root. */
    public static class EpisodeDoc {
        public List<Episode> episodes = new ArrayList<>();
    }

    /* ========================== EULERIAN DECK =========================== */

    /** Directed complete graph Eulerian cycle (no self-loops). Returns nodes of length E+1 with seq[0]==seq[E]. */
    static List<Integer> randomEulerianCycle(List<Integer> nodes, Random rng, Integer start) {
        Map<Integer, Deque<Integer>> g = new HashMap<>();
        for (Integer u : nodes) {
            List<Integer> outs = new ArrayList<>();
            for (Integer v : nodes) if (!v.equals(u)) outs.add(v);
            Collections.shuffle(outs, rng);
            g.put(u, new ArrayDeque<>(outs));
        }
        int s = (start != null) ? start : nodes.get(rng.nextInt(nodes.size()));
        Deque<Integer> st = new ArrayDeque<>();
        List<Integer> circuit = new ArrayList<>();
        st.push(s);
        while (!st.isEmpty()) {
            int u = st.peek();
            Deque<Integer> outs = g.get(u);
            if (outs != null && !outs.isEmpty()) {
                int v = outs.pop();
                st.push(v);
            } else {
                circuit.add(st.pop());
            }
        }
        Collections.reverse(circuit);
        return circuit; // seq[0]==seq[last]
    }

    /** Absolute "index distance" in node ordering. */
    static int indexDist(List<Integer> nodes, int a, int b) {
        return Math.abs(nodes.indexOf(b) - nodes.indexOf(a));
    }

    /** Choose the first start for the first cycle. */
    static Integer chooseFirstStart(DeckConfig cfg, Random rng) {
        switch (cfg.firstStartMode) {
            case FIXED:
                if (cfg.firstStart == null || !cfg.nodes.contains(cfg.firstStart)) {
                    throw new IllegalArgumentException("FIXED firstStart must be set and in cfg.nodes");
                }
                return cfg.firstStart;
            case ROUND_ROBIN:
                try {
                    int last = -1;
                    if (Files.exists(cfg.rrStatePath)) {
                        String s = Files.readString(cfg.rrStatePath).trim();
                        last = Integer.parseInt(s);
                    }
                    int nextIdx = (last + 1) % cfg.nodes.size();
                    Files.writeString(cfg.rrStatePath, Integer.toString(nextIdx));
                    return cfg.nodes.get(nextIdx);
                } catch (Exception e) {
                    // fallback to random
                    return cfg.nodes.get(rng.nextInt(cfg.nodes.size()));
                }
            case RANDOM:
            default:
                return cfg.nodes.get(rng.nextInt(cfg.nodes.size()));
        }
    }

    /** Generate a cycle that starts at 'start' and, if requested, isn't "boring" at the beginning. */
    static List<Integer> generateCycleWithConstraints(
            List<Integer> nodes, Random rng, Integer start, boolean avoidBoringStart, int minMix, int maxTries) {
        for (int t = 0; t < maxTries; t++) {
            List<Integer> seq = randomEulerianCycle(nodes, rng, start);
            if (avoidBoringStart && minMix > 0) {
                boolean allOne = true;
                for (int i = 0; i < Math.min(minMix, seq.size() - 1); i++) {
                    if (indexDist(nodes, seq.get(i), seq.get(i + 1)) != 1) { allOne = false; break; }
                }
                if (allOne) continue; // try again
            }
            return seq;
        }
        throw new IllegalStateException("Failed to generate a suitably mixed cycle after " + maxTries + " tries.");
    }

    /** Build C chained cycles; cycle 1 uses firstStart policy, cycles 2..C start at previous end. */
    static List<Integer> buildChainedNodeSequence(DeckConfig cfg, Random rng,
                                                  StringBuilder log, List<List<Integer>> perCycle) {
        List<Integer> master = new ArrayList<>();
        Integer prevEnd = null;

        for (int c = 1; c <= cfg.repetitions; c++) {
            Integer startForCycle = (prevEnd == null) ? chooseFirstStart(cfg, rng) : prevEnd;
            List<Integer> seq = generateCycleWithConstraints(cfg.nodes, rng, startForCycle,
                    cfg.avoidBoringStart, cfg.minMix, 200);

            perCycle.add(seq);

            int E = cfg.nodes.size() * (cfg.nodes.size() - 1);
            if (seq.size() != E + 1) throw new AssertionError("Bad cycle length");

            if (prevEnd == null) {
                master.addAll(seq);
            } else {
                // guaranteed start == prevEnd; drop boundary node to avoid duplication
                master.addAll(seq.subList(1, seq.size()));
            }
            prevEnd = seq.get(seq.size() - 1);

            // FULL per-cycle sequence
            log.append(String.format("Cycle %d sequence (%d nodes) start=%d end=%d\n%s\n",
                    c, seq.size(), seq.get(0), prevEnd, seq));
        }
        return master;
    }

    /* =========================== EPISODES ============================== */

    static List<String> balancedColours(int N, List<String> palette, Random rng) {
        int p = palette.size();
        if (N % p != 0) {
            throw new IllegalArgumentException("numAgents=" + N + " not multiple of palette size=" + p
                    + " (adjust nodes or palette).");
        }
        List<String> colours = new ArrayList<>(N);
        int blocks = N / p;
        for (int i = 0; i < blocks; i++) colours.addAll(palette);
        Collections.shuffle(colours, rng);
        return colours;
    }

    static int pickDegradationTime(int[] choices, Random rng) {
        return choices[rng.nextInt(choices.length)];
    }

    static EpisodeDoc buildEpisodes(DeckConfig cfg, StringBuilder runLog) {
        Random rng = (cfg.seed == null) ? new Random() : new Random(cfg.seed);
        EpisodeDoc doc = new EpisodeDoc();

        // 1) Deck(s)
        List<List<Integer>> perCycleSequences = new ArrayList<>();
        List<Integer> nodeSeq = buildChainedNodeSequence(cfg, rng, runLog, perCycleSequences);

        // Full concatenated sequence + pair counts
        runLog.append(String.format("%nConcatenated sequence (%d nodes): %s%n", nodeSeq.size(), nodeSeq));

        Map<String, Integer> pairCounts = new TreeMap<>();
        for (int i = 0; i < nodeSeq.size() - 1; i++) {
            int a = nodeSeq.get(i), b = nodeSeq.get(i + 1);
            pairCounts.put(a + "->" + b, pairCounts.getOrDefault(a + "->" + b, 0) + 1);
        }
        runLog.append("Ordered-pair counts across all cycles:\n");
        for (int a : cfg.nodes) for (int b : cfg.nodes) if (a != b) {
            String k = a + "->" + b;
            runLog.append(String.format("  %-6s : %d%n", k, pairCounts.getOrDefault(k, 0)));
        }

        // 2) Episodes
        for (int n : nodeSeq) {
            List<String> colours = balancedColours(n, cfg.palette, rng);
            String degColour = colours.get(rng.nextInt(colours.size()));
            int degTime = pickDegradationTime(cfg.degradationChoices, rng);
            doc.episodes.add(new Episode(cfg.episodeLength, n, degTime, colours, degColour));
        }

        // Footer summary
        runLog.append(String.format("%nTotal episodes: %d%n", doc.episodes.size()));
        runLog.append(String.format("Unique N values: %s%n", new TreeSet<>(cfg.nodes)));
        runLog.append(String.format("Output path: %s%n", cfg.outPath.toAbsolutePath()));
        return doc;
    }

    /* =========================== VALIDATION ============================ */

    static void validateAll(DeckConfig cfg, EpisodeDoc doc, StringBuilder report) {
        // A. numAgents are allowed
        Set<Integer> allowed = new HashSet<>(cfg.nodes);
        for (int i = 0; i < doc.episodes.size(); i++) {
            int n = doc.episodes.get(i).numAgents;
            if (!allowed.contains(n))
                throw new AssertionError("Episode " + i + ": numAgents=" + n + " not in " + allowed);
        }

        // B. adjacency & ordered-pair uniformity
        Map<String, Integer> pairCounts = new TreeMap<>();
        for (int i = 0; i < doc.episodes.size() - 1; i++) {
            int a = doc.episodes.get(i).numAgents;
            int b = doc.episodes.get(i + 1).numAgents;
            if (a == b) throw new AssertionError("Consecutive equal N at episodes " + i + " and " + (i + 1));
            String key = a + "->" + b;
            pairCounts.put(key, pairCounts.getOrDefault(key, 0) + 1);
        }
        int expectedPerPair = cfg.repetitions;
        int wrong = 0;
        for (int a : cfg.nodes) for (int b : cfg.nodes) if (a != b) {
            String key = a + "->" + b;
            int got = pairCounts.getOrDefault(key, 0);
            if (got != expectedPerPair) {
                wrong++;
                report.append(String.format("WARN pair %s count=%d (expected=%d)%n", key, got, expectedPerPair));
            }
        }
        if (wrong == 0) report.append("OK: Every ordered pair appears exactly " + expectedPerPair + " times.\n");

        // C. colours balanced; degColour∈colours; degradationTime in set
        Set<Integer> allowedTimes = new HashSet<>();
        for (int x : cfg.degradationChoices) allowedTimes.add(x);

        for (int i = 0; i < doc.episodes.size(); i++) {
            Episode e = doc.episodes.get(i);
            if (!allowedTimes.contains(e.degradationTime))
                throw new AssertionError("Episode " + i + ": degradationTime=" + e.degradationTime + " not in " + allowedTimes);
            if (!e.colours.contains(e.degColour))
                throw new AssertionError("Episode " + i + ": degColour not in colours");

            Map<String, Integer> cts = new HashMap<>();
            for (String c : e.colours) cts.put(c, cts.getOrDefault(c, 0) + 1);
            int target = e.numAgents / cfg.palette.size();
            for (String c : cfg.palette) {
                int got = cts.getOrDefault(c, 0);
                if (got != target) {
                    throw new AssertionError("Episode " + i + ": colour count for '" + c + "' = " + got + " (target=" + target + ")");
                }
            }
        }
        report.append("OK: Colours balanced, degColour present, and degradationTime ∈ " + allowedTimes + ".\n");
    }

    /* ============================ OUTPUT ================================ */

    static void writeJson(EpisodeDoc doc, Path out) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(Map.of("episodes", doc.episodes));
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent); // ensure parent dirs
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println(json);
        }
    }

    /* ============================== MAIN ================================ */

    public static void main(String[] args) throws Exception {
        DeckConfig cfg = new DeckConfig();

        // EXAMPLES (uncomment one):
        // cfg.seed = null;                                // different each run
        // cfg.firstStartMode = FirstStartMode.FIXED; cfg.firstStart = 12;    // fixed non-8 start
        // cfg.firstStartMode = FirstStartMode.ROUND_ROBIN;                   // rotates across runs
        // cfg.outPath = Path.of("/server/web/scenarios/eps/episodes.json");  // custom path

        System.out.println("Working directory: " + Path.of("").toAbsolutePath());
        System.out.println("Target JSON path : " + cfg.outPath.toAbsolutePath());

        StringBuilder log = new StringBuilder();
        log.append("=== FastPI Episode Generator (Java) ===\n")
                .append("Nodes: ").append(cfg.nodes).append("\n")
                .append("Repetitions (cycles): ").append(cfg.repetitions).append("\n")
                .append("Seed: ").append(cfg.seed).append("\n")
                .append("First-start mode: ").append(cfg.firstStartMode)
                .append(cfg.firstStartMode == FirstStartMode.FIXED ? " (" + cfg.firstStart + ")" : "")
                .append("\nAvoid boring start: ").append(cfg.avoidBoringStart)
                .append(" (first ").append(cfg.minMix).append(" edges not all 1-step)\n\n");

        // Build
        EpisodeDoc doc = buildEpisodes(cfg, log);

        // Validate in-memory
        StringBuilder report = new StringBuilder();
        validateAll(cfg, doc, report);

        // Write
        writeJson(doc, cfg.outPath);

        // Print full self-doc
        log.append("\n=== Validation Report ===\n").append(report);
        log.append("\nWrote ").append(cfg.outPath.toAbsolutePath()).append("\n");
        System.out.println(log);
    }
}
