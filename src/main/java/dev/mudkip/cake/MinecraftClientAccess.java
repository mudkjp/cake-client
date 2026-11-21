package dev.mudkip.cake;

//? if !modrinth {
import dev.mudkip.cake.badge.BadgeService;
//?}
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleManager;
import org.jspecify.annotations.Nullable;

public interface MinecraftClientAccess {
    @Nullable ModuleManager cake$getModuleManager();

    void cake$setModuleManager(ModuleManager moduleManager);

    //? if !modrinth {
    @Nullable BadgeService cake$getBadgeService();

    void cake$setBadgeService(BadgeService badgeService);
    //?}

    default <T extends ClientModule> @Nullable T cake$getModule(Class<T> type) {
        var modules = this.cake$getModuleManager();
        return modules == null ? null : modules.getModule(type);
    }

    default <T extends ClientModule> @Nullable T cake$getActiveModule(Class<T> type) {
        var module = this.cake$getModule(type);
        return module != null && module.isEnabled() ? module : null;
    }
}
