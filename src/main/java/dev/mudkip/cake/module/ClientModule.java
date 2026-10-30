package dev.mudkip.cake.module;

import com.google.gson.JsonObject;
import dev.mudkip.cake.CakeClientKeybinds;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.ColorSetting;
import dev.mudkip.cake.module.setting.DoubleSetting;
import dev.mudkip.cake.module.setting.EnumSetting;
import dev.mudkip.cake.module.setting.IntSetting;
import dev.mudkip.cake.module.setting.ModuleSetting;
import dev.mudkip.cake.module.setting.StringSetting;
import dev.mudkip.cake.server.ServerIntegration;
import dev.mudkip.cake.server.ServerSession;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public abstract class ClientModule {
    private final String id;
    private final Component name;
    private final Component description;
    private final ModuleCategory category;
    private final Identifier icon;
    private final List<ModuleSetting<?>> settings;
    private final List<ModuleSetting<?>> settingsView;
    private final KeyMapping keyMapping;
    private final @Nullable ServerIntegration integration;
    protected final ServerSession serverSession;
    protected final Minecraft client;
    private Runnable changeListener;
    private boolean requestedEnabled;
    private boolean active;

    protected ClientModule(ModuleDefinition definition, ModuleContext context) {
        this.id = definition.id();
        this.name = Component.translatable("cake.module." + this.id + ".name");
        this.description = Component.translatable("cake.module." + this.id + ".description");
        this.category = definition.category();
        this.icon = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/" + this.id + ".png");
        this.settings = new ArrayList<>();
        this.settingsView = Collections.unmodifiableList(this.settings);
        this.integration = definition.integration();
        this.serverSession = context.serverSession();
        this.client = context.client();
        this.keyMapping = new KeyMapping(
                "key.cake.module." + this.id,
                definition.keyType(),
                definition.defaultKey(),
                CakeClientKeybinds.getCategory()
        );
        this.changeListener = () -> { };
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
        return this.settingsView;
    }

    public KeyMapping getKeyMapping() {
        return this.keyMapping;
    }

    public @Nullable ServerIntegration getIntegration() {
        return this.integration;
    }

    public boolean isAvailable() {
        return this.integration == null || this.serverSession.isOn(this.integration);
    }

    public boolean isEnabled() {
        return this.active;
    }

    public void setEnabled(boolean enabled) {
        if (this.requestedEnabled == enabled) {
            return;
        }

        if (enabled && !this.isAvailable()) {
            return;
        }

        this.requestedEnabled = enabled;
        this.refreshAvailability();
        this.notifyChanged();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTick() {
    }

    protected JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("enabled", this.requestedEnabled);

        if (!this.settings.isEmpty()) {
            var settingsJson = new JsonObject();

            for (var setting : this.settings) {
                settingsJson.add(setting.getId(), setting.toJson());
            }

            json.add("settings", settingsJson);
        }

        return json;
    }

    protected void fromJson(JsonObject json) {
        var settingsJson = json.getAsJsonObject("settings");

        if (settingsJson != null) {
            for (var setting : this.settings) {
                var element = settingsJson.get(setting.getId());

                if (element != null) {
                    setting.fromJson(element);
                }
            }
        }

        this.requestedEnabled = json.has("enabled") && json.get("enabled").getAsBoolean();
        this.refreshAvailability();
    }

    protected BooleanSetting booleanSetting(String id, boolean defaultValue) {
        var key = this.settingKey(id);
        var name = this.settingName(key);
        var description = this.settingDescription(key);
        return this.setting(new BooleanSetting(id, name, description, defaultValue));
    }

    protected IntSetting intSetting(String id, int min, int max, int defaultValue, String suffix) {
        var key = this.settingKey(id);
        var name = this.settingName(key);
        var description = this.settingDescription(key);
        return this.setting(new IntSetting(id, name, description, min, max, defaultValue, suffix));
    }

    protected DoubleSetting doubleSetting(
            String id,
            double min,
            double max,
            double step,
            double defaultValue,
            String suffix
    ) {
        var key = this.settingKey(id);
        var name = this.settingName(key);
        var description = this.settingDescription(key);
        return this.setting(new DoubleSetting(id, name, description, min, max, step, defaultValue, suffix));
    }

    protected ColorSetting colorSetting(String id, int defaultValue) {
        var key = this.settingKey(id);
        var name = this.settingName(key);
        var description = this.settingDescription(key);
        return this.setting(new ColorSetting(id, name, description, defaultValue));
    }

    protected StringSetting stringSetting(String id, String defaultValue, int maxLength) {
        var key = this.settingKey(id);
        var name = this.settingName(key);
        var description = this.settingDescription(key);
        return this.setting(new StringSetting(id, name, description, defaultValue, maxLength));
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(
            String id,
            Class<E> enumType,
            Function<E, Component> formatter,
            E defaultValue
    ) {
        var key = this.settingKey(id);
        var name = this.settingName(key);
        var description = this.settingDescription(key);
        return this.setting(new EnumSetting<>(id, name, description, enumType, formatter, defaultValue));
    }

    protected void notifyChanged() {
        this.changeListener.run();
    }

    void finishLoading() {
        this.refreshAvailability();
    }

    void refreshAvailability() {
        var shouldBeActive = this.requestedEnabled && this.isAvailable();

        if (this.active == shouldBeActive) {
            return;
        }

        this.active = shouldBeActive;

        if (shouldBeActive) {
            this.onEnable();
        } else {
            this.onDisable();
        }
    }

    void tick() {
        this.onTick();
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    private <T extends ModuleSetting<?>> T setting(T setting) {
        setting.setChangeListener(this::notifyChanged);
        this.settings.add(setting);
        return setting;
    }

    private Component settingName(String key) {
        return Component.translatable(key + ".name");
    }

    private Component settingDescription(String key) {
        return Component.translatable(key + ".description");
    }

    private String settingKey(String settingId) {
        return "cake.setting." + this.id + "." + settingId;
    }
}
