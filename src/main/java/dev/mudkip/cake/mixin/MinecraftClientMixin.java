package dev.mudkip.cake.mixin;

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
}
