package dev.mudkip.cake.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.PacketComponentCodec;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(ComponentSerialization.class)
public abstract class FingerprintSerializationMixin {
    @WrapOperation(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            )
    )
    private static Codec<Component> cake$wrapComponentCodec(String name, Function<Codec<Component>, Codec<Component>> body, Operation<Codec<Component>> original) {
        return new PacketComponentCodec(original.call(name, body), () -> {
            var minecraft = Minecraft.getInstance();

            if (minecraft == null) {
                return false;
            }

            var module = ((MinecraftClientAccess) minecraft).cake$getActiveModule(FingerprintModule.class);
            return module != null && module.shouldMarkPackets();
        });
    }
}
