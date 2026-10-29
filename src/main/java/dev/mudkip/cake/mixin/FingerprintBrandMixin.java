package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public abstract class FingerprintBrandMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void cake$spoofBrand(CallbackInfoReturnable<String> callbackInfo) {
        if (FingerprintModule.shouldSpoofBrand()) {
            callbackInfo.setReturnValue(ClientBrandRetriever.VANILLA_NAME);
        }
    }
}
