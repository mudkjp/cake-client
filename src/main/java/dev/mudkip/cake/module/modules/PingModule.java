package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;

public final class PingModule extends HudModule {
    public PingModule(ModuleContext context) {
        super(ModuleDefinition.of("ping", ModuleCategory.MISC), context, Anchor.TOP_LEFT, 4.0D, 26.0D);
    }

    @Override
    public String getText() {
        var connection = this.client.getConnection();

        if (connection == null || this.client.player == null) {
            return "Ping: --";
        }

        var playerInfo = connection.getPlayerInfo(this.client.player.getUUID());
        return playerInfo == null ? "Ping: --" : "Ping: " + playerInfo.getLatency() + " ms";
    }
}
