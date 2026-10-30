package dev.mudkip.cake.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.mudkip.compresso.core.v1.CompressionOptions;
import dev.mudkip.compresso.core.v1.ScreenshotWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;

public final class ScreenshotCompressionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenshotCompressionHandler.class);

    private ScreenshotCompressionHandler() {
    }

    public static void grab(Minecraft client, File workingDirectory, @Nullable String forceName, RenderTarget target, int downscaleFactor, CompressionOptions options, Consumer<Component> callback) {
        Screenshot.takeScreenshot(target, downscaleFactor, nativeImage -> {
            BufferedImage image;

            try (nativeImage) {
                image = ScreenshotCompressionHandler.toBufferedImage(nativeImage);
            }

            var window = client.getWindow();
            var screenWidth = window.getScreenWidth();
            var screenHeight = window.getScreenHeight();
            Util.ioPool().execute(() -> ScreenshotCompressionHandler.process(workingDirectory, forceName, image, screenWidth, screenHeight, options, callback));
        });
    }

    private static void process(File workingDirectory, @Nullable String forceName, BufferedImage source, int screenWidth, int screenHeight, CompressionOptions options, Consumer<Component> callback) {
        try {
            var saved = ScreenshotWriter.write(source, workingDirectory.toPath(), forceName, screenWidth, screenHeight, Util.getFilenameFormattedDateTime(), options);

            if (saved.fallbackCause() != null) {
                LOGGER.warn("Screenshot encoder failed for {}, falling back to PNG", options.format(), saved.fallbackCause());
            }

            LOGGER.info("Saved screenshot {} ({} bytes, {})", saved.path().getFileName(), saved.bytes(), saved.backend());
            callback.accept(ScreenshotCompressionHandler.savedMessage(saved.path().toFile(), saved.bytes()));
        } catch (Throwable throwable) {
            LOGGER.error("Failed to save compressed screenshot", throwable);
            callback.accept(Component.translatable("screenshot.failure", String.valueOf(throwable.getMessage())));
        }
    }

    private static BufferedImage toBufferedImage(NativeImage nativeImage) {
        var width = nativeImage.getWidth();
        var height = nativeImage.getHeight();
        var pixels = nativeImage.getPixels();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static Component savedMessage(File file, long fileBytes) {
        var link = Component.literal(file.getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.getAbsoluteFile())));

        return Component.translatable("screenshot.success", link)
                .append(Component.literal(" (" + ScreenshotCompressionHandler.formatBytes(fileBytes) + ")").withStyle(ChatFormatting.GRAY));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0D);
        }

        return String.format(Locale.ROOT, "%.2f MiB", bytes / (1024.0D * 1024.0D));
    }
}
