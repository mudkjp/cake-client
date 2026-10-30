package dev.mudkip.cake.mixin;

import dev.mudkip.cake.MinecraftClientAccess;
import dev.mudkip.cake.module.PacketComponentAccess;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeybindContents.class)
public abstract class FingerprintKeybindMixin implements PacketComponentAccess {
    @Shadow
    @Final
    private String name;

    @Unique
    private boolean cake$fromPacket;

    @Inject(method = "getNestedComponent", at = @At("HEAD"), cancellable = true)
    private void cake$hideKeybind(CallbackInfoReturnable<Component> callbackInfo) {
        var module = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getActiveModule(FingerprintModule.class);

        if (this.cake$fromPacket && module != null && module.shouldHideKeybinds()) {
            callbackInfo.setReturnValue(module.spoofKeybind(this.name));
        }
    }

    @Override
    public void cake$setFromPacket() {
        this.cake$fromPacket = true;
    }
}
