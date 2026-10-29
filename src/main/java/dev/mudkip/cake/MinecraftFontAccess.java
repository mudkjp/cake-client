package dev.mudkip.cake;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;

public interface MinecraftFontAccess {
    Font cake$getFont();

    void cake$setFont(Font font);

    Font cake$getFontFilterFishy();

    void cake$setFontFilterFishy(Font font);

    FontManager cake$getFontManager();
}
