package dev.mudkip.cake.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ModuleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleConfig.class);

    private final Path path;
    private final Path legacyPath;
    private boolean dirty;
    private boolean loading;

    public ModuleConfig(Path path, Path legacyPath) {
        this.path = path;
        this.legacyPath = legacyPath;
    }

    public void load(List<ClientModule> modules) {
        this.migrateLegacyConfig();

        if (!Files.exists(this.path)) {
            return;
        }

        this.loading = true;

        try {
            var root = JsonParser.parseString(Files.readString(this.path)).getAsJsonObject();
            var modulesJson = root.getAsJsonObject("modules");

            if (modulesJson != null) {
                for (var module : modules) {
                    var moduleJson = modulesJson.getAsJsonObject(module.getId());

                    if (moduleJson != null) {
                        module.fromJson(moduleJson);
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to read the Cake Client config", exception);
        } finally {
            this.loading = false;
        }
    }

    public void markDirty() {
        if (!this.loading) {
            this.dirty = true;
        }
    }

    public void saveIfDirty(List<ClientModule> modules) {
        if (this.dirty) {
            this.save(modules);
        }
    }

    public void save(List<ClientModule> modules) {
        if (this.loading) {
            return;
        }

        this.dirty = false;

        var root = new JsonObject();
        var modulesJson = new JsonObject();

        for (var module : modules) {
            modulesJson.add(module.getId(), module.toJson());
        }

        root.add("modules", modulesJson);

        try {
            Files.createDirectories(this.path.toAbsolutePath().getParent());
            Files.writeString(this.path, GSON.toJson(root));
        } catch (IOException exception) {
            this.dirty = true;
            LOGGER.warn("Failed to write the Cake Client config", exception);
        }
    }

    private void migrateLegacyConfig() {
        if (Files.exists(this.path) || !Files.exists(this.legacyPath)) {
            return;
        }

        try {
            Files.copy(this.legacyPath, this.path);
        } catch (IOException exception) {
            LOGGER.warn("Failed to migrate the Cake Client config", exception);
        }
    }
}
