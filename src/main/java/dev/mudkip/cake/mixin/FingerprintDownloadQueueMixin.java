package dev.mudkip.cake.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;
import java.util.UUID;

@Mixin(DownloadQueue.class)
public abstract class FingerprintDownloadQueueMixin {
    @Shadow
    @Final
    private Path cacheDir;

    @ModifyExpressionValue(
            method = "lambda$runDownload$0",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;")
    )
    private Path cake$isolatePackCache(Path original, @Local(argsOnly = true) UUID id) {
        var minecraft = Minecraft.getInstance();

        if (!FingerprintModule.shouldIsolatePackCache() || minecraft == null) {
            return original;
        }

        return this.cacheDir.resolve(minecraft.getUser().getProfileId().toString()).resolve(id.toString());
    }
}
