package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.modules.ScreenshotCompressionModule;
import dev.mudkip.cake.screenshot.ScreenshotCompressionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(value = Screenshot.class, priority = 2000)
public abstract class ScreenshotCompressionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenshotCompressionMixin.class);

    @Inject(
            method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cake$compressScreenshot(File workingDirectory, String forceName, RenderTarget target, int downscaleFactor, Consumer<Component> callback, CallbackInfo callbackInfo) {
        var minecraft = Minecraft.getInstance();
        var module = ((MinecraftClientAccess) minecraft).cake$getActiveModule(ScreenshotCompressionModule.class);

        if (module == null) {
            return;
        }

        try {
            ScreenshotCompressionHandler.grab(minecraft, workingDirectory, forceName, target, downscaleFactor, module.createOptions(), callback);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to start compressed screenshot, falling back to vanilla", throwable);
            return;
        }

        callbackInfo.cancel();
    }
}
