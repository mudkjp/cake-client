package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;

public final class MessageSetting extends ModuleSetting<Boolean> {
    public MessageSetting(String id, Component name, boolean defaultValue) {
        super(id, name, Component.empty(), defaultValue);
    }

    @Override
    public UIComponent createControl() {
        var checkbox = UIComponents.checkbox(Component.empty())
                .checked(this.getValue())
                .onChanged(this::setValue);
        var label = UIComponents.label(this.getName()).shadow(true);
        label.horizontalSizing(Sizing.expand(100));

        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(4)
                .child(checkbox)
                .child(label);
        row.verticalAlignment(VerticalAlignment.TOP);
        return row;
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
