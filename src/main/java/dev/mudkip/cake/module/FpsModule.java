package dev.mudkip.cake.module;

public final class FpsModule extends HudModule {
    public FpsModule(ModuleContext context) {
        super(ModuleDefinition.of("fps", ModuleCategory.MISC), context);
    }

    @Override
    public String getText() {
        return "FPS: " + this.client.getFps();
    }
}
