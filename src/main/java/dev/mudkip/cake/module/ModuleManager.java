package dev.mudkip.cake.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mudkip.cake.module.modules.BlockOutlineModule;
import dev.mudkip.cake.module.modules.CpsModule;
import dev.mudkip.cake.module.modules.FingerprintModule;
import dev.mudkip.cake.module.modules.FpsModule;
import dev.mudkip.cake.module.modules.FullBrightModule;
import dev.mudkip.cake.module.modules.PingModule;
import dev.mudkip.cake.module.modules.ScreenshotCompressionModule;
import dev.mudkip.cake.module.modules.SmoothFontModule;
import dev.mudkip.cake.module.modules.TimeChangerModule;
import dev.mudkip.cake.module.modules.ZoomModule;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("Cake Client");
    private static ModuleManager instance;

    private final List<ClientModule> modules;
    private final List<ClientModule> modulesView;
    private final Map<String, ClientModule> modulesById;
    private final Map<Class<? extends ClientModule>, ClientModule> modulesByClass;
    private boolean loading;
    private boolean dirty;

    private ModuleManager() {
        this.modules = new ArrayList<>();
        this.modulesById = new HashMap<>();
        this.modulesByClass = new HashMap<>();
        this.registerModules();
        this.modulesView = Collections.unmodifiableList(this.modules);
    }

    public static ModuleManager get() {
        if (ModuleManager.instance == null) {
            ModuleManager.instance = new ModuleManager();
        }

        return ModuleManager.instance;
    }

    public List<ClientModule> getModules() {
        return this.modulesView;
    }

    public @Nullable ClientModule get(String id) {
        return this.modulesById.get(id);
    }

    public <T extends ClientModule> @Nullable T get(Class<T> type) {
        return type.cast(this.modulesByClass.get(type));
    }

    public void load() {
        var path = ModuleManager.configPath();

        if (!Files.exists(path)) {
            this.save();
            return;
        }

        this.loading = true;

        try {
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var modules = root.getAsJsonObject("modules");

            if (modules != null) {
                for (var module : this.modules) {
                    var data = modules.getAsJsonObject(module.getId());

                    if (data != null) {
                        module.fromJson(data);
                    }
                }
            }

            for (var module : this.modules) {
                module.finishLoading();
            }
        } catch (Exception exception) {
            ModuleManager.LOGGER.warn("Failed to read Cake Client config", exception);
        } finally {
            this.loading = false;
        }

        this.save();
    }

    public void saveIfDirty() {
        if (!this.dirty) {
            return;
        }

        this.save();
    }

    public void save() {
        if (this.loading) {
            return;
        }

        this.dirty = false;

        try {
            var root = new JsonObject();
            var modules = new JsonObject();

            for (var module : this.modules) {
                modules.add(module.getId(), module.toJson());
            }

            root.add("modules", modules);

            var path = ModuleManager.configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, ModuleManager.GSON.toJson(root));
        } catch (IOException exception) {
            this.dirty = true;
            ModuleManager.LOGGER.warn("Failed to write Cake Client config", exception);
        }
    }

    private void registerModules() {
        this.register(new FullBrightModule());
        this.register(new TimeChangerModule());
        this.register(new BlockOutlineModule());
        this.register(new SmoothFontModule());
        this.register(new FpsModule());
        this.register(new CpsModule());
        this.register(new PingModule());
        this.register(new ZoomModule());
        this.register(new FingerprintModule());
        this.register(new ScreenshotCompressionModule());
    }

    private void register(ClientModule module) {
        if (this.modulesById.putIfAbsent(module.getId(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.getId());
        }

        if (this.modulesByClass.putIfAbsent(module.getClass(), module) != null) {
            throw new IllegalArgumentException("Duplicate module class: " + module.getClass().getName());
        }

        this.modules.add(module);
        module.setChangeListener(this::markDirty);
    }

    private void markDirty() {
        this.dirty = true;
    }

    private static Path configPath() {
        var configDir = FabricLoader.getInstance().getConfigDir();
        var path = configDir.resolve("cake.json");
        var legacyPath = configDir.resolve("cakeclient.json");

        if (!Files.exists(path) && Files.exists(legacyPath)) {
            try {
                Files.copy(legacyPath, path);
            } catch (IOException exception) {
                ModuleManager.LOGGER.warn("Failed to migrate the Cake Client config", exception);
            }
        }

        return path;
    }
}
