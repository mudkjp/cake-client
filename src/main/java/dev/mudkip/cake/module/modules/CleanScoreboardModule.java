package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;

public final class CleanScoreboardModule extends ClientModule {
    public CleanScoreboardModule(ModuleContext context) {
        super(ModuleDefinition.of("cleanscoreboard", ModuleCategory.RENDER), context);
    }
}
