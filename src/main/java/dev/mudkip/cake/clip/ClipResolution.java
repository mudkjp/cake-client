package dev.mudkip.cake.clip;

import net.minecraft.network.chat.Component;

public enum ClipResolution {
    P720("720p", 720),
    P1080("1080p", 1080),
    P1440("1440p", 1440),
    NATIVE("native", 0);

    private final String id;
    private final int height;

    ClipResolution(String id, int height) {
        this.id = id;
        this.height = height;
    }

    public String getId() {
        return this.id;
    }

    public int getHeight() {
        return this.height;
    }

    public int limit(int sourceHeight) {
        return this.height == 0 ? sourceHeight : Math.min(sourceHeight, this.height);
    }

    public Component getDisplayName() {
        return Component.translatable("cake.setting.replaybuffer.resolution." + this.id);
    }
}
