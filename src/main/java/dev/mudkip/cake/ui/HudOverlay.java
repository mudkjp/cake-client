package dev.mudkip.cake.ui;

import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.HudModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class HudOverlay {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("cake", "hud");

    private HudOverlay() {

    }

    public static void register() {
        HudElementRegistry.addLast(HudOverlay.ID, HudOverlay::extractRenderState);
    }

    private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var minecraft = Minecraft.getInstance();

        if (minecraft.gui.hud.isHidden() || minecraft.gui.screen() instanceof HudEditorScreen) {
            return;
        }

        for (var module : ModuleManager.get().getModules()) {
            if (module instanceof HudModule hudModule && hudModule.isEnabled()) {
                HudOverlay.render(graphics, hudModule, 1.0F);
            }
        }
    }

    public static void render(GuiGraphicsExtractor graphics, HudModule module, float alpha) {
        var font = Minecraft.getInstance().font;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(
                (float) Math.round(module.getX(graphics.guiWidth())),
                (float) Math.round(module.getY(graphics.guiHeight()))
        );
        pose.scale((float) module.getScale(), (float) module.getScale());
        graphics.text(font, module.getText(), 0, 0, ARGB.multiplyAlpha(0xFFFFFFFF, alpha), true);
        pose.popMatrix();
    }
}
