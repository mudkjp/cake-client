package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PingModule extends HudModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/ping.png");

    public PingModule() {
        super(
                "ping",
                Component.translatable("cake.module.ping.name"),
                Component.translatable("cake.module.ping.description"),
                ModuleCategory.MISC,
                PingModule.ICON,
                Anchor.TOP_LEFT,
                4.0D,
                26.0D
        );
    }

    @Override
    public String getText() {
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();

        if (connection == null || minecraft.player == null) {
            return "Ping: --";
        }

        var playerInfo = connection.getPlayerInfo(minecraft.player.getUUID());
        return playerInfo == null ? "Ping: --" : "Ping: " + playerInfo.getLatency() + " ms";
    }
}
