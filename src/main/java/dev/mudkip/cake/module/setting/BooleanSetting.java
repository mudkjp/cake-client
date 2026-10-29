package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

public final class BooleanSetting extends ModuleSetting<Boolean> {
    public BooleanSetting(String id, Component name, Component description, boolean defaultValue) {
        super(id, name, description, defaultValue);
    }

    @Override
    public UIComponent createControl() {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        container.child(UIComponents.checkbox(this.getName())
                .checked(this.getValue())
                .onChanged(this::setValue));
        container.child(UIComponents.label(this.getDescription()).color(Color.ofArgb(0xFF9A9A9A)));
        return container;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(this.getValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            this.setValue(json.getAsBoolean());
        }
    }
}
