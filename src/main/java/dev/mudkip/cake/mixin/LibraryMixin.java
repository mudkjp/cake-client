package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.audio.DeviceList;
import com.mojang.blaze3d.audio.Library;
import dev.mudkip.cake.clip.ClipAudioTap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Library.class)
public abstract class LibraryMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void cake$clipAudioInit(String preferredDevice, DeviceList currentDevices, boolean useHrtf, CallbackInfo callback) {
        ClipAudioTap.onLibraryInit(useHrtf);
    }

    @Inject(method = "cleanup", at = @At("HEAD"))
    private void cake$clipAudioCleanup(CallbackInfo callback) {
        ClipAudioTap.onLibraryCleanup();
    }
}
