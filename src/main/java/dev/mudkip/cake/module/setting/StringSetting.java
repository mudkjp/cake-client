package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

public final class StringSetting extends ModuleSetting<String> {
    private final int maxLength;

    public StringSetting(String id, Component name, Component description, String defaultValue, int maxLength) {
        super(id, name, description, defaultValue);
        this.maxLength = maxLength;
    }

    @Override
    public UIComponent createControl() {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        container.child(UIComponents.label(this.getName()).shadow(true));
        container.child(UIComponents.label(this.getDescription()).color(Color.ofArgb(0xFF9A9A9A)));

        var textBox = UIComponents.textBox(Sizing.fill(100));
        textBox.setMaxLength(this.maxLength);
        textBox.text(this.getValue());
        textBox.onChanged().subscribe(this::setValue);
        container.child(textBox);
        return container;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(this.getValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            this.setValue(json.getAsString());
        }
    }
}
