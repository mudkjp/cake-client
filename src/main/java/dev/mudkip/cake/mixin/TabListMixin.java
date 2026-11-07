package dev.mudkip.cake.mixin;

import dev.mudkip.cake.module.PlayerPrefixes;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void cake$prependPlayerPrefix(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        var original = callback.getReturnValue();

        if (original == null) {
            return;
        }

        var prefix = PlayerPrefixes.tabList(info.getProfile());

        if (prefix != null) {
            callback.setReturnValue(Component.empty().append(prefix).append(original));
        }
    }
}
