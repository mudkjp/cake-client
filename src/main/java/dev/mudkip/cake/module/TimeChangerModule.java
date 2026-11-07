package dev.mudkip.cake.module;

import dev.mudkip.cake.module.setting.IntSetting;

public final class TimeChangerModule extends ClientModule {
    private static final int DAY_LENGTH = 24000;

    private final IntSetting time;

    public TimeChangerModule(ModuleContext context) {
        super(ModuleDefinition.of("timechanger", ModuleCategory.RENDER), context);
        this.time = this.intSetting("time", 0, TimeChangerModule.DAY_LENGTH - 1, 6000, " ticks");
    }

    public int getTime() {
        return this.time.getValue();
    }

    public interface ClockAccess {
        void cake$setOverworld(boolean overworld);

        boolean cake$isOverworld();
    }
}
