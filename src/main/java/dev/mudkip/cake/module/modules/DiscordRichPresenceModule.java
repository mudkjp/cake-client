package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.discord.DiscordRpc;
import dev.mudkip.cake.discord.ServerMappings;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.setting.BooleanSetting;
import fun.crashsystem.jdrpc.activity.Activity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
        this.rpc.start();
    }

    @Override
    public void onDisable() {
        this.rpc.stop();
    }

    private void onClientTick(Minecraft client) {
        if (!this.isEnabled()) {
            return;
        }

        if (this.showServer.getValue()) {
            ServerMappings.ensureLoaded();
        }

        this.rpc.update(this.createActivity(client));
    }

    private Activity createActivity(Minecraft client) {
        return new Activity.Builder()
                .setDetails(this.getDetails(client))
                .setState("Minecraft " + SharedConstants.getCurrentVersion().name())
                .setStartTimestamp(this.startTime)
                .setLargeImage(DiscordRichPresenceModule.LARGE_IMAGE, DiscordRichPresenceModule.LARGE_IMAGE_TEXT)
                .build();
    }

    private String getDetails(Minecraft client) {
        if (client.level == null) {
            return "In the menus";
        }

        if (client.hasSingleplayerServer()) {
            return "Playing Singleplayer";
        }

        var server = client.getCurrentServer();

        if (server == null) {
            return "Playing Multiplayer";
        }

        if (server.isRealm()) {
            return "Playing on Realms";
        }

        var name = this.showServer.getValue() ? ServerMappings.lookup(server.ip) : null;

        if (name == null) {
            return "Playing Multiplayer";
        }

        if (name.codePointCount(0, name.length()) > DiscordRichPresenceModule.MAX_SERVER_NAME_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, DiscordRichPresenceModule.MAX_SERVER_NAME_LENGTH));
        }

        return "Playing on " + name;
    }
}
