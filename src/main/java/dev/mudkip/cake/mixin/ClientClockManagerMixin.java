package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.TimeChangerClockAccess;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
    @Inject(
            method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;",
            at = @At("RETURN")
    )
    private void cake$markOverworldClock(Holder<WorldClock> holder, CallbackInfoReturnable<ClientClockManager.ClientClockInstance> callbackInfo) {
        if (holder.unwrapKey().map(key -> key.equals(WorldClocks.OVERWORLD)).orElse(false)) {
            ((TimeChangerClockAccess) callbackInfo.getReturnValue()).cake$setOverworld(true);
        }
    }
}
