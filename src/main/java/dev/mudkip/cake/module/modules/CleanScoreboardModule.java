package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CleanScoreboardModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/cleanscoreboard.png");

    public CleanScoreboardModule() {
        super(
                "cleanscoreboard",
                Component.translatable("cake.module.cleanscoreboard.name"),
                Component.translatable("cake.module.cleanscoreboard.description"),
                ModuleCategory.RENDER,
                CleanScoreboardModule.ICON
        );
    }
}
