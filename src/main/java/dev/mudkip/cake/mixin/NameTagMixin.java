package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.PlayerPrefixes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void cake$prependPlayerPrefix(Entity entity, CallbackInfoReturnable<Component> callback) {
        if (!(entity instanceof Player player)) {
            return;
        }

        var original = callback.getReturnValue();

        if (original == null) {
            return;
        }

        var prefix = PlayerPrefixes.aboveHead(player.getGameProfile());

        if (prefix != null) {
            callback.setReturnValue(Component.empty().append(prefix).append(original));
        }
    }
}
