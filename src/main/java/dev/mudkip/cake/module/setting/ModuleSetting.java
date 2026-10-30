package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public abstract class ModuleSetting<T> {
    private final String id;
    private final Component name;
    private final Component description;
    private final T defaultValue;
    private T value;
    private Runnable changeListener;

    protected ModuleSetting(String id, Component name, Component description, T defaultValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
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

    public T getValue() {
        return this.value;
    }

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public void setValue(T value) {
        var sanitized = this.sanitize(value);

        if (Objects.equals(this.value, sanitized)) {
            return;
        }

        this.value = sanitized;
        this.changeListener.run();
    }

    public void reset() {
        this.setValue(this.defaultValue);
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public abstract UIComponent createControl();

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);

    protected T sanitize(T value) {
        return value;
    }

    protected void setInitialValue(T value) {
        this.value = value;
    }
}
