package server;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FFmpegScreenRecorder {
    private final String ffmpegExe;
    private final Path outDir;

    private volatile Process proc;
    private volatile OutputStream procStdin;
    private long sessionWallStartMs;
    private Path sessionFile;
    private Thread shutdownHook;
    private volatile boolean running;

    // NEW: single-thread pool to run cuts off the main loop
    private final ExecutorService cutPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ffmpeg-cuts");
        t.setDaemon(true);
        return t;
    });

    public FFmpegScreenRecorder(String ffmpegExe, Path outDir) {
        this.ffmpegExe = Objects.requireNonNull(ffmpegExe);
        this.outDir = Objects.requireNonNull(outDir);
    }

    public synchronized boolean startSession() throws IOException {
        if (running) {
            System.out.println(ts() + " [FFMPEG] session already running");
            return true;
        }
        Files.createDirectories(outDir);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        sessionFile = outDir.resolve("session_" + stamp + ".mkv"); // MKV = safe during crashes

        File log = outDir.resolve("ffmpeg-session.log").toFile();

        // Important: enforce boring, steady timestamps from the start.
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegExe,
                "-hide_banner", "-y",
                "-f", "dshow",
                "-rtbufsize", "256M",
                "-thread_queue_size", "512",
                "-use_wallclock_as_timestamps", "1",     // input PTS from wall clock
                "-i", "video=OBS Virtual Camera",
                "-fflags", "+genpts",                    // generate clean PTS
                "-vsync", "cfr",                         // constant frame rate timeline
                "-r", "60",                              // CFR @ 60fps
                "-c:v", "libx264",                       // CPU; use h264_nvenc if you upgrade drivers
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                sessionFile.toString()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(log);                          // log file (no gobbler thread needed)
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);  // so we can send 'q'
        proc = pb.start();
        procStdin = proc.getOutputStream();
        running = true;

        // Wait until the file actually exists and has some data, then set the wall start
        // so our wall clock aligns with *real* captured frames.
        try {
            for (int i = 0; i < 60; i++) { // up to ~6s
                if (Files.exists(sessionFile) && Files.size(sessionFile) > 512 * 1024) break;
                Thread.sleep(100);
            }
        } catch (InterruptedException ignored) {}
        sessionWallStartMs = System.currentTimeMillis();

        // shutdown safety
        if (shutdownHook == null) {
            shutdownHook = new Thread(() -> {
                try { stopSessionGracefully(); } catch (Exception ignored) {}
                try { shutdownCuts(); } catch (Exception ignored) {}
            }, "ffmpeg-shutdown-hook");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        System.out.printf("%s [FFMPEG] session started -> %s%n", ts(), sessionFile.getFileName());
        return true;
    }

    /** Preferred way to stop: closes the file cleanly and releases Windows locks. */
    public synchronized void stopSessionGracefully() {
        if (!running || proc == null) return;

        try {
            // 1) Ask ffmpeg to stop nicely so it writes trailer (critical for MP4; still good for MKV)
            try {
                if (procStdin != null) {
                    procStdin.write('q');
                    procStdin.write('\n');
                    procStdin.flush();
                }
            } catch (IOException ignored) {}

            // 2) Wait a bit for clean exit
            if (!proc.waitFor(4, TimeUnit.SECONDS)) {
                // 3) Nudge
                proc.destroy();
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                    // 4) Last resort
                    proc.destroyForcibly();
                    proc.waitFor(2, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuiet(procStdin);
            procStdin = null;
            proc = null;
            running = false;
            System.out.printf("%s [FFMPEG] session stopped%n", ts());
        }
    }

    /** Optional: stop accepting new cuts and let queued cuts finish briefly. */
    public void shutdownCuts() {
        cutPool.shutdown();
        try { cutPool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /** (Kept for offline use) waits until file size stops growing. Not used during live recording anymore. */
    @SuppressWarnings("unused")
    private void waitForStableFile(Path file, long minBytes, long settleMillis, long timeoutMillis) throws IOException {
        long start = System.currentTimeMillis();
        long lastSize = -1;
        long lastChange = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (!Files.exists(file)) {
                sleep(100);
                continue;
            }
            long size = Files.size(file);
            if (size >= minBytes) {
                if (size != lastSize) {
                    lastSize = size;
                    lastChange = System.currentTimeMillis();
                } else {
                    if (System.currentTimeMillis() - lastChange >= settleMillis) return; // stable
                }
            }
            sleep(100);
        }
        // timeout reached; continue anyway
    }

    // NEW: queue the cut on a background thread (non-blocking for the caller)
    public void cutEpisodeAsync(String epCode, int epIndex,
                                long epWallStartMs, long epWallEndMs,
                                boolean reencode) {
        cutPool.submit(() -> {
            try {
                cutEpisode(epCode, epIndex, epWallStartMs, epWallEndMs, reencode);
            } catch (Exception e) {
                System.out.printf("%s [FFMPEG] async cut failed (%s_%03d): %s%n",
                        ts(), epCode, epIndex, e.toString());
            }
        });
    }

    /** Cut an episode clip from the long session by wall clock times. (Blocking, used by async wrapper) */
    public Path cutEpisode(String epCode, int epIndex,
                           long epWallStartMs, long epWallEndMs,
                           boolean reencode) throws IOException, InterruptedException {
        if (sessionFile == null) throw new IllegalStateException("Session not started");

        // --- Compute absolute start and end (seconds from session start) ---
        double startSec = Math.max(0.0, (epWallStartMs - sessionWallStartMs) / 1000.0);
        double endSec   = Math.max(startSec, (epWallEndMs   - sessionWallStartMs) / 1000.0);

        // --- Guard: don't cut before the long MKV has those bytes recorded ---
        long needWallMs = sessionWallStartMs + (long) Math.floor(endSec * 1000.0);
        long safetyLagMs = 2000; // encoder/OS buffering slack
        long now = System.currentTimeMillis();
        if (now < needWallMs + safetyLagMs) {
            try { Thread.sleep((needWallMs + safetyLagMs) - now); } catch (InterruptedException ignored) {}
        }

        // --- Tiny settle to avoid racing the tail of the file (kept short to reduce latency) ---
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(epWallEndMs));
        Path out = outDir.resolve(String.format("%s_%03d_%s.mp4", epCode, epIndex, stamp));
        File cutLog = outDir.resolve("ffmpeg-cut.log").toFile();

        // --- Build ffmpeg args ---
        List<String> args = new ArrayList<>();
        args.addAll(List.of(ffmpegExe, "-hide_banner", "-y"));

        // Decode-seek (accurate): put -ss AFTER -i, and use absolute -to
        args.addAll(List.of(
                "-i", sessionFile.toString(),
                "-ss", String.format(Locale.US, "%.3f", startSec),
                "-to", String.format(Locale.US, "%.3f", endSec)
        ));

        if (reencode) {
            args.addAll(List.of(
                    // Tip: switch to h264_nvenc/h264_qsv/h264_amf if available for faster cuts
                    "-c:v","libx264",
                    "-preset","veryfast",
                    "-crf","23",
                    "-pix_fmt","yuv420p",
                    "-vsync","cfr",
                    "-fflags","+genpts",
                    "-reset_timestamps","1",
                    "-movflags","+faststart"
            ));
        } else {
            // Best-effort stream copy (keyframe-aligned)
            args.addAll(List.of(
                    "-c","copy",
                    "-movflags","+faststart",
                    "-copyts",
                    "-avoid_negative_ts","make_zero"
            ));
        }
        args.add(out.toString());

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        pb.redirectOutput(cutLog);
        Process p = pb.start();
        int rc = p.waitFor();

        double requestedDur = Math.max(0.0, endSec - startSec);
        System.out.printf("%s [FFMPEG] cut %s (ss=%.3f,to=%.3f,reqDur=%.3f,reencode=%s,rc=%d)%n",
                ts(), out.getFileName(), startSec, endSec, requestedDur, reencode, rc);

        if (rc != 0) {
            System.out.printf("%s [FFMPEG] Cut failed; see %s%n", ts(), cutLog.getPath());
            return out;
        }

        // Probe the actual duration with ffprobe and warn if it’s far off.
        Double trueDur = probeDurationSeconds(out);
        if (trueDur != null) {
            System.out.printf("%s [FFMPEG] probe %s -> trueDuration=%.3fs (requested=%.3fs, delta=%.3fs)%n",
                    ts(), out.getFileName(), trueDur, requestedDur, (trueDur - requestedDur));
            if (Math.abs(trueDur - requestedDur) > 0.75) {
                System.out.printf("%s [FFMPEG] WARN duration mismatch >750ms for %s%n",
                        ts(), out.getFileName());
            }
        } else {
            System.out.printf("%s [FFMPEG] probe failed for %s%n", ts(), out.getFileName());
        }

        return out;
    }

    /** Try to find ffprobe next to ffmpeg, otherwise fall back to "ffprobe" on PATH. */
    private String ffprobeExe() {
        Path ff = Paths.get(ffmpegExe);
        String name = ff.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("ffmpeg")) {
            Path sibling = ff.getParent() != null ? ff.getParent().resolve(name.replace("ffmpeg","ffprobe")) : null;
            if (sibling != null && Files.isRegularFile(sibling)) return sibling.toString();
            // common Windows executable name
            sibling = ff.getParent() != null ? ff.getParent().resolve("ffprobe.exe") : null;
            if (sibling != null && Files.isRegularFile(sibling)) return sibling.toString();
            sibling = ff.getParent() != null ? ff.getParent().resolve("ffprobe") : null;
            if (sibling != null && Files.isRegularFile(sibling)) return sibling.toString();
        }
        return "ffprobe";
    }

    /** Returns duration in seconds (Double) or null on failure. */
    private Double probeDurationSeconds(Path file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobeExe(),
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                out = br.readLine();
            }
            p.waitFor(3, TimeUnit.SECONDS);
            if (out == null) return null;
            return Double.parseDouble(out.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public long getSessionWallStartMs() { return sessionWallStartMs; }
    public Path getSessionFile() { return sessionFile; }

    // --- helpers ---
    private static String ts() { return new Date().toString() + ";"; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    private static void closeQuiet(Closeable c) { try { if (c != null) c.close(); } catch (IOException ignored) {} }
}
