package dev.mudkip.cake.server;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ServerSession {
    private volatile @Nullable String address;

    public void update(Minecraft client) {
        var current = ServerSession.resolve(client);
        this.address = current == null ? null : ServerAddresses.normalize(current);
    }

    public @Nullable String getAddress() {
        return this.address;
    }

    public boolean isOn(ServerIntegration integration) {
        var current = this.address;
        return current != null && integration.matches(current);
    }

    private static @Nullable String resolve(Minecraft client) {
        if (client.level == null || client.player == null || client.hasSingleplayerServer()) {
            return null;
        }

        var server = client.getCurrentServer();

        if (server == null || server.isRealm() || server.ip == null || server.ip.isBlank()) {
            return null;
        }

        return server.ip;
    }
}
