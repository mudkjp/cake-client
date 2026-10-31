package dev.mudkip.cake.clip;

import net.minecraft.network.chat.Component;

public enum ClipEncoderPreference {
    AUTO("auto"),
    SOFTWARE("software"),
    NVENC("nvenc"),
    QSV("qsv"),
    AMF("amf"),
    VAAPI("vaapi"),
    VIDEOTOOLBOX("videotoolbox");

    private final String id;

    ClipEncoderPreference(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public Component getDisplayName() {
        return Component.translatable("cake.setting.replaybuffer.encoder." + this.id);
    }
}
