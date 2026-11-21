package dev.mudkip.cake.mixin;

//? if !modrinth {
import dev.mudkip.cake.badge.BadgeService;
//?}
import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.ModuleManager;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin implements MinecraftClientAccess {
    @Unique
    private @Nullable ModuleManager cake$moduleManager;

    @Override
    public @Nullable ModuleManager cake$getModuleManager() {
        return this.cake$moduleManager;
    }

    @Override
    public void cake$setModuleManager(ModuleManager moduleManager) {
        this.cake$moduleManager = moduleManager;
    }

    //? if !modrinth {
    @Unique
    private @Nullable BadgeService cake$badgeService;

    @Override
    public @Nullable BadgeService cake$getBadgeService() {
        return this.cake$badgeService;
    }

    @Override
    public void cake$setBadgeService(BadgeService badgeService) {
        this.cake$badgeService = badgeService;
    }
    //?}
}
