package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.modules.FingerprintModule;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractChanneledNetworkAddon.class)
public abstract class FingerprintChannelAddonMixin {
    @Inject(
            method = "handle(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cake$dropInboundChannels(CustomPacketPayload payload, CallbackInfoReturnable<Boolean> callbackInfo) {
        var addon = (Object) this;

        if (!(addon instanceof ClientPlayNetworkAddon) && !(addon instanceof ClientConfigurationNetworkAddon)) {
            return;
        }

        if (FingerprintModule.shouldDropInboundChannel(payload.type().id())) {
            callbackInfo.setReturnValue(false);
        }
    }
}
