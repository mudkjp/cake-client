package dev.mudkip.cake.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.module.ModuleCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayDeque;

public final class CpsModule extends HudModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/cps.png");
    private static final long WINDOW_MILLIS = 1000L;
    private static final ArrayDeque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT_CLICKS = new ArrayDeque<>();

    public CpsModule() {
        super(
                "cps",
                Component.translatable("cake.module.cps.name"),
                Component.translatable("cake.module.cps.description"),
                ModuleCategory.MISC,
                CpsModule.ICON,
                Anchor.TOP_LEFT,
                4.0D,
                15.0D
        );
    }

    public static void recordClick(int button) {
        var clicks = switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> CpsModule.LEFT_CLICKS;
            case InputConstants.MOUSE_BUTTON_RIGHT -> CpsModule.RIGHT_CLICKS;
            default -> null;
        };

        if (clicks != null) {
            var now = Util.getMillis();
            clicks.addLast(now);
            CpsModule.prune(clicks, now);
        }
    }

    public static int getLeftClicks() {
        return CpsModule.count(CpsModule.LEFT_CLICKS);
    }

    public static int getRightClicks() {
        return CpsModule.count(CpsModule.RIGHT_CLICKS);
    }

    private static int count(ArrayDeque<Long> clicks) {
        CpsModule.prune(clicks, Util.getMillis());
        return clicks.size();
    }

    private static void prune(ArrayDeque<Long> clicks, long now) {
        var cutoff = now - CpsModule.WINDOW_MILLIS;

        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.removeFirst();
        }
    }

    @Override
    public String getText() {
        return "CPS: " + CpsModule.getLeftClicks() + " | " + CpsModule.getRightClicks();
    }
}
