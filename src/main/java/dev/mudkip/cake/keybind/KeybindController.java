package dev.mudkip.cake.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.CakeClientKeybinds;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.ReplayBufferModule;
import dev.mudkip.cake.ui.HudEditorScreen;
import dev.mudkip.cake.ui.ModMenuScreen;
import dev.mudkip.cake.ui.ModuleDetailsScreen;
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

public final class KeybindController {
    private static final String LEGACY_LINE_PREFIX = "key_key.cakeclient.";
    private static final String LINE_PREFIX = "key_key.";
    private static final String LEGACY_KEY_NAMESPACE = "key.cakeclient.";
    private static final String KEY_NAMESPACE = "key.cake.";
    private static final Logger LOGGER = LoggerFactory.getLogger(KeybindController.class);

    private final Minecraft client;
    private final ModuleManager modules;
    private final KeyMapping openMenuKey;
    private final KeyMapping saveClipKey;
    private final Map<String, InputConstants.Key> legacyKeybinds;
    private boolean legacyKeybindsMigrated;

    public KeybindController(Minecraft client, ModuleManager modules) {
        this.client = client;
        this.modules = modules;
        this.legacyKeybinds = new HashMap<>();
        this.openMenuKey = new KeyMapping(
                "key.cake.open_menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                CakeClientKeybinds.getCategory()
        );
        this.saveClipKey = new KeyMapping(
                "key.cake.save_clip",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F9,
                CakeClientKeybinds.getCategory()
        );
        this.readLegacyKeybinds();
    }

    public void register() {
        KeyMappingHelper.registerKeyMapping(this.openMenuKey);
        KeyMappingHelper.registerKeyMapping(this.saveClipKey);

        for (var module : this.modules.getModules()) {
            KeyMappingHelper.registerKeyMapping(module.getKeyMapping());
        }
    }

    public void tick() {
        this.migrateLegacyKeybinds();

        if (this.client.level == null) {
            KeybindController.drainKey(this.openMenuKey);
            KeybindController.drainKey(this.saveClipKey);

            for (var module : this.modules.getModules()) {
                KeybindController.drainKey(module.getKeyMapping());
            }

            return;
        }

        var openedMenu = this.client.gui.screen() == null && this.openMenuKey.consumeClick();

        if (openedMenu) {
            this.client.gui.setScreen(new ModMenuScreen(this.modules));
            return;
        }

        KeybindController.drainKey(this.openMenuKey);

        for (var module : this.modules.getModules()) {
            if (module.getKeyMapping().consumeClick()
                    && module.isAvailable()
                    && this.canToggleFromScreen()) {
                module.setEnabled(!module.isEnabled());
            }
        }

        if (this.saveClipKey.consumeClick() && this.canToggleFromScreen()) {
            var module = this.modules.getModule(ReplayBufferModule.class);

            if (module != null) {
                module.saveClip();
            }
        }
    }

    private boolean canToggleFromScreen() {
        var screen = this.client.gui.screen();

        if (screen == null) {
            return true;
        }

        if (screen instanceof ModMenuScreen || screen instanceof ModuleDetailsScreen || screen instanceof HudEditorScreen) {
            return false;
        }

        return !(screen.getFocused() instanceof EditBox);
    }

    private void readLegacyKeybinds() {
        var path = FabricLoader.getInstance().getGameDir().resolve("options.txt");

        if (!Files.exists(path)) {
            return;
        }

        try (var lines = Files.lines(path)) {
            lines.filter(line -> line.startsWith(KeybindController.LEGACY_LINE_PREFIX)).forEach(line -> {
                var separator = line.indexOf(':');

                if (separator < 0) {
                    return;
                }

                var oldName = line.substring(KeybindController.LINE_PREFIX.length(), separator);
                var newName = oldName.replace(KeybindController.LEGACY_KEY_NAMESPACE, KeybindController.KEY_NAMESPACE);

                try {
                    this.legacyKeybinds.put(newName, InputConstants.getKey(line.substring(separator + 1)));
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read legacy keybind {}", line, exception);
                }
            });
        } catch (Exception exception) {
            LOGGER.warn("Failed to read legacy keybinds", exception);
        }
    }

    private void migrateLegacyKeybinds() {
        if (this.legacyKeybindsMigrated) {
            return;
        }

        this.legacyKeybindsMigrated = true;

        if (this.legacyKeybinds.isEmpty()) {
            return;
        }

        var changed = false;

        for (var mapping : this.client.options.keyMappings) {
            var key = this.legacyKeybinds.get(mapping.getName());

            if (key != null) {
                mapping.setKey(key);
                changed = true;
            }
        }

        if (changed) {
            this.client.options.save();
        }

        this.legacyKeybinds.clear();
    }

    private static void drainKey(KeyMapping key) {
        while (key.consumeClick()) {
        }
    }
}
