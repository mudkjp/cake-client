package dev.mudkip.cake.clip;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FrameCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrameCapture.class);
    private static final int MAX_BUFFERS = 4;
    private static final int MAX_FRAMES = 4;

    private final GpuDevice device;
    private final RenderTarget target;
    private final int width;
    private final int height;
    private final int outputWidth;
    private final int outputHeight;
    private final int frameSize;
    private final long intervalNanos;
    private final long startedNanos;
    private final ArrayBlockingQueue<CapturedFrame> frames;
    private final AtomicBoolean closed;
    private volatile ArrayBlockingQueue<GpuBuffer> freeBuffers;
    private volatile ArrayBlockingQueue<ByteBuffer> freeFrames;
    private volatile Set<GpuBuffer> inFlight;
    private volatile int generation;
    private long nextCaptureNanos;
    private volatile long firstFrameNanos;
    private volatile long lastRenderNanos;
    private volatile long lastCopiedNanos;

    public FrameCapture(RenderTarget target, ClipResolution resolution, ClipFps fps) {
        this.device = RenderSystem.getDevice();
        this.target = target;
        this.width = target.width;
        this.height = target.height;
        var limitedHeight = resolution.limit(this.height);
        this.outputHeight = Math.max(2, limitedHeight + (limitedHeight & 1));
        this.outputWidth = FrameCapture.evenWidth(this.width, this.height, this.outputHeight);
        this.frameSize = Math.multiplyExact(this.width, this.height) * 4;
        this.intervalNanos = 1_000_000_000L / fps.getValue();
        this.startedNanos = System.nanoTime();
        this.lastCopiedNanos = this.startedNanos;
        this.frames = new ArrayBlockingQueue<>(FrameCapture.MAX_FRAMES);
        this.closed = new AtomicBoolean();
        this.createPool();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getOutputWidth() {
        return this.outputWidth;
    }

    public int getOutputHeight() {
        return this.outputHeight;
    }

    public long getFirstFrameNanos() {
        return this.firstFrameNanos;
    }

    public long getLastRenderNanos() {
        return this.lastRenderNanos;
    }

    public long getLastCopiedNanos() {
        return this.lastCopiedNanos;
    }

    public int getInFlightCount() {
        return this.inFlight.size();
    }

    public int getQueuedFrames() {
        return this.frames.size();
    }

    public int getFreeFrameCount() {
        return this.freeFrames.size();
    }

    public int getFreeBuffers() {
        return this.freeBuffers.size();
    }

    public int getFrameSize() {
        return this.frameSize;
    }

    public ArrayBlockingQueue<CapturedFrame> getFrames() {
        return this.frames;
    }

    public ArrayBlockingQueue<ByteBuffer> getFreeFrames() {
        return this.freeFrames;
    }

    public boolean isStuck() {
        if (this.closed.get() || this.inFlight.isEmpty()) {
            return false;
        }

        if (this.target.width != this.width || this.target.height != this.height) {
            return false;
        }

        var now = System.nanoTime();

        if (now - this.lastRenderNanos > 2_000_000_000L) {
            return false;
        }

        if (now - this.startedNanos < 5_000_000_000L) {
            return false;
        }

        return now - this.lastCopiedNanos > 2_000_000_000L;
    }

    public void recover() {
        if (this.closed.get()) {
            return;
        }

        this.generation++;
        GpuBuffer buffer;

        while ((buffer = this.freeBuffers.poll()) != null) {
            buffer.close();
        }

        this.createPool();
        this.frames.clear();
        this.nextCaptureNanos = 0L;
        this.lastCopiedNanos = System.nanoTime();
        LOGGER.warn("Reset the clip capture buffer pool");
    }

    public void onRenderEnd() {
        this.lastRenderNanos = System.nanoTime();

        if (this.closed.get() || this.target.width != this.width || this.target.height != this.height) {
            return;
        }

        var now = System.nanoTime();

        if (this.nextCaptureNanos == 0L) {
            this.nextCaptureNanos = now;
        }

        if (now < this.nextCaptureNanos - this.intervalNanos / 4L) {
            return;
        }

        var buffer = this.freeBuffers.poll();
        var frame = this.freeFrames.poll();

        if (buffer == null || frame == null) {
            return;
        }

        var texture = this.target.getColorTexture();

        if (texture == null) {
            this.freeBuffers.add(buffer);
            this.freeFrames.add(frame);
            return;
        }

        this.nextCaptureNanos += this.intervalNanos;

        if (this.nextCaptureNanos < now) {
            this.nextCaptureNanos = now + this.intervalNanos;
        }

        this.inFlight.add(buffer);
        var captureGeneration = this.generation;

        try {
            this.device.createCommandEncoder().copyTextureToBuffer(
                    texture,
                    buffer,
                    0L,
                    () -> this.onCopied(buffer, frame, captureGeneration),
                    0
            );
        } catch (Throwable throwable) {
            this.inFlight.remove(buffer);
            this.freeBuffers.add(buffer);
            this.freeFrames.add(frame);
            LOGGER.debug("Could not schedule clip frame capture", throwable);
        }
    }

    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        GpuBuffer buffer;

        while ((buffer = this.freeBuffers.poll()) != null) {
            buffer.close();
        }

        this.frames.clear();
    }

    private void createPool() {
        var buffers = new ArrayBlockingQueue<GpuBuffer>(FrameCapture.MAX_BUFFERS);
        var frameBuffers = new ArrayBlockingQueue<ByteBuffer>(FrameCapture.MAX_FRAMES);

        for (var index = 0; index < FrameCapture.MAX_BUFFERS; index++) {
            buffers.add(this.device.createBuffer(
                    () -> "Cake clip frame",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    this.frameSize
            ));
        }

        for (var index = 0; index < FrameCapture.MAX_FRAMES; index++) {
            frameBuffers.add(ByteBuffer.allocateDirect(this.frameSize).order(ByteOrder.nativeOrder()));
        }

        this.freeBuffers = buffers;
        this.freeFrames = frameBuffers;
        this.inFlight = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private void onCopied(GpuBuffer buffer, ByteBuffer frame, int captureGeneration) {
        if (this.closed.get()) {
            buffer.close();
            this.freeFrames.offer(frame);
            return;
        }

        if (captureGeneration != this.generation) {
            buffer.close();
            this.freeFrames.offer(frame);
            return;
        }

        this.inFlight.remove(buffer);
        this.lastCopiedNanos = System.nanoTime();

        try (var view = buffer.map(true, false)) {
            frame.clear();
            frame.put(view.data());
            frame.flip();
        } catch (Throwable throwable) {
            this.freeBuffers.add(buffer);
            this.freeFrames.add(frame);
            LOGGER.debug("Could not read clip frame", throwable);
            return;
        }

        this.freeBuffers.add(buffer);

        if (this.firstFrameNanos == 0L) {
            this.firstFrameNanos = System.nanoTime();
        }

        if (!this.frames.offer(new CapturedFrame(this.firstFrameNanos, frame))) {
            this.freeFrames.add(frame);
        }
    }

    private static int evenWidth(int width, int height, int outputHeight) {
        if (outputHeight >= height) {
            return width + (width & 1);
        }

        var scaled = (int) Math.round(width * (double) outputHeight / height);
        return Math.max(2, scaled + (scaled & 1));
    }

    public record CapturedFrame(long firstFrameNanos, ByteBuffer data) {
    }
}
