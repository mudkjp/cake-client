package dev.mudkip.cake.ui;

import dev.mudkip.cake.FontManagerAccess;
import dev.mudkip.cake.MinecraftFontAccess;
import dev.mudkip.cake.module.modules.SmoothFontModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

public final class MenuFonts {
    private static final Identifier SMALL_FONT = Identifier.fromNamespaceAndPath("cake", "smooth_small");

    private MenuFonts() {

    }

    public static Font smallFont() {
        var minecraft = Minecraft.getInstance();
        var fontManager = ((MinecraftFontAccess) minecraft).cake$getFontManager();

        if (fontManager instanceof FontManagerAccess manager) {
            return SmoothFontModule.createFont(manager, MenuFonts.SMALL_FONT, false);
        }

        return minecraft.font;
    }
}
