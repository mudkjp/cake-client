package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.TimeChangerClockAccess;
import dev.mudkip.cake.module.modules.TimeChangerModule;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockInstanceMixin implements TimeChangerClockAccess {
    @Shadow
    private long totalTicks;
    @Unique
    private boolean cake$overworld;

    @Override
    public void cake$setOverworld(boolean overworld) {
        this.cake$overworld = overworld;
    }

    @Override
    public boolean cake$isOverworld() {
        return this.cake$overworld;
    }

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void cake$overrideTotalTicks(CallbackInfoReturnable<Long> callbackInfo) {
        var module = ModuleManager.get().get(TimeChangerModule.class);

        if (this.cake$overworld && module != null && module.isEnabled()) {
            var dayStart = Math.floorDiv(this.totalTicks, 24000L) * 24000L;
            callbackInfo.setReturnValue(dayStart + module.getTime());
        }
    }

    @Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
    private void cake$overridePartialTick(CallbackInfoReturnable<Float> callbackInfo) {
        var module = ModuleManager.get().get(TimeChangerModule.class);

        if (this.cake$overworld && module != null && module.isEnabled()) {
            callbackInfo.setReturnValue(0.0F);
        }
    }
}
