package dev.mudkip.cake.clip;

import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ClipAudioRing {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 2;
    public static final int BYTES_PER_FRAME = ClipAudioRing.CHANNELS * 2;
    public static final int FRAMES_PER_CHUNK = 480;
    public static final int CHUNK_BYTES = ClipAudioRing.FRAMES_PER_CHUNK * ClipAudioRing.BYTES_PER_FRAME;
    public static final long CHUNK_NANOS = 10_000_000L;
    private static final byte[] ZEROS = new byte[8192];

    private final Deque<Chunk> chunks;
    private long bufferNanos;

    public ClipAudioRing() {
        this.chunks = new ArrayDeque<>();
        this.bufferNanos = 35_000_000_000L;
    }

    public synchronized void setBufferSeconds(int seconds) {
        this.bufferNanos = (seconds + 5L) * 1_000_000_000L;
        this.trim();
    }

    public synchronized void add(long startNanos, ByteBuffer pcm) {
        pcm.position(0);
        pcm.limit(ClipAudioRing.CHUNK_BYTES);
        var copy = new byte[ClipAudioRing.CHUNK_BYTES];
        pcm.get(copy);
        this.chunks.addLast(new Chunk(startNanos, copy));
        this.trim();
    }

    public synchronized void clear() {
        this.chunks.clear();
    }

    public synchronized @Nullable Long newestNanos() {
        var last = this.chunks.peekLast();
        return last == null ? null : last.startNanos();
    }

    public synchronized @Nullable Long oldestNanos() {
        var first = this.chunks.peekFirst();
        return first == null ? null : first.startNanos();
    }

    public boolean writeWav(Path path, long startNanos, long endNanos) throws IOException {
        var durationNanos = endNanos - startNanos;

        if (durationNanos <= 0L) {
            return false;
        }

        var chunks = this.snapshot();
        var totalFrames = Math.max(0L, Math.round(durationNanos * ClipAudioRing.SAMPLE_RATE / 1_000_000_000.0D));
        var dataSize = totalFrames * ClipAudioRing.BYTES_PER_FRAME;

        try (var output = new BufferedOutputStream(Files.newOutputStream(path), 64 * 1024)) {
            ClipAudioRing.writeHeader(output, dataSize);
            var written = 0L;
            var cursor = startNanos;

            for (var chunk : chunks) {
                var chunkStart = chunk.startNanos();
                var chunkEnd = chunkStart + ClipAudioRing.CHUNK_NANOS;

                if (chunkEnd <= startNanos) {
                    continue;
                }

                if (chunkStart >= endNanos) {
                    break;
                }

                var fromNanos = Math.max(chunkStart, startNanos);

                if (fromNanos > cursor) {
                    written += ClipAudioRing.writeZeros(output, Math.min(dataSize - written, ClipAudioRing.nanosToBytes(fromNanos - cursor)));
                }

                var toNanos = Math.min(chunkEnd, endNanos);

                if (written >= dataSize) {
                    break;
                }

                var fromFrame = Math.clamp(Math.round((fromNanos - chunkStart) * (double) ClipAudioRing.SAMPLE_RATE / 1_000_000_000.0D), 0L, ClipAudioRing.FRAMES_PER_CHUNK);
                var toFrame = Math.clamp(Math.round((toNanos - chunkStart) * (double) ClipAudioRing.SAMPLE_RATE / 1_000_000_000.0D), 0L, ClipAudioRing.FRAMES_PER_CHUNK);
                var fromByte = (int) fromFrame * ClipAudioRing.BYTES_PER_FRAME;
                var toByte = (int) toFrame * ClipAudioRing.BYTES_PER_FRAME;
                var length = Math.min(toByte - fromByte, (int) Math.min(Integer.MAX_VALUE, dataSize - written));

                if (length > 0) {
                    output.write(chunk.pcm(), fromByte, length);
                    written += length;
                }

                cursor = Math.max(cursor, toNanos);
            }

            if (written < dataSize) {
                ClipAudioRing.writeZeros(output, dataSize - written);
            }
        }

        return true;
    }

    private synchronized List<Chunk> snapshot() {
        return new ArrayList<>(this.chunks);
    }

    private void trim() {
        var last = this.chunks.peekLast();

        if (last == null) {
            return;
        }

        var cutoff = last.startNanos() - this.bufferNanos;

        while (!this.chunks.isEmpty() && this.chunks.peekFirst().startNanos() < cutoff) {
            this.chunks.removeFirst();
        }
    }

    private static long nanosToBytes(long nanos) {
        return Math.round(nanos * ClipAudioRing.SAMPLE_RATE / 1_000_000_000.0D) * ClipAudioRing.BYTES_PER_FRAME;
    }

    private static long writeZeros(OutputStream output, long bytes) throws IOException {
        var remaining = bytes;

        while (remaining > 0) {
            var length = (int) Math.min(ClipAudioRing.ZEROS.length, remaining);
            output.write(ClipAudioRing.ZEROS, 0, length);
            remaining -= length;
        }

        return bytes;
    }

    private static void writeHeader(OutputStream output, long dataSize) throws IOException {
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x46464952);
        header.putInt((int) (36L + dataSize));
        header.putInt(0x45564157);
        header.putInt(0x20746D66);
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) ClipAudioRing.CHANNELS);
        header.putInt(ClipAudioRing.SAMPLE_RATE);
        header.putInt(ClipAudioRing.SAMPLE_RATE * ClipAudioRing.BYTES_PER_FRAME);
        header.putShort((short) ClipAudioRing.BYTES_PER_FRAME);
        header.putShort((short) 16);
        header.putInt(0x61746164);
        header.putInt((int) dataSize);
        output.write(header.array());
    }

    private record Chunk(long startNanos, byte[] pcm) {
    }
}
