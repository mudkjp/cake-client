package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public final class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void cake$applyZoom(float partialTicks, CallbackInfoReturnable<Float> callbackInfo) {
        var module = ModuleManager.get().get(ZoomModule.class);

        if (module != null) {
            callbackInfo.setReturnValue(module.applyZoom(callbackInfo.getReturnValueF()));
        }
    }
}
