package tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * FastPI Episode Generator (multigraph Eulerian tour)
 *
 * - Builds ONE Eulerian tour on a directed multigraph with 'repetitions' parallel edges
 *   for each ordered pair (u->v, u!=v). This naturally interleaves cycles.
 * - First-start policy: RANDOM | FIXED | ROUND_ROBIN.
 * - "Not boring" start: first 'minMix' edges not all ±1-step (optional).
 * - Episodes have extra fields: prevAgents and episodeCode ("EP-prev-cur-cT" where c is colour initial, T is time).
 * - Colours balanced (one of each per |palette| agents, shuffled).
 * - degradationTime ∈ {3,4,5}.
 * - Writes episodes.json and prints a full self-doc + validation.
 */
public class GenerateEpisodes {

    /* ============================== CONFIG ============================== */

    public enum FirstStartMode { RANDOM, FIXED, ROUND_ROBIN }

    public static class DeckConfig {
        // Node values (keep each multiple of palette size for exact colour balancing)
        public List<Integer> nodes = Arrays.asList(4, 8, 12, 16, 20);

        // Each ordered pair u->v appears exactly 'repetitions' times (parallel edges)
        public int repetitions = 3;

        // Randomness
        public Long seed = null; //20251013L;           // null => nondeterministic

        // Start control
        public FirstStartMode firstStartMode = FirstStartMode.RANDOM;
        public Integer firstStart = null;       // used when FIXED
        public Path rrStatePath = Path.of(".fastpi_round_robin_state.txt"); // state file for ROUND_ROBIN

        // Human-friendly start: avoid first 'minMix' edges all ±1-step
        public boolean avoidBoringStart = true;
        public int minMix = 4;

        // Episode content
        public double episodeLength = 7.0;      // seconds
        public int[] degradationChoices = new int[]{3, 4, 5};
        public List<String> palette = Arrays.asList("red", "green", "blue", "yellow");

        // Output path (set to your server path if desired)
        public Path outPath = Path.of("server/web/scenarios/eps/episodes.json");
    }

    /* =============================== DATA =============================== */

    public static class Episode {
        public double episodeLength;
        public int numAgents;
        public int degradationTime;
        public List<String> colours;
        public String degColour;

        // NEW
        public int prevAgents;        // 0 for the first episode
        // "EP-prev-cur-cT" => c = first letter of degColour (lowercase), T = degradationTime
        public String episodeCode;

        public Episode(double episodeLength, int numAgents, int degradationTime,
                       List<String> colours, String degColour,
                       int prevAgents, String episodeCode) {
            this.episodeLength = episodeLength;
            this.numAgents = numAgents;
            this.degradationTime = degradationTime;
            this.colours = colours;
            this.degColour = degColour;
            this.prevAgents = prevAgents;
            this.episodeCode = episodeCode;
        }
    }

    public static class EpisodeDoc {
        public List<Episode> episodes = new ArrayList<>();
    }

    /* ========================= MULTIGRAPH TOUR ========================== */

    /** Choose the first node for the tour. */
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

    /**
     * Build ONE Eulerian tour on a directed multigraph:
     * for each u!=v, we add 'repetitions' parallel edges (u->v).
     * Output: node sequence of length C*E + 1 (C=repetitions, E=|V|(|V|-1)), first==last.
     */
    static List<Integer> multiEulerianTour(DeckConfig cfg, Random rng, StringBuilder runLog) {
        List<Integer> V = cfg.nodes;
        int C = cfg.repetitions;
        int n = V.size();
        int E = n * (n - 1);

        // adjacency with multiplicity; shuffle each bucket
        Map<Integer, Deque<Integer>> g = new HashMap<>();
        for (int u : V) {
            List<Integer> outs = new ArrayList<>(E); // upper bound
            for (int v : V) {
                if (v == u) continue;
                for (int k = 0; k < C; k++) outs.add(v);
            }
            Collections.shuffle(outs, rng);
            g.put(u, new ArrayDeque<>(outs));
        }

        int start = chooseFirstStart(cfg, rng);
        Deque<Integer> stack = new ArrayDeque<>();
        List<Integer> circuit = new ArrayList<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int u = stack.peek();
            Deque<Integer> outs = g.get(u);
            if (outs != null && !outs.isEmpty()) {
                int v = outs.pop();
                stack.push(v);
            } else {
                circuit.add(stack.pop());
            }
        }
        Collections.reverse(circuit);

