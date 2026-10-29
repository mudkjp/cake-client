package dev.mudkip.cake.mixin;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.services.MinecraftServicesUserApiService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;

@Mixin(value = MinecraftServicesUserApiService.class, remap = false)
public abstract class TelemetrySessionMixin {
    @Inject(method = "newTelemetrySession", at = @At("HEAD"), cancellable = true)
    private void cake$disableTelemetrySession(Executor executor, CallbackInfoReturnable<TelemetrySession> callbackInfo) {
        callbackInfo.setReturnValue(TelemetrySession.DISABLED);
    }
}
