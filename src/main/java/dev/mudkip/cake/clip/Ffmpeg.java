package dev.mudkip.cake.clip;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

public final class Ffmpeg {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ffmpeg.class);
    private static final String VERSION = "b6.1.1";
    private static final String RELEASE_URL = "https://github.com/eugeneware/ffmpeg-static/releases/download/" + Ffmpeg.VERSION + "/";
    private static final Map<String, String> HASHES = Map.of(
            "linux-x64", "bfe8a8fc511530457b528c48d77b5737527b504a3797a9bc4866aeca69c2dffa",
            "linux-arm64", "754a678672298bc68156adff58aa7385a592c2b30b1d0ae8750c45c915c4bac0",
            "linux-arm", "64b115a12f0ab77c277e3c418aae8b40ef881e75e746a0e2d066a206b9bc5172",
            "darwin-x64", "929b375c1182d956c51f7ac25e0b2b0411fb01f6f407aa15c9758efeb4242106",
            "darwin-arm64", "8923876afa8db5585022d7860ec7e589af192f441c56793971276d450ed3bbfa",
            "win32-x64", "8883a3dffbd0a16cf4ef95206ea05283f78908dbfb118f73c83f4951dcc06d77"
    );
    private static final Map<String, ClipEncoder> ENCODER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ENCODER_LIST_CACHE = new ConcurrentHashMap<>();

    private Ffmpeg() {
    }

    public static String resolve(@Nullable String configuredPath, Consumer<Component> notifier) throws Exception {
        if (configuredPath != null && !configuredPath.isBlank() && Ffmpeg.canRun(configuredPath)) {
            return configuredPath;
        }

        if (Ffmpeg.canRun("ffmpeg")) {
            return "ffmpeg";
        }

        var cached = Ffmpeg.cachedPath();

        if (cached != null && Ffmpeg.canRun(cached.toString())) {
            return cached.toString();
        }

        return Ffmpeg.download(notifier).toString();
    }

    public static ClipEncoder detect(String command, ClipEncoderPreference preference, int quality, int fps) throws Exception {
        var key = command + "|" + preference.name() + "|" + quality + "|" + fps;
        var cached = Ffmpeg.ENCODER_CACHE.get(key);

        if (cached != null) {
            return cached;
        }

        var encoders = Ffmpeg.encoders(command);
        var candidates = Ffmpeg.candidates(preference, encoders, quality, fps);
        Exception lastFailure = null;

        for (var candidate : candidates) {
            try {
                Ffmpeg.testEncoder(command, candidate, quality, fps);
                Ffmpeg.ENCODER_CACHE.put(key, candidate);
                LOGGER.info("Using clip encoder {}", candidate.name());
                return candidate;
            } catch (Exception exception) {
                lastFailure = exception;
                LOGGER.debug("Clip encoder {} is unavailable", candidate.name(), exception);
            }
        }

        if (lastFailure != null) {
            throw new IOException("No usable H.264 encoder found", lastFailure);
        }

        throw new IOException("No usable H.264 encoder found");
    }

    public static ProcessBuilder builder(String command, List<String> arguments) {
        var process = new ProcessBuilder(command);
        process.command().addAll(arguments);
        process.redirectErrorStream(true);
        return process;
    }

    public static boolean run(ProcessBuilder process, int timeoutSeconds) {
        try {
            process.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process.redirectError(ProcessBuilder.Redirect.DISCARD);
            var started = process.start();
            started.getOutputStream().close();

            if (!started.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                started.destroyForcibly();
                return false;
            }

            return started.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    public static long runAndRead(ProcessBuilder process, int timeoutSeconds, Consumer<String> output) {
        try {
            var started = process.start();

            try (var reader = started.inputReader()) {
                reader.lines().forEach(output);
            }

            if (!started.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                started.destroyForcibly();
                return -1L;
            }

            return started.exitValue();
        } catch (Exception exception) {
            return -1L;
        }
    }

    private static boolean canRun(String command) {
        return Ffmpeg.run(new ProcessBuilder(command, "-version"), 10);
    }

    private static @Nullable Path cachedPath() {
        var key = Ffmpeg.platformKey();

        if (key == null) {
            return null;
        }

        var name = key.startsWith("win32") ? "ffmpeg.exe" : "ffmpeg";
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("cake")
                .resolve("ffmpeg")
                .resolve(Ffmpeg.VERSION)
                .resolve(name);
    }

    private static Path download(Consumer<Component> notifier) throws Exception {
        var key = Ffmpeg.platformKey();

        if (key == null) {
            throw new IOException("No video encoder build is available for " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        }

        var expected = Ffmpeg.HASHES.get(key);

        if (expected == null) {
            throw new IOException("No video encoder build is available for " + key);
        }

        var directory = FabricLoader.getInstance().getConfigDir().resolve("cake").resolve("ffmpeg").resolve(Ffmpeg.VERSION);
        var target = directory.resolve(key.startsWith("win32") ? "ffmpeg.exe" : "ffmpeg");
        Files.createDirectories(directory);
        notifier.accept(Component.translatable("cake.clip.downloading"));
        LOGGER.info("Downloading ffmpeg {}", key);

        var archive = directory.resolve("ffmpeg-" + key + ".gz");
        var temporary = directory.resolve("ffmpeg.tmp");
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        var request = HttpRequest.newBuilder(URI.create(Ffmpeg.RELEASE_URL + "ffmpeg-" + key + ".gz"))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }

        var digest = MessageDigest.getInstance("SHA-256");

        try (var input = response.body(); var output = Files.newOutputStream(archive)) {
            var buffer = new byte[64 * 1024];
            var read = 0;

            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }

        var actual = HexFormat.of().formatHex(digest.digest());

        if (!actual.equalsIgnoreCase(expected)) {
            Files.deleteIfExists(archive);
            throw new IOException("Downloaded video encoder failed its checksum");
        }

        try (var input = new GZIPInputStream(Files.newInputStream(archive));
             var output = Files.newOutputStream(temporary)) {
            input.transferTo(output);
        }

        Files.deleteIfExists(archive);
        Ffmpeg.makeExecutable(temporary);

        if (!Ffmpeg.canRun(temporary.toString())) {
            Files.deleteIfExists(temporary);
            throw new IOException("Downloaded video encoder could not start");
        }

        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOGGER.info("Installed ffmpeg to {}", target);
        return target;
    }

    private static void makeExecutable(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static Set<String> encoders(String command) {
        var cached = Ffmpeg.ENCODER_LIST_CACHE.get(command);

        if (cached != null) {
            return cached;
        }

        var names = new HashSet<String>();
        Ffmpeg.runAndRead(
                Ffmpeg.builder(command, List.of("-hide_banner", "-encoders")),
                20,
                line -> {
                    var trimmed = line.trim();

                    if (!trimmed.isEmpty() && trimmed.charAt(0) == 'V') {
                        var parts = trimmed.split("\\s+");

                        for (var part : parts) {
                            if (part.startsWith("h264_") || part.equals("libx264") || part.equals("libopenh264")) {
                                names.add(part);
                            }
                        }
                    }
                }
        );
        Ffmpeg.ENCODER_LIST_CACHE.put(command, names);
        return names;
    }

    private static List<ClipEncoder> candidates(
            ClipEncoderPreference preference,
            Set<String> encoders,
            int quality,
            int fps
    ) {
        var candidates = new ArrayList<ClipEncoder>();
        var os = Ffmpeg.os();

        if (preference != ClipEncoderPreference.AUTO && preference != ClipEncoderPreference.SOFTWARE) {
            Ffmpeg.addCandidate(candidates, preference, encoders, os, quality, fps);
        } else if (preference == ClipEncoderPreference.AUTO) {
            if (os.equals("windows")) {
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.NVENC, encoders, os, quality, fps);
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.QSV, encoders, os, quality, fps);
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.AMF, encoders, os, quality, fps);
            } else if (os.equals("macos")) {
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.VIDEOTOOLBOX, encoders, os, quality, fps);
            } else {
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.NVENC, encoders, os, quality, fps);
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.QSV, encoders, os, quality, fps);
                Ffmpeg.addCandidate(candidates, ClipEncoderPreference.AMF, encoders, os, quality, fps);
            }
        }

        candidates.add(Ffmpeg.softwareEncoder(quality, fps));

        if (preference == ClipEncoderPreference.AUTO && os.equals("linux")) {
            Ffmpeg.addCandidate(candidates, ClipEncoderPreference.VAAPI, encoders, os, quality, fps);
        }

        return candidates;
    }

    private static void addCandidate(
            List<ClipEncoder> candidates,
            ClipEncoderPreference preference,
            Set<String> encoders,
            String os,
            int quality,
            int fps
    ) {
        switch (preference) {
            case NVENC -> {
                if (encoders.contains("h264_nvenc")) {
                    candidates.add(new ClipEncoder(
                            "NVENC",
                            List.of(),
                            List.of(),
                            List.of(
                                    "-c:v", "h264_nvenc",
                                    "-preset", "p5",
                                    "-rc", "vbr",
                                    "-cq", Integer.toString(quality),
                                    "-b:v", "0",
                                    "-profile:v", "high",
                                    "-pix_fmt", "yuv420p",
                                    "-g", Integer.toString(fps)
                            )
                    ));
                }
            }
            case QSV -> {
                if (encoders.contains("h264_qsv")) {
                    candidates.add(new ClipEncoder(
                            "Quick Sync",
                            List.of(),
                            List.of(),
                            List.of(
                                    "-c:v", "h264_qsv",
                                    "-preset", "veryfast",
                                    "-global_quality", Integer.toString(quality),
                                    "-profile:v", "high",
                                    "-pix_fmt", "yuv420p",
                                    "-g", Integer.toString(fps)
                            )
                    ));
                }
            }
            case AMF -> {
                if (encoders.contains("h264_amf")) {
                    candidates.add(new ClipEncoder(
                            "AMF",
                            List.of(),
                            List.of(),
                            List.of(
                                    "-c:v", "h264_amf",
                                    "-rc", "cqp",
                                    "-qp_i", Integer.toString(quality),
                                    "-qp_p", Integer.toString(quality),
                                    "-quality", "balanced",
                                    "-pix_fmt", "yuv420p",
                                    "-g", Integer.toString(fps)
                            )
                    ));
                }
            }
            case VAAPI -> {
                if (encoders.contains("h264_vaapi") && os.equals("linux")) {
                    for (var node : List.of("/dev/dri/renderD128", "/dev/dri/renderD129", "/dev/dri/renderD130", "/dev/dri/renderD131")) {
                        if (Files.exists(Path.of(node))) {
                            candidates.add(new ClipEncoder(
                                    "VAAPI",
                                    List.of("-vaapi_device", node),
                                    List.of("format=nv12", "hwupload"),
                                    List.of(
                                            "-c:v", "h264_vaapi",
                                            "-rc_mode", "CQP",
                                            "-qp", Integer.toString(quality),
                                            "-profile:v", "high",
                                            "-g", Integer.toString(fps)
                                    )
                            ));
                        }
                    }
                }
            }
            case VIDEOTOOLBOX -> {
                if (encoders.contains("h264_videotoolbox") && os.equals("macos")) {
                    candidates.add(new ClipEncoder(
                            "VideoToolbox",
                            List.of(),
                            List.of(),
                            List.of(
                                    "-c:v", "h264_videotoolbox",
                                    "-q:v", Integer.toString(Math.clamp(100 - quality * 2, 1, 100)),
                                    "-profile:v", "high",
                                    "-pix_fmt", "yuv420p",
                                    "-g", Integer.toString(fps)
                            )
                    ));
                }
            }
            default -> {
            }
        }
    }

    private static ClipEncoder softwareEncoder(int quality, int fps) {
        return new ClipEncoder(
                "x264",
                List.of(),
                List.of(),
                List.of(
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-tune", "zerolatency",
                        "-profile:v", "high",
                        "-pix_fmt", "yuv420p",
                        "-crf", Integer.toString(quality),
                        "-g", Integer.toString(fps),
                        "-keyint_min", Integer.toString(fps),
                        "-sc_threshold", "0"
                )
        );
    }

    private static void testEncoder(String command, ClipEncoder encoder, int quality, int fps) throws Exception {
        var arguments = new ArrayList<String>();
        arguments.add("-hide_banner");
        arguments.add("-loglevel");
        arguments.add("error");
        arguments.addAll(encoder.inputArgs());
        arguments.add("-f");
        arguments.add("lavfi");
        arguments.add("-i");
        arguments.add("color=c=black:s=256x256:r=30");
        arguments.add("-frames:v");
        arguments.add("2");

        if (!encoder.filterArgs().isEmpty()) {
            arguments.add("-vf");
            arguments.add(String.join(",", encoder.filterArgs()));
        }

        arguments.addAll(encoder.outputArgs());
        arguments.add("-force_key_frames");
        arguments.add("expr:gte(t,n_forced*1)");
        arguments.add("-f");
        arguments.add("null");
        arguments.add("-");

        if (!Ffmpeg.run(Ffmpeg.builder(command, arguments), 20)) {
            throw new IOException("Encoder test failed for " + encoder.name());
        }
    }

    private static String os() {
        var name = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (name.contains("win")) {
            return "windows";
        }

        if (name.contains("mac")) {
            return "macos";
        }

        return "linux";
    }

    private static @Nullable String platformKey() {
        var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        var architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            return "win32-x64";
        }

        var arch = switch (architecture) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            case "arm", "armv7l" -> "arm";
            default -> null;
        };

        if (arch == null) {
            return null;
        }

        if (os.contains("mac")) {
            return arch.equals("arm64") ? "darwin-arm64" : "darwin-x64";
        }

        if (os.contains("linux")) {
            return arch.equals("x64") ? "linux-x64" : "linux-" + arch;
        }

        return null;
    }
}
