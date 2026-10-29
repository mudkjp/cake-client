package dev.mudkip.cake.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public abstract class FingerprintLanguageMixin {
    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void cake$beginTranslationScan(ResourceManager resourceManager, List<String> languages, boolean rightToLeft, CallbackInfoReturnable<ClientLanguage> callbackInfo) {
        FingerprintModule.beginTranslationScan();
    }

    @Inject(method = "loadFrom", at = @At("RETURN"))
    private static void cake$finishTranslationScan(ResourceManager resourceManager, List<String> languages, boolean rightToLeft, CallbackInfoReturnable<ClientLanguage> callbackInfo) {
        FingerprintModule.finishTranslationScan();
    }

    @WrapOperation(
            method = "appendFrom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"
            )
    )
    private static void cake$recordTranslations(InputStream stream, BiConsumer<String, String> output, Operation<Void> original, @Local Resource resource) {
        if (!FingerprintModule.isTrustedTranslationPack(resource.sourcePackId())) {
            original.call(stream, output);
            return;
        }

        original.call(stream, (BiConsumer<String, String>) (key, value) -> {
            FingerprintModule.recordTranslationKey(key);
            output.accept(key, value);
        });
    }
}
