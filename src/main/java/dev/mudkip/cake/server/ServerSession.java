package dev.mudkip.cake.server;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ServerSession {
    private static volatile @Nullable String address;

    private ServerSession() {

    }

    public static void update(Minecraft client) {
        var current = ServerSession.resolve(client);
        ServerSession.address = current == null ? null : ServerIntegration.normalize(current);
    }

    public static @Nullable String getAddress() {
        return ServerSession.address;
    }

    static boolean isOn(ServerIntegration integration) {
        var current = ServerSession.address;
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
