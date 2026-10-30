package dev.mudkip.cake.ui;

import dev.mudkip.cake.MinecraftFontAccess;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public abstract class MenuOwoScreen extends BaseOwoScreen<FlowLayout> {
    protected Font menuFont;

    protected MenuOwoScreen(Component title) {
        super(title);
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void init() {
        this.menuFont = MenuFonts.smallFont();
        this.withMenuFont(() -> super.init());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.withMenuFont(() -> super.extractRenderState(graphics, mouseX, mouseY, partialTicks));
    }

    protected final void withMenuFont(Runnable action) {
        var access = (MinecraftFontAccess) Minecraft.getInstance();
        var previous = access.cake$getFont();
        access.cake$setFont(this.menuFont);

        try {
            action.run();
        } finally {
            access.cake$setFont(previous);
        }
    }
}
