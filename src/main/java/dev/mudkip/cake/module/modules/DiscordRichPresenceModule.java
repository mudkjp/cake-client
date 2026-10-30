package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.discord.DiscordRpc;
import dev.mudkip.cake.discord.ServerMappings;
import dev.mudkip.cake.discord.ServerMappings.Mapping;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.server.BedwarsPracticeIntegration;
import dev.mudkip.cake.server.Locraw;
import fun.crashsystem.jdrpc.activity.Activity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class DiscordRichPresenceModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/discordrichpresence.png");
    private static final long APPLICATION_ID = 1565545232320299088L;
    private static final String LARGE_IMAGE = "cake";
    private static final String LARGE_IMAGE_TEXT = "Cake Client";
    private static final int MAX_SERVER_NAME_LENGTH = 116;

    private final BooleanSetting showServer;
    private final DiscordRpc rpc;
    private long startTime;

    public DiscordRichPresenceModule() {
        super(
                "discordrichpresence",
                Component.translatable("cake.module.discordrichpresence.name"),
                Component.translatable("cake.module.discordrichpresence.description"),
                ModuleCategory.MISC,
                DiscordRichPresenceModule.ICON
        );
        this.showServer = this.setting(new BooleanSetting(
                "show_server",
                Component.translatable("cake.setting.discordrichpresence.show_server.name"),
                Component.translatable("cake.setting.discordrichpresence.show_server.description"),
                false
        ));
        this.rpc = new DiscordRpc(DiscordRichPresenceModule.APPLICATION_ID);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    @Override
    public void onEnable() {
        this.startTime = System.currentTimeMillis() / 1000L;
        Locraw.reset();
        this.rpc.start();
    }

    @Override
    public void onDisable() {
        Locraw.reset();
        this.rpc.stop();
    }

    private void onClientTick(Minecraft client) {
        if (!this.isEnabled()) {
            return;
        }

        Locraw.tick(client, this.showServer.getValue());

        if (this.showServer.getValue()) {
            ServerMappings.ensureLoaded();
        }

        this.rpc.update(this.createActivity(client));
    }

    private Activity createActivity(Minecraft client) {
        var server = this.getServerContext(client);
        var game = server == null ? null : Locraw.getGame();
        var builder = new Activity.Builder()
                .setDetails(this.getDetails(client, server))
                .setState(this.getState(server, game))
                .setStartTimestamp(this.startTime)
                .setLargeImage(DiscordRichPresenceModule.LARGE_IMAGE, this.getLargeImageText(server));

        if (server != null && server.mapping() != null && server.mapping().logo() != null) {
            builder.setSmallImage(server.mapping().logo(), server.address());
        }

        return builder.build();
    }

    private @Nullable ServerContext getServerContext(Minecraft client) {
        if (!this.showServer.getValue() || client.level == null || client.hasSingleplayerServer()) {
            return null;
        }

        var server = client.getCurrentServer();

        if (server == null || server.isRealm() || server.ip == null || server.ip.isBlank()) {
            return null;
        }

        var mapping = ServerMappings.lookup(server.ip);
        var name = mapping != null
                ? mapping.name()
                : BedwarsPracticeIntegration.INSTANCE.isCurrent() ? BedwarsPracticeIntegration.INSTANCE.getName() : null;

        if (name == null) {
            return null;
        }

        return new ServerContext(name, server.ip, mapping);
    }

    private String getDetails(Minecraft client, @Nullable ServerContext server) {
        if (client.level == null) {
            return "In the menus";
        }

        if (client.hasSingleplayerServer()) {
            return "Playing Singleplayer";
        }

        var current = client.getCurrentServer();

        if (current == null) {
            return "Playing Multiplayer";
        }

        if (current.isRealm()) {
            return "Playing on Realms";
        }

        if (server == null) {
            return "Playing Multiplayer";
        }

        var name = server.name();

        if (name.codePointCount(0, name.length()) > DiscordRichPresenceModule.MAX_SERVER_NAME_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, DiscordRichPresenceModule.MAX_SERVER_NAME_LENGTH));
        }

        return "Playing on " + name;
    }

    private @Nullable String getState(@Nullable ServerContext server, @Nullable String game) {
        if (server == null) {
            return "Minecraft " + SharedConstants.getCurrentVersion().name();
        }

        return game;
    }

    private String getLargeImageText(@Nullable ServerContext server) {
        return server == null
                ? DiscordRichPresenceModule.LARGE_IMAGE_TEXT
                : "Minecraft " + SharedConstants.getCurrentVersion().name();
    }

    private record ServerContext(String name, String address, @Nullable Mapping mapping) {

    }
}
