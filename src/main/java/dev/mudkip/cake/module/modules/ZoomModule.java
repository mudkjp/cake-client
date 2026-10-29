package dev.mudkip.cake.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class ZoomModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/zoom.png");
    private static final double EASE_SPEED = 12.0D;
    private static final double SCROLL_STEP = 0.25D;
    private static final double MIN_ZOOM = 1.5D;
    private static final double MAX_ZOOM = 8.0D;

    private final DoubleSetting zoom;
    private double currentFovFactor;
    private long lastUpdateNanos;

    public ZoomModule() {
        super(
                "zoom",
                Component.translatable("cake.module.zoom.name"),
                Component.translatable("cake.module.zoom.description"),
                ModuleCategory.RENDER,
                ZoomModule.ICON,
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_C
        );
        this.zoom = this.setting(new DoubleSetting(
                "zoom",
                Component.translatable("cake.setting.zoom.zoom.name"),
                Component.translatable("cake.setting.zoom.zoom.description"),
                ZoomModule.MIN_ZOOM,
                ZoomModule.MAX_ZOOM,
                0.1D,
                3.0D,
                "x"
        ));
        this.currentFovFactor = 1.0D;
    }

    public double getZoom() {
        return this.zoom.getValue();
    }

    public void adjustZoom(double scrollAmount) {
        if (scrollAmount == 0.0D) {
            return;
        }

        this.zoom.setValue(this.zoom.getValue() + scrollAmount * ZoomModule.SCROLL_STEP);
    }

    public float applyZoom(float fov) {
        var minecraft = Minecraft.getInstance();
        var active = this.isEnabled() && minecraft.level != null && minecraft.player != null;
        var now = Util.getNanos();
        var target = active ? 1.0D / this.zoom.getValue() : 1.0D;

        if (this.lastUpdateNanos == 0L) {
            this.currentFovFactor = target;
        } else {
            var deltaSeconds = Math.min((now - this.lastUpdateNanos) / 1_000_000_000.0D, 0.1D);
            var factor = 1.0D - Math.exp(-deltaSeconds * ZoomModule.EASE_SPEED);
            this.currentFovFactor += (target - this.currentFovFactor) * factor;

            if (Math.abs(target - this.currentFovFactor) < 0.0005D) {
                this.currentFovFactor = target;
            }
        }

        this.lastUpdateNanos = now;
        return (float) (fov * this.currentFovFactor);
    }
}
