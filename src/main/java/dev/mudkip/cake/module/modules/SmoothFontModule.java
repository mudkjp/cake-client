package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.FontManagerAccess;
import dev.mudkip.cake.MinecraftFontAccess;
import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class SmoothFontModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/smoothfont.png");
    private static final Identifier SMOOTH_FONT = Identifier.fromNamespaceAndPath("cake", "smooth");
    private static final String LEGACY_PACK_ID = "cakeclient:smooth_font";
    private static final String LEGACY_CAKE_PACK_ID = "cake:smooth_font";
    private static boolean legacyPackCleaned;

    public SmoothFontModule() {
        super(
                "smoothfont",
                Component.translatable("cake.module.smoothfont.name"),
                Component.translatable("cake.module.smoothfont.description"),
                ModuleCategory.RENDER,
                SmoothFontModule.ICON
        );
    }

    public static void cleanupLegacyPack() {
        if (SmoothFontModule.legacyPackCleaned) {
            return;
        }

        var minecraft = Minecraft.getInstance();

        if (minecraft == null || minecraft.options == null) {
            return;
        }

        var removed = minecraft.options.resourcePacks.removeIf(SmoothFontModule::isLegacyPack);
        removed |= minecraft.options.incompatibleResourcePacks.removeIf(SmoothFontModule::isLegacyPack);

        if (removed) {
            minecraft.options.save();
        }

        SmoothFontModule.legacyPackCleaned = true;
    }

    private static boolean isLegacyPack(String id) {
        return SmoothFontModule.LEGACY_PACK_ID.equals(id) || SmoothFontModule.LEGACY_CAKE_PACK_ID.equals(id);
    }

    public static void onFontReload(MinecraftFontAccess minecraft) {
        var module = ModuleManager.get().get(SmoothFontModule.class);

        if (module != null && module.isEnabled()) {
            SmoothFontModule.applyFont(minecraft, true);
        }
    }

    @Override
    public void onEnable() {
        SmoothFontModule.applyFont(true);
    }

    @Override
    public void onDisable() {
        SmoothFontModule.applyFont(false);
    }

    private static boolean applyFont(boolean smooth) {
        var minecraft = Minecraft.getInstance();

        if (minecraft == null) {
            return false;
        }

        return SmoothFontModule.applyFont((MinecraftFontAccess) minecraft, smooth);
    }

    private static boolean applyFont(MinecraftFontAccess minecraft, boolean smooth) {
        var fontManager = minecraft.cake$getFontManager();

        if (!(fontManager instanceof FontManagerAccess manager)) {
            return false;
        }

        if (!smooth) {
            minecraft.cake$setFont(fontManager.createFont());
            minecraft.cake$setFontFilterFishy(fontManager.createFontFilterFishy());
            return true;
        }

        minecraft.cake$setFont(SmoothFontModule.createFont(manager, false));
        minecraft.cake$setFontFilterFishy(SmoothFontModule.createFont(manager, true));
        return true;
    }

    private static Font createFont(FontManagerAccess manager, boolean nonFishy) {
        return new Font(new Font.Provider() {
            @Override
            public GlyphSource glyphs(FontDescription description) {
                return SmoothFontModule.resolveSet(manager, description).source(nonFishy);
            }

            @Override
            public EffectGlyph effect() {
                return SmoothFontModule.resolveSet(manager, FontDescription.DEFAULT).whiteGlyph();
            }
        });
    }

    private static FontSet resolveSet(FontManagerAccess manager, FontDescription description) {
        if (description instanceof FontDescription.Resource resource
                && !Minecraft.DEFAULT_FONT.equals(resource.id())) {
            var set = manager.cake$getFontSet(resource.id());

            if (set != null) {
                return set;
            }
        }

        var smooth = manager.cake$getFontSet(SmoothFontModule.SMOOTH_FONT);

        if (smooth != null) {
            return smooth;
        }

        var vanilla = manager.cake$getFontSet(Minecraft.DEFAULT_FONT);
        return vanilla != null ? vanilla : manager.cake$getMissingFontSet();
    }
}
