package dev.mudkip.cake.mixin;

import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.modules.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void cake$applyZoom(float partialTicks, CallbackInfoReturnable<Float> callbackInfo) {
        var module = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getActiveModule(ZoomModule.class);

        if (module != null) {
            callbackInfo.setReturnValue(module.applyZoom(callbackInfo.getReturnValueF()));
        }
    }
}
