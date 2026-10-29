package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FullBrightModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/fullbright.png");

    public FullBrightModule() {
        super(
                "fullbright",
                Component.translatable("cake.module.fullbright.name"),
                Component.translatable("cake.module.fullbright.description"),
                ModuleCategory.RENDER,
                FullBrightModule.ICON
        );
    }
}
