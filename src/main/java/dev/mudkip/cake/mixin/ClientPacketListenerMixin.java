package dev.mudkip.cake.mixin;

import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.PingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void cake$recordPong(ClientboundPongResponsePacket packet, CallbackInfo callbackInfo) {
        var module = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getModule(PingModule.class);

        if (module != null) {
            module.recordPong(packet.time());
        }
    }
}
