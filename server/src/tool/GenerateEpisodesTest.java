package tool;

import com.google.gson.Gson;
import tool.GenerateEpisodes.DeckConfig;
import tool.GenerateEpisodes.Episode;
import tool.GenerateEpisodes.EpisodeDoc;
import tool.GenerateEpisodes.FirstStartMode;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modular tests for FastPI episode generation.
 *
 * Generates a JSON once into a temp directory, then validates:
 *  - schema basics
 *  - allowed numAgents
 *  - no self-loops between adjacent episodes
 *  - ordered-pair coverage = repetitions for each a->b
 *  - balanced colours + degColour ∈ colours
 *  - degradationTime ∈ {3,4,5}
 *  - per-cycle length math for chained cycles
 *  - optional: first-start adherence (when FIXED)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenerateEpisodesTest {

    // Parsed structures reused across tests
    private static Path outPath;
    private static DeckConfig cfg;
    private static Root root;

    // Minimal POJOs for parsing the written JSON
    static class Root {
        List<EpisodeLite> episodes;
    }
    static class EpisodeLite {
        double episodeLength;
        int numAgents;
        int degradationTime;
        List<String> colours;
        String degColour;
    }

    @BeforeAll
    static void setupOnce() throws Exception {
        // Generate once into a temp path so tests are self-contained
        Path tmp = Files.createTempDirectory("eps-json");
        outPath = tmp.resolve("episodes.json");

        // --- Configure a representative run ---
        cfg = new DeckConfig();
        cfg.outPath = outPath;
        cfg.repetitions = 3;                 // a decent size but still quick
        cfg.seed = 42L;                      // deterministic
        cfg.firstStartMode = FirstStartMode.FIXED;
        cfg.firstStart = 12;                 // demonstrate non-8 start in this test run
        cfg.avoidBoringStart = true;
        cfg.minMix = 4;

        // Build + validate in-memory, then write JSON
        StringBuilder log = new StringBuilder();
        EpisodeDoc doc = GenerateEpisodes.buildEpisodes(cfg, log);
        StringBuilder report = new StringBuilder();
        GenerateEpisodes.validateAll(cfg, doc, report);
        GenerateEpisodes.writeJson(doc, cfg.outPath);

        // Parse JSON once for all tests
        String json = Files.readString(outPath);
        root = new Gson().fromJson(json, Root.class);
        assertNotNull(root, "Parsed root is null");
        assertNotNull(root.episodes, "Parsed episodes list is null");
    }

    // ---------------------- Tests ----------------------

    @Test @Order(1)
    void fileExistsAndBasicShape() throws Exception {
        assertTrue(Files.exists(outPath),
                "episodes.json not written (path: " + outPath.toAbsolutePath() + ")");
        assertFalse(root.episodes.isEmpty(), "No episodes generated");
    }

    @Test @Order(2)
    void numAgentsAreAllowed() {
        Set<Integer> allowed = new HashSet<>(cfg.nodes);
        for (int i = 0; i < root.episodes.size(); i++) {
            int n = root.episodes.get(i).numAgents;
            assertTrue(allowed.contains(n), "Episode " + i + ": numAgents=" + n + " not in " + allowed);
        }
    }

    @Test @Order(3)
    void noSelfLoopsBetweenEpisodes() {
        for (int i = 0; i < root.episodes.size() - 1; i++) {
            int a = root.episodes.get(i).numAgents;
            int b = root.episodes.get(i + 1).numAgents;
            assertNotEquals(a, b, "Consecutive equal Ns at episodes " + i + " and " + (i + 1));
        }
    }

    @Test @Order(4)
    void orderedPairCoverageIsUniformAndEqualsRepetitions() {
        Map<String,Integer> counts = new TreeMap<>();
        for (int i = 0; i < root.episodes.size() - 1; i++) {
            int a = root.episodes.get(i).numAgents;
            int b = root.episodes.get(i + 1).numAgents;
            counts.merge(a + "->" + b, 1, Integer::sum);
        }
        for (int a : cfg.nodes) for (int b : cfg.nodes) if (a != b) {
            String key = a + "->" + b;
            int got = counts.getOrDefault(key, 0);
            assertEquals(cfg.repetitions, got, "pair " + key + " count " + got + " != repetitions " + cfg.repetitions);
        }
    }

    @Test @Order(5)
    void coloursBalancedAndDegColourInColours() {
        List<String> palette = cfg.palette;
        for (int i = 0; i < root.episodes.size(); i++) {
            EpisodeLite e = root.episodes.get(i);
            assertTrue(e.colours.contains(e.degColour),
                    "Episode " + i + ": degColour not in colours");

            Map<String,Integer> cts = new HashMap<>();
            for (String c : e.colours) cts.merge(c, 1, Integer::sum);

            int target = e.numAgents / palette.size();
            for (String c : palette) {
                int got = cts.getOrDefault(c, 0);
                assertEquals(target, got,
                        "Episode " + i + ": colour count for '" + c + "' = " + got + " (target=" + target + ")");
            }
        }
    }

    @Test @Order(6)
    void degradationTimeIsOneOfThree() {
        Set<Integer> allowedTimes = new HashSet<>();
        for (int x : cfg.degradationChoices) allowedTimes.add(x);
        for (int i = 0; i < root.episodes.size(); i++) {
            int t = root.episodes.get(i).degradationTime;
            assertTrue(allowedTimes.contains(t),
                    "Episode " + i + ": degradationTime=" + t + " not in " + allowedTimes);
        }
    }

    @Test @Order(7)
    void perCycleLengthsAreConsistentForChainedCycles() {
        // Each Eulerian cycle over |V| nodes contributes E+1 nodes (E = |V|(|V|-1)).
        // When chaining C cycles, the concatenated sequence length is:
        //   C*(E+1) - (C-1)  (we drop 1 node at each cycle join)
        int V = cfg.nodes.size();
        int E = V * (V - 1);
        int perCycleNodes = E + 1;
        int expected = cfg.repetitions * perCycleNodes - (cfg.repetitions - 1);
        assertEquals(expected, root.episodes.size(),
                "Total episodes not matching chained-cycle expectation");
    }

    @Test @Order(8)
    void firstStartIsRespectedWhenFixed() {
        if (cfg.firstStartMode == FirstStartMode.FIXED && cfg.firstStart != null) {
            assertEquals(cfg.firstStart.intValue(), root.episodes.get(0).numAgents,
                    "First episode N does not match FIXED firstStart");
        }
    }
}
