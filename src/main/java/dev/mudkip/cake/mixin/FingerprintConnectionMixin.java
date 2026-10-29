package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.modules.FingerprintModule;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class FingerprintConnectionMixin {
    @Inject(method = "channelActive", at = @At("HEAD"))
    private void cake$trackServerAddress(ChannelHandlerContext context, CallbackInfo callbackInfo) {
        FingerprintModule.setServerAddress(context.channel().remoteAddress());
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void cake$blockModPayloads(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo callbackInfo) {
        if (packet instanceof ServerboundCustomPayloadPacket customPayload
                && FingerprintModule.shouldBlockPayload(customPayload.payload())) {
            callbackInfo.cancel();
        }
    }
}
