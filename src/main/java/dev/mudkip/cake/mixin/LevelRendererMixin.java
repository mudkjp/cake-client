package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.BlockOutlineModule;
import dev.mudkip.cake.render.BlockOutlineRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void cake$customBlockOutline(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState, CallbackInfo callbackInfo) {
        var module = ModuleManager.get().get(BlockOutlineModule.class);

        if (module == null || !module.isEnabled()) {
            return;
        }

        callbackInfo.cancel();
        BlockOutlineRenderer.submit(poseStack, submitNodeCollector, levelRenderState, module);
    }
}
