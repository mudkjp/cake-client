package dev.mudkip.cake;

import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;

public interface FontManagerAccess {
    FontSet cake$getFontSet(Identifier id);

    FontSet cake$getMissingFontSet();
}
