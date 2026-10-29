package dev.mudkip.cake.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class TelemetryMixin {
    @Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
    private void cake$disableTelemetry(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(false);
    }
}
