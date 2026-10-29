package dev.mudkip.cake.mixin;

import dev.mudkip.cake.MinecraftFontAccess;
import dev.mudkip.cake.module.modules.SmoothFontModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFontMixin implements MinecraftFontAccess {
    @Shadow
    @Mutable
    private Font font;
    @Shadow
    @Mutable
    private Font fontFilterFishy;
    @Shadow
    private FontManager fontManager;

    @Inject(method = "updateFontOptions", at = @At("TAIL"))
    private void cake$applySmoothFont(CallbackInfo callbackInfo) {
        SmoothFontModule.onFontReload(this);
    }

    @Override
    public Font cake$getFont() {
        return this.font;
    }

    @Override
    public void cake$setFont(Font font) {
        this.font = font;
    }

    @Override
    public Font cake$getFontFilterFishy() {
        return this.fontFilterFishy;
    }

    @Override
    public void cake$setFontFilterFishy(Font font) {
        this.fontFilterFishy = font;
    }

    @Override
    public FontManager cake$getFontManager() {
        return this.fontManager;
    }
}
