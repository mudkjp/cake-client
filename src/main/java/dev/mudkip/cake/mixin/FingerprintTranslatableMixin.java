package dev.mudkip.cake.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.PacketComponentAccess;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public abstract class FingerprintTranslatableMixin implements PacketComponentAccess {
    @Unique
    private boolean cake$fromPacket;

    @WrapOperation(
            method = "decompose",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"
            )
    )
    private String cake$hideTranslation(Language language, String key, Operation<String> original) {
        var module = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getActiveModule(FingerprintModule.class);
        return module != null && module.shouldHideTranslation(this.cake$fromPacket, key) ? key : original.call(language, key);
    }

    @WrapOperation(
            method = "decompose",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
            )
    )
    private String cake$hideTranslationWithFallback(Language language, String key, String fallback, Operation<String> original) {
        var module = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getActiveModule(FingerprintModule.class);
        return module != null && module.shouldHideTranslation(this.cake$fromPacket, key)
                ? fallback
                : original.call(language, key, fallback);
    }

    @Override
    public void cake$setFromPacket() {
        this.cake$fromPacket = true;
    }
}
