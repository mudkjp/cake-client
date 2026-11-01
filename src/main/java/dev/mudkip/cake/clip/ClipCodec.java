package dev.mudkip.cake.clip;

import net.minecraft.network.chat.Component;

public enum ClipCodec {
    H264("h264"),
    HEVC("hevc"),
    AV1("av1");

    private final String id;

    ClipCodec(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public boolean isHevc() {
        return this == ClipCodec.HEVC;
    }

    public boolean isAv1() {
        return this == ClipCodec.AV1;
    }

    public String encoderName(String vendor) {
        return this.id + "_" + vendor;
    }

    public String profile() {
        return switch (this) {
            case H264 -> "high";
            case HEVC, AV1 -> "main";
        };
    }

    public Component getDisplayName() {
        return Component.translatable("cake.setting.replaybuffer.codec." + this.id);
    }
}
