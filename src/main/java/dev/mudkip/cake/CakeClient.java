package dev.mudkip.cake;

import dev.mudkip.cake.module.HudModule;
import dev.mudkip.cake.module.ModuleConfig;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.server.ServerMappings;
import dev.mudkip.cake.server.ServerSession;
import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import dev.mudkip.cake.ui.HudEditorScreen;
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

    private final Minecraft client;
    private final ServerSession serverSession;
    private final BedwarsPractice bedwarsPractice;
    private final ModuleManager modules;
    private final KeybindController keybinds;

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
    }

    @Override
    public void onInitializeClient() {
        this.keybinds.register();
        this.registerHud();
        this.bedwarsPractice.register();
        this.modules.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.modules.shutdown());
    }

    private void registerHud() {
        HudElementRegistry.addLast(
                CakeClient.HUD_ID,
                (graphics, deltaTracker) -> this.renderHud(graphics)
        );
    }

    private void renderHud(GuiGraphicsExtractor graphics) {
        if (this.client.gui.hud.isHidden() || this.client.gui.screen() instanceof HudEditorScreen) {
            return;
        }

        for (var module : this.modules.getModules()) {
            if (module instanceof HudModule hudModule && hudModule.isEnabled()) {
                HudModule.render(graphics, hudModule, 1.0F);
            }
        }
    }

    private void tick(Minecraft client) {
        this.serverSession.update(client);
        this.modules.refreshAvailability();
        this.bedwarsPractice.tick();
        this.keybinds.tick();
        this.modules.tick();
        this.modules.saveIfDirty();
    }
}
