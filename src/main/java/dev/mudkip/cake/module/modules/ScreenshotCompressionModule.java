package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.EnumSetting;
import dev.mudkip.cake.module.setting.IntSetting;
import dev.mudkip.cake.module.setting.StringSetting;
import dev.mudkip.compresso.core.v1.CompressionMode;
import dev.mudkip.compresso.core.v1.CompressionOptions;
import dev.mudkip.compresso.core.v1.ImageFormat;
import net.minecraft.network.chat.Component;

public final class ScreenshotCompressionModule extends ClientModule {
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

    private static Component modeName(CompressionMode mode) {
        return Component.translatable("cake.setting.screenshotcompression.mode." + mode.getId());
    }
}
