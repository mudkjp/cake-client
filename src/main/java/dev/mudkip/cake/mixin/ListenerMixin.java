package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import dev.mudkip.cake.clip.ClipAudioTap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Listener.class)
public abstract class ListenerMixin {
    @Inject(method = "setTransform", at = @At("HEAD"))
    private void cake$clipListener(ListenerTransform transform, CallbackInfo callback) {
        ClipAudioTap.setListener(transform.position(), transform.forward(), transform.up());
    }
}
