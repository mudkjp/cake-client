package dev.mudkip.cake.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class ZoomedOwoScreen extends BaseOwoScreen<FlowLayout> {
    protected static final float ZOOM = 0.85F;

    protected ZoomedOwoScreen(Component title) {
        super(title);
    }

    protected static int layoutPixels(int physicalPixels) {
        return Math.max(1, Math.round(physicalPixels / ZoomedOwoScreen.ZOOM));
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        var adapter = OwoUIAdapter.<FlowLayout>create(this, (horizontalSizing, verticalSizing) ->
                new ZoomedFlowLayout(horizontalSizing, verticalSizing, ZoomedOwoScreen.ZOOM));
        adapter.moveAndResize(0, 0, this.layoutWidth(), this.layoutHeight());
        return adapter;
    }

    @Override
    protected void init() {
        super.init();

        if (this.uiAdapter != null
                && (this.uiAdapter.width() != this.layoutWidth() || this.uiAdapter.height() != this.layoutHeight())) {
            this.uiAdapter.moveAndResize(0, 0, this.layoutWidth(), this.layoutHeight());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, this.toLayout(mouseX), this.toLayout(mouseY), partialTicks);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(this.toLayout(click), doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return super.mouseReleased(this.toLayout(click));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        return super.mouseDragged(this.toLayout(click), deltaX / ZoomedOwoScreen.ZOOM, deltaY / ZoomedOwoScreen.ZOOM);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(this.toLayout(mouseX), this.toLayout(mouseY), horizontalAmount, verticalAmount);
    }

    protected final int layoutWidth() {
        return ZoomedOwoScreen.layoutPixels(this.width);
    }

    protected final int layoutHeight() {
        return ZoomedOwoScreen.layoutPixels(this.height);
    }

    private MouseButtonEvent toLayout(MouseButtonEvent click) {
        return new MouseButtonEvent(this.toLayout(click.x()), this.toLayout(click.y()), click.buttonInfo());
    }

    private int toLayout(double value) {
        return (int) Math.round(value / ZoomedOwoScreen.ZOOM);
    }
}
