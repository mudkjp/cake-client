package dev.mudkip.cake.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.network.chat.Component;

public abstract class MenuOwoScreen extends BaseOwoScreen<FlowLayout> {
    public static final int PANEL_BACKGROUND = 0xB8141414;
    public static final int PANEL_OUTLINE = 0x40FFFFFF;
    public static final int CARD_BACKGROUND = 0xCC202020;
    public static final int CARD_OUTLINE = 0x30FFFFFF;
    public static final int SEARCH_BACKGROUND = 0xCC202020;
    public static final int SEARCH_OUTLINE = 0x30FFFFFF;
    public static final int MUTED_TEXT = 0xFFB0B0B0;
    public static final int ALL_ACCENT = 0xFF6F9FD8;
    public static final int SERVER_ACCENT = 0xFFE0A458;
    public static final int SCROLLBAR = 0x60FFFFFF;
    public static final ButtonComponent.Renderer SECONDARY_BUTTON = (context, button, delta) -> {
        var background = button.isHovered() ? 0xCC383838 : 0xCC2A2A2A;
        MenuOwoScreen.drawButton(context, button, background);
    };
    public static final ButtonComponent.Renderer CLOSE_BUTTON = (context, button, delta) -> {
        if (button.isHovered()) {
            context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), 0x30FFFFFF);
        }
    };
    public static final ButtonComponent.Renderer ENABLED_BUTTON = (context, button, delta) -> {
        MenuOwoScreen.drawButton(context, button, button.isHovered() ? 0xFF4FA875 : 0xFF3F8F63);
    };
    public static final ButtonComponent.Renderer DISABLED_BUTTON = (context, button, delta) -> {
        MenuOwoScreen.drawButton(context, button, button.isHovered() ? 0xFFA84F63 : 0xFF8F3F4F);
    };
    public static final ButtonComponent.Renderer UNAVAILABLE_BUTTON = (context, button, delta) -> {
        MenuOwoScreen.drawButton(context, button, 0xCC242424);
    };

    protected MenuOwoScreen(Component title) {
        super(title);
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawButton(OwoUIGraphics context, ButtonComponent button, int background) {
        context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), background);
        context.drawRectOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), 0x40FFFFFF);
    }
}
