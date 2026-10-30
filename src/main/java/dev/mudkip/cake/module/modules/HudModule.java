package dev.mudkip.cake.module.modules;

import com.google.gson.JsonObject;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.server.ServerIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public abstract class HudModule extends ClientModule {
    public static final double MIN_SCALE = 0.5D;
    public static final double MAX_SCALE = 3.0D;

    private final Anchor defaultAnchor;
    private final double defaultOffsetX;
    private final double defaultOffsetY;
    private Anchor anchor;
    private double offsetX;
    private double offsetY;
    private double scale;

    protected HudModule(String id, Component name, Component description, ModuleCategory category, Identifier icon, Anchor anchor, double offsetX, double offsetY) {
        this(id, name, description, category, icon, null, anchor, offsetX, offsetY);
    }

    protected HudModule(String id, Component name, Component description, ModuleCategory category, Identifier icon, ServerIntegration integration, Anchor anchor, double offsetX, double offsetY) {
        super(id, name, description, category, icon, integration);
        this.defaultAnchor = anchor;
        this.defaultOffsetX = offsetX;
        this.defaultOffsetY = offsetY;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scale = 1.0D;
    }

    public abstract String getText();

    public int getTextWidth() {
        return Minecraft.getInstance().font.width(this.getText());
    }

    public int getWidth() {
        return Math.max(1, (int) Math.round(this.getTextWidth() * this.scale));
    }

    public int getHeight() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().font.lineHeight * this.scale));
    }

    public double getX(int screenWidth) {
        return (screenWidth - this.getWidth()) * this.anchor.getXFactor() + this.offsetX;
    }

    public double getY(int screenHeight) {
        return (screenHeight - this.getHeight()) * this.anchor.getYFactor() + this.offsetY;
    }

    public Anchor getAnchor() {
        return this.anchor;
    }

    public double getOffsetX() {
        return this.offsetX;
    }

    public double getOffsetY() {
        return this.offsetY;
    }

    public double getScale() {
        return this.scale;
    }

    public void setLayout(Anchor anchor, double offsetX, double offsetY) {
        if (this.anchor == anchor && this.offsetX == offsetX && this.offsetY == offsetY) {
            return;
        }

        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.notifyChanged();
    }

    public void setScale(double scale) {
        var clamped = Math.clamp(scale, HudModule.MIN_SCALE, HudModule.MAX_SCALE);

        if (this.scale == clamped) {
            return;
        }

        this.scale = clamped;
        this.notifyChanged();
    }

    public void resetLayout() {
        this.setLayout(this.defaultAnchor, this.defaultOffsetX, this.defaultOffsetY);
        this.setScale(1.0D);
    }

    @Override
    public JsonObject toJson() {
        var json = super.toJson();
        var hud = new JsonObject();
        hud.addProperty("anchor", this.anchor.name());
        hud.addProperty("x", this.offsetX);
        hud.addProperty("y", this.offsetY);
        hud.addProperty("scale", this.scale);
        json.add("hud", hud);
        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        super.fromJson(json);

        var hud = json.getAsJsonObject("hud");

        if (hud == null) {
            return;
        }

        if (hud.has("anchor")) {
            try {
                this.anchor = Anchor.valueOf(hud.get("anchor").getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (hud.has("x")) {
            this.offsetX = hud.get("x").getAsDouble();
        }

        if (hud.has("y")) {
            this.offsetY = hud.get("y").getAsDouble();
        }

        if (hud.has("scale")) {
            this.scale = Math.clamp(hud.get("scale").getAsDouble(), HudModule.MIN_SCALE, HudModule.MAX_SCALE);
        }
    }

    public enum Anchor {
        TOP_LEFT(0.0D, 0.0D, "top_left"),
        TOP_CENTER(0.5D, 0.0D, "top_center"),
        TOP_RIGHT(1.0D, 0.0D, "top_right"),
        MIDDLE_LEFT(0.0D, 0.5D, "middle_left"),
        MIDDLE_CENTER(0.5D, 0.5D, "middle_center"),
        MIDDLE_RIGHT(1.0D, 0.5D, "middle_right"),
        BOTTOM_LEFT(0.0D, 1.0D, "bottom_left"),
        BOTTOM_CENTER(0.5D, 1.0D, "bottom_center"),
        BOTTOM_RIGHT(1.0D, 1.0D, "bottom_right");

        private final double xFactor;
        private final double yFactor;
        private final String id;

        Anchor(double xFactor, double yFactor, String id) {
            this.xFactor = xFactor;
            this.yFactor = yFactor;
            this.id = id;
        }

        public double getXFactor() {
            return this.xFactor;
        }

        public double getYFactor() {
            return this.yFactor;
        }

        public Component getDisplayName() {
            return Component.translatable("cake.hud.anchor." + this.id);
        }

        public static Anchor fromCenter(double centerX, double centerY, int screenWidth, int screenHeight) {
            var column = centerX < screenWidth / 3.0D ? 0 : centerX > screenWidth * 2.0D / 3.0D ? 2 : 1;
            var row = centerY < screenHeight / 3.0D ? 0 : centerY > screenHeight * 2.0D / 3.0D ? 2 : 1;
            return Anchor.values()[row * 3 + column];
        }
    }
}
