package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.CleanScoreboardModule;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerScoreEntry.class)
public final class CleanScoreboardMixin {
    @Inject(method = "formatValue", at = @At("HEAD"), cancellable = true)
    private void cake$hideScoreboardNumbers(NumberFormat format, CallbackInfoReturnable<MutableComponent> callbackInfo) {
        var module = ModuleManager.get().get(CleanScoreboardModule.class);

        if (module != null && module.isEnabled()) {
            callbackInfo.setReturnValue(Component.empty());
        }
    }
}
