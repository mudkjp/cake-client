package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.setting.IntSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TimeChangerModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/timechanger.png");
    private static final int DAY_LENGTH = 24000;

    private final IntSetting time;

    public TimeChangerModule() {
        super(
                "timechanger",
                Component.translatable("cake.module.timechanger.name"),
                Component.translatable("cake.module.timechanger.description"),
                ModuleCategory.RENDER,
                TimeChangerModule.ICON
        );
        this.time = this.setting(new IntSetting(
                "time",
                Component.translatable("cake.setting.timechanger.time.name"),
                Component.translatable("cake.setting.timechanger.time.description"),
                0,
                TimeChangerModule.DAY_LENGTH - 1,
                6000,
                " ticks"
        ));
    }

    public int getTime() {
        return this.time.getValue();
    }
}
