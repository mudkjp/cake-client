package dev.mudkip.cake.ui;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;

public final class ZoomedFlowLayout extends FlowLayout {
    private final float zoom;

    public ZoomedFlowLayout(Sizing horizontalSizing, Sizing verticalSizing, float zoom) {
        super(horizontalSizing, verticalSizing, FlowLayout.Algorithm.VERTICAL);
        this.zoom = zoom;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(this.zoom, this.zoom);

        try {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        } finally {
            pose.popMatrix();
        }
    }
}
