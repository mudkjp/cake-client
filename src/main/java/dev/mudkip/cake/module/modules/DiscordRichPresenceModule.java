package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.discord.DiscordRpc;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.server.Locraw;
import dev.mudkip.cake.server.ServerIntegrations;
import dev.mudkip.cake.server.ServerMappings;
import dev.mudkip.cake.server.ServerMappings.Mapping;
import fun.crashsystem.jdrpc.activity.Activity;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;

public final class DiscordRichPresenceModule extends ClientModule {
    private static final long APPLICATION_ID = 1565545232320299088L;
    private static final String LARGE_IMAGE = "cake";
    private static final String LARGE_IMAGE_TEXT = "Cake Client";
    private static final int MAX_SERVER_NAME_LENGTH = 116;

    private final BooleanSetting showServer;
    private final DiscordRpc rpc;
    private final Locraw locraw;
    private final ServerMappings serverMappings;
    private long startTime;

    public DiscordRichPresenceModule(ModuleContext context) {
        super(ModuleDefinition.of("discordrichpresence", ModuleCategory.MISC), context);
        this.showServer = this.booleanSetting("show_server", false);
        this.rpc = new DiscordRpc(DiscordRichPresenceModule.APPLICATION_ID);
        this.locraw = new Locraw(context.client(), context.serverSession(), ServerIntegrations.BEDWARS_PRACTICE);
        this.locraw.register();
        this.serverMappings = context.serverMappings();
    }

    @Override
    protected void onEnable() {
        this.startTime = System.currentTimeMillis() / 1000L;
        this.locraw.reset();
        this.rpc.start();
    }

    @Override
    protected void onDisable() {
        this.locraw.reset();
        this.rpc.stop();
    }

    @Override
    protected void onTick() {
        this.locraw.tick(this.showServer.getValue());

        if (this.showServer.getValue()) {
            this.serverMappings.ensureLoaded();
        }

        this.rpc.update(this.createActivity());
    }

    private Activity createActivity() {
        var server = this.getServerContext();
        var game = server == null ? null : this.locraw.getGame();
        var builder = new Activity.Builder()
                .setDetails(this.getDetails(server))
                .setState(this.getState(server, game))
                .setStartTimestamp(this.startTime)
                .setLargeImage(DiscordRichPresenceModule.LARGE_IMAGE, this.getLargeImageText(server));

        if (server != null && server.mapping() != null && server.mapping().logo() != null) {
            builder.setSmallImage(server.mapping().logo(), server.address());
        }

        return builder.build();
    }

    private @Nullable ServerContext getServerContext() {
        if (!this.showServer.getValue() || this.client.level == null || this.client.hasSingleplayerServer()) {
            return null;
        }

        var server = this.client.getCurrentServer();

        if (server == null || server.isRealm() || server.ip == null || server.ip.isBlank()) {
            return null;
        }

        var mapping = this.serverMappings.lookup(server.ip);
        var name = mapping != null
                ? mapping.name()
                : this.serverSession.isOn(ServerIntegrations.BEDWARS_PRACTICE)
                        ? ServerIntegrations.BEDWARS_PRACTICE.name()
                        : null;

        if (name == null) {
            return null;
        }

        return new ServerContext(name, server.ip, mapping);
    }

    private String getDetails(@Nullable ServerContext server) {
        if (this.client.level == null) {
            return "In the menus";
        }

        if (this.client.hasSingleplayerServer()) {
            return "Playing Singleplayer";
        }

        var current = this.client.getCurrentServer();

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
