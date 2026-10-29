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
    private Runnable changeListener;
    protected T value;

    protected ModuleSetting(String id, Component name, Component description, T defaultValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
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

    public T getValue() {
        return this.value;
    }

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public void setValue(T value) {
        if (Objects.equals(this.value, value)) {
            return;
        }

        this.value = value;
        this.changeListener.run();
    }

    public void reset() {
        this.setValue(this.defaultValue);
    }

    public abstract UIComponent createControl();

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }
}
