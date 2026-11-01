package dev.mudkip.cake.clip;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.mudkip.cake.module.modules.ReplayBufferModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClipRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipRecorder.class);
    private static final String SEGMENT_PATTERN = "segment-%06d.mp4";
    private static final String SEGMENT_PREFIX = "segment-";
    private static final String SEGMENT_LIST = "segments.csv";
    private static final int SEGMENT_SECONDS = 1;
    private static final long SAVE_WAIT_NANOS = 2_000_000_000L;
    private static volatile @Nullable Session activeSession;
    private final Minecraft client;
    private final ReplayBufferModule module;
    private final ClipAudioTap audio;
    private volatile boolean enabled;
    private volatile boolean preparing;
    private volatile boolean failed;
    private volatile boolean restartPending;
    private @Nullable Level level;
    private @Nullable Session session;
    private @Nullable ClipFps lastFps;
    private @Nullable ClipResolution lastResolution;
    private @Nullable ClipCodec lastCodec;
    private @Nullable ClipEncoderPreference lastEncoder;
    private @Nullable String lastFfmpegPath;
    private int lastQuality;
    private boolean audioFailureReported;
    private int stallAttempts;
    private long settingsRestartAtNanos;
    private int prepareGeneration;

    public ClipRecorder(Minecraft client, ReplayBufferModule module, ClipAudioTap audio) {
        this.client = client;
        this.module = module;
        this.audio = audio;
        this.cleanStaleDirectories();
    }

    public static void onRenderEnd() {
        var active = ClipRecorder.activeSession;

        if (active != null) {
            active.captureFrame();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.failed = false;
        this.restartPending = false;
        this.settingsRestartAtNanos = 0L;
        this.audioFailureReported = false;
        this.stallAttempts = 0;
        this.audio.setEnabled(enabled && this.module.isAudioEnabled());

        if (!enabled) {
            this.prepareGeneration++;
            this.preparing = false;
            this.stopSession();
        }
    }

    public void tick() {
        if (!this.enabled) {
            this.audio.setEnabled(false);
            this.stopSession();
            this.level = null;
            return;
        }

        this.audio.setEnabled(this.module.isAudioEnabled());

        if (this.audio.isEnabled() && this.audio.hasFailed() && !this.audioFailureReported) {
            this.audioFailureReported = true;
            this.notify(Component.translatable("cake.clip.audio_failed"));
        }

        var currentLevel = this.client.level;

        if (currentLevel == null) {
            this.stopSession();
            this.level = null;
            return;
        }

        if (currentLevel != this.level) {
            this.stopSession();
            this.level = currentLevel;
            this.failed = false;
        }

        if (this.settingsRestartAtNanos != 0L && System.nanoTime() >= this.settingsRestartAtNanos) {
            this.settingsRestartAtNanos = 0L;
            this.restartPending = true;
        }

        if (this.restartPending) {
            this.restartPending = false;
            this.settingsRestartAtNanos = 0L;
            this.stopSession();
            this.failed = false;
        }

        var currentSession = this.session;

        if (currentSession != null && !currentSession.isAlive()) {
            LOGGER.warn("Clip recorder stopped unexpectedly");
            currentSession.stop();
            this.session = null;
            ClipRecorder.activeSession = null;
            this.failed = true;
            this.notify(Component.translatable("cake.clip.recorder_stopped"));
            return;
        }

        if (currentSession != null
                && this.stallAttempts > 0
                && System.nanoTime() - currentSession.getStartedNanos() > 60_000_000_000L) {
            this.stallAttempts = 0;
        }

        if (currentSession != null && currentSession.isCaptureStuck()) {
            if (currentSession.recoverCapture()) {
                return;
            }
        }

        if (currentSession != null && currentSession.isStalled()) {
            this.handleStall(currentSession);
            return;
        }

        if (currentSession != null && currentSession.isResized()) {
            this.restartPending = true;
        }

        if (currentSession == null && !this.preparing && !this.failed) {
            this.prepare();
        }
    }

    public void saveClip() {
        var current = this.session;

        if (current == null || !current.isAlive()) {
            this.notify(Component.translatable("cake.clip.not_running"));
            return;
        }

        if (current.isStalled()) {
            this.handleStall(current);
            return;
        }

        current.requestSave(this.module.isAudioEnabled());
    }

    private void handleStall(Session current) {
        var now = System.nanoTime();
        var capture = current.capture;
        LOGGER.warn(
                "Clip recorder stalled with encoder {} (frame write {}s ago, segment {}s ago, capture {}s ago, {} in flight)",
                current.encoder.name(),
                (now - current.lastFrameWrittenNanos) / 1_000_000_000.0D,
                (now - current.lastNewSegmentNanos) / 1_000_000_000.0D,
                capture == null ? -1.0D : (now - capture.getLastCopiedNanos()) / 1_000_000_000.0D,
                capture == null ? -1 : capture.getInFlightCount()
        );
        current.logDiagnostics();
        current.abort();
        this.session = null;
        ClipRecorder.activeSession = null;

        this.stallAttempts++;

        if (this.stallAttempts >= 5) {
            this.failed = true;
            this.notify(Component.translatable("cake.clip.recorder_stopped"));
            return;
        }

        this.restartPending = true;
    }

    public void settingsChanged() {
        this.audio.getRing().setBufferSeconds(this.module.getDuration());

        if (!this.enabled) {
            return;
        }

        this.stallAttempts = 0;
        var audioEnabled = this.module.isAudioEnabled();

        if (this.audio.isEnabled() != audioEnabled) {
            this.audio.setEnabled(audioEnabled);
            this.audioFailureReported = false;
        }

        if (this.preparing) {
            this.prepareGeneration++;
            this.preparing = false;
            this.restartPending = false;
        }

        if (this.immediateVideoSettingsChanged()) {
            this.restartPending = true;
        }

        if (this.lastQuality != this.module.getQuality()
                || !Objects.equals(this.lastFfmpegPath, this.module.getFfmpegPath())) {
            this.settingsRestartAtNanos = System.nanoTime() + 1_000_000_000L;
        }
    }

    private boolean immediateVideoSettingsChanged() {
        return this.lastFps != this.module.getFps()
                || this.lastResolution != this.module.getResolution()
                || this.lastCodec != this.module.getCodec()
                || this.lastEncoder != this.module.getEncoderPreference();
    }

    private void rememberSettings() {
        this.lastFps = this.module.getFps();
        this.lastResolution = this.module.getResolution();
        this.lastCodec = this.module.getCodec();
        this.lastEncoder = this.module.getEncoderPreference();
        this.lastQuality = this.module.getQuality();
        this.lastFfmpegPath = this.module.getFfmpegPath();
    }

    public void close() {
        this.enabled = false;
        this.prepareGeneration++;
        this.preparing = false;
        this.audio.setEnabled(false);
        this.stopSession();
    }

    private void prepare() {
        this.preparing = true;
        var generation = ++this.prepareGeneration;
        var configured = this.module.getFfmpegPath();
        var preference = this.module.getEncoderPreference();
        var codec = this.module.getCodec();
        var quality = this.module.getQuality();
        var fps = this.module.getFps().getValue();

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        var command = Ffmpeg.resolve(configured, this::notify);
                        var encoder = Ffmpeg.detect(command, preference, codec, quality, fps);
                        return new Prepared(command, encoder);
                    } catch (Exception exception) {
                        throw new CompletionException(exception);
                    }
                }, Util.ioPool())
                .whenComplete((prepared, error) -> this.client.execute(() -> this.finishPrepare(generation, prepared, error)));
    }

    private void finishPrepare(int generation, @Nullable Prepared prepared, @Nullable Throwable error) {
        if (generation != this.prepareGeneration) {
            return;
        }

        this.preparing = false;

        if (error != null) {
            this.failed = true;
            var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            LOGGER.warn("Could not prepare the clip recorder", cause);
            this.notify(Component.translatable("cake.clip.encoder_failed", Component.literal(String.valueOf(cause.getMessage()))));
            return;
        }

        if (prepared == null || !this.enabled || this.client.level == null) {
            return;
        }

        try {
            this.startSession(prepared);
        } catch (Throwable throwable) {
            this.failed = true;
            LOGGER.warn("Could not start the clip recorder", throwable);
            this.notify(Component.translatable("cake.clip.failure", Component.literal(String.valueOf(throwable.getMessage()))));
        }
    }

    private void startSession(Prepared prepared) throws IOException {
        var clips = FabricLoader.getInstance().getGameDir().resolve("clips");
        Files.createDirectories(clips);

        var directory = ClipRecorder.createSessionDirectory(clips);
        var target = this.client.gameRenderer.mainRenderTarget();

        if (target.width <= 0 || target.height <= 0) {
            throw new IOException("The game window is not ready");
        }

        var created = new Session(
                this.client,
                this.module,
                this.audio,
                clips,
                directory,
                prepared.command(),
                prepared.encoder(),
                target
        );

        try {
            created.start();
        } catch (Throwable throwable) {
            created.stop();

            if (throwable instanceof IOException exception) {
                throw exception;
            }

            throw new IOException(String.valueOf(throwable.getMessage()), throwable);
        }

        this.session = created;
        this.rememberSettings();
        this.audio.setEnabled(this.module.isAudioEnabled());
        ClipRecorder.activeSession = created;
        LOGGER.info(
                "Clip recorder started with {} ({})",
                prepared.encoder().name(),
                prepared.encoder().codec()
        );
    }

    private void stopSession() {
        var current = this.session;
        this.session = null;
        ClipRecorder.activeSession = null;

        if (current != null) {
            current.stop();
        }
    }

    private void notify(Component message) {
        this.client.execute(() -> this.client.showDebugChat(message));
    }

    private void cleanStaleDirectories() {
        try {
            var clips = FabricLoader.getInstance().getGameDir().resolve("clips");
            Files.createDirectories(clips);
            ClipRecorder.cleanStaleDirectories(clips);
        } catch (IOException exception) {
            LOGGER.debug("Could not clean old clip directories", exception);
        }

        try {
            ClipRecorder.cleanStaleDirectories(ClipRecorder.tempRoot());
        } catch (IOException exception) {
            LOGGER.debug("Could not clean old clip temp directories", exception);
        }
    }

    private static Path tempRoot() throws IOException {
        var root = Path.of(System.getProperty("java.io.tmpdir"), "cake-client");
        Files.createDirectories(root);
        return root;
    }

    private static Path createSessionDirectory(Path clips) throws IOException {
        try {
            return Files.createTempDirectory(ClipRecorder.tempRoot(), ".cake-clip-");
        } catch (IOException exception) {
            LOGGER.warn("Could not use the system temp directory for clip segments", exception);
            return Files.createTempDirectory(clips, ".cake-clip-");
        }
    }

    private static void cleanStaleDirectories(Path clips) {
        ClipRecorder.cleanStaleDirectories(clips, ".cake-clip-*");
        ClipRecorder.cleanStaleDirectories(clips, ".cake-save-*");
    }

    private static void cleanStaleDirectories(Path clips, String pattern) {
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(clips, pattern)) {
            for (var directory : directories) {
                if (Files.isDirectory(directory)) {
                    ClipRecorder.deleteDirectory(directory);
                }
            }
        } catch (IOException exception) {
            LOGGER.debug("Could not clean old clip directories", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (var files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException exception) {
            LOGGER.debug("Could not delete clip directory {}", directory, exception);
        }
    }

    private record Prepared(String command, ClipEncoder encoder) {
    }

    private static final class Session {
        private final Minecraft client;
        private final ReplayBufferModule module;
        private final ClipAudioTap audio;
        private final Path clips;
        private final Path directory;
        private final String command;
        private final ClipEncoder encoder;
        private final RenderTarget target;
        private final List<Segment> segments;
        private final Object segmentLock;
        private final LinkedBlockingQueue<SaveJob> saveJobs;
        private final AtomicBoolean saving;
        private @Nullable Process process;
        private @Nullable FrameCapture capture;
        private @Nullable Thread writerThread;
        private @Nullable Thread watcherThread;
        private @Nullable Thread saveThread;
        private @Nullable WritableByteChannel frameChannel;
        private @Nullable Path processLog;
        private volatile boolean running;
        private volatile boolean saveRunning;
        private int nextSegmentIndex;
        private final long startedNanos;
        private volatile long lastFrameWrittenNanos;
        private volatile long lastNewSegmentNanos;
        private int captureRecoveries;

        private Session(
                Minecraft client,
                ReplayBufferModule module,
                ClipAudioTap audio,
                Path clips,
                Path directory,
                String command,
                ClipEncoder encoder,
                RenderTarget target
        ) {
            this.client = client;
            this.module = module;
            this.audio = audio;
            this.clips = clips;
            this.directory = directory;
            this.command = command;
            this.encoder = encoder;
            this.target = target;
            this.segments = new ArrayList<>();
            this.segmentLock = new Object();
            this.saveJobs = new LinkedBlockingQueue<>();
            this.saving = new AtomicBoolean();
            this.running = true;
            this.saveRunning = true;
            this.startedNanos = System.nanoTime();
            this.lastFrameWrittenNanos = this.startedNanos;
            this.lastNewSegmentNanos = this.startedNanos;
        }

        private void start() throws IOException {
            this.capture = new FrameCapture(this.target, this.module.getResolution(), this.module.getFps());
            var arguments = this.createArguments();
            this.processLog = this.directory.resolve("ffmpeg.log");
            var builder = new ProcessBuilder(arguments);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.to(this.processLog.toFile()));

            try {
                this.process = builder.start();
            } catch (IOException exception) {
                this.capture.close();
                throw exception;
            }

            LOGGER.info(
                    "Clip capture {}x{} -> {}x{} at {} fps, ffmpeg pid {}",
                    this.capture.getWidth(),
                    this.capture.getHeight(),
                    this.capture.getOutputWidth(),
                    this.capture.getOutputHeight(),
                    this.module.getFps().getValue(),
                    this.process.pid()
            );
            this.frameChannel = Channels.newChannel(this.process.getOutputStream());
            this.writerThread = new Thread(this::writeFrames, "Cake clip writer");
            this.writerThread.setDaemon(true);
            this.writerThread.start();
            this.watcherThread = new Thread(this::watchSegments, "Cake clip segments");
            this.watcherThread.setDaemon(true);
            this.watcherThread.start();
            this.saveThread = new Thread(this::runSaves, "Cake clip saves");
            this.saveThread.setDaemon(true);
            this.saveThread.start();
        }

        private boolean isAlive() {
            var current = this.process;
            return this.running && current != null && current.isAlive();
        }

        private long getStartedNanos() {
            return this.startedNanos;
        }

        private boolean isResized() {
            var current = this.capture;
            return current != null && (this.target.width != current.getWidth() || this.target.height != current.getHeight());
        }

        private boolean isCaptureStuck() {
            var current = this.capture;
            return current != null && current.isStuck();
        }

        private boolean recoverCapture() {
            var current = this.capture;

            if (current == null || ++this.captureRecoveries > 5) {
                return false;
            }

            current.recover();
            return true;
        }

        private void logDiagnostics() {
            var writer = this.writerThread;

            if (writer != null) {
                LOGGER.warn("Clip writer thread state: {}", writer.getState());

                for (var frame : writer.getStackTrace()) {
                    LOGGER.warn("Clip writer at {}", frame);
                }
            }

            var current = this.process;

            if (current != null) {
                LOGGER.warn("Clip ffmpeg pid {} alive {}", current.pid(), current.isAlive());

                if (current.isAlive()) {
                    ClipRecorder.logProcessState(current.pid());
                }
            }

            var currentCapture = this.capture;

            if (currentCapture != null) {
                LOGGER.warn(
                        "Clip capture queues: frames {}, free frames {}, free buffers {}, in flight {}",
                        currentCapture.getQueuedFrames(),
                        currentCapture.getFreeFrameCount(),
                        currentCapture.getFreeBuffers(),
                        currentCapture.getInFlightCount()
                );
            }

            var log = this.processLog;

            if (log != null) {
                try {
                    var lines = Files.readAllLines(log);
                    var from = Math.max(0, lines.size() - 20);

                    for (var index = from; index < lines.size(); index++) {
                        LOGGER.warn("Clip ffmpeg: {}", lines.get(index));
                    }
                } catch (IOException exception) {
                    LOGGER.debug("Could not read the clip ffmpeg log", exception);
                }
            }
        }

        private boolean isStalled() {
            if (!this.running || this.saving.get()) {
                return false;
            }

            var current = this.capture;

            if (current == null) {
                return false;
            }

            var now = System.nanoTime();

            if (now - this.startedNanos < 10_000_000_000L) {
                return false;
            }

            if (now - current.getLastRenderNanos() > 2_000_000_000L) {
                return false;
            }

            return now - this.lastFrameWrittenNanos > 5_000_000_000L
                    && now - this.lastNewSegmentNanos > 15_000_000_000L;
        }

        private void captureFrame() {
            if (!this.running) {
                return;
            }

            var current = this.capture;

            if (current != null) {
                current.onRenderEnd();
            }
        }

        private void requestSave(boolean withAudio) {
            synchronized (this.segmentLock) {
                if (!this.saving.compareAndSet(false, true)) {
                    this.notify(Component.translatable("cake.clip.already_saving"));
                    return;
                }
            }

            var future = new CompletableFuture<SaveResult>();
            this.saveJobs.add(new SaveJob(future, withAudio));
            future.whenComplete((result, error) -> {
                this.saving.set(false);
                this.client.execute(() -> {
                    if (result == null) {
                        this.notify(Component.translatable("cake.clip.failure", Component.literal(String.valueOf(error))));
                    } else if (result.errorKey() != null) {
                        this.notify(Component.translatable(result.errorKey()));
                    } else if (result.error() != null) {
                        this.notify(Component.translatable("cake.clip.failure", Component.literal(result.error())));
                    } else {
                        this.notify(ClipRecorder.savedMessage(result.path(), result.bytes(), result.seconds()));
                    }
                });
            });
        }

        private void abort() {
            this.running = false;
            this.saveRunning = false;
            var current = this.process;

            if (current != null) {
                current.destroyForcibly();
            }

            if (this.writerThread != null) {
                this.writerThread.interrupt();
            }

            if (this.watcherThread != null) {
                this.watcherThread.interrupt();
            }

            if (this.saveThread != null) {
                this.saveThread.interrupt();
            }

            if (this.frameChannel != null) {
                try {
                    this.frameChannel.close();
                } catch (IOException ignored) {
                }
            }

            if (this.capture != null) {
                this.capture.close();
            }

            ClipRecorder.deleteDirectory(this.directory);
        }

        private void stop() {
            this.running = false;
            this.saveRunning = false;

            if (this.writerThread != null) {
                try {
                    this.writerThread.join(2000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }

            if (this.frameChannel != null) {
                try {
                    this.frameChannel.close();
                } catch (IOException ignored) {
                }
            }

            var current = this.process;

            if (current != null) {
                try {
                    if (!current.waitFor(2L, TimeUnit.SECONDS)) {
                        current.destroyForcibly();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    current.destroyForcibly();
                }
            }

            if (this.capture != null) {
                this.capture.close();
            }

            if (this.watcherThread != null) {
                this.watcherThread.interrupt();

                try {
                    this.watcherThread.join(1000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }

            if (this.saveThread != null) {
                this.saveThread.interrupt();

                try {
                    this.saveThread.join(5000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }

            if (this.saveThread == null || !this.saveThread.isAlive()) {
                ClipRecorder.deleteDirectory(this.directory);
            }
        }

        private List<String> createArguments() {
            var captureWidth = this.capture.getWidth();
            var captureHeight = this.capture.getHeight();
            var outputWidth = this.capture.getOutputWidth();
            var outputHeight = this.capture.getOutputHeight();
            var arguments = new ArrayList<String>();
            arguments.add(this.command);
            arguments.add("-hide_banner");
            arguments.add("-loglevel");
            arguments.add("warning");
            arguments.add("-nostdin");
            arguments.add("-y");
            arguments.addAll(this.encoder.inputArgs());
            arguments.add("-f");
            arguments.add("rawvideo");
            arguments.add("-pix_fmt");
            arguments.add("rgba");
            arguments.add("-s");
            arguments.add(captureWidth + "x" + captureHeight);
            arguments.add("-framerate");
            arguments.add(Integer.toString(this.module.getFps().getValue()));
            arguments.add("-i");
            arguments.add("pipe:0");
            var filters = new ArrayList<String>();
            filters.add("vflip");
            var scaled = outputWidth != captureWidth || outputHeight != captureHeight;

            if (this.encoder.name().equals("VAAPI")) {
                filters.addAll(this.encoder.filterArgs());

                if (scaled) {
                    filters.add("scale_vaapi=" + outputWidth + ":" + outputHeight);
                }
            } else {
                if (scaled) {
                    filters.add("scale=" + outputWidth + ":" + outputHeight + ":flags=bilinear");
                }

                filters.addAll(this.encoder.filterArgs());
            }

            arguments.add("-vf");
            arguments.add(String.join(",", filters));
            arguments.addAll(this.encoder.outputArgs());
            arguments.add("-fps_mode");
            arguments.add("vfr");
            arguments.add("-f");
            arguments.add("segment");
            arguments.add("-segment_time");
            arguments.add(Integer.toString(ClipRecorder.SEGMENT_SECONDS));
            arguments.add("-reset_timestamps");
            arguments.add("1");
            arguments.add("-segment_format");
            arguments.add("mp4");
            arguments.add("-segment_list");
            arguments.add(this.directory.resolve(ClipRecorder.SEGMENT_LIST).toString());
            arguments.add("-segment_list_type");
            arguments.add("csv");

            if (this.encoder.codec() == ClipCodec.HEVC) {
                arguments.add("-tag:v");
                arguments.add("hvc1");
            }

            arguments.add(this.directory.resolve(ClipRecorder.SEGMENT_PATTERN).toString());
            return arguments;
        }

        private void writeFrames() {
            var currentCapture = this.capture;
            var channel = this.frameChannel;

            if (currentCapture == null || channel == null) {
                return;
            }

            var frames = currentCapture.getFrames();

            while (this.running || !frames.isEmpty()) {
                FrameCapture.CapturedFrame frame;

                try {
                    frame = frames.poll(50L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (frame == null) {
                    continue;
                }

                try {
                    var data = frame.data();
                    data.position(0);
                    data.limit(currentCapture.getFrameSize());
                    ClipRecorder.writeFully(channel, data);
                    this.lastFrameWrittenNanos = System.nanoTime();
                } catch (IOException exception) {
                    LOGGER.warn("Clip encoder pipe closed", exception);
                    this.running = false;
                } finally {
                    frame.data().clear();
                    currentCapture.getFreeFrames().offer(frame.data());
                }
            }
        }

        private void watchSegments() {
            while (this.running) {
                try {
                    this.scanSegments();
                    Thread.sleep(50L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception exception) {
                    LOGGER.debug("Could not scan clip segments", exception);
                }
            }
        }

        private void scanSegments() {
            var found = new ArrayList<Path>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory, ClipRecorder.SEGMENT_PREFIX + "*")) {
                for (var path : stream) {
                    if (Files.isRegularFile(path)) {
                        found.add(path);
                    }
                }
            } catch (IOException exception) {
                return;
            }

            found.sort(Comparator.comparing(path -> path.getFileName().toString()));
            var now = System.nanoTime();
            var segmentList = this.readSegmentList();

            synchronized (this.segmentLock) {
                var added = new ArrayList<Path>();

                for (var path : found) {
                    var known = false;

                    for (var segment : this.segments) {
                        if (segment.path.equals(path)) {
                            known = true;
                            break;
                        }
                    }

                    if (!known) {
                        added.add(path);
                    }
                }

                var currentCapture = this.capture;
                var captureStart = currentCapture == null ? 0L : currentCapture.getFirstFrameNanos();

                for (var path : added) {
                    var start = this.segments.isEmpty()
                            ? captureStart != 0L ? captureStart : now
                            : this.segments.getLast().endNanos != 0L ? this.segments.getLast().endNanos : now;
                    this.segments.add(new Segment(this.nextSegmentIndex++, path, start));
                }

                if (!added.isEmpty()) {
                    this.lastNewSegmentNanos = now;
                }

                if (captureStart != 0L) {
                    for (var segment : this.segments) {
                        var row = segmentList.get(segment.path.getFileName().toString());

                        if (row != null) {
                            segment.startNanos = captureStart + Math.round(row[0] * 1_000_000_000.0D);
                            segment.endNanos = captureStart + Math.round(row[1] * 1_000_000_000.0D);
                        }
                    }
                }

                this.trimSegments(now);
                this.segmentLock.notifyAll();
            }
        }

        private Map<String, double[]> readSegmentList() {
            var path = this.directory.resolve(ClipRecorder.SEGMENT_LIST);

            if (!Files.isRegularFile(path)) {
                return Map.of();
            }

            var rows = new HashMap<String, double[]>();

            try {
                for (var line : Files.readAllLines(path)) {
                    var parts = line.split(",");

                    if (parts.length < 3) {
                        continue;
                    }

                    var name = parts[0].substring(Math.max(parts[0].lastIndexOf('/'), parts[0].lastIndexOf('\\')) + 1);

                    try {
                        rows.put(name, new double[]{Double.parseDouble(parts[1]), Double.parseDouble(parts[2])});
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (IOException exception) {
                return Map.of();
            }

            return rows;
        }

        private void trimSegments(long now) {
            if (this.saving.get()) {
                return;
            }

            var cutoff = now - (this.module.getDuration() + 5L) * 1_000_000_000L;

            while (this.segments.size() > 1) {
                var oldest = this.segments.getFirst();

                if (oldest.endNanos == 0L || oldest.endNanos >= cutoff) {
                    break;
                }

                this.segments.removeFirst();

                try {
                    Files.deleteIfExists(oldest.path);
                } catch (IOException exception) {
                    LOGGER.debug("Could not delete old clip segment {}", oldest.path, exception);
                }
            }
        }

        private void runSaves() {
            while (this.saveRunning || !this.saveJobs.isEmpty()) {
                SaveJob job;

                try {
                    job = this.saveJobs.poll(100L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (job == null) {
                    continue;
                }

                try {
                    job.future.complete(this.performSave(job.withAudio));
                } catch (TooEarlyException exception) {
                    job.future.complete(new SaveResult(null, 0L, 0.0D, null, "cake.clip.too_early"));
                } catch (Throwable throwable) {
                    job.future.complete(new SaveResult(null, 0L, 0.0D, String.valueOf(throwable.getMessage()), null));
                }
            }
        }

        private SaveResult performSave(boolean withAudio) throws IOException {
            var latest = this.latestIndex();
            this.waitForNextSegment(latest);
            List<Segment> completed;
            long endNanos;

            synchronized (this.segmentLock) {
                if (this.segments.size() < 2) {
                    throw new TooEarlyException();
                }

                completed = new ArrayList<>(this.segments.subList(0, this.segments.size() - 1));
                endNanos = completed.getLast().endNanos;

                if (endNanos == 0L) {
                    endNanos = completed.getLast().startNanos + ClipRecorder.SEGMENT_SECONDS * 1_000_000_000L;
                }
            }

            var durationNanos = this.module.getDuration() * 1_000_000_000L;
            var selected = new ArrayList<Segment>();
            var startNanos = endNanos;

            for (var index = completed.size() - 1; index >= 0; index--) {
                var segment = completed.get(index);
                selected.add(0, segment);
                startNanos = segment.startNanos;

                if (endNanos - startNanos >= durationNanos) {
                    break;
                }
            }

            if (selected.isEmpty()) {
                throw new TooEarlyException();
            }

            var saveDirectory = Files.createTempDirectory(this.directory.getParent(), ".cake-save-");
            var concatFile = saveDirectory.resolve("concat.txt");
            var videoFile = saveDirectory.resolve("video.mp4");
            var audioFile = saveDirectory.resolve("audio.wav");
            var outputFile = saveDirectory.resolve("final.mp4");
            var concat = new StringBuilder();

            for (var segment : selected) {
                concat.append("file '")
                        .append(segment.path.toAbsolutePath().toString().replace("'", "'\\''"))
                        .append("'\n");
            }

            Files.writeString(concatFile, concat.toString(), StandardCharsets.UTF_8);

            try {
                this.runFfmpeg(
                        this.command,
                        List.of(
                                "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                                "-f", "concat", "-safe", "0", "-i", concatFile.toString(),
                                "-c", "copy", "-movflags", "+faststart",
                                videoFile.toString()
                        ),
                        60
                );

                var hasAudio = withAudio && this.audio.getRing().writeWav(audioFile, startNanos, endNanos);

                if (hasAudio) {
                    try {
                        this.runFfmpeg(
                                this.command,
                                List.of(
                                        "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                                        "-i", videoFile.toString(),
                                        "-i", audioFile.toString(),
                                        "-map", "0:v:0", "-map", "1:a:0",
                                        "-c:v", "copy",
                                        "-c:a", "aac", "-b:a", "192k",
                                        "-shortest", "-movflags", "+faststart",
                                        outputFile.toString()
                                ),
                                60
                        );
                    } catch (IOException exception) {
                        LOGGER.warn("Could not add audio to the clip, saving video only", exception);
                        Files.deleteIfExists(outputFile);
                        Files.move(videoFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.move(videoFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                }

                var name = Util.getFilenameFormattedDateTime();
                var target = this.clips.resolve(name + ".mp4");
                var suffix = 1;

                while (Files.exists(target)) {
                    target = clips.resolve(name + "_" + suffix++ + ".mp4");
                }

                try {
                    Files.move(outputFile, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException exception) {
                    Files.move(outputFile, target, StandardCopyOption.REPLACE_EXISTING);
                }

                var bytes = Files.size(target);
                var seconds = (endNanos - startNanos) / 1_000_000_000.0D;
                return new SaveResult(target, bytes, seconds, null, null);
            } finally {
                ClipRecorder.deleteDirectory(saveDirectory);
            }
        }

        private int latestIndex() {
            synchronized (this.segmentLock) {
                return this.segments.isEmpty() ? -1 : this.segments.getLast().index;
            }
        }

        private void waitForNextSegment(int latestIndex) {
            var deadline = System.nanoTime() + ClipRecorder.SAVE_WAIT_NANOS;

            synchronized (this.segmentLock) {
                while (this.running && (this.segments.isEmpty() || this.segments.getLast().index <= latestIndex)) {
                    var remaining = deadline - System.nanoTime();

                    if (remaining <= 0L) {
                        break;
                    }

                    try {
                        this.segmentLock.wait(Math.min(50L, TimeUnit.NANOSECONDS.toMillis(remaining) + 1L));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        private void runFfmpeg(String command, List<String> arguments, int timeoutSeconds) throws IOException {
            var full = new ArrayList<String>(arguments.size() + 1);
            full.add(command);
            full.addAll(arguments);
            LOGGER.debug("Clip save ffmpeg: {}", full);
            var builder = new ProcessBuilder(full).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);

            try {
                var started = builder.start();

                if (!started.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    started.destroyForcibly();
                    throw new IOException("ffmpeg timed out");
                }

                if (started.exitValue() != 0) {
                    throw new IOException("ffmpeg failed with exit code " + started.exitValue());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("ffmpeg was interrupted", exception);
            }
        }

        private void notify(Component message) {
            this.client.execute(() -> this.client.showDebugChat(message));
        }
    }

    private static void logProcessState(long pid) {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            return;
        }

        try {
            LOGGER.warn("Clip ffmpeg stat: {}", Files.readString(Path.of("/proc/" + pid + "/stat")).trim());
        } catch (IOException exception) {
            LOGGER.debug("Could not read the clip ffmpeg stat", exception);
        }

        try {
            LOGGER.warn("Clip ffmpeg wchan: {}", Files.readString(Path.of("/proc/" + pid + "/wchan")).trim());
        } catch (IOException exception) {
            LOGGER.debug("Could not read the clip ffmpeg wchan", exception);
        }
    }

    private static void writeFully(WritableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static Component savedMessage(Path path, long bytes, double seconds) {
        var file = path.toFile();
        var link = Component.literal(file.getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.getAbsoluteFile())));
        var details = String.format(Locale.ROOT, " (%.1fs, %s)", seconds, ClipRecorder.formatBytes(bytes));
        return Component.translatable("cake.clip.success", link)
                .append(Component.literal(details).withStyle(ChatFormatting.GRAY));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0D);
        }

        return String.format(Locale.ROOT, "%.2f MiB", bytes / (1024.0D * 1024.0D));
    }

    private static final class Segment {
        private final int index;
        private final Path path;
        private long startNanos;
        private long endNanos;

        private Segment(int index, Path path, long startNanos) {
            this.index = index;
            this.path = path;
            this.startNanos = startNanos;
            this.endNanos = 0L;
        }
    }

    private record SaveJob(CompletableFuture<SaveResult> future, boolean withAudio) {
    }

    private record SaveResult(
            @Nullable Path path,
            long bytes,
            double seconds,
            @Nullable String error,
            @Nullable String errorKey
    ) {
    }

    private static final class TooEarlyException extends IOException {
        private TooEarlyException() {
            super("Not enough footage yet");
        }
    }
}
