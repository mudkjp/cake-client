package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import dev.mudkip.cake.clip.ClipAudioTap;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Unique
    private long cake$clipId;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cake$assignClipId(int source, CallbackInfo callback) {
        this.cake$clipId = ClipAudioTap.nextChannelId();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void cake$clipDestroy(CallbackInfo callback) {
        ClipAudioTap.onChannelDestroyed(this.cake$clipId);
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void cake$clipPlay(CallbackInfo callback) {
        ClipAudioTap.play(this.cake$clipId);
    }

    @Inject(method = "pause", at = @At("HEAD"))
    private void cake$clipPause(CallbackInfo callback) {
        ClipAudioTap.pause(this.cake$clipId);
    }

    @Inject(method = "unpause", at = @At("HEAD"))
    private void cake$clipUnpause(CallbackInfo callback) {
        ClipAudioTap.unpause(this.cake$clipId);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void cake$clipStop(CallbackInfo callback) {
        ClipAudioTap.stop(this.cake$clipId);
    }

    @Inject(method = "setSelfPosition", at = @At("HEAD"))
    private void cake$clipPosition(Vec3 position, CallbackInfo callback) {
        ClipAudioTap.setPosition(this.cake$clipId, position);
    }

    @Inject(method = "setPitch", at = @At("HEAD"))
    private void cake$clipPitch(float pitch, CallbackInfo callback) {
        ClipAudioTap.setPitch(this.cake$clipId, pitch);
    }

    @Inject(method = "setLooping", at = @At("HEAD"))
    private void cake$clipLooping(boolean looping, CallbackInfo callback) {
        ClipAudioTap.setLooping(this.cake$clipId, looping);
    }

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void cake$clipVolume(float volume, CallbackInfo callback) {
        ClipAudioTap.setVolume(this.cake$clipId, volume);
    }

    @Inject(method = "disableAttenuation", at = @At("HEAD"))
    private void cake$clipDisableAttenuation(CallbackInfo callback) {
        ClipAudioTap.disableAttenuation(this.cake$clipId);
    }

    @Inject(method = "linearAttenuation", at = @At("HEAD"))
    private void cake$clipLinearAttenuation(float distance, CallbackInfo callback) {
        ClipAudioTap.linearAttenuation(this.cake$clipId, distance);
    }

    @Inject(method = "setRelative", at = @At("HEAD"))
    private void cake$clipRelative(boolean relative, CallbackInfo callback) {
        ClipAudioTap.setRelative(this.cake$clipId, relative);
    }

    @Inject(method = "attachStaticBuffer", at = @At("RETURN"))
    private void cake$clipStaticBuffer(SoundBuffer buffer, CallbackInfo callback) {
        ClipAudioTap.attachStaticBuffer(this.cake$clipId, buffer);
    }

    @ModifyVariable(method = "attachBufferStream", at = @At("HEAD"), argsOnly = true)
    private AudioStream cake$clipWrapStream(AudioStream stream) {
        return ClipAudioTap.wrapStream(this.cake$clipId, stream);
    }
}
