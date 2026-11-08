package dev.mudkip.cake.clip;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private final ArrayBlockingQueue<CapturedFrame> frames;
    private final ArrayBlockingQueue<GpuBuffer> freeBuffers;
    private final ArrayBlockingQueue<ByteBuffer> freeFrames;
    private final Set<GpuBuffer> allBuffers;
    private final Set<GpuBuffer> inFlight;
    private final AtomicBoolean closed;
    private long nextCaptureNanos;
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
        this.lastRenderNanos = System.nanoTime();
        this.lastCopiedNanos = this.lastRenderNanos;
        this.frames = new ArrayBlockingQueue<>(FrameCapture.MAX_FRAMES);
        this.freeBuffers = new ArrayBlockingQueue<>(FrameCapture.MAX_BUFFERS);
        this.freeFrames = new ArrayBlockingQueue<>(FrameCapture.MAX_FRAMES);
        this.allBuffers = ConcurrentHashMap.newKeySet();
        this.inFlight = ConcurrentHashMap.newKeySet();
        this.closed = new AtomicBoolean();

        try {
            for (var index = 0; index < FrameCapture.MAX_BUFFERS; index++) {
                var buffer = this.device.createBuffer(
                        () -> "Cake clip frame",
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                        this.frameSize
                );
                this.freeBuffers.add(buffer);
                this.allBuffers.add(buffer);
            }

            for (var index = 0; index < FrameCapture.MAX_FRAMES; index++) {
                this.freeFrames.add(ByteBuffer.allocateDirect(this.frameSize).order(ByteOrder.nativeOrder()));
            }
        } catch (Throwable throwable) {
            this.close();
            throw throwable;
        }
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

    public int getFrameSize() {
        return this.frameSize;
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

    public ArrayBlockingQueue<CapturedFrame> getFrames() {
        return this.frames;
    }

    public ArrayBlockingQueue<ByteBuffer> getFreeFrames() {
        return this.freeFrames;
    }

    public void onRenderEnd() {
        var now = System.nanoTime();
        this.lastRenderNanos = now;

        if (this.closed.get() || this.target.width != this.width || this.target.height != this.height) {
            return;
        }

        if (this.nextCaptureNanos == 0L) {
            this.nextCaptureNanos = now;
        }

        if (now < this.nextCaptureNanos - this.intervalNanos / 4L) {
            return;
        }

        var buffer = this.freeBuffers.poll();
        var frame = this.freeFrames.poll();

        if (buffer == null || frame == null) {
            if (buffer != null) {
                this.freeBuffers.add(buffer);
            }

            if (frame != null) {
                this.freeFrames.add(frame);
            }

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

        try {
            this.device.createCommandEncoder().copyTextureToBuffer(
                    texture,
                    buffer,
                    0L,
                    () -> this.onCopied(buffer, frame, now),
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

        for (var buffer : this.allBuffers) {
            try {
                buffer.close();
            } catch (Throwable throwable) {
                LOGGER.debug("Could not close a clip capture buffer", throwable);
            }
        }

        this.allBuffers.clear();
        this.inFlight.clear();
        this.freeBuffers.clear();
        this.freeFrames.clear();
        this.frames.clear();
    }

    private void onCopied(GpuBuffer buffer, ByteBuffer frame, long captureNanos) {
        this.inFlight.remove(buffer);

        if (this.closed.get()) {
            return;
        }

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

        if (!this.frames.offer(new CapturedFrame(captureNanos, frame))) {
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

    public record CapturedFrame(long captureNanos, ByteBuffer data) {
    }
}