        // Ensure "not boring" start if requested; if boring, reshuffle and try again
        if (cfg.avoidBoringStart && cfg.minMix > 0) {
            int tries = 0;
            while (isBoringStart(circuit, V, cfg.minMix) && tries < 200) {
                // rebuild with fresh shuffles
                for (int u : V) {
                    List<Integer> outs = new ArrayList<>();
                    for (int v : V) if (v != u) for (int k = 0; k < C; k++) outs.add(v);
                    Collections.shuffle(outs, rng);
                    g.put(u, new ArrayDeque<>(outs));
                }
                stack.clear(); circuit.clear();
                stack.push(start);
                while (!stack.isEmpty()) {
                    int u = stack.peek();
                    Deque<Integer> outs = g.get(u);
                    if (outs != null && !outs.isEmpty()) {
                        int v = outs.pop();
                        stack.push(v);
                    } else {
                        circuit.add(stack.pop());
                    }
                }
                Collections.reverse(circuit);
                tries++;
            }
        }

        runLog.append(String.format("Multigraph tour length: %d nodes (should be C*E+1 = %d)\n",
                circuit.size(), C * E + 1));
        runLog.append("Start node: ").append(circuit.get(0))
                .append(" | End node: ").append(circuit.get(circuit.size()-1)).append("\n");
        return circuit;
    }

    static boolean isBoringStart(List<Integer> seq, List<Integer> nodes, int minMix) {
        int m = Math.min(minMix, seq.size() - 1);
        for (int i = 0; i < m; i++) {
            int a = seq.get(i), b = seq.get(i + 1);
            int d = Math.abs(nodes.indexOf(b) - nodes.indexOf(a));
            if (d != 1) return false; // found a non-±1 step
        }
        return true; // all were ±1
    }

    /* ============================ EPISODES ============================== */

    static List<String> balancedColours(int N, List<String> palette, Random rng) {
        int p = palette.size();
        if (N % p != 0) {
            throw new IllegalArgumentException("numAgents=" + N + " not multiple of palette size=" + p +
                    " (adjust nodes or palette).");
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

    /** One-letter code from colour name (lowercase first char); robust to custom palette strings. */
    static String colourTag(String colour) {
        if (colour == null || colour.isEmpty()) return "?";
        return String.valueOf(Character.toLowerCase(colour.charAt(0)));
    }

    /** Episode code now includes disappearing colour + time: EP-prev-cur-cT (e.g., EP-12-8-b6). */
    static String epCode(int prev, int cur, String degColour, int degTime) {
        return "EP-" + prev + "-" + cur + "-" + colourTag(degColour) + "-" + degTime;
    }

    static EpisodeDoc buildEpisodes(DeckConfig cfg, StringBuilder runLog) {
        Random rng = (cfg.seed == null) ? new Random() : new Random(cfg.seed);
        EpisodeDoc doc = new EpisodeDoc();

        // 1) Build the multi-cycle Eulerian tour (interleaved by construction)
        List<Integer> seq = multiEulerianTour(cfg, rng, runLog);

        // 2) Diagnostics: ordered-pair counts
        Map<String,Integer> pairCounts = new TreeMap<>();
        for (int i = 0; i < seq.size() - 1; i++) {
            int a = seq.get(i), b = seq.get(i + 1);
            pairCounts.merge(a + "->" + b, 1, Integer::sum);
        }
        runLog.append("Ordered-pair counts (should be = repetitions):\n");
        for (int a : cfg.nodes) for (int b : cfg.nodes) if (a != b) {
            String key = a + "->" + b;
            runLog.append(String.format("  %-6s : %d%n", key, pairCounts.getOrDefault(key, 0)));
        }

        // 3) Episodes: one per node in seq
        for (int i = 0; i < seq.size(); i++) {
            int cur = seq.get(i);
            int prev = (i == 0) ? 0 : seq.get(i - 1);

            List<String> colours = balancedColours(cur, cfg.palette, rng);
            String degColour = colours.get(rng.nextInt(colours.size()));
            int degTime = pickDegradationTime(cfg.degradationChoices, rng);

            doc.episodes.add(new Episode(
                    cfg.episodeLength, cur, degTime, colours, degColour,
                    prev, epCode(prev, cur, degColour, degTime)
            ));
        }

        // 4) Footer summary
        runLog.append(String.format("%nTotal episodes (nodes in tour): %d%n", doc.episodes.size()));
        runLog.append(String.format("Unique N values: %s%n", new TreeSet<>(cfg.nodes)));
        runLog.append(String.format("Output path: %s%n", cfg.outPath.toAbsolutePath()));
        return doc;
    }

    /* ============================ VALIDATION ============================ */

    static void validateAll(DeckConfig cfg, EpisodeDoc doc, StringBuilder report) {
        // A. basic fields + allowed sets
        Set<Integer> allowedN = new HashSet<>(cfg.nodes);
        Set<Integer> allowedTimes = new HashSet<>();
        for (int t : cfg.degradationChoices) allowedTimes.add(t);

        for (int i = 0; i < doc.episodes.size(); i++) {
            Episode e = doc.episodes.get(i);
            if (!allowedN.contains(e.numAgents))
                throw new AssertionError("Episode " + i + ": numAgents=" + e.numAgents + " not in " + allowedN);
            if (!allowedTimes.contains(e.degradationTime))
                throw new AssertionError("Episode " + i + ": degradationTime=" + e.degradationTime + " not in " + allowedTimes);
            if (!e.colours.contains(e.degColour))
                throw new AssertionError("Episode " + i + ": degColour not in colours");

            // colour balance
            Map<String,Integer> cts = new HashMap<>();
            for (String c : e.colours) cts.merge(c, 1, Integer::sum);
            int target = e.numAgents / cfg.palette.size();
            for (String c : cfg.palette) {
                int got = cts.getOrDefault(c, 0);
                if (got != target) {
                    throw new AssertionError("Episode " + i + ": colour count for '" + c + "' = " + got + " (target=" + target + ")");
                }
            }

            // prevAgents + episodeCode consistency
            int prev = (i == 0) ? 0 : doc.episodes.get(i - 1).numAgents;
            if (e.prevAgents != prev)
                throw new AssertionError("Episode " + i + ": prevAgents=" + e.prevAgents + " expected " + prev);
            String expectedCode = epCode(prev, e.numAgents, e.degColour, e.degradationTime);
            if (!expectedCode.equals(e.episodeCode))
                throw new AssertionError("Episode " + i + ": episodeCode=" + e.episodeCode + " expected " + expectedCode);
        }

        // B. no self-loops across adjacent episodes (except first prev=0)
        for (int i = 0; i < doc.episodes.size() - 1; i++) {
            int a = doc.episodes.get(i).numAgents;
            int b = doc.episodes.get(i + 1).numAgents;
            if (a == b) throw new AssertionError("Consecutive equal N at episodes " + i + " and " + (i + 1));
        }

        // C. ordered-pair uniform coverage == repetitions
        Map<String,Integer> counts = new TreeMap<>();
        for (int i = 0; i < doc.episodes.size() - 1; i++) {
            int a = doc.episodes.get(i).numAgents;
            int b = doc.episodes.get(i + 1).numAgents;
            counts.merge(a + "->" + b, 1, Integer::sum);
        }
        int wrong = 0;
        for (int a : cfg.nodes) for (int b : cfg.nodes) if (a != b) {
            String key = a + "->" + b;
            int got = counts.getOrDefault(key, 0);
            if (got != cfg.repetitions) {
                wrong++;
                report.append(String.format("WARN pair %s count=%d (expected=%d)%n", key, got, cfg.repetitions));
            }
        }
        if (wrong == 0) report.append("OK: Every ordered pair appears exactly " + cfg.repetitions + " times.\n");

        report.append("OK: Colours balanced, degColour present, times valid, prev/epCode consistent.\n");
    }

    /* ============================== OUTPUT ============================== */

    static void writeJson(EpisodeDoc doc, Path out) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(Map.of("episodes", doc.episodes));
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
            pw.println(json);
        }
    }

    /* =============================== MAIN =============================== */

    public static void main(String[] args) throws Exception {
        DeckConfig cfg = new DeckConfig();

        // EXAMPLES:
        // cfg.seed = null;                                 // different each run
        // cfg.firstStartMode = FirstStartMode.FIXED; cfg.firstStart = 12;
        // cfg.firstStartMode = FirstStartMode.ROUND_ROBIN;
        // cfg.outPath = Path.of("/server/web/scenarios/eps/episodes.json");

        System.out.println("Working directory: " + Path.of("").toAbsolutePath());
        System.out.println("Target JSON path : " + cfg.outPath.toAbsolutePath());

        StringBuilder log = new StringBuilder();
        log.append("=== FastPI Episode Generator (Multigraph) ===\n")
                .append("Nodes: ").append(cfg.nodes).append("\n")
                .append("Repetitions per ordered pair: ").append(cfg.repetitions).append("\n")
                .append("Seed: ").append(cfg.seed).append("\n")
                .append("First-start mode: ").append(cfg.firstStartMode)
                .append(cfg.firstStartMode == FirstStartMode.FIXED ? " (" + cfg.firstStart + ")" : "")
                .append("\nAvoid boring start: ").append(cfg.avoidBoringStart)
                .append(" (first ").append(cfg.minMix).append(" edges not all 1-step)\n\n");

        // Build
        EpisodeDoc doc = buildEpisodes(cfg, log);

        // --- FULL NODE CYCLE (4->12->8->... including the final return node) ---
        {
            List<String> nodesChain = new ArrayList<>();
            for (GenerateEpisodes.Episode e : doc.episodes) nodesChain.add(Integer.toString(e.numAgents));
            log.append("\nNode cycle:\n  ").append(String.join("->", nodesChain)).append("\n");
        }

        // --- EDGE LABEL CHAIN (A->B->C->... for each transition) ---
        {
            // Excel-like labels: A..Z, AA..AZ, BA.. etc.
            java.util.function.IntFunction<String> edgeLabel = idx -> {
                int n = idx;
                StringBuilder sb = new StringBuilder();
                do {
                    int rem = n % 26;
                    sb.append((char)('A' + rem));
                    n = n / 26 - 1;
                } while (n >= 0);
                return sb.reverse().toString();
            };

            List<String> edgeLabels = new ArrayList<>();
            for (int i = 0; i < doc.episodes.size() - 1; i++) edgeLabels.add(edgeLabel.apply(i));
            log.append("Edge order:\n  ").append(String.join("->", edgeLabels)).append("\n");

            // (Optional) also show label-to-transition mapping for readability
            for (int i = 0; i < doc.episodes.size() - 1; i++) {
                String L = edgeLabels.get(i);
                int a = doc.episodes.get(i).numAgents;
                int b = doc.episodes.get(i + 1).numAgents;
                log.append(String.format("  %s: %d->%d%n", L, a, b));
            }
        }

        // Validate
        StringBuilder report = new StringBuilder();
        validateAll(cfg, doc, report);

        // Write
        writeJson(doc, cfg.outPath);

        // Print self-doc
        log.append("\n=== Validation Report ===\n").append(report);
        log.append("\nWrote ").append(cfg.outPath.toAbsolutePath()).append("\n");
        System.out.println(log);
    }
}
