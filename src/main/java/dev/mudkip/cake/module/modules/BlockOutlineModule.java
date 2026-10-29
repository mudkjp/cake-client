package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.ColorSetting;
import dev.mudkip.cake.module.setting.DoubleSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public final class BlockOutlineModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/blockoutline.png");
    private static final double CHROMA_CYCLE_SECONDS = 5.0D;
    private static final float MIN_CHROMA_SATURATION = 0.5F;

    private final BooleanSetting border;
    private final ColorSetting borderColor;
    private final DoubleSetting borderThickness;
    private final BooleanSetting overlay;
    private final ColorSetting overlayColor;
    private final BooleanSetting chroma;
    private final DoubleSetting chromaSpeed;

    public BlockOutlineModule() {
        super(
                "blockoutline",
                Component.translatable("cake.module.blockoutline.name"),
                Component.translatable("cake.module.blockoutline.description"),
                ModuleCategory.RENDER,
                BlockOutlineModule.ICON
        );
        this.border = this.setting(new BooleanSetting(
                "border",
                Component.translatable("cake.setting.blockoutline.border.name"),
                Component.translatable("cake.setting.blockoutline.border.description"),
                true
        ));
        this.borderColor = this.setting(new ColorSetting(
                "border_color",
                Component.translatable("cake.setting.blockoutline.border_color.name"),
                Component.translatable("cake.setting.blockoutline.border_color.description"),
                0xFF6F9FD8
        ));
        this.borderThickness = this.setting(new DoubleSetting(
                "border_thickness",
                Component.translatable("cake.setting.blockoutline.border_thickness.name"),
                Component.translatable("cake.setting.blockoutline.border_thickness.description"),
                0.5D,
                6.0D,
                0.5D,
                1.5D,
                "x"
        ));
        this.overlay = this.setting(new BooleanSetting(
                "overlay",
                Component.translatable("cake.setting.blockoutline.overlay.name"),
                Component.translatable("cake.setting.blockoutline.overlay.description"),
                true
        ));
        this.overlayColor = this.setting(new ColorSetting(
                "overlay_color",
                Component.translatable("cake.setting.blockoutline.overlay_color.name"),
                Component.translatable("cake.setting.blockoutline.overlay_color.description"),
                0x406F9FD8
        ));
        this.chroma = this.setting(new BooleanSetting(
                "chroma",
                Component.translatable("cake.setting.blockoutline.chroma.name"),
                Component.translatable("cake.setting.blockoutline.chroma.description"),
                false
        ));
        this.chromaSpeed = this.setting(new DoubleSetting(
                "chroma_speed",
                Component.translatable("cake.setting.blockoutline.chroma_speed.name"),
                Component.translatable("cake.setting.blockoutline.chroma_speed.description"),
                0.1D,
                3.0D,
                0.1D,
                1.0D,
                "x"
        ));
    }

    public boolean isBorderEnabled() {
        return this.border.getValue();
    }

    public int getBorderColor() {
        return this.applyChroma(this.borderColor.getValue());
    }

    public float getBorderThickness() {
        return this.borderThickness.getValue().floatValue();
    }

    public boolean isOverlayEnabled() {
        return this.overlay.getValue();
    }

    public int getOverlayColor() {
        return this.applyChroma(this.overlayColor.getValue());
    }

    private int applyChroma(int color) {
        if (!this.chroma.getValue()) {
            return color;
        }

        var seconds = Util.getMillis() / 1000.0D;
        var offset = (float) ((seconds * this.chromaSpeed.getValue() / BlockOutlineModule.CHROMA_CYCLE_SECONDS) % 1.0D);
        var red = ARGB.red(color) / 255.0F;
        var green = ARGB.green(color) / 255.0F;
        var blue = ARGB.blue(color) / 255.0F;
        var max = Math.max(red, Math.max(green, blue));
        var min = Math.min(red, Math.min(green, blue));
        var delta = max - min;
        var hue = 0.0F;

        if (delta > 0.0F) {
            if (max == red) {
                hue = ((green - blue) / delta) % 6.0F;
            } else if (max == green) {
                hue = (blue - red) / delta + 2.0F;
            } else {
                hue = (red - green) / delta + 4.0F;
            }

            hue = hue < 0.0F ? hue / 6.0F + 1.0F : hue / 6.0F;
        }

        var saturation = Math.max(max == 0.0F ? 0.0F : delta / max, BlockOutlineModule.MIN_CHROMA_SATURATION);
        return Mth.hsvToArgb((hue + offset) % 1.0F, saturation, max, ARGB.alpha(color));
    }
}
