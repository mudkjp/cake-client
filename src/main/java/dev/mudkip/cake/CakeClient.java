package dev.mudkip.cake;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.SmoothFontModule;
import dev.mudkip.cake.server.Locraw;
import dev.mudkip.cake.server.ServerSession;
import dev.mudkip.cake.ui.HudEditorScreen;
import dev.mudkip.cake.ui.HudOverlay;
import dev.mudkip.cake.ui.ModMenuScreen;
import dev.mudkip.cake.ui.ModuleDetailsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public final class CakeClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("Cake Client");
    private static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.cake.open_menu",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_RSHIFT,
            CakeClientKeybinds.getCategory()
    );
    private static final Map<String, InputConstants.Key> LEGACY_KEYBINDS = new HashMap<>();
    private static boolean legacyKeybindsMigrated;

    private CakeClient() {

    }

    public static void init() {
        CakeClient.readLegacyKeybinds();
        ModuleManager.get().load();

        KeyMappingHelper.registerKeyMapping(CakeClient.OPEN_MENU_KEY);
        HudOverlay.register();
        Locraw.register();

        for (var module : ModuleManager.get().getModules()) {
            KeyMappingHelper.registerKeyMapping(module.getKeyMapping());
        }

        ClientTickEvents.END_CLIENT_TICK.register(CakeClient::tick);
    }

    private static void tick(Minecraft client) {
        SmoothFontModule.cleanupLegacyPack();
        CakeClient.migrateLegacyKeybinds(client);
        ServerSession.update(client);
        ModuleManager.get().refreshAvailability();

        if (client.level != null) {
            var openedMenu = client.gui.screen() == null && CakeClient.OPEN_MENU_KEY.consumeClick();

            if (openedMenu) {
                client.gui.setScreen(new ModMenuScreen());
            } else {
                CakeClient.drainKey(CakeClient.OPEN_MENU_KEY);

                for (var module : ModuleManager.get().getModules()) {
                    if (module.getKeyMapping().consumeClick() && module.isAvailable() && CakeClient.canToggleFromScreen(client)) {
                        module.setEnabled(!module.isEnabled());
                    }
                }
            }
        } else {
            CakeClient.drainKey(CakeClient.OPEN_MENU_KEY);

            for (var module : ModuleManager.get().getModules()) {
                CakeClient.drainKey(module.getKeyMapping());
            }
        }

        if (client.level != null) {
            for (var module : ModuleManager.get().getModules()) {
                if (module.isEnabled()) {
                    module.onTick();
                }
            }
        }

        ModuleManager.get().saveIfDirty();
    }

    private static boolean canToggleFromScreen(Minecraft client) {
        var screen = client.gui.screen();

        if (screen == null) {
            return true;
        }

        if (screen instanceof ModMenuScreen || screen instanceof ModuleDetailsScreen || screen instanceof HudEditorScreen) {
            return false;
        }

        return !(screen.getFocused() instanceof EditBox);
    }

    private static void drainKey(KeyMapping key) {
        while (key.consumeClick()) {
        }
    }

    private static void readLegacyKeybinds() {
        var path = FabricLoader.getInstance().getGameDir().resolve("options.txt");

        if (!Files.exists(path)) {
            return;
        }

        try (var lines = Files.lines(path)) {
            lines.filter(line -> line.startsWith("key_key.cakeclient.")).forEach(line -> {
                var separator = line.indexOf(':');

                if (separator < 0) {
                    return;
                }

                var oldName = line.substring("key_key.".length(), separator);
                var newName = oldName.replace("key.cakeclient.", "key.cake.");

                try {
                    CakeClient.LEGACY_KEYBINDS.put(newName, InputConstants.getKey(line.substring(separator + 1)));
                } catch (Exception exception) {
                    CakeClient.LOGGER.warn("Failed to read legacy keybind {}", line, exception);
                }
            });
        } catch (Exception exception) {
            CakeClient.LOGGER.warn("Failed to read legacy keybinds", exception);
        }
    }

    private static void migrateLegacyKeybinds(Minecraft client) {
        if (CakeClient.legacyKeybindsMigrated) {
            return;
        }

        CakeClient.legacyKeybindsMigrated = true;

        if (CakeClient.LEGACY_KEYBINDS.isEmpty()) {
            return;
        }

        var changed = false;

        for (var mapping : client.options.keyMappings) {
            var key = CakeClient.LEGACY_KEYBINDS.get(mapping.getName());

            if (key != null) {
                mapping.setKey(key);
                changed = true;
            }
        }

        if (changed) {
            client.options.save();
        }

        CakeClient.LEGACY_KEYBINDS.clear();
    }
}
