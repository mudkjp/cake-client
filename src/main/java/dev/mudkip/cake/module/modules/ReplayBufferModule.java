package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.clip.ClipAudioTap;
import dev.mudkip.cake.clip.ClipCodec;
import dev.mudkip.cake.clip.ClipEncoderPreference;
import dev.mudkip.cake.clip.ClipFps;
import dev.mudkip.cake.clip.ClipRecorder;
import dev.mudkip.cake.clip.ClipResolution;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.EnumSetting;
import dev.mudkip.cake.module.setting.IntSetting;
import dev.mudkip.cake.module.setting.StringSetting;

public final class ReplayBufferModule extends ClientModule {
    private final IntSetting duration;
    private final EnumSetting<ClipCodec> codec;
    private final EnumSetting<ClipFps> fps;
    private final EnumSetting<ClipResolution> resolution;
    private final IntSetting quality;
    private final EnumSetting<ClipEncoderPreference> encoder;
    private final BooleanSetting audio;
    private final StringSetting ffmpegPath;
    private final ClipAudioTap audioTap;
    private final ClipRecorder recorder;

    public ReplayBufferModule(ModuleContext context) {
        super(ModuleDefinition.of("replaybuffer", ModuleCategory.MISC), context);
        this.duration = this.intSetting("duration", 5, 180, 30, "s");
        this.codec = this.enumSetting("codec", ClipCodec.class, ClipCodec::getDisplayName, ClipCodec.HEVC);
        this.fps = this.enumSetting("fps", ClipFps.class, ClipFps::getDisplayName, ClipFps.FPS60);
        this.resolution = this.enumSetting(
                "resolution",
                ClipResolution.class,
                ClipResolution::getDisplayName,
                ClipResolution.P1080
        );
        this.quality = this.intSetting("quality", 18, 30, 24, "");
        this.encoder = this.enumSetting(
                "encoder",
                ClipEncoderPreference.class,
                ClipEncoderPreference::getDisplayName,
                ClipEncoderPreference.AUTO
        );
        this.audio = this.booleanSetting("audio", true);
        this.ffmpegPath = this.stringSetting("ffmpeg_path", "", 512);
        this.audioTap = new ClipAudioTap(this);
        this.recorder = new ClipRecorder(context.client(), this, this.audioTap);
    }

    public int getDuration() {
        return this.duration.getValue();
    }

    public ClipCodec getCodec() {
        return this.codec.getValue();
    }

    public ClipFps getFps() {
        return this.fps.getValue();
    }

    public ClipResolution getResolution() {
        return this.resolution.getValue();
    }

    public int getQuality() {
        return this.quality.getValue();
    }

    public ClipEncoderPreference getEncoderPreference() {
        return this.encoder.getValue();
    }

    public boolean isAudioEnabled() {
        return this.audio.getValue();
    }

    public String getFfmpegPath() {
        return this.ffmpegPath.getValue();
    }

    public ClipAudioTap getAudioTap() {
        return this.audioTap;
    }

    public void saveClip() {
        this.recorder.saveClip();
    }

    @Override
    protected void onEnable() {
        this.recorder.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        this.recorder.setEnabled(false);
    }

    @Override
    protected void onTick() {
        this.recorder.tick();
    }

    @Override
    protected void onShutdown() {
        this.recorder.close();
    }

    @Override
    protected void notifyChanged() {
        super.notifyChanged();

        if (this.recorder != null) {
            this.recorder.settingsChanged();
        }
    }
}
