package dev.mudkip.cake.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public final class ZoomModule extends ClientModule {
    private static final double BASE_ZOOM = 4.0D;
    private static final double ZOOM_PER_STEP = 1.5D;
    private static final double MAX_ZOOM = 500.0D;
    private static final int MAX_SCROLL_STEPS = 10;
    private static final double ZOOM_IN_SECONDS = 0.3D;
    private static final double ZOOM_OUT_SECONDS = 0.2D;
    private static final double SCROLL_EASE_SPEED = 24.0D;
    private static final double MAX_FRAME_SECONDS = 0.1D;
    private static final double LOG2_1023 = Math.log(1023.0D) / Math.log(2.0D);

    private int scrollSteps;
    private double scrollTier;
    private double progress;
    private long lastFrameNanos;
    private boolean zoomingIn;

    public ZoomModule(ModuleContext context) {
        super(
                ModuleDefinition.of("zoom", ModuleCategory.RENDER)
                        .withKey(InputConstants.Type.KEYBOARD, InputConstants.KEY_C),
                context
        );
    }

    private static double easeOutExponential(double value) {
        if (value <= 0.0D) {
            return 0.0D;
        }

        if (value >= 1.0D) {
            return 1.0D;
        }

        return 1.0D - Math.pow(2.0D, 10.0D - ZoomModule.LOG2_1023 - 10.0D * value) + 1.0D / 1023.0D;
    }

    private static double easeOutExponentialInverse(double value) {
        if (value <= 0.0D) {
            return 0.0D;
        }

        if (value >= 1.0D) {
            return 1.0D;
        }

        return 1.0D - ZoomModule.LOG2_1023 / 10.0D
                - Math.log(1024.0D / 1023.0D - value) / (10.0D * Math.log(2.0D));
    }

    private static double easeInExponential(double value) {
        return 1.0D - ZoomModule.easeOutExponential(1.0D - value);
    }

    private static double easeInExponentialInverse(double value) {
        return 1.0D - ZoomModule.easeOutExponentialInverse(1.0D - value);
    }

    public void adjustZoom(double scrollAmount) {
        if (scrollAmount == 0.0D) {
            return;
        }

        var direction = scrollAmount > 0.0D ? 1 : -1;
        this.scrollSteps = Mth.clamp(this.scrollSteps + direction, 0, ZoomModule.MAX_SCROLL_STEPS);
    }

    public float applyZoom(float fov) {
        var now = Util.getNanos();
        var deltaSeconds = this.lastFrameNanos == 0L
                ? 0.0D
                : Math.min((now - this.lastFrameNanos) / 1_000_000_000.0D, ZoomModule.MAX_FRAME_SECONDS);
        this.lastFrameNanos = now;
        var zooming = this.isEnabled() && this.client.level != null && this.client.player != null;

        if (zooming != this.zoomingIn) {
            var visualProgress = this.zoomingIn
                    ? ZoomModule.easeOutExponential(this.progress)
                    : ZoomModule.easeInExponential(this.progress);
            this.progress = zooming
                    ? ZoomModule.easeOutExponentialInverse(visualProgress)
                    : ZoomModule.easeInExponentialInverse(visualProgress);
            this.zoomingIn = zooming;
        }

        var target = zooming ? 1.0D : 0.0D;
        var duration = target > this.progress ? ZoomModule.ZOOM_IN_SECONDS : ZoomModule.ZOOM_OUT_SECONDS;
        var progressStep = deltaSeconds / duration;
        this.progress = target > this.progress
                ? Math.min(this.progress + progressStep, target)
                : Math.max(this.progress - progressStep, target);

        if (!zooming && this.progress <= 0.0D) {
            this.scrollSteps = 0;
        }

        var scrollEase = 1.0D - Math.exp(-deltaSeconds * ZoomModule.SCROLL_EASE_SPEED);
        this.scrollTier += (this.scrollSteps - this.scrollTier) * scrollEase;

        if (Math.abs(this.scrollSteps - this.scrollTier) < 0.001D) {
            this.scrollTier = this.scrollSteps;
        }

        var divisor = Mth.clamp(
                ZoomModule.BASE_ZOOM * Math.pow(ZoomModule.ZOOM_PER_STEP, this.scrollTier),
                ZoomModule.BASE_ZOOM,
                ZoomModule.MAX_ZOOM
        );
        var eased = zooming
                ? ZoomModule.easeOutExponential(this.progress)
                : ZoomModule.easeInExponential(this.progress);
        var multiplier = Mth.lerp(eased, 1.0D, 1.0D / divisor);
        return (float) (fov * multiplier);
    }
}
