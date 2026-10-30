package dev.mudkip.cake.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;
import net.minecraft.util.Util;

import java.util.ArrayDeque;

public final class CpsModule extends HudModule {
    private static final long WINDOW_MILLIS = 1000L;

    private final ArrayDeque<Long> leftClicks;
    private final ArrayDeque<Long> rightClicks;

    public CpsModule(ModuleContext context) {
        super(ModuleDefinition.of("cps", ModuleCategory.MISC), context, Anchor.TOP_LEFT, 4.0D, 15.0D);
        this.leftClicks = new ArrayDeque<>();
        this.rightClicks = new ArrayDeque<>();
    }

    public void recordClick(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.addClick(this.leftClicks);
        } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.addClick(this.rightClicks);
        }
    }

    public int getLeftClicks() {
        return this.count(this.leftClicks);
    }

    public int getRightClicks() {
        return this.count(this.rightClicks);
    }

    @Override
    public String getText() {
        return "CPS: " + this.getLeftClicks() + " | " + this.getRightClicks();
    }

    private void addClick(ArrayDeque<Long> clicks) {
        var now = Util.getMillis();
        clicks.addLast(now);
        this.prune(clicks, now);
    }

    private int count(ArrayDeque<Long> clicks) {
        this.prune(clicks, Util.getMillis());
        return clicks.size();
    }

    private void prune(ArrayDeque<Long> clicks, long now) {
        var cutoff = now - CpsModule.WINDOW_MILLIS;

        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.removeFirst();
        }
    }
}
