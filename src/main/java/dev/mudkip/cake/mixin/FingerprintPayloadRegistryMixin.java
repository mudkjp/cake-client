package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.PacketSource;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PayloadTypeRegistryImpl.class)
public abstract class FingerprintPayloadRegistryMixin {
    @Inject(
            method = "get(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void cake$dropInboundChannels(Identifier id, CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> callbackInfo) {
        this.cake$dropInboundChannel(id, callbackInfo);
    }

    @Inject(
            method = "get(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void cake$dropInboundChannelsWithDecodeCheck(Identifier id, boolean allowDecode, CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> callbackInfo) {
        this.cake$dropInboundChannel(id, callbackInfo);
    }

    @Unique
    private void cake$dropInboundChannel(Identifier id, CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> callbackInfo) {
        if (!PacketSource.isDecoding()) {
            return;
        }

        var registry = (PayloadTypeRegistryImpl<?>) (Object) this;

        if (registry.getFlow() != PacketFlow.CLIENTBOUND || !FingerprintModule.shouldDropInboundChannel(id)) {
            return;
        }

        callbackInfo.setReturnValue(null);
    }
}
