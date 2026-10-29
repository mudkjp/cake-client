package dev.mudkip.cake.module;

import net.minecraft.network.chat.Component;

public enum ModuleCategory {
    COMBAT("combat", 0xFFD46A6A),
    MOVEMENT("movement", 0xFF6FBF8A),
    RENDER("render", 0xFF6F9FD8),
    PLAYER("player", 0xFFB58AD8),
    WORLD("world", 0xFF6FC4C4),
    MISC("misc", 0xFFA0A0A0);

    private final String id;
    private final int color;

    ModuleCategory(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return this.id;
    }

    public int getColor() {
        return this.color;
    }

    public Component getDisplayName() {
        return Component.translatable("cake.category." + this.id);
    }
}
