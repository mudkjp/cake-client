package dev.mudkip.cake.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.HudModule;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ModuleDetailsScreen extends ZoomedOwoScreen {
    private static final int PANEL_MAX_WIDTH = ZoomedOwoScreen.layoutPixels(460);
    private static final int PANEL_MAX_HEIGHT = ZoomedOwoScreen.layoutPixels(420);

    private final ClientModule module;
    private ButtonComponent keyButton;
    private LabelComponent conflictLabel;
    private boolean capturingKey;

    public ModuleDetailsScreen(ClientModule module) {
        super(module.getName());
        this.module = module;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.BLANK);
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        var width = Math.min(PANEL_MAX_WIDTH, Math.max(1, this.layoutWidth() - ZoomedOwoScreen.layoutPixels(32)));
        var height = Math.min(PANEL_MAX_HEIGHT, Math.max(1, this.layoutHeight() - ZoomedOwoScreen.layoutPixels(32)));

        var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height))
                .gap(8);
        panel.padding(Insets.of(12));
        panel.surface(Surface.flat(UiStyles.PANEL_BACKGROUND).and(Surface.outline(UiStyles.PANEL_OUTLINE)));

        var back = UIComponents.button(Component.translatable("cake.menu.back"), button -> this.onClose());
        back.renderer(UiStyles.SECONDARY_BUTTON);
        back.textShadow(false);
        back.horizontalSizing(Sizing.fixed(56));

        var icon = UIComponents.texture(this.module.getIcon(), 0, 0, 128, 128, 128, 128);
        icon.horizontalSizing(Sizing.fixed(24));
        icon.verticalSizing(Sizing.fixed(24));
        icon.blend(true);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(8)
                .child(icon)
                .child(UIComponents.label(this.module.getName())
                        .shadow(true)
                        .horizontalSizing(Sizing.expand(100)))
                .child(back);
        header.verticalAlignment(VerticalAlignment.CENTER);

        panel.child(header);
        panel.child(UIComponents.label(this.module.getCategory().getDisplayName())
                .color(Color.ofArgb(this.module.getCategory().getColor())));

        var enabled = ModuleWidgets.enabledButton(this.module, () -> {
        });
        enabled.horizontalSizing(Sizing.fill(100));
        panel.child(enabled);

        if (this.module instanceof HudModule) {
            var editHud = UIComponents.button(
                    Component.translatable("cake.menu.edit_hud"),
                    button -> this.minecraft.gui.setScreen(new HudEditorScreen(this, (HudModule) this.module))
            );
            editHud.renderer(UiStyles.SECONDARY_BUTTON);
            editHud.textShadow(false);
            editHud.horizontalSizing(Sizing.fill(100));
            panel.child(editHud);
        }

        panel.child(UIComponents.label(Component.translatable("cake.menu.keybind"))
                .color(Color.ofArgb(UiStyles.MUTED_TEXT)));

        this.keyButton = UIComponents.button(this.keyLabel(), button -> this.startCapture());
        this.keyButton.renderer(UiStyles.SECONDARY_BUTTON);
        this.keyButton.textShadow(false);
        this.keyButton.horizontalSizing(Sizing.expand(100));

        var clear = UIComponents.button(Component.translatable("cake.menu.clear"), button -> this.clearKeybind());
        clear.renderer(UiStyles.SECONDARY_BUTTON);
        clear.textShadow(false);
        clear.horizontalSizing(Sizing.fixed(56));

        var keyRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .child(this.keyButton)
                .child(clear);
        keyRow.verticalAlignment(VerticalAlignment.CENTER);
        panel.child(keyRow);

        this.conflictLabel = UIComponents.label(Component.empty())
                .color(Color.ofArgb(0xFFFF8080));
        panel.child(this.conflictLabel);

        var settings = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(10);

        if (this.module.getSettings().isEmpty()) {
            settings.child(UIComponents.label(Component.translatable("cake.menu.no_settings"))
                    .color(Color.ofArgb(UiStyles.MUTED_TEXT)));
        } else {
            settings.child(UIComponents.label(Component.translatable("cake.menu.settings"))
                    .color(Color.ofArgb(UiStyles.MUTED_TEXT)));

            for (var setting : this.module.getSettings()) {
                settings.child(setting.createControl());
            }
        }

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), settings)
                .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(UiStyles.SCROLLBAR)))
                .scrollbarThiccness(4)
                .scrollStep(0);
        panel.child(scroll);
        root.child(panel);

        this.updateKeyButton();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.capturingKey) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                this.capturingKey = false;
                this.updateKeyButton();
                return true;
            }

            this.module.getKeyMapping().setKey(InputConstants.getKey(event));
            this.minecraft.options.save();
            this.capturingKey = false;
            this.updateKeyButton();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModuleManager.get().saveIfDirty();
        this.minecraft.gui.setScreen(new ModMenuScreen());
    }

    private Component keyLabel() {
        return this.module.getKeyMapping().isUnbound()
                ? Component.translatable("cake.menu.unbound")
                : this.module.getKeyMapping().getTranslatedKeyMessage();
    }

    private void startCapture() {
        this.capturingKey = true;
        this.updateKeyButton();
    }

    private void clearKeybind() {
        this.capturingKey = false;
        this.module.getKeyMapping().setKey(InputConstants.UNKNOWN);
        this.minecraft.options.save();
        this.updateKeyButton();
    }

    private void updateKeyButton() {
        if (this.keyButton == null) {
            return;
        }

        this.keyButton.setMessage(this.capturingKey
                ? Component.translatable("cake.menu.press_key")
                : this.keyLabel());
        this.updateConflictLabel();
    }

    private void updateConflictLabel() {
        if (this.conflictLabel == null) {
            return;
        }

        var conflict = this.findConflict();
        this.conflictLabel.text(conflict == null
                ? Component.empty()
                : Component.translatable("cake.menu.key_conflict", Component.translatable(conflict.getName())));
    }

    private @Nullable KeyMapping findConflict() {
        var keyMapping = this.module.getKeyMapping();

        if (keyMapping.isUnbound()) {
            return null;
        }

        var key = keyMapping.saveString();

        for (var mapping : this.minecraft.options.keyMappings) {
            if (mapping != keyMapping && !mapping.isUnbound() && mapping.saveString().equals(key)) {
                return mapping;
            }
        }

        return null;
    }
}
