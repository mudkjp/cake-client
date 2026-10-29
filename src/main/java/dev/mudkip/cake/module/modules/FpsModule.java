package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FpsModule extends HudModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/fps.png");

    public FpsModule() {
        super(
                "fps",
                Component.translatable("cake.module.fps.name"),
                Component.translatable("cake.module.fps.description"),
                ModuleCategory.MISC,
                FpsModule.ICON,
                Anchor.TOP_LEFT,
                4.0D,
                4.0D
        );
    }

    @Override
    public String getText() {
        return "FPS: " + Minecraft.getInstance().getFps();
    }
}
