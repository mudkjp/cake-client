package dev.mudkip.cake.module;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.CakeClientKeybinds;
import dev.mudkip.cake.module.setting.ModuleSetting;
import dev.mudkip.cake.server.ServerIntegration;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ClientModule {
    private final String id;
    private final Component name;
    private final Component description;
    private final ModuleCategory category;
    private final Identifier icon;
    private final List<ModuleSetting<?>> settings;
    private final KeyMapping keyMapping;
    private final @Nullable ServerIntegration integration;
    private Runnable changeListener;
    private boolean enabled;
    private boolean active;

    protected ClientModule(String id, Component name, Component description, ModuleCategory category, Identifier icon) {
        this(id, name, description, category, icon, null, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue());
    }

    protected ClientModule(String id, Component name, Component description, ModuleCategory category, Identifier icon, ServerIntegration integration) {
        this(id, name, description, category, icon, integration, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue());
    }

    protected ClientModule(String id, Component name, Component description, ModuleCategory category, Identifier icon, InputConstants.Type keyType, int defaultKey) {
        this(id, name, description, category, icon, null, keyType, defaultKey);
    }

    protected ClientModule(String id, Component name, Component description, ModuleCategory category, Identifier icon, @Nullable ServerIntegration integration, InputConstants.Type keyType, int defaultKey) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.integration = integration;
        this.settings = new ArrayList<>();
        this.keyMapping = new KeyMapping(
                "key.cake.module." + id,
                keyType,
                defaultKey,
                CakeClientKeybinds.getCategory()
        );
        this.changeListener = () -> {};
    }

    public String getId() {
        return this.id;
    }

    public Component getName() {
        return this.name;
    }

    public Component getDescription() {
        return this.description;
    }

    public ModuleCategory getCategory() {
        return this.category;
    }

    public Identifier getIcon() {
        return this.icon;
    }

    public List<ModuleSetting<?>> getSettings() {
        return this.settings;
    }

    public KeyMapping getKeyMapping() {
        return this.keyMapping;
    }

    public @Nullable ServerIntegration getIntegration() {
        return this.integration;
    }

    public boolean isAvailable() {
        return this.integration == null || this.integration.isCurrent();
    }

    public boolean isEnabled() {
        return this.active;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        if (enabled && !this.isAvailable()) {
            return;
        }

        this.enabled = enabled;
        this.refreshAvailability();
        this.notifyChanged();
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onTick() {
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("enabled", this.enabled);

        if (!this.settings.isEmpty()) {
            var settings = new JsonObject();

            for (var setting : this.settings) {
                settings.add(setting.getId(), setting.toJson());
            }

            json.add("settings", settings);
        }

        return json;
    }

    public void fromJson(JsonObject json) {
        var settings = json.getAsJsonObject("settings");

        if (settings != null) {
            for (var setting : this.settings) {
                var element = settings.get(setting.getId());

                if (element != null) {
                    setting.fromJson(element);
                }
            }
        }

        var enabled = json.has("enabled") && json.get("enabled").getAsBoolean();
        this.enabled = enabled;
        this.refreshAvailability();
    }

    protected <T extends ModuleSetting<?>> T setting(T setting) {
        setting.setChangeListener(this::notifyChanged);
        this.settings.add(setting);
        return setting;
    }

    void finishLoading() {
        this.refreshAvailability();
    }

    void refreshAvailability() {
        var active = this.enabled && this.isAvailable();

        if (this.active == active) {
            return;
        }

        this.active = active;

        if (active) {
            this.onEnable();
        } else {
            this.onDisable();
        }
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    protected void notifyChanged() {
        this.changeListener.run();
    }
}
