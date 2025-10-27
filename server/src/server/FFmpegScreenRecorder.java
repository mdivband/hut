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
    private Path outDir;

    private volatile Process proc;
    private volatile OutputStream procStdin;
    private long sessionWallStartMs;
    private Path sessionFile;
    private Thread shutdownHook;
    private volatile boolean running;

    // Single-thread pool to run cuts off the main loop
    private final ExecutorService cutPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ffmpeg-cuts");
        t.setDaemon(true);
        return t;
    });

    // Fine-tuning (seconds)
    private static final double START_LEAD_S = 0.050;  // include a touch earlier
    private static final double END_TRIM_S   = 0.000;  // no tail trim; aim for exact 7.0s

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

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegExe,
                "-hide_banner", "-y",
                "-f", "dshow",
                "-rtbufsize", "256M",
                "-thread_queue_size", "512",
                "-use_wallclock_as_timestamps", "1",
                "-i", "video=OBS Virtual Camera",
                "-fflags", "+genpts",
                "-vsync", "cfr",
                "-r", "60",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                sessionFile.toString()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(log);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        // Record the wall time right at launch
        long launchWall = System.currentTimeMillis();
        proc = pb.start();
        procStdin = proc.getOutputStream();
        running = true;

        // (Optional) Wait briefly until the file appears
        try {
            for (int i = 0; i < 30; i++) { // up to ~3s
                if (Files.exists(sessionFile) && Files.size(sessionFile) > 128 * 1024) break;
                Thread.sleep(100);
            }
        } catch (InterruptedException ignored) {}

        sessionWallStartMs = launchWall;

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
            try {
                if (procStdin != null) {
                    procStdin.write('q');
                    procStdin.write('\n');
                    procStdin.flush();
                }
            } catch (IOException ignored) {}

            if (!proc.waitFor(4, TimeUnit.SECONDS)) {
                proc.destroy();
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
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

    // =======================================
    // Naming helpers
    // =======================================
    private static String zeroPad(int value, int width) {
        return String.format(Locale.US, "%0" + width + "d", value);
    }

    // In FFmpegScreenRecorder
    public void stopClipAndWait(Process proc, long timeoutMs) {
        try {
            // Send 'q' on stdin or destroy gracefully
            try (OutputStream os = proc.getOutputStream()) {
                os.write('q');
                os.flush();
            } catch (IOException ignored) {}

            if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proc.destroy();
                if (!proc.waitFor(500, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
        }
    }


    /**
     * Build filename:
     *  - With total:  Video[<i>-<total>]-<EpCode>.mp4  (i is 1-based; both zero-padded to total's width)
     *  - Without:     Video[<i>]-<EpCode>.mp4          (i is 1-based; 3-digit pad)
     */
    private static String makeVideoName(String epCode, int epIndexZeroBased, int totalVideos) {
        int oneBased = Math.max(1, epIndexZeroBased + 1);
        if (totalVideos > 0) {
            int width = Integer.toString(totalVideos).length();
            return "Video[" + zeroPad(oneBased, width) + "-" + zeroPad(totalVideos, width) + "]-" + epCode + ".mp4";
        } else {
            return String.format(Locale.US, "Video[%03d]-%s.mp4", oneBased, epCode);
        }
    }

    // =======================================
    // Cutting API
    // =======================================

    // Queue the cut on a background thread — with total count
    public void cutEpisodeAsync(String epCode, int epIndex, int totalVideos,
                                long epWallStartMs, long epWallEndMs,
                                boolean reencode) {
        cutPool.submit(() -> {
            try {
                cutEpisode(epCode, epIndex, totalVideos, epWallStartMs, epWallEndMs, reencode);
            } catch (Exception e) {
                System.out.printf("%s [FFMPEG] async cut failed (%s idx=%d/%d): %s%n",
                        ts(), epCode, epIndex, totalVideos, e.toString());
            }
        });
    }

    // Back-compat overload (no total known)
    public void cutEpisodeAsync(String epCode, int epIndex,
                                long epWallStartMs, long epWallEndMs,
                                boolean reencode) {
        cutEpisodeAsync(epCode, epIndex, -1, epWallStartMs, epWallEndMs, reencode);
    }

    /** Blocking cut (called by async wrapper) — with total count. */
    public Path cutEpisode(String epCode, int epIndex, int totalVideos,
                           long epWallStartMs, long epWallEndMs,
                           boolean reencode) throws IOException, InterruptedException {
        if (sessionFile == null) throw new IllegalStateException("Session not started");

        // Compute seconds from session start, and apply small lead/trim
        double startSec = Math.max(0.0, (epWallStartMs - sessionWallStartMs) / 1000.0);
        double endSec   = Math.max(startSec, (epWallEndMs   - sessionWallStartMs) / 1000.0);

        startSec = Math.max(0.0, startSec - START_LEAD_S);
        endSec   = Math.max(startSec, endSec - END_TRIM_S);

        // Guard: wait until we’re safely past the desired end so the container has all frames
        long needWallMs = sessionWallStartMs + (long) Math.floor(endSec * 1000.0);
        long safetyLagMs = 5000; // allow encoder/muxer to flush frames
        long now = System.currentTimeMillis();
        if (now < needWallMs + safetyLagMs) {
            try { Thread.sleep((needWallMs + safetyLagMs) - now); } catch (InterruptedException ignored) {}
        }

        // Tiny settle only
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        // Build output filename as requested
        String fileName = makeVideoName(epCode, epIndex, totalVideos);
        Path out = outDir.resolve(fileName);
        File cutLog = outDir.resolve("ffmpeg-cut.log").toFile();

        // Build args
        double durSec = Math.max(0.0, endSec - startSec);

        List<String> args = new ArrayList<>();
        args.addAll(List.of(ffmpegExe, "-hide_banner", "-y"));

        // Accurate seeking with re-encode path: put -ss after -i
        args.addAll(List.of("-i", sessionFile.toString()));
        args.addAll(List.of(
                "-ss", String.format(Locale.US, "%.3f", startSec),
                "-t",  String.format(Locale.US, "%.3f", durSec)
        ));

        if (reencode) {
            args.addAll(List.of(
                    "-c:v","libx264",         // consider h264_nvenc/h264_qsv/h264_amf if available
                    "-preset","veryfast",
                    "-crf","23",
                    "-pix_fmt","yuv420p",
                    "-vsync","cfr",
                    "-fflags","+genpts",
                    "-reset_timestamps","1",
                    "-movflags","+faststart",
                    "-muxpreload","0",
                    "-muxdelay","0"
            ));
        } else {
            // NOTE: copy mode is fragile with dshow timestamps; prefer reencode=true
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

        double requestedDur = durSec;
        System.out.printf("%s [FFMPEG] cut %s (ss=%.3f,t=%.3f,reqDur=%.3f,reencode=%s,rc=%d)%n",
                ts(), out.getFileName(), startSec, durSec, requestedDur, reencode, rc);

        if (rc != 0) {
            System.out.printf("%s [FFMPEG] Cut failed; see %s%n", ts(), cutLog.getPath());
            return out;
        }

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

    // Back-compat overload (no total known)
    public Path cutEpisode(String epCode, int epIndex,
                           long epWallStartMs, long epWallEndMs,
                           boolean reencode) throws IOException, InterruptedException {
        return cutEpisode(epCode, epIndex, -1, epWallStartMs, epWallEndMs, reencode);
    }

    private String ffprobeExe() {
        Path ff = Paths.get(ffmpegExe);
        String name = ff.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("ffmpeg")) {
            Path sibling = ff.getParent() != null ? ff.getParent().resolve(name.replace("ffmpeg","ffprobe")) : null;
            if (sibling != null && Files.isRegularFile(sibling)) return sibling.toString();
            sibling = ff.getParent() != null ? ff.getParent().resolve("ffprobe.exe") : null;
            if (sibling != null && Files.isRegularFile(sibling)) return sibling.toString();
            sibling = ff.getParent() != null ? ff.getParent().resolve("ffprobe") : null;
            if (sibling != null && Files.isRegularFile(sibling)) return sibling.toString();
        }
        return "ffprobe";
    }

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

    private static String ts() { return new Date().toString() + ";"; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    private static void closeQuiet(Closeable c) { try { if (c != null) c.close(); } catch (IOException ignored) {} }

    // In FFmpegScreenRecorder
    public synchronized void setOutDir(Path newOutDir) {
        this.outDir = Objects.requireNonNull(newOutDir);
    }

    private static String safePathSegment(String s) {
        if (s == null || s.isBlank()) return "unnamed";
        // keep letters, numbers, dot, underscore, dash; replace the rest with "_"
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Convenience: switch to /recordings/<gameId> (or whatever base you pass). */
    public synchronized void useSubfolderFor(String baseFolder, String gameId) {
        String safe = safePathSegment(gameId);
        this.outDir = Paths.get(baseFolder).resolve(safe);
    }


}
