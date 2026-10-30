package dev.mudkip.cake.mixin;

import dev.mudkip.cake.CakeClientKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.Category.class)
public abstract class KeyMappingCategoryMixin {
    @Inject(method = "label", at = @At("HEAD"), cancellable = true)
    private void cake$label(CallbackInfoReturnable<Component> callbackInfo) {
        if (((KeyMapping.Category) (Object) this).equals(CakeClientKeybinds.getCategory())) {
            callbackInfo.setReturnValue(Component.literal("Cake Client"));
        }
    }
}
