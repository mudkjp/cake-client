package dev.mudkip.cake.mixin;

import dev.mudkip.cake.clip.ClipRecorder;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void cake$captureClipFrame(CallbackInfo callback) {
        ClipRecorder.onRenderEnd();
    }
}
