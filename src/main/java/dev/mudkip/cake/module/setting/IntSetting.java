package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

public final class IntSetting extends ModuleSetting<Integer> {
    private final int min;
    private final int max;
    private final String suffix;

    public IntSetting(
            String id,
            Component name,
            Component description,
            int min,
            int max,
            int defaultValue,
            String suffix
    ) {
        super(id, name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.suffix = suffix;
        this.setInitialValue(this.clamp(defaultValue));
    }

    @Override
    public UIComponent createControl() {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        container.child(UIComponents.label(this.getName()).shadow(true));
        container.child(UIComponents.label(this.getDescription()).color(Color.ofArgb(0xFF9A9A9A)));

        var slider = UIComponents.discreteSlider(Sizing.fill(100), this.min, this.max);
        slider.decimalPlaces(0);
        slider.snap(true);
        slider.setFromDiscreteValue(this.getValue());
        slider.message(value -> Component.literal(value + this.suffix));
        slider.onChanged().subscribe(value -> this.setValue((int) value));
        container.child(slider);
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

    @Override
    protected Integer sanitize(Integer value) {
        return this.clamp(value);
    }

    private int clamp(int value) {
        return Math.clamp(value, this.min, this.max);
    }
}
