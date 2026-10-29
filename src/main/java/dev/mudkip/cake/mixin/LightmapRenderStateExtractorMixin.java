package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.FullBrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public final class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void cake$applyFullBright(LightmapRenderState state, float partialTick, CallbackInfo callbackInfo) {
        var module = ModuleManager.get().get(FullBrightModule.class);

        if (module == null || !module.isEnabled()) {
            return;
        }

        state.ambientColor = LightmapRenderStateExtractor.WHITE;
    }
}
