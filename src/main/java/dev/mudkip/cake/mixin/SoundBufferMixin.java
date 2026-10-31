package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import dev.mudkip.cake.clip.SoundBufferAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

@Mixin(SoundBuffer.class)
public abstract class SoundBufferMixin implements SoundBufferAccess {
    @Shadow
    private @Nullable ByteBuffer data;
    @Unique
    private @Nullable ByteBuffer cake$pcm;

    @Inject(method = "getAlBuffer", at = @At("HEAD"))
    private void cake$retainPcm(CallbackInfoReturnable<OptionalInt> callback) {
        if (this.cake$pcm == null && this.data != null) {
            this.cake$pcm = this.data;
        }
    }

    @Override
    public @Nullable ByteBuffer cake$getPcm() {
        return this.cake$pcm;
    }
}
