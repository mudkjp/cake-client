package dev.mudkip.cake.module;

public abstract class HudModule extends ClientModule {
    protected HudModule(ModuleDefinition definition, ModuleContext context) {
        super(definition, context);
    }

    public abstract String getText();
}
