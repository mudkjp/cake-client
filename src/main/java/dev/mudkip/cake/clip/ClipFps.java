package dev.mudkip.cake.clip;

import net.minecraft.network.chat.Component;

public enum ClipFps {
    FPS30(30),
    FPS60(60);

    private final int value;

    ClipFps(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    public String getId() {
        return Integer.toString(this.value);
    }

    public Component getDisplayName() {
        return Component.translatable("cake.setting.replaybuffer.fps." + this.getId());
    }
}
