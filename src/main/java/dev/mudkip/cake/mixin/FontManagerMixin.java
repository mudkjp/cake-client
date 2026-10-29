package dev.mudkip.cake.mixin;

import dev.mudkip.cake.FontManagerAccess;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(FontManager.class)
public abstract class FontManagerMixin implements FontManagerAccess {
    @Shadow
    @Final
    private Map<Identifier, FontSet> fontSets;
    @Shadow
    @Final
    private FontSet missingFontSet;

    @Override
    public FontSet cake$getFontSet(Identifier id) {
        return this.fontSets.get(id);
    }

    @Override
    public FontSet cake$getMissingFontSet() {
        return this.missingFontSet;
    }
}
