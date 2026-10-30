package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.ColorSetting;
import dev.mudkip.cake.module.setting.DoubleSetting;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public final class BlockOutlineModule extends ClientModule {
    private static final double CHROMA_CYCLE_SECONDS = 5.0D;
    private static final float MIN_CHROMA_SATURATION = 0.5F;

    private final BooleanSetting border;
    private final ColorSetting borderColor;
    private final DoubleSetting borderThickness;
    private final BooleanSetting overlay;
    private final ColorSetting overlayColor;
    private final BooleanSetting chroma;
    private final DoubleSetting chromaSpeed;

    public BlockOutlineModule(ModuleContext context) {
        super(ModuleDefinition.of("blockoutline", ModuleCategory.RENDER), context);
        this.border = this.booleanSetting("border", true);
        this.borderColor = this.colorSetting("border_color", 0xFF6F9FD8);
        this.borderThickness = this.doubleSetting("border_thickness", 0.5D, 6.0D, 0.5D, 1.5D, "x");
        this.overlay = this.booleanSetting("overlay", true);
        this.overlayColor = this.colorSetting("overlay_color", 0x406F9FD8);
        this.chroma = this.booleanSetting("chroma", false);
        this.chromaSpeed = this.doubleSetting("chroma_speed", 0.1D, 3.0D, 0.1D, 1.0D, "x");
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
