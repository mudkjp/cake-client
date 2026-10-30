package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;

public final class FullBrightModule extends ClientModule {
    public FullBrightModule(ModuleContext context) {
        super(ModuleDefinition.of("fullbright", ModuleCategory.RENDER), context);
    }
}
