package dev.mudkip.cake.ui;

import dev.mudkip.cake.module.ClientModule;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import net.minecraft.network.chat.Component;

public final class ModuleWidgets {
    private ModuleWidgets() {

    }

    public static ButtonComponent enabledButton(ClientModule module, Runnable afterToggle) {
        var button = UIComponents.button(ModuleWidgets.label(module), component -> {
            if (module.isAvailable()) {
                module.setEnabled(!module.isEnabled());
            }

            ModuleWidgets.updateEnabledButton(component, module);
            afterToggle.run();
        });
        button.textShadow(false);
        ModuleWidgets.updateEnabledButton(button, module);
        return button;
    }

    public static void updateEnabledButton(ButtonComponent button, ClientModule module) {
        if (!module.isAvailable()) {
            button.setMessage(Component.translatable("cake.menu.unavailable"));
            button.active(false);
            button.renderer(UiStyles.UNAVAILABLE_BUTTON);
            return;
        }

        button.setMessage(ModuleWidgets.label(module));
        button.active(true);
        button.renderer(module.isEnabled() ? UiStyles.ENABLED_BUTTON : UiStyles.DISABLED_BUTTON);
    }

    private static Component label(ClientModule module) {
        if (!module.isAvailable()) {
            return Component.translatable("cake.menu.unavailable");
        }

        return Component.translatable(module.isEnabled() ? "cake.menu.enabled_state" : "cake.menu.disabled_state");
    }
}
