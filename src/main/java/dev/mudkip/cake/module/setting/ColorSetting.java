package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

public final class ColorSetting extends ModuleSetting<Integer> {
    public ColorSetting(String id, Component name, Component description, int defaultValue) {
        super(id, name, description, defaultValue);
    }

    @Override
    public UIComponent createControl() {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        container.child(UIComponents.label(this.getName()).shadow(true));
        container.child(UIComponents.label(this.getDescription()).color(Color.ofArgb(0xFF9A9A9A)));

        var picker = new ColorPickerComponent();
        picker.showAlpha(true);
        picker.selectedColor(Color.ofArgb(this.getValue()));
        picker.horizontalSizing(Sizing.fill(100));
        picker.verticalSizing(Sizing.fixed(64));
        picker.onChanged().subscribe(color -> this.setValue(color.argb()));
        container.child(picker);
        return container;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(this.getValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            this.setValue(json.getAsInt());
        }
    }
}
