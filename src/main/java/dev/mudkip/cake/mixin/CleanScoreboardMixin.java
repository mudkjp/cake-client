package dev.mudkip.cake.mixin;

import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.modules.CleanScoreboardModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerScoreEntry.class)
public abstract class CleanScoreboardMixin {
    @Inject(method = "formatValue", at = @At("HEAD"), cancellable = true)
    private void cake$hideScoreboardNumbers(NumberFormat format, CallbackInfoReturnable<MutableComponent> callbackInfo) {
        var module = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getActiveModule(CleanScoreboardModule.class);

        if (module != null) {
            callbackInfo.setReturnValue(Component.empty());
        }
    }
}
