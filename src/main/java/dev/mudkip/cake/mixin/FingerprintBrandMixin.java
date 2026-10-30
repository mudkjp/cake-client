package dev.mudkip.cake.mixin;

import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public abstract class FingerprintBrandMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void cake$spoofBrand(CallbackInfoReturnable<String> callbackInfo) {
        var minecraft = Minecraft.getInstance();
        var module = minecraft == null ? null : ((MinecraftClientAccess) minecraft).cake$getActiveModule(FingerprintModule.class);

        if (module != null && module.shouldSpoofBrand()) {
            callbackInfo.setReturnValue(ClientBrandRetriever.VANILLA_NAME);
        }
    }
}
