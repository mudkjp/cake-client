package dev.mudkip.cake.module;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.EnumSetting;
import dev.mudkip.cake.module.setting.IntSetting;
import dev.mudkip.cake.module.setting.StringSetting;
import dev.mudkip.compresso.core.v1.CompressionMode;
import dev.mudkip.compresso.core.v1.CompressionOptions;
import dev.mudkip.compresso.core.v1.ImageFormat;
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

public final class ScreenshotCompressionModule extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenshotCompressionModule.class);

    private final EnumSetting<ImageFormat> format;
    private final EnumSetting<CompressionMode> mode;
    private final IntSetting quality;
    private final BooleanSetting matchWindowResolution;
    private final IntSetting pngLevel;
    private final IntSetting webpMethod;
    private final IntSetting avifSpeed;
    private final IntSetting jxlEffort;
    private final BooleanSetting useExternalEncoders;
    private final BooleanSetting preferExternalWhenAvailable;
    private final StringSetting avifEncoder;
    private final StringSetting webpEncoder;
    private final StringSetting pngOptimizer;
    private final StringSetting jpegEncoder;
    private final StringSetting jxlEncoder;

    public ScreenshotCompressionModule(ModuleContext context) {
        super(ModuleDefinition.of("screenshotcompression", ModuleCategory.MISC), context);
        this.format = this.enumSetting(
                "format",
                ImageFormat.class,
                value -> Component.literal(value.getDisplayName()),
                ImageFormat.WEBP
        );
        this.mode = this.enumSetting(
                "mode",
                CompressionMode.class,
                ScreenshotCompressionModule::modeName,
                CompressionMode.LOSSLESS
        );
        this.quality = this.intSetting("quality", 0, 100, 85, "%");
        this.matchWindowResolution = this.booleanSetting("match_window", true);
        this.pngLevel = this.intSetting("png_level", 0, 9, 9, "");
        this.webpMethod = this.intSetting("webp_method", 0, 6, 4, "");
        this.avifSpeed = this.intSetting("avif_speed", 0, 10, 6, "");
        this.jxlEffort = this.intSetting("jxl_effort", 1, 10, 7, "");
        this.useExternalEncoders = this.booleanSetting("use_external", false);
        this.preferExternalWhenAvailable = this.booleanSetting("prefer_external", false);
        this.avifEncoder = this.stringSetting("avif_encoder", "avifenc", 256);
        this.webpEncoder = this.stringSetting("webp_encoder", "cwebp", 256);
        this.pngOptimizer = this.stringSetting("png_optimizer", "oxipng", 256);
        this.jpegEncoder = this.stringSetting("jpeg_encoder", "cjpeg", 256);
        this.jxlEncoder = this.stringSetting("jxl_encoder", "cjxl", 256);
    }

    public static void grab(Minecraft client, File workingDirectory, @Nullable String forceName, RenderTarget target, int downscaleFactor, CompressionOptions options, Consumer<Component> callback) {
        Screenshot.takeScreenshot(target, downscaleFactor, nativeImage -> {
            BufferedImage image;

            try (nativeImage) {
                image = ScreenshotCompressionModule.toBufferedImage(nativeImage);
            }

            var window = client.getWindow();
            var screenWidth = window.getScreenWidth();
            var screenHeight = window.getScreenHeight();
            Util.ioPool().execute(() -> ScreenshotCompressionModule.process(workingDirectory, forceName, image, screenWidth, screenHeight, options, callback));
        });
    }

    public CompressionOptions createOptions() {
        return new CompressionOptions(
                this.format.getValue(),
                this.mode.getValue(),
                this.quality.getValue(),
                this.matchWindowResolution.getValue(),
                this.pngLevel.getValue(),
                this.webpMethod.getValue(),
                this.avifSpeed.getValue(),
                this.jxlEffort.getValue(),
                this.useExternalEncoders.getValue(),
                this.preferExternalWhenAvailable.getValue(),
                this.avifEncoder.getValue(),
                this.webpEncoder.getValue(),
                this.pngOptimizer.getValue(),
                this.jpegEncoder.getValue(),
                this.jxlEncoder.getValue()
        );
    }

    private static void process(File workingDirectory, @Nullable String forceName, BufferedImage source, int screenWidth, int screenHeight, CompressionOptions options, Consumer<Component> callback) {
        try {
            var saved = ScreenshotWriter.write(source, workingDirectory.toPath(), forceName, screenWidth, screenHeight, Util.getFilenameFormattedDateTime(), options);

            if (saved.fallbackCause() != null) {
                ScreenshotCompressionModule.LOGGER.warn("Screenshot encoder failed for {}, falling back to PNG", options.format(), saved.fallbackCause());
            }

            ScreenshotCompressionModule.LOGGER.info("Saved screenshot {} ({} bytes, {})", saved.path().getFileName(), saved.bytes(), saved.backend());
            callback.accept(ScreenshotCompressionModule.savedMessage(saved.path().toFile(), saved.bytes()));
        } catch (Throwable throwable) {
            ScreenshotCompressionModule.LOGGER.error("Failed to save compressed screenshot", throwable);
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
                .append(Component.literal(" (" + ScreenshotCompressionModule.formatBytes(fileBytes) + ")").withStyle(ChatFormatting.GRAY));
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

    private static Component modeName(CompressionMode mode) {
        return Component.translatable("cake.setting.screenshotcompression.mode." + mode.getId());
    }
}
