package dev.mudkip.cake.module;

public final class FullBrightModule extends ClientModule {
    public FullBrightModule(ModuleContext context) {
        super(ModuleDefinition.of("fullbright", ModuleCategory.RENDER), context);
    }
}
