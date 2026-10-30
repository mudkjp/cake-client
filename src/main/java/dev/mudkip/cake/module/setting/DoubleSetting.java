package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class DoubleSetting extends ModuleSetting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public DoubleSetting(
            String id,
            Component name,
            Component description,
            double min,
            double max,
            double step,
            double defaultValue,
            String suffix
    ) {
        super(id, name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        this.setInitialValue(this.clamp(defaultValue));
    }

    @Override
    public UIComponent createControl() {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        container.child(UIComponents.label(this.getName()).shadow(true));
        container.child(UIComponents.label(this.getDescription()).color(Color.ofArgb(0xFF9A9A9A)));

        var slider = UIComponents.slider(Sizing.fill(100));
        slider.value(this.normalized());
        slider.message(value -> Component.literal(this.format(this.denormalize(Double.parseDouble(value)))));
        slider.onChanged().subscribe(value -> this.setValue(this.denormalize(value)));
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
            this.setValue(json.getAsDouble());
        }
    }

    @Override
    protected Double sanitize(Double value) {
        return this.clamp(value);
    }

    private double clamp(double value) {
        var clamped = Math.clamp(value, this.min, this.max);

        if (this.step > 0.0D) {
            clamped = Math.round((clamped - this.min) / this.step) * this.step + this.min;
        }

        return Math.clamp(clamped, this.min, this.max);
    }

    private double normalized() {
        if (this.max <= this.min) {
            return 0.0D;
        }

        return (this.getValue() - this.min) / (this.max - this.min);
    }

    private double denormalize(double normalized) {
        return this.min + normalized * (this.max - this.min);
    }

    private String format(double value) {
        var decimals = this.step >= 1.0D ? 0 : this.step >= 0.1D ? 1 : 2;
        return String.format(Locale.ROOT, "%." + decimals + "f%s", value, this.suffix);
    }
}
