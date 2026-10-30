package dev.mudkip.cake.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

public final class EnumSetting<E extends Enum<E>> extends ModuleSetting<E> {
    private final Class<E> enumType;
    private final Function<E, Component> formatter;

    public EnumSetting(
            String id,
            Component name,
            Component description,
            Class<E> type,
            Function<E, Component> formatter,
            E defaultValue
    ) {
        super(id, name, description, defaultValue);
        this.enumType = type;
        this.formatter = formatter;
    }

    @Override
    public UIComponent createControl() {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        container.child(UIComponents.label(this.getName()).shadow(true));
        container.child(UIComponents.label(this.getDescription()).color(Color.ofArgb(0xFF9A9A9A)));

        var button = UIComponents.button(this.formatter.apply(this.getValue()), this::cycle);
        button.horizontalSizing(Sizing.fixed(120));
        container.child(button);
        return container;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(this.getValue().name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            return;
        }

        try {
            this.setValue(Enum.valueOf(this.enumType, json.getAsString()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void cycle(ButtonComponent button) {
        var constants = this.enumType.getEnumConstants();
        var next = constants[(this.getValue().ordinal() + 1) % constants.length];
        this.setValue(next);
        button.setMessage(this.formatter.apply(next));
    }
}
