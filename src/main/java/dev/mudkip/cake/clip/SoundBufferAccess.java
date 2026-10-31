package dev.mudkip.cake.clip;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

public interface SoundBufferAccess {
    @Nullable ByteBuffer cake$getPcm();
}
