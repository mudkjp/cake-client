package dev.mudkip.cake.module;

import dev.mudkip.cake.module.modules.BlockOutlineModule;
import dev.mudkip.cake.module.modules.CleanScoreboardModule;
import dev.mudkip.cake.module.modules.CpsModule;
import dev.mudkip.cake.module.modules.DiscordRichPresenceModule;
import dev.mudkip.cake.module.modules.FingerprintModule;
import dev.mudkip.cake.module.modules.FpsModule;
import dev.mudkip.cake.module.modules.FullBrightModule;
import dev.mudkip.cake.module.modules.PingModule;
import dev.mudkip.cake.module.modules.ReplayBufferModule;
import dev.mudkip.cake.module.modules.ScreenshotCompressionModule;
import dev.mudkip.cake.module.modules.TimeChangerModule;
import dev.mudkip.cake.module.modules.ZoomModule;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private final List<ClientModule> modules;
    private final List<ClientModule> modulesView;
    private final Map<String, ClientModule> modulesById;
    private final Map<Class<? extends ClientModule>, ClientModule> modulesByType;
    private final ModuleConfig config;

    public ModuleManager(ModuleContext context, ModuleConfig config) {
        this.modules = new ArrayList<>();
        this.modulesView = Collections.unmodifiableList(this.modules);
        this.modulesById = new HashMap<>();
        this.modulesByType = new HashMap<>();
        this.config = config;
        this.registerModules(context);
    }

    public List<ClientModule> getModules() {
        return this.modulesView;
    }

    public @Nullable ClientModule getModule(String id) {
        return this.modulesById.get(id);
    }

    public <T extends ClientModule> @Nullable T getModule(Class<T> type) {
        return type.cast(this.modulesByType.get(type));
    }

    public void load() {
        this.config.load(this.modules);

        for (var module : this.modules) {
            module.finishLoading();
        }

        this.config.save(this.modules);
    }

    public void saveIfDirty() {
        this.config.saveIfDirty(this.modules);
    }

    public void refreshAvailability() {
        for (var module : this.modules) {
            module.refreshAvailability();
        }
    }

    public void tick() {
        for (var module : this.modules) {
            if (module.isEnabled()) {
                module.tick();
            }
        }
    }

    public void shutdown() {
        for (var module : this.modules) {
            module.onShutdown();
        }
    }

    private void registerModules(ModuleContext context) {
        this.register(new FullBrightModule(context));
        this.register(new TimeChangerModule(context));
        this.register(new BlockOutlineModule(context));
        this.register(new CleanScoreboardModule(context));
        this.register(new FpsModule(context));
        this.register(new CpsModule(context));
        this.register(new PingModule(context));
        this.register(new ReplayBufferModule(context));
        this.register(new ZoomModule(context));
        this.register(new FingerprintModule(context));
        this.register(new ScreenshotCompressionModule(context));
        this.register(new DiscordRichPresenceModule(context));
    }

    private void register(ClientModule module) {
        if (this.modulesById.putIfAbsent(module.getId(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.getId());
        }

        if (this.modulesByType.putIfAbsent(module.getClass(), module) != null) {
            throw new IllegalArgumentException("Duplicate module type: " + module.getClass().getName());
        }

        module.setChangeListener(this.config::markDirty);
        this.modules.add(module);
    }
}
