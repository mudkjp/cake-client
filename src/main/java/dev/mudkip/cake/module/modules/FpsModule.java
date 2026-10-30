package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;

public final class FpsModule extends HudModule {
    public FpsModule(ModuleContext context) {
        super(ModuleDefinition.of("fps", ModuleCategory.MISC), context, Anchor.TOP_LEFT, 4.0D, 4.0D);
    }

    @Override
    public String getText() {
        return "FPS: " + this.client.getFps();
    }
}
