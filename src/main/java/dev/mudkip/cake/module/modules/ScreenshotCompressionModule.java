package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.EnumSetting;
import dev.mudkip.cake.module.setting.IntSetting;
import dev.mudkip.cake.module.setting.StringSetting;
import dev.mudkip.compresso.core.v1.CompressionMode;
import dev.mudkip.compresso.core.v1.CompressionOptions;
import dev.mudkip.compresso.core.v1.ImageFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ScreenshotCompressionModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/screenshotcompression.png");

    private final EnumSetting<ImageFormat> format = this.setting(new EnumSetting<>(
            "format",
            Component.translatable("cake.setting.screenshotcompression.format.name"),
            Component.translatable("cake.setting.screenshotcompression.format.description"),
            ImageFormat.class,
            value -> Component.literal(value.getDisplayName()),
            ImageFormat.WEBP
    ));
    private final EnumSetting<CompressionMode> mode = this.setting(new EnumSetting<>(
            "mode",
            Component.translatable("cake.setting.screenshotcompression.mode.name"),
            Component.translatable("cake.setting.screenshotcompression.mode.description"),
            CompressionMode.class,
            ScreenshotCompressionModule::modeName,
            CompressionMode.LOSSLESS
    ));
    private final IntSetting quality = this.setting(new IntSetting(
            "quality",
            Component.translatable("cake.setting.screenshotcompression.quality.name"),
            Component.translatable("cake.setting.screenshotcompression.quality.description"),
            0,
            100,
            85,
            "%"
    ));
    private final BooleanSetting matchWindowResolution = this.setting(new BooleanSetting(
            "match_window",
            Component.translatable("cake.setting.screenshotcompression.match_window.name"),
            Component.translatable("cake.setting.screenshotcompression.match_window.description"),
            true
    ));
    private final IntSetting pngLevel = this.setting(new IntSetting(
            "png_level",
            Component.translatable("cake.setting.screenshotcompression.png_level.name"),
            Component.translatable("cake.setting.screenshotcompression.png_level.description"),
            0,
            9,
            9,
            ""
    ));
    private final IntSetting webpMethod = this.setting(new IntSetting(
            "webp_method",
            Component.translatable("cake.setting.screenshotcompression.webp_method.name"),
            Component.translatable("cake.setting.screenshotcompression.webp_method.description"),
            0,
            6,
            4,
            ""
    ));
    private final IntSetting avifSpeed = this.setting(new IntSetting(
            "avif_speed",
            Component.translatable("cake.setting.screenshotcompression.avif_speed.name"),
            Component.translatable("cake.setting.screenshotcompression.avif_speed.description"),
            0,
            10,
            6,
            ""
    ));
    private final IntSetting jxlEffort = this.setting(new IntSetting(
            "jxl_effort",
            Component.translatable("cake.setting.screenshotcompression.jxl_effort.name"),
            Component.translatable("cake.setting.screenshotcompression.jxl_effort.description"),
            1,
            10,
            7,
            ""
    ));
    private final BooleanSetting useExternalEncoders = this.setting(new BooleanSetting(
            "use_external",
            Component.translatable("cake.setting.screenshotcompression.use_external.name"),
            Component.translatable("cake.setting.screenshotcompression.use_external.description"),
            false
    ));
    private final BooleanSetting preferExternalWhenAvailable = this.setting(new BooleanSetting(
            "prefer_external",
            Component.translatable("cake.setting.screenshotcompression.prefer_external.name"),
            Component.translatable("cake.setting.screenshotcompression.prefer_external.description"),
            false
    ));
    private final StringSetting avifEncoder = this.setting(new StringSetting(
            "avif_encoder",
            Component.translatable("cake.setting.screenshotcompression.avif_encoder.name"),
            Component.translatable("cake.setting.screenshotcompression.avif_encoder.description"),
            "avifenc",
            256
    ));
    private final StringSetting webpEncoder = this.setting(new StringSetting(
            "webp_encoder",
            Component.translatable("cake.setting.screenshotcompression.webp_encoder.name"),
            Component.translatable("cake.setting.screenshotcompression.webp_encoder.description"),
            "cwebp",
            256
    ));
    private final StringSetting pngOptimizer = this.setting(new StringSetting(
            "png_optimizer",
            Component.translatable("cake.setting.screenshotcompression.png_optimizer.name"),
            Component.translatable("cake.setting.screenshotcompression.png_optimizer.description"),
            "oxipng",
            256
    ));
    private final StringSetting jpegEncoder = this.setting(new StringSetting(
            "jpeg_encoder",
            Component.translatable("cake.setting.screenshotcompression.jpeg_encoder.name"),
            Component.translatable("cake.setting.screenshotcompression.jpeg_encoder.description"),
            "cjpeg",
            256
    ));
    private final StringSetting jxlEncoder = this.setting(new StringSetting(
            "jxl_encoder",
            Component.translatable("cake.setting.screenshotcompression.jxl_encoder.name"),
            Component.translatable("cake.setting.screenshotcompression.jxl_encoder.description"),
            "cjxl",
            256
    ));

    public ScreenshotCompressionModule() {
        super(
                "screenshotcompression",
                Component.translatable("cake.module.screenshotcompression.name"),
                Component.translatable("cake.module.screenshotcompression.description"),
                ModuleCategory.MISC,
                ScreenshotCompressionModule.ICON
        );
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
