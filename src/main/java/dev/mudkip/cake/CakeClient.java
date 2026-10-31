package dev.mudkip.cake;

import dev.mudkip.cake.keybind.KeybindController;
import dev.mudkip.cake.module.ModuleConfig;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.server.ServerMappings;
import dev.mudkip.cake.server.ServerSession;
import dev.mudkip.cake.ui.HudOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class CakeClient {
    private final ServerSession serverSession;
    private final ModuleManager modules;
    private final KeybindController keybinds;

    public CakeClient(Minecraft client) {
        this.serverSession = new ServerSession();
        var configDirectory = FabricLoader.getInstance().getConfigDir();
        var serverMappings = new ServerMappings(configDirectory.resolve("cake-servermappings.json"));
        var config = new ModuleConfig(configDirectory.resolve("cake.json"), configDirectory.resolve("cakeclient.json"));
        this.modules = new ModuleManager(new ModuleContext(client, this.serverSession, serverMappings), config);
        this.keybinds = new KeybindController(client, this.modules);
        ((MinecraftClientAccess) client).cake$setModuleManager(this.modules);
    }

    public void initialize() {
        this.keybinds.register();
        HudOverlay.register(this.modules);
        this.modules.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.modules.shutdown());
    }

    private void tick(Minecraft client) {
        this.serverSession.update(client);
        this.modules.refreshAvailability();
        this.keybinds.tick();
        this.modules.tick();
        this.modules.saveIfDirty();
    }
}
