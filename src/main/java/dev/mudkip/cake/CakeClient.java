package dev.mudkip.cake;

//? if !modrinth {
import dev.mudkip.cake.badge.BadgeService;
//?}
import dev.mudkip.cake.module.HudModule;
import dev.mudkip.cake.module.ModuleConfig;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.server.ServerMappings;
import dev.mudkip.cake.server.ServerSession;
import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class CakeClient implements ClientModInitializer {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("cake", "hud");
    private static final int HUD_MARGIN = 4;

    private final Minecraft client;
    private final ServerSession serverSession;
    private final BedwarsPractice bedwarsPractice;
    private final ModuleManager modules;
    private final KeybindController keybinds;
    //? if !modrinth {
    private final BadgeService badge;
    //?}

    public CakeClient() {
        this.client = Minecraft.getInstance();
        this.serverSession = new ServerSession();
        this.bedwarsPractice = new BedwarsPractice(this.client, this.serverSession);
        var configDirectory = FabricLoader.getInstance().getConfigDir();
        var serverMappings = new ServerMappings(configDirectory.resolve("cake-servermappings.json"));
        var config = new ModuleConfig(configDirectory.resolve("cake.json"), configDirectory.resolve("cakeclient.json"));
        this.modules = new ModuleManager(
                new ModuleContext(this.client, this.serverSession, serverMappings, this.bedwarsPractice),
                config
        );
        this.keybinds = new KeybindController(this.client, this.modules);
        ((MinecraftClientAccess) this.client).cake$setModuleManager(this.modules);
        //? if !modrinth {
        this.badge = new BadgeService(this.client, this.serverSession);
        ((MinecraftClientAccess) this.client).cake$setBadgeService(this.badge);
        //?}
    }

    @Override
    public void onInitializeClient() {
        this.keybinds.register();
        this.registerHud();
        this.bedwarsPractice.register();
        this.modules.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.modules.shutdown());
        //? if !modrinth {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.badge.shutdown());
        //?}
    }

    private void registerHud() {
        HudElementRegistry.addLast(
                CakeClient.HUD_ID,
                (graphics, deltaTracker) -> this.renderHud(graphics)
        );
    }

    private void renderHud(GuiGraphicsExtractor graphics) {
        if (this.client.gui.hud.isHidden()) {
            return;
        }

        var font = this.client.font;
        var y = CakeClient.HUD_MARGIN;

        for (var module : this.modules.getModules()) {
            if (module instanceof HudModule hudModule && hudModule.isEnabled()) {
                graphics.text(font, hudModule.getText(), CakeClient.HUD_MARGIN, y, 0xFFFFFFFF, true);
                y += font.lineHeight;
            }
        }
    }

    private void tick(Minecraft client) {
        this.serverSession.update(client);
        //? if !modrinth {
        this.badge.tick();
        //?}
        this.modules.refreshAvailability();
        this.bedwarsPractice.tick();
        this.keybinds.tick();
        this.modules.tick();
        this.modules.saveIfDirty();
    }
}
