package dev.mudkip.cake.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mudkip.cake.module.PacketSource;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketDecoder.class)
public abstract class FingerprintPacketDecoderMixin {
    @WrapOperation(
            method = "decode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object cake$enterDecode(StreamCodec<?, ?> codec, Object input, Operation<Object> original) {
        PacketSource.enter();

        try {
            return original.call(codec, input);
        } finally {
            PacketSource.exit();
        }
    }
}
