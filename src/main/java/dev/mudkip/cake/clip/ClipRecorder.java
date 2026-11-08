package dev.mudkip.cake.clip;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.mudkip.cake.module.ReplayBufferModule;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClipRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipRecorder.class);
    private static final String SEGMENT_PATTERN = "segment-%06d.mp4";
    private static final String SEGMENT_PREFIX = "segment-";
    private static final String SEGMENT_LIST = "segments.csv";
    private static final int SEGMENT_SECONDS = 1;
    private static final int WRAP_MARGIN_SECONDS = 10;
    private static final long SAVE_WAIT_NANOS = 1_500_000_000L;
    private static final long STALL_NANOS = 3_000_000_000L;
    private static final long STARTUP_GRACE_NANOS = 10_000_000_000L;
    private static final long RENDER_IDLE_NANOS = 2_000_000_000L;
    private static final long MAX_FILL_NANOS = 500_000_000L;
    private static final long STALE_DIRECTORY_MILLIS = 3_600_000L;
    private static final ExecutorService SAVES = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Cake clip saves");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile @Nullable Session activeSession;
    private final Minecraft client;
    private final ReplayBufferModule module;
    private final ClipAudioTap audio;
    private final AtomicBoolean saving;
    private volatile boolean enabled;
    private volatile boolean preparing;
    private @Nullable Level level;
    private @Nullable Session session;
    private @Nullable ClipFps lastFps;
    private @Nullable ClipResolution lastResolution;
    private @Nullable ClipCodec lastCodec;
    private @Nullable ClipEncoderPreference lastEncoder;
    private @Nullable String lastFfmpegPath;
    private int lastQuality;
    private int lastDuration;
    private boolean lastAudio;
    private boolean audioFailureReported;
    private boolean prepareFailureReported;
    private long prepareRetryAtNanos;
    private long settingsRestartAtNanos;
    private int prepareGeneration;

    public ClipRecorder(Minecraft client, ReplayBufferModule module, ClipAudioTap audio) {
        this.client = client;
        this.module = module;
        this.audio = audio;
        this.saving = new AtomicBoolean();
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
        this.settingsRestartAtNanos = 0L;
        this.audioFailureReported = false;
        this.prepareFailureReported = false;
        this.prepareRetryAtNanos = 0L;

        if (!enabled) {
            this.prepareGeneration++;
            this.preparing = false;
            this.audio.setEnabled(false);
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

        var currentLevel = this.client.level;

        if (currentLevel == null) {
            this.audio.setEnabled(false);
            this.stopSession();
            this.level = null;
            return;
        }

        if (currentLevel != this.level) {
            this.stopSession();
            this.level = currentLevel;
        }

        this.audio.setEnabled(this.module.isAudioEnabled());

        if (this.audio.isEnabled() && this.audio.hasFailed() && !this.audioFailureReported) {
            this.audioFailureReported = true;
            this.notify(Component.translatable("cake.clip.audio_failed"));
        }

        var now = System.nanoTime();

        if (this.settingsRestartAtNanos != 0L && now >= this.settingsRestartAtNanos) {
            this.settingsRestartAtNanos = 0L;
            this.stopSession();
        }

        var currentSession = this.session;

        if (currentSession != null && currentSession.isResized()) {
            LOGGER.info("Clip recorder restarting after a window resize");
            this.stopSession();
        } else if (currentSession != null && currentSession.isRestartRequested()) {
            LOGGER.warn("Clip recorder restarted after a long pause");
            this.prepareRetryAtNanos = now + 2_000_000_000L;
            this.stopSession();
        } else if (currentSession != null && !currentSession.isAlive()) {
            LOGGER.warn("Clip encoder exited, restarting the session");
            this.prepareRetryAtNanos = now + 2_000_000_000L;
            this.stopSession();
        } else if (currentSession != null && currentSession.isStalled(now)) {
            LOGGER.warn("Clip recorder stalled, restarting the session");
            currentSession.logDiagnostics();
            this.prepareRetryAtNanos = now + 2_000_000_000L;
            this.stopSession();
        }

        if (this.session == null && !this.preparing && now >= this.prepareRetryAtNanos) {
            this.prepare();
        }
    }

    public void saveClip() {
        var current = this.session;

        if (current == null || !current.isAlive()) {
            this.notify(Component.translatable("cake.clip.not_running"));
            return;
        }

        if (!this.saving.compareAndSet(false, true)) {
            this.notify(Component.translatable("cake.clip.already_saving"));
            return;
        }

        ClipRecorder.SAVES.execute(() -> {
            SaveResult result;

            try {
                result = current.save();
            } catch (Throwable throwable) {
                result = SaveResult.error(String.valueOf(throwable.getMessage()));
            } finally {
                current.cleanupAfterSave();
                this.saving.set(false);
            }

            this.notifySave(result);
        });
    }

    public void settingsChanged() {
        this.audio.getRing().setBufferSeconds(this.module.getDuration());

        if (!this.enabled) {
            return;
        }

        this.prepareFailureReported = false;
        this.prepareRetryAtNanos = 0L;
        var audioEnabled = this.module.isAudioEnabled();

        if (this.audio.isEnabled() != audioEnabled) {
            this.audio.setEnabled(audioEnabled);
            this.audioFailureReported = false;
        }

        if (this.preparing) {
            this.prepareGeneration++;
            this.preparing = false;
            this.settingsRestartAtNanos = 0L;
            return;
        }

        if (this.settingsDiffer()) {
            this.settingsRestartAtNanos = System.nanoTime() + 1_000_000_000L;
        }
    }

    public void close() {
        this.enabled = false;
        this.prepareGeneration++;
        this.preparing = false;
        this.audio.setEnabled(false);
        this.stopSession();
    }

    private boolean settingsDiffer() {
        return this.lastFps != this.module.getFps()
                || this.lastResolution != this.module.getResolution()
                || this.lastCodec != this.module.getCodec()
                || this.lastEncoder != this.module.getEncoderPreference()
                || this.lastQuality != this.module.getQuality()
                || this.lastDuration != this.module.getDuration()
                || this.lastAudio != this.module.isAudioEnabled()
                || !Objects.equals(this.lastFfmpegPath, this.module.getFfmpegPath());
    }

    private void rememberSettings() {
        this.lastFps = this.module.getFps();
        this.lastResolution = this.module.getResolution();
        this.lastCodec = this.module.getCodec();
        this.lastEncoder = this.module.getEncoderPreference();
        this.lastQuality = this.module.getQuality();
        this.lastDuration = this.module.getDuration();
        this.lastAudio = this.module.isAudioEnabled();
        this.lastFfmpegPath = this.module.getFfmpegPath();
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
            var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            LOGGER.warn("Could not prepare the clip recorder", cause);
            this.prepareRetryAtNanos = System.nanoTime() + 15_000_000_000L;

            if (!this.prepareFailureReported) {
                this.prepareFailureReported = true;
                this.notify(Component.translatable("cake.clip.encoder_failed", Component.literal(String.valueOf(cause.getMessage()))));
            }

            return;
        }

        if (prepared == null || !this.enabled || this.client.level == null) {
            return;
        }

        try {
            this.startSession(prepared);
        } catch (Throwable throwable) {
            LOGGER.warn("Could not start the clip recorder", throwable);
            this.prepareRetryAtNanos = System.nanoTime() + 15_000_000_000L;

            if (!this.prepareFailureReported) {
                this.prepareFailureReported = true;
                this.notify(Component.translatable("cake.clip.failure", Component.literal(String.valueOf(throwable.getMessage()))));
            }
        }
    }

    private void startSession(Prepared prepared) throws IOException {
        var clips = FabricLoader.getInstance().getGameDir().resolve("clips");
        Files.createDirectories(clips);

        var directory = ClipRecorder.createTempDirectory(ClipRecorder.tempRoot(), clips, ".cake-clip-");
        var target = this.client.gameRenderer.mainRenderTarget();

        if (target.width <= 0 || target.height <= 0) {
            ClipRecorder.deleteDirectory(directory);
            throw new IOException("The game window is not ready");
        }

        var withAudio = this.audio.isEnabled() && !this.audio.hasFailed();
        this.audio.getRing().setBufferSeconds(this.module.getDuration());
        var created = new Session(
                this.module,
                this.audio,
                this.saving,
                clips,
                directory,
                prepared.command(),
                prepared.encoder(),
                target,
                withAudio
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
        this.prepareRetryAtNanos = 0L;
        this.prepareFailureReported = false;
        this.rememberSettings();
        ClipRecorder.activeSession = created;
        LOGGER.info(
                "Clip recorder started with {} ({}), audio {}",
                prepared.encoder().name(),
                prepared.encoder().codec(),
                withAudio ? "on" : "off"
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

    private void notifySave(SaveResult result) {
        if (result.errorKey() != null) {
            this.notify(Component.translatable(result.errorKey()));
        } else if (result.error() != null) {
            this.notify(Component.translatable("cake.clip.failure", Component.literal(result.error())));
        } else if (result.path() != null) {
            this.notify(ClipRecorder.savedMessage(result.path(), result.bytes(), result.seconds()));
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

    private static Path createTempDirectory(Path preferred, Path fallback, String prefix) throws IOException {
        try {
            return Files.createTempDirectory(preferred, prefix);
        } catch (IOException exception) {
            LOGGER.warn("Could not use the system temp directory for clip files", exception);
            return Files.createTempDirectory(fallback, prefix);
        }
    }

    private static void cleanStaleDirectories(Path root) {
        ClipRecorder.cleanStaleDirectories(root, ".cake-clip-*");
        ClipRecorder.cleanStaleDirectories(root, ".cake-save-*");
    }

    private static void cleanStaleDirectories(Path root, String pattern) {
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root, pattern)) {
            var now = System.currentTimeMillis();

            for (var directory : directories) {
                if (!Files.isDirectory(directory)) {
                    continue;
                }

                try {
                    if (now - Files.getLastModifiedTime(directory).toMillis() < ClipRecorder.STALE_DIRECTORY_MILLIS) {
                        continue;
                    }
                } catch (IOException exception) {
                    continue;
                }

                ClipRecorder.deleteDirectory(directory);
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

    private static void writeFrame(WritableByteChannel channel, ByteBuffer data, int frameSize) throws IOException {
        data.position(0);
        data.limit(frameSize);

        while (data.hasRemaining()) {
            channel.write(data);
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

    private record Prepared(String command, ClipEncoder encoder) {
    }

    private record SegmentRow(String name, double start, double end) {
    }

    private record SaveResult(
            @Nullable Path path,
            long bytes,
            double seconds,
            @Nullable String error,
            @Nullable String errorKey
    ) {
        private static SaveResult error(String message) {
            return new SaveResult(null, 0L, 0.0D, message, null);
        }
    }

    private static final class Session {
        private final ReplayBufferModule module;
        private final ClipAudioTap audio;
        private final AtomicBoolean recorderSaving;
        private final Path clips;
        private final Path directory;
        private final String command;
        private final ClipEncoder encoder;
        private final RenderTarget target;
        private final boolean withAudio;
        private final int durationSeconds;
        private final int fps;
        private final long startedNanos;
        private @Nullable Process process;
        private @Nullable FrameCapture capture;
        private @Nullable Thread writerThread;
        private @Nullable WritableByteChannel frameChannel;
        private @Nullable Path processLog;
        private volatile boolean running;
        private volatile boolean restartRequested;
        private volatile long firstVideoCaptureNanos;
        private volatile long lastCaptureWrittenNanos;

        private Session(
                ReplayBufferModule module,
                ClipAudioTap audio,
                AtomicBoolean recorderSaving,
                Path clips,
                Path directory,
                String command,
                ClipEncoder encoder,
                RenderTarget target,
                boolean withAudio
        ) {
            this.module = module;
            this.audio = audio;
            this.recorderSaving = recorderSaving;
            this.clips = clips;
            this.directory = directory;
            this.command = command;
            this.encoder = encoder;
            this.target = target;
            this.withAudio = withAudio;
            this.durationSeconds = module.getDuration();
            this.fps = module.getFps().getValue();
            this.running = true;
            this.startedNanos = System.nanoTime();
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
                this.closeResources();
                throw exception;
            }

            this.frameChannel = Channels.newChannel(this.process.getOutputStream());
            this.writerThread = new Thread(this::writeFrames, "Cake clip writer");
            this.writerThread.setDaemon(true);
            this.writerThread.start();
            LOGGER.info(
                    "Clip capture {}x{} -> {}x{} at {} fps, ffmpeg pid {}",
                    this.capture.getWidth(),
                    this.capture.getHeight(),
                    this.capture.getOutputWidth(),
                    this.capture.getOutputHeight(),
                    this.fps,
                    this.process.pid()
            );
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

        private boolean isAlive() {
            var current = this.process;
            return this.running && current != null && current.isAlive();
        }

        private boolean isResized() {
            var current = this.capture;
            return current != null && (this.target.width != current.getWidth() || this.target.height != current.getHeight());
        }

        private boolean isRestartRequested() {
            return this.restartRequested;
        }

        private boolean isStalled(long now) {
            if (!this.running) {
                return false;
            }

            if (now - this.startedNanos < ClipRecorder.STARTUP_GRACE_NANOS) {
                return false;
            }

            var current = this.capture;

            if (current == null) {
                return false;
            }

            if (now - current.getLastRenderNanos() > ClipRecorder.RENDER_IDLE_NANOS) {
                return false;
            }

            return this.lastCaptureWrittenNanos == 0L || now - this.lastCaptureWrittenNanos > ClipRecorder.STALL_NANOS;
        }

        private void logDiagnostics() {
            var current = this.capture;
            var written = this.lastCaptureWrittenNanos == 0L
                    ? -1.0D
                    : (System.nanoTime() - this.lastCaptureWrittenNanos) / 1_000_000_000.0D;
            LOGGER.warn(
                    "Clip session: frame write {}s ago, capture {}s ago, queues {}/{}, in flight {}, process alive {}",
                    written,
                    current == null ? -1.0D : (System.nanoTime() - current.getLastCopiedNanos()) / 1_000_000_000.0D,
                    current == null ? -1 : current.getQueuedFrames(),
                    current == null ? -1 : current.getFreeFrameCount(),
                    current == null ? -1 : current.getInFlightCount(),
                    this.process != null && this.process.isAlive()
            );
            var log = this.processLog;

            if (log != null) {
                try {
                    var lines = Files.readAllLines(log);
                    var from = Math.max(0, lines.size() - 10);

                    for (var index = from; index < lines.size(); index++) {
                        LOGGER.warn("Clip ffmpeg: {}", lines.get(index));
                    }
                } catch (IOException exception) {
                    LOGGER.debug("Could not read the clip ffmpeg log", exception);
                }
            }
        }

        private void stop() {
            this.running = false;
            var current = this.process;

            if (current != null) {
                current.destroyForcibly();
            }

            this.closeResources();
            this.cleanupDirectory();
        }

        private void closeResources() {
            var channel = this.frameChannel;

            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }

            if (this.writerThread != null) {
                this.writerThread.interrupt();
            }

            var current = this.capture;

            if (current != null) {
                current.close();
            }
        }

        private void cleanupDirectory() {
            if (!this.recorderSaving.get()) {
                ClipRecorder.deleteDirectory(this.directory);
            }
        }

        private void cleanupAfterSave() {
            if (!this.running) {
                ClipRecorder.deleteDirectory(this.directory);
            }
        }

        private List<String> createArguments() {
            var current = this.capture;
            var captureWidth = current.getWidth();
            var captureHeight = current.getHeight();
            var outputWidth = current.getOutputWidth();
            var outputHeight = current.getOutputHeight();
            var wrap = this.durationSeconds + ClipRecorder.WRAP_MARGIN_SECONDS;
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
            arguments.add(Integer.toString(this.fps));
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
            arguments.add("-map");
            arguments.add("0:v:0");
            arguments.add("-force_key_frames");
            arguments.add("expr:gte(t,n_forced*" + ClipRecorder.SEGMENT_SECONDS + ")");
            arguments.add("-fps_mode");
            arguments.add("vfr");
            arguments.add("-f");
            arguments.add("segment");
            arguments.add("-segment_time");
            arguments.add(Integer.toString(ClipRecorder.SEGMENT_SECONDS));
            arguments.add("-reset_timestamps");
            arguments.add("1");
            arguments.add("-segment_wrap");
            arguments.add(Integer.toString(wrap));
            arguments.add("-segment_list");
            arguments.add(this.directory.resolve(ClipRecorder.SEGMENT_LIST).toString());
            arguments.add("-segment_list_type");
            arguments.add("csv");
            arguments.add("-segment_list_size");
            arguments.add(Integer.toString(wrap));
            arguments.add("-segment_format");
            arguments.add("mp4");

            if (this.encoder.codec() == ClipCodec.HEVC) {
                arguments.add("-tag:v");
                arguments.add("hvc1");
            }

            arguments.add(this.directory.resolve(ClipRecorder.SEGMENT_PATTERN).toString());
            return arguments;
        }

        private void writeFrames() {
            var current = this.capture;
            var channel = this.frameChannel;

            if (current == null || channel == null) {
                return;
            }

            var frames = current.getFrames();
            var frameSize = current.getFrameSize();
            var interval = 1_000_000_000.0D / this.fps;
            var maxFillFrames = Math.max(1L, Math.round(ClipRecorder.MAX_FILL_NANOS / interval));
            var baseNanos = 0L;
            var nextSlot = 0L;
            ByteBuffer previous = null;

            try {
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

                    if (baseNanos == 0L) {
                        baseNanos = frame.captureNanos();
                        this.firstVideoCaptureNanos = baseNanos;
                    }

                    var target = Math.max(nextSlot, Math.round((frame.captureNanos() - baseNanos) / interval));
                    var missing = target - nextSlot;

                    if (missing > maxFillFrames) {
                        LOGGER.warn(
                                "Clip recorder restarting after a {}s pause",
                                missing * interval / 1_000_000_000.0D
                        );
                        current.getFreeFrames().offer(frame.data());
                        this.restartRequested = true;
                        break;
                    }

                    for (var index = 0L; index < missing; index++) {
                        ClipRecorder.writeFrame(channel, previous != null ? previous : frame.data(), frameSize);
                    }

                    ClipRecorder.writeFrame(channel, frame.data(), frameSize);

                    if (previous != null) {
                        current.getFreeFrames().offer(previous);
                    }

                    previous = frame.data();
                    nextSlot = target + 1L;
                    this.lastCaptureWrittenNanos = frame.captureNanos();
                }
            } catch (IOException exception) {
                if (this.running) {
                    LOGGER.warn("Clip encoder pipe closed", exception);
                }
            } finally {
                if (previous != null) {
                    current.getFreeFrames().offer(previous);
                }
            }
        }

        private SaveResult save() throws IOException {
            var rows = this.waitForSegment();

            if (rows.isEmpty()) {
                return new SaveResult(null, 0L, 0.0D, null, "cake.clip.too_early");
            }

            var newestEnd = rows.getLast().end;
            var selected = new ArrayList<SegmentRow>();

            for (var index = rows.size() - 1; index >= 0; index--) {
                var row = rows.get(index);
                selected.add(0, row);

                if (newestEnd - row.start >= this.durationSeconds) {
                    break;
                }
            }

            var saveDirectory = ClipRecorder.createTempDirectory(ClipRecorder.tempRoot(), this.clips, ".cake-save-");

            try {
                var concat = new StringBuilder();

                for (var row : selected) {
                    var source = this.directory.resolve(row.name);
                    var target = saveDirectory.resolve(row.name);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    concat.append("file '").append(target.toAbsolutePath().toString().replace("'", "'\\''")).append("'\n");
                }

                var concatFile = saveDirectory.resolve("concat.txt");
                Files.writeString(concatFile, concat.toString(), StandardCharsets.UTF_8);
                var videoFile = saveDirectory.resolve("video.mp4");
                var videoArguments = new ArrayList<String>(List.of(
                        "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                        "-f", "concat", "-safe", "0", "-i", concatFile.toString(),
                        "-c", "copy", "-movflags", "+faststart"
                ));

                if (this.encoder.codec() == ClipCodec.HEVC) {
                    videoArguments.add("-tag:v");
                    videoArguments.add("hvc1");
                }

                videoArguments.add(videoFile.toString());
                this.runFfmpeg(videoArguments);
                var outputFile = videoFile;
                var firstStart = selected.getFirst().start;

                if (this.withAudio && this.firstVideoCaptureNanos != 0L) {
                    var audioFile = saveDirectory.resolve("audio.wav");
                    var startNanos = this.firstVideoCaptureNanos + Math.round(firstStart * 1_000_000_000.0D);
                    var endNanos = this.firstVideoCaptureNanos + Math.round(newestEnd * 1_000_000_000.0D);

                    if (this.audio.getRing().writeWav(audioFile, startNanos, endNanos)) {
                        var finalFile = saveDirectory.resolve("final.mp4");
                        var audioArguments = new ArrayList<String>(List.of(
                                "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                                "-i", videoFile.toString(),
                                "-i", audioFile.toString(),
                                "-map", "0:v:0", "-map", "1:a:0",
                                "-c:v", "copy",
                                "-c:a", "aac", "-b:a", "192k",
                                "-shortest", "-movflags", "+faststart"
                        ));

                        if (this.encoder.codec() == ClipCodec.HEVC) {
                            audioArguments.add("-tag:v");
                            audioArguments.add("hvc1");
                        }

                        audioArguments.add(finalFile.toString());

                        try {
                            this.runFfmpeg(audioArguments);
                            outputFile = finalFile;
                        } catch (IOException exception) {
                            LOGGER.warn("Could not add audio to the clip, saving video only", exception);
                        }
                    }
                }

                var seconds = newestEnd - firstStart;
                var name = Util.getFilenameFormattedDateTime();
                var target = this.clips.resolve(name + ".mp4");
                var suffix = 1;

                while (Files.exists(target)) {
                    target = this.clips.resolve(name + "_" + suffix++ + ".mp4");
                }

                try {
                    Files.move(outputFile, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException exception) {
                    Files.move(outputFile, target, StandardCopyOption.REPLACE_EXISTING);
                }

                return new SaveResult(target, Files.size(target), seconds, null, null);
            } finally {
                ClipRecorder.deleteDirectory(saveDirectory);
            }
        }

        private List<SegmentRow> waitForSegment() {
            var deadline = System.nanoTime() + ClipRecorder.SAVE_WAIT_NANOS;
            var rows = this.readSegmentList();

            if (rows.isEmpty()) {
                while (this.running && System.nanoTime() < deadline) {
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    rows = this.readSegmentList();

                    if (!rows.isEmpty()) {
                        break;
                    }
                }

                return rows;
            }

            var newestEnd = rows.getLast().end;

            while (this.running && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }

                var next = this.readSegmentList();

                if (!next.isEmpty() && next.getLast().end > newestEnd + 0.000001D) {
                    return next;
                }
            }

            return this.readSegmentList();
        }

        private List<SegmentRow> readSegmentList() {
            var path = this.directory.resolve(ClipRecorder.SEGMENT_LIST);

            for (var attempt = 0; attempt < 3; attempt++) {
                if (!Files.isRegularFile(path)) {
                    return List.of();
                }

                Map<String, SegmentRow> rows = new HashMap<>();

                try {
                    for (var line : Files.readAllLines(path)) {
                        var parts = line.split(",");

                        if (parts.length < 3) {
                            continue;
                        }

                        var name = parts[0].substring(Math.max(parts[0].lastIndexOf('/'), parts[0].lastIndexOf('\\')) + 1);

                        try {
                            var start = Double.parseDouble(parts[1]);
                            var end = Double.parseDouble(parts[2]);
                            rows.put(name, new SegmentRow(name, start, end));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } catch (IOException exception) {
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return List.of();
                    }

                    continue;
                }

                var result = new ArrayList<>(rows.values());
                result.sort(Comparator.comparingDouble(SegmentRow::start));
                return result;
            }

            return List.of();
        }

        private void runFfmpeg(List<String> arguments) throws IOException {
            var full = new ArrayList<String>(arguments.size() + 1);
            full.add(this.command);
            full.addAll(arguments);
            LOGGER.debug("Clip save ffmpeg: {}", full);
            var builder = new ProcessBuilder(full).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);

            try {
                var started = builder.start();

                if (!started.waitFor(60L, TimeUnit.SECONDS)) {
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
    }
}
