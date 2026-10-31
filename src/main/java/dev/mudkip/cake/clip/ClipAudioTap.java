package dev.mudkip.cake.clip;

import com.mojang.blaze3d.audio.OpenAlUtil;
import com.mojang.blaze3d.audio.SoundBuffer;
import dev.mudkip.cake.module.modules.ReplayBufferModule;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.SOFTHRTF;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class ClipAudioTap {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipAudioTap.class);
    private static final AtomicLong CHANNEL_IDS = new AtomicLong();
    private static volatile @Nullable ClipAudioTap instance;

    private final ReplayBufferModule module;
    private final ClipAudioRing ring;
    private volatile boolean enabled;
    private volatile boolean libraryReady;
    private volatile boolean hrtf;
    private volatile boolean failed;
    private volatile @Nullable Mirror mirror;

    public ClipAudioTap(ReplayBufferModule module) {
        this.module = module;
        this.ring = new ClipAudioRing();
        ClipAudioTap.instance = this;
    }

    public static @Nullable ClipAudioTap get() {
        return ClipAudioTap.instance;
    }

    public static long nextChannelId() {
        return ClipAudioTap.CHANNEL_IDS.incrementAndGet();
    }

    public static void onLibraryInit(boolean hrtf) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.libraryInit(hrtf);
        }
    }

    public static void onLibraryCleanup() {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.libraryCleanup();
        }
    }

    public static void onChannelDestroyed(long channelId) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.destroyChannel(channelId));
        }
    }

    public static void play(long channelId) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.play(channelId));
        }
    }

    public static void pause(long channelId) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.pause(channelId));
        }
    }

    public static void unpause(long channelId) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.unpause(channelId));
        }
    }

    public static void stop(long channelId) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.stop(channelId));
        }
    }

    public static void setPosition(long channelId, Vec3 position) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.setPosition(channelId, position));
        }
    }

    public static void setPitch(long channelId, float pitch) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.setPitch(channelId, pitch));
        }
    }

    public static void setLooping(long channelId, boolean looping) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.setLooping(channelId, looping));
        }
    }

    public static void setVolume(long channelId, float volume) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.setVolume(channelId, volume));
        }
    }

    public static void disableAttenuation(long channelId) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.disableAttenuation(channelId));
        }
    }

    public static void linearAttenuation(long channelId, float distance) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.linearAttenuation(channelId, distance));
        }
    }

    public static void setRelative(long channelId, boolean relative) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.setRelative(channelId, relative));
        }
    }

    public static void attachStaticBuffer(long channelId, SoundBuffer buffer) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.attachStaticBuffer(channelId, buffer));
        }
    }

    public static AudioStream wrapStream(long channelId, AudioStream stream) {
        var tap = ClipAudioTap.instance;

        if (tap == null || !tap.enabled) {
            return stream;
        }

        return new MirroringAudioStream(tap, channelId, stream);
    }

    public static void setListener(Vec3 position, Vec3 forward, Vec3 up) {
        var tap = ClipAudioTap.instance;

        if (tap != null) {
            tap.enqueue(mirror -> mirror.setListener(position, forward, up));
        }
    }

    public ClipAudioRing getRing() {
        return this.ring;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;

        if (enabled) {
            this.failed = false;
            this.ring.setBufferSeconds(this.module.getDuration());
            this.ring.clear();

            if (this.libraryReady) {
                this.start();
            }
        } else {
            this.stop();
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean hasFailed() {
        return this.failed;
    }

    private void libraryInit(boolean hrtf) {
        this.libraryReady = true;
        this.hrtf = hrtf;

        if (this.enabled) {
            this.start();
        }
    }

    private void libraryCleanup() {
        this.libraryReady = false;
        this.stop();
    }

    private void start() {
        if (this.mirror != null) {
            return;
        }

        this.failed = false;
        var created = new Mirror(this.hrtf, this.ring, () -> this.failed = true);
        this.mirror = created;
        created.start();
    }

    private void stop() {
        var active = this.mirror;
        this.mirror = null;

        if (active != null) {
            active.close();
        }
    }

    private void enqueue(Consumer<Mirror> command) {
        var active = this.mirror;

        if (!this.enabled || active == null || !active.isRunning()) {
            return;
        }

        active.commands.add(command);
    }

    private static final class Mirror {
        private final boolean hrtf;
        private final ClipAudioRing ring;
        private final Runnable onFailure;
        private final ConcurrentLinkedQueue<Consumer<Mirror>> commands;
        private final Map<Long, Source> sources;
        private final Map<SoundBuffer, Integer> staticBuffers;
        private final Thread thread;
        private volatile boolean running;

        private Mirror(boolean hrtf, ClipAudioRing ring, Runnable onFailure) {
            this.hrtf = hrtf;
            this.ring = ring;
            this.onFailure = onFailure;
            this.commands = new ConcurrentLinkedQueue<>();
            this.sources = new ConcurrentHashMap<>();
            this.staticBuffers = new IdentityHashMap<>();
            this.thread = new Thread(this::run, "Cake clip audio");
            this.thread.setDaemon(true);
            this.running = true;
        }

        private void start() {
            this.thread.start();
        }

        private boolean isRunning() {
            return this.running;
        }

        private void close() {
            this.running = false;
            this.thread.interrupt();

            try {
                this.thread.join(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private void play(long channelId) {
            var source = this.source(channelId);
            source.wantsToPlay = true;
            source.paused = false;
            AL10.alSourcePlay(source.id);
        }

        private void pause(long channelId) {
            var source = this.source(channelId);
            source.paused = true;
            AL10.alSourcePause(source.id);
        }

        private void unpause(long channelId) {
            var source = this.source(channelId);
            source.paused = false;
            AL10.alSourcePlay(source.id);
        }

        private void stop(long channelId) {
            var source = this.source(channelId);
            source.wantsToPlay = false;
            source.paused = false;
            AL10.alSourceStop(source.id);
        }

        private void setPosition(long channelId, Vec3 position) {
            var source = this.source(channelId);
            AL10.alSource3f(source.id, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
        }

        private void setPitch(long channelId, float pitch) {
            AL10.alSourcef(this.source(channelId).id, AL10.AL_PITCH, pitch);
        }

        private void setLooping(long channelId, boolean looping) {
            AL10.alSourcei(this.source(channelId).id, AL10.AL_LOOPING, looping ? 1 : 0);
        }

        private void setVolume(long channelId, float volume) {
            AL10.alSourcef(this.source(channelId).id, AL10.AL_GAIN, volume);
        }

        private void disableAttenuation(long channelId) {
            AL10.alSourcei(this.source(channelId).id, 53248, 0);
        }

        private void linearAttenuation(long channelId, float distance) {
            var source = this.source(channelId).id;
            AL10.alSourcei(source, 53248, 53251);
            AL10.alSourcef(source, 4131, distance);
            AL10.alSourcef(source, 4129, 1.0F);
            AL10.alSourcef(source, 4128, 0.0F);
        }

        private void setRelative(long channelId, boolean relative) {
            AL10.alSourcei(this.source(channelId).id, 514, relative ? 1 : 0);
        }

        private void attachStaticBuffer(long channelId, SoundBuffer buffer) {
            var source = this.source(channelId);
            var id = this.staticBuffers.get(buffer);

            if (id == null) {
                var access = (SoundBufferAccess) buffer;
                var pcm = access.cake$getPcm();

                if (pcm == null) {
                    return;
                }

                id = AL10.alGenBuffers();
                AL10.alBufferData(id, OpenAlUtil.audioFormatToOpenAl(buffer.format()), pcm.duplicate(), (int) buffer.format().getSampleRate());
                this.staticBuffers.put(buffer, id);
            }

            AL10.alSourcei(source.id, AL10.AL_BUFFER, id);

            if (source.wantsToPlay && !source.paused && AL10.alGetSourcei(source.id, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source.id);
            }
        }

        private void queueStream(long channelId, ByteBuffer pcm, AudioFormat format) {
            var source = this.source(channelId);
            var id = AL10.alGenBuffers();
            AL10.alBufferData(id, OpenAlUtil.audioFormatToOpenAl(format), pcm, (int) format.getSampleRate());
            AL10.alSourceQueueBuffers(source.id, new int[]{id});
            source.queued.addLast(id);

            if (source.wantsToPlay && !source.paused && AL10.alGetSourcei(source.id, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source.id);
            }
        }

        private void setListener(Vec3 position, Vec3 forward, Vec3 up) {
            AL10.alListener3f(AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
            AL10.alListenerfv(AL10.AL_ORIENTATION, new float[]{
                    (float) forward.x, (float) forward.y, (float) forward.z,
                    (float) up.x, (float) up.y, (float) up.z
            });
        }

        private void destroyChannel(long channelId) {
            var source = this.sources.remove(channelId);

            if (source == null) {
                return;
            }

            AL10.alSourceStop(source.id);
            this.deleteQueued(source);

            if (!source.queued.isEmpty()) {
                var ids = new int[source.queued.size()];
                var index = 0;

                for (var id : source.queued) {
                    ids[index++] = id;
                }

                AL10.alDeleteBuffers(ids);
                source.queued.clear();
            }

            AL10.alDeleteSources(new int[]{source.id});
        }

        private Source source(long channelId) {
            return this.sources.computeIfAbsent(channelId, id -> new Source(AL10.alGenSources()));
        }

        private void run() {
            var device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);

            if (device == 0L) {
                LOGGER.warn("Could not open the clip audio loopback device");
                this.running = false;
                this.onFailure.run();
                return;
            }

            ALCCapabilities capabilities;

            try {
                capabilities = ALC.createCapabilities(device);
            } catch (Throwable throwable) {
                LOGGER.error("Could not read the clip audio loopback capabilities", throwable);
                this.running = false;
                this.onFailure.run();
                ALC10.alcCloseDevice(device);
                return;
            }

            var context = 0L;

            try {
                if (!capabilities.ALC_EXT_thread_local_context) {
                    LOGGER.warn("OpenAL does not support thread-local contexts, clip audio is disabled");
                    this.onFailure.run();
                    return;
                }

                context = this.createContext(device, capabilities, this.hrtf);

                if (context == 0L) {
                    LOGGER.warn("Could not create the clip audio loopback context");
                    this.onFailure.run();
                    return;
                }

                if (!JNI.invokePZ(context, capabilities.alcSetThreadContext)) {
                    LOGGER.warn("Could not make the clip audio loopback context current");
                    this.onFailure.run();
                    return;
                }

                AL.createCapabilities(capabilities);
                AL10.alEnable(512);
                this.setListener(Vec3.ZERO, new Vec3(0.0D, 0.0D, -1.0D), new Vec3(0.0D, 1.0D, 0.0D));
                this.render(device, capabilities);
                this.destroyAll();
                JNI.invokePZ(0L, capabilities.alcSetThreadContext);
                JNI.invokePV(context, capabilities.alcDestroyContext);
                context = 0L;
            } catch (Throwable throwable) {
                LOGGER.error("Clip audio capture stopped unexpectedly", throwable);
                this.onFailure.run();
            } finally {
                this.running = false;

                if (context != 0L) {
                    JNI.invokePV(context, capabilities.alcDestroyContext);
                }

                JNI.invokePZ(device, capabilities.alcCloseDevice);
            }
        }

        private long createContext(long device, ALCCapabilities capabilities, boolean useHrtf) {
            var context = this.createContext(device, capabilities, useHrtf, capabilities.ALC_SOFT_output_limiter);

            if (context == 0L) {
                context = this.createContext(device, capabilities, false, capabilities.ALC_SOFT_output_limiter);
            }

            if (context == 0L) {
                context = this.createContext(device, capabilities, false, false);
            }

            return context;
        }

        private long createContext(long device, ALCCapabilities capabilities, boolean useHrtf, boolean outputLimiter) {
            var values = new java.util.ArrayList<Integer>();
            values.add(ALC10.ALC_FREQUENCY);
            values.add(ClipAudioRing.SAMPLE_RATE);
            values.add(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT);
            values.add(SOFTLoopback.ALC_STEREO_SOFT);
            values.add(SOFTLoopback.ALC_FORMAT_TYPE_SOFT);
            values.add(SOFTLoopback.ALC_SHORT_SOFT);

            if (useHrtf && capabilities.ALC_SOFT_HRTF) {
                values.add(SOFTHRTF.ALC_HRTF_SOFT);
                values.add(1);
                values.add(SOFTHRTF.ALC_HRTF_ID_SOFT);
                values.add(0);
            }

            if (outputLimiter) {
                values.add(SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT);
                values.add(1);
            }

            values.add(0);
            var buffer = ByteBuffer.allocateDirect(values.size() * Integer.BYTES).order(ByteOrder.nativeOrder());
            var ints = buffer.asIntBuffer();

            for (var value : values) {
                ints.put(value);
            }

            ints.flip();
            return JNI.invokePPP(device, MemoryUtil.memAddress(ints), capabilities.alcCreateContext);
        }

        private void render(long device, ALCCapabilities capabilities) {
            var pcm = ByteBuffer.allocateDirect(ClipAudioRing.CHUNK_BYTES).order(ByteOrder.nativeOrder());
            var next = System.nanoTime();

            while (this.running) {
                this.drainCommands();

                for (var source : this.sources.values()) {
                    this.deleteQueued(source);
                }

                pcm.clear();
                JNI.invokePPV(device, MemoryUtil.memAddress(pcm), ClipAudioRing.FRAMES_PER_CHUNK, capabilities.alcRenderSamplesSOFT);
                pcm.clear();
                pcm.limit(ClipAudioRing.CHUNK_BYTES);
                this.ring.add(next, pcm);
                next += ClipAudioRing.CHUNK_NANOS;
                var sleep = next - System.nanoTime();

                if (sleep > 0L) {
                    LockSupport.parkNanos(sleep);
                } else if (sleep < -200_000_000L) {
                    next = System.nanoTime();
                }
            }
        }

        private void drainCommands() {
            Consumer<Mirror> command;

            while ((command = this.commands.poll()) != null) {
                try {
                    command.accept(this);
                } catch (Throwable throwable) {
                    LOGGER.debug("Clip audio command failed", throwable);
                }
            }
        }

        private void deleteQueued(Source source) {
            var processed = AL10.alGetSourcei(source.id, AL10.AL_BUFFERS_PROCESSED);

            if (processed <= 0) {
                return;
            }

            var ids = new int[processed];
            AL10.alSourceUnqueueBuffers(source.id, ids);
            AL10.alDeleteBuffers(ids);

            for (var index = 0; index < processed && !source.queued.isEmpty(); index++) {
                source.queued.removeFirst();
            }
        }

        private void destroyAll() {
            for (var source : this.sources.values()) {
                AL10.alSourceStop(source.id);
                this.deleteQueued(source);

                if (!source.queued.isEmpty()) {
                    var ids = new int[source.queued.size()];
                    var index = 0;

                    for (var id : source.queued) {
                        ids[index++] = id;
                    }

                    AL10.alDeleteBuffers(ids);
                }

                AL10.alDeleteSources(new int[]{source.id});
            }

            this.sources.clear();
            this.staticBuffers.clear();
        }
    }

    private static final class MirroringAudioStream implements AudioStream {
        private final ClipAudioTap tap;
        private final long channelId;
        private final AudioStream delegate;

        private MirroringAudioStream(ClipAudioTap tap, long channelId, AudioStream delegate) {
            this.tap = tap;
            this.channelId = channelId;
            this.delegate = delegate;
        }

        @Override
        public AudioFormat getFormat() {
            return this.delegate.getFormat();
        }

        @Override
        public ByteBuffer read(int expectedSize) throws IOException {
            var buffer = this.delegate.read(expectedSize);

            if (buffer == null || !buffer.hasRemaining()) {
                return buffer;
            }

            var copy = ByteBuffer.allocateDirect(buffer.remaining()).order(ByteOrder.nativeOrder());
            copy.put(buffer.duplicate());
            copy.flip();
            this.tap.enqueue(mirror -> mirror.queueStream(this.channelId, copy, this.delegate.getFormat()));
            return buffer;
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }
    }

    private static final class Source {
        private final int id;
        private final Deque<Integer> queued;
        private boolean wantsToPlay;
        private boolean paused;

        private Source(int id) {
            this.id = id;
            this.queued = new ArrayDeque<>();
        }
    }
}
