package dev.mudkip.cake.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.module.ModuleManager;
import dev.mudkip.cake.module.modules.CpsModule;
import dev.mudkip.cake.module.modules.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public final class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void cake$trackClicks(long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo callbackInfo) {
        if (handle == 0L
                || handle != this.minecraft.getWindow().handle()
                || action != InputConstants.PRESS
                || this.minecraft.gui.screen() != null
                || this.minecraft.gui.overlay() != null
                || this.minecraft.player == null) {
            return;
        }

        CpsModule.recordClick(buttonInfo.button());
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void cake$adjustZoom(long handle, double xOffset, double yOffset, CallbackInfo callbackInfo) {
        if (handle == 0L
                || handle != this.minecraft.getWindow().handle()
                || this.minecraft.gui.screen() != null
                || this.minecraft.gui.overlay() != null
                || this.minecraft.player == null
                || yOffset == 0.0D) {
            return;
        }

        var module = ModuleManager.get().get(ZoomModule.class);

        if (module == null || !module.isEnabled()) {
            return;
        }

        module.adjustZoom(yOffset);
        this.minecraft.getFramerateLimitTracker().onInputReceived();
        callbackInfo.cancel();
    }
}
