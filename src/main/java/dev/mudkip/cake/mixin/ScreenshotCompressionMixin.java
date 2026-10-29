package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.ScreenshotCompressionModule;
import dev.mudkip.cake.screenshot.ScreenshotCompressionHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(value = Screenshot.class, priority = 2000)
public final class ScreenshotCompressionMixin {
    @Inject(
            method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cake$compressScreenshot(File workingDirectory, String forceName, RenderTarget target, int downscaleFactor, Consumer<Component> callback, CallbackInfo callbackInfo) {
        var module = ModuleManager.get().get(ScreenshotCompressionModule.class);

        if (module == null || !module.isEnabled()) {
            return;
        }

        ScreenshotCompressionHandler.grab(workingDirectory, forceName, target, downscaleFactor, module.createOptions(), callback);
        callbackInfo.cancel();
    }
}
