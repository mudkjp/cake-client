package dev.mudkip.cake.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.HudModule;
import dev.mudkip.cake.module.modules.HudModule.Anchor;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudEditorScreen extends BaseOwoScreen<FlowLayout> {
    private static final int PADDING = 4;
    private static final int ANCHOR_MARKER_SIZE = 7;
    private static final int ANCHOR_MARKER_INSET = 5;
    private static final double ANCHOR_HIT_RADIUS = 6.0D;
    private static final double SNAP_DISTANCE = 14.0D;
    private static final int SELECTION_OUTLINE = 0xFF6F9FD8;
    private static final int HOVER_OUTLINE = 0x60FFFFFF;
    private static final int ANCHOR_MARKER = 0x80FFFFFF;
    private static final int ANCHOR_MARKER_ACTIVE = 0xFFFFD166;
    private static final float DISABLED_ALPHA = 0.45F;

    private final ModuleManager moduleManager;
    private final List<HudModule> hudModules;
    private final @Nullable Screen returnScreen;
    private @Nullable HudModule selected;
    private @Nullable HudModule dragging;
    private @Nullable Anchor activeSnap;
    private @Nullable LabelComponent statusLabel;
    private boolean resizing;
    private double dragOffsetX;
    private double dragOffsetY;
    private double resizeStartX;
    private double resizeStartY;
    private double resizeStartScale;

    public HudEditorScreen(ModuleManager moduleManager, @Nullable Screen returnScreen, @Nullable HudModule selected) {
        super(Component.translatable("cake.hud.editor.title"));
        this.moduleManager = moduleManager;
        this.hudModules = HudEditorScreen.findHudModules(moduleManager);
        this.returnScreen = returnScreen;
        this.selected = selected;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.flat(0x66000000));
        root.padding(Insets.of(12));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM);

        var width = Math.max(1, Math.min(500, this.width - 24));
        var title = UIComponents.label(Component.translatable("cake.hud.editor.title"))
                .shadow(true)
                .horizontalSizing(Sizing.expand(100));
        var hint = UIComponents.label(Component.translatable("cake.hud.editor.hint"))
                .color(Color.ofArgb(UiStyles.MUTED_TEXT))
                .horizontalSizing(Sizing.fill(100));

        this.statusLabel = UIComponents.label(Component.empty())
                .color(Color.ofArgb(UiStyles.MUTED_TEXT));

        var reset = UIComponents.button(Component.translatable("cake.hud.editor.reset"), button -> this.resetAll());
        reset.renderer(UiStyles.SECONDARY_BUTTON);
        reset.textShadow(false);
        reset.horizontalSizing(Sizing.fixed(76));

        var done = UIComponents.button(Component.translatable("cake.hud.editor.done"), button -> this.onClose());
        done.renderer(UiStyles.SECONDARY_BUTTON);
        done.textShadow(false);
        done.horizontalSizing(Sizing.fixed(56));

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(8)
                .child(title)
                .child(this.statusLabel)
                .child(reset)
                .child(done);
        header.verticalAlignment(VerticalAlignment.CENTER);

        var bar = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content()).gap(4);
        bar.padding(Insets.of(8));
        bar.surface(Surface.flat(UiStyles.PANEL_BACKGROUND).and(Surface.outline(UiStyles.PANEL_OUTLINE)));
        bar.child(header);
        bar.child(hint);
        root.child(bar);

        this.updateStatus();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        var screenWidth = graphics.guiWidth();
        var screenHeight = graphics.guiHeight();
        var hoveredModule = this.moduleAt(mouseX, mouseY);
        var hoveredAnchor = this.selected == null ? null : this.anchorAt(mouseX, mouseY);
        var hoveredResize = this.selected != null && this.isResizeHandle(mouseX, mouseY);

        for (var module : this.hudModules) {
            var x = (int) Math.round(module.getX(screenWidth));
            var y = (int) Math.round(module.getY(screenHeight));
            var width = module.getWidth();
            var height = module.getHeight();
            HudOverlay.render(graphics, module, module.isEnabled() ? 1.0F : HudEditorScreen.DISABLED_ALPHA);

            if (module == this.selected || module == hoveredModule) {
                var color = module == this.selected ? HudEditorScreen.SELECTION_OUTLINE : HudEditorScreen.HOVER_OUTLINE;
                HudEditorScreen.drawOutline(
                        graphics,
                        x - HudEditorScreen.PADDING,
                        y - HudEditorScreen.PADDING,
                        width + HudEditorScreen.PADDING * 2,
                        height + HudEditorScreen.PADDING * 2,
                        color
                );
            }
        }

        this.drawAnchorMarkers(graphics, screenWidth, screenHeight, hoveredAnchor);

        if (this.activeSnap != null) {
            this.drawAnchorMarker(graphics, this.activeSnap, screenWidth, screenHeight, HudEditorScreen.ANCHOR_MARKER_ACTIVE);
        }

        if (this.selected != null) {
            this.drawResizeHandle(graphics, this.selected, screenWidth, screenHeight);
        }

        if (hoveredResize) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
        } else if (hoveredAnchor != null || hoveredModule != null) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (this.selected != null && this.isResizeHandle(click.x(), click.y())) {
                this.resizing = true;
                this.dragging = null;
                this.activeSnap = null;
                this.resizeStartX = this.selected.getX(this.width);
                this.resizeStartY = this.selected.getY(this.height);
                this.resizeStartScale = this.selected.getScale();
                return true;
            }

            if (this.selected != null) {
                var anchor = this.anchorAt(click.x(), click.y());

                if (anchor != null) {
                    this.selected.setLayout(anchor, 0.0D, 0.0D);
                    this.dragging = null;
                    this.activeSnap = null;
                    this.updateStatus();
                    return true;
                }
            }

            var module = this.moduleAt(click.x(), click.y());

            if (module != null) {
                this.selected = module;
                this.dragging = module;
                this.resizing = false;
                this.activeSnap = null;
                this.dragOffsetX = click.x() - module.getX(this.width);
                this.dragOffsetY = click.y() - module.getY(this.height);
                this.updateStatus();
                return true;
            }

            this.selected = null;
            this.dragging = null;
            this.resizing = false;
            this.activeSnap = null;
            this.updateStatus();
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (this.resizing) {
            this.resizeSelected(click.x(), click.y());
            return true;
        }

        if (this.dragging != null) {
            this.dragModule(click.x(), click.y());
            return true;
        }

        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        var handled = this.dragging != null || this.resizing;

        if (this.dragging != null) {
            if (this.activeSnap != null) {
                this.dragging.setLayout(this.activeSnap, 0.0D, 0.0D);
                this.updateStatus();
            }

            this.dragging = null;
        }

        this.resizing = false;
        this.activeSnap = null;

        return handled || super.mouseReleased(click);
    }

    @Override
    public void onClose() {
        this.moduleManager.saveIfDirty();
        this.minecraft.gui.setScreen(this.returnScreen);
    }

    private void dragModule(double mouseX, double mouseY) {
        var module = this.dragging;

        if (module == null) {
            return;
        }

        var width = module.getWidth();
        var height = module.getHeight();
        var x = mouseX - this.dragOffsetX;
        var y = mouseY - this.dragOffsetY;
        var anchor = module.getAnchor();
        module.setLayout(
                anchor,
                x - (this.width - width) * anchor.getXFactor(),
                y - (this.height - height) * anchor.getYFactor()
        );
        this.activeSnap = this.findSnapAnchor(x, y, width, height);
    }

    private void resizeSelected(double mouseX, double mouseY) {
        var module = this.selected;

        if (module == null) {
            return;
        }

        var naturalWidth = Math.max(1, module.getTextWidth());
        var naturalHeight = Math.max(1, this.minecraft.font.lineHeight);
        var scaleX = (mouseX - this.resizeStartX) / naturalWidth;
        var scaleY = (mouseY - this.resizeStartY) / naturalHeight;
        var scale = Math.abs(scaleX - this.resizeStartScale) >= Math.abs(scaleY - this.resizeStartScale)
                ? scaleX
                : scaleY;
        module.setScale(scale);

        var width = module.getWidth();
        var height = module.getHeight();
        var anchor = module.getAnchor();
        module.setLayout(
                anchor,
                this.resizeStartX - (this.width - width) * anchor.getXFactor(),
                this.resizeStartY - (this.height - height) * anchor.getYFactor()
        );
        this.updateStatus();
    }

    private @Nullable Anchor findSnapAnchor(double x, double y, int width, int height) {
        @Nullable Anchor nearest = null;
        var nearestDistance = HudEditorScreen.SNAP_DISTANCE;

        for (var anchor : Anchor.values()) {
            var pointX = x + width * anchor.getXFactor();
            var pointY = y + height * anchor.getYFactor();
            var screenX = this.width * anchor.getXFactor();
            var screenY = this.height * anchor.getYFactor();
            var distance = Math.hypot(pointX - screenX, pointY - screenY);

            if (distance <= nearestDistance) {
                nearest = anchor;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private @Nullable Anchor anchorAt(double mouseX, double mouseY) {
        for (var anchor : Anchor.values()) {
            var x = Math.max(
                    HudEditorScreen.ANCHOR_MARKER_INSET,
                    Math.min(this.width - HudEditorScreen.ANCHOR_MARKER_INSET, (int) Math.round(this.width * anchor.getXFactor()))
            );
            var y = Math.max(
                    HudEditorScreen.ANCHOR_MARKER_INSET,
                    Math.min(this.height - HudEditorScreen.ANCHOR_MARKER_INSET, (int) Math.round(this.height * anchor.getYFactor()))
            );

            if (Math.abs(mouseX - x) <= HudEditorScreen.ANCHOR_HIT_RADIUS
                    && Math.abs(mouseY - y) <= HudEditorScreen.ANCHOR_HIT_RADIUS) {
                return anchor;
            }
        }

        return null;
    }

    private boolean isResizeHandle(double mouseX, double mouseY) {
        var module = this.selected;

        if (module == null) {
            return false;
        }

        var x = module.getX(this.width) + module.getWidth();
        var y = module.getY(this.height) + module.getHeight();
        return mouseX >= x - 5.0D
                && mouseX <= x + 7.0D
                && mouseY >= y - 5.0D
                && mouseY <= y + 7.0D;
    }

    private void updateStatus() {
        if (this.statusLabel == null) {
            return;
        }

        if (this.selected == null) {
            this.statusLabel.text(Component.empty());
            return;
        }

        this.statusLabel.text(Component.translatable(
                "cake.hud.editor.status",
                this.selected.getName(),
                this.selected.getAnchor().getDisplayName(),
                String.format(Locale.ROOT, "%.2fx", this.selected.getScale())
        ));
    }

    private void drawAnchorMarkers(
            GuiGraphicsExtractor graphics,
            int screenWidth,
            int screenHeight,
            @Nullable Anchor hovered
    ) {
        for (var anchor : Anchor.values()) {
            var color = HudEditorScreen.ANCHOR_MARKER;

            if (this.selected != null && this.selected.getAnchor() == anchor) {
                color = HudEditorScreen.SELECTION_OUTLINE;
            }

            if (anchor == hovered) {
                color = 0xFFFFFFFF;
            }

            this.drawAnchorMarker(graphics, anchor, screenWidth, screenHeight, color);
        }
    }

    private void drawAnchorMarker(
            GuiGraphicsExtractor graphics,
            Anchor anchor,
            int screenWidth,
            int screenHeight,
            int color
    ) {
        var half = HudEditorScreen.ANCHOR_MARKER_SIZE / 2;
        var x = Math.max(
                HudEditorScreen.ANCHOR_MARKER_INSET,
                Math.min(screenWidth - HudEditorScreen.ANCHOR_MARKER_INSET, (int) Math.round(screenWidth * anchor.getXFactor()))
        );
        var y = Math.max(
                HudEditorScreen.ANCHOR_MARKER_INSET,
                Math.min(screenHeight - HudEditorScreen.ANCHOR_MARKER_INSET, (int) Math.round(screenHeight * anchor.getYFactor()))
        );
        graphics.fill(x - half, y - half, x + half + 1, y - half + 1, color);
        graphics.fill(x - half, y + half, x + half + 1, y + half + 1, color);
        graphics.fill(x - half, y - half, x - half + 1, y + half + 1, color);
        graphics.fill(x + half, y - half, x + half + 1, y + half + 1, color);
    }

    private void drawResizeHandle(GuiGraphicsExtractor graphics, HudModule module, int screenWidth, int screenHeight) {
        var x = (int) Math.round(module.getX(screenWidth)) + module.getWidth();
        var y = (int) Math.round(module.getY(screenHeight)) + module.getHeight();
        graphics.fill(x - 1, y - 1, x + 5, y + 5, HudEditorScreen.SELECTION_OUTLINE);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, 0xFF141414);
    }

    private void resetAll() {
        for (var module : this.hudModules) {
            module.resetLayout();
        }

        this.selected = null;
        this.dragging = null;
        this.resizing = false;
        this.activeSnap = null;
        this.updateStatus();
    }

    private @Nullable HudModule moduleAt(double mouseX, double mouseY) {
        for (var index = this.hudModules.size() - 1; index >= 0; index--) {
            var module = this.hudModules.get(index);
            var x = module.getX(this.width);
            var y = module.getY(this.height);
            var width = module.getWidth();
            var height = module.getHeight();

            if (mouseX >= x - HudEditorScreen.PADDING
                    && mouseX <= x + width + HudEditorScreen.PADDING
                    && mouseY >= y - HudEditorScreen.PADDING
                    && mouseY <= y + height + HudEditorScreen.PADDING) {
                return module;
            }
        }

        return null;
    }

    private static List<HudModule> findHudModules(ModuleManager moduleManager) {
        var modules = new ArrayList<HudModule>();

        for (var module : moduleManager.getModules()) {
            if (module instanceof HudModule hudModule && hudModule.isAvailable()) {
                modules.add(hudModule);
            }
        }

        return modules;
    }

    private static void drawOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
