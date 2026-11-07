package dev.mudkip.cake.module;

import com.mojang.authlib.GameProfile;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class NickDetectorModule extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(NickDetectorModule.class);
    private static final String NICK_SKINS_PATH = "/assets/cake/nick_skins.txt";
    private static final Set<String> NICK_SKINS = NickDetectorModule.loadNickSkins();

    private final BooleanSetting aboveHead;
    private final BooleanSetting tabList;

    public NickDetectorModule(ModuleContext context) {
        super(
                ModuleDefinition.of("nickdetector", ModuleCategory.RENDER)
                        .withIntegration(BedwarsPractice.INTEGRATION),
                context
        );
        this.aboveHead = this.booleanSetting("above_head", true);
        this.tabList = this.booleanSetting("tab_list", true);
    }

    public static boolean hasNickSkin(GameProfile profile) {
        for (var property : profile.properties().get("textures")) {
            if (NickDetectorModule.NICK_SKINS.contains(property.value())) {
                return true;
            }
        }

        return false;
    }

    public @Nullable Component prefix(GameProfile profile, boolean tabList) {
        if (tabList ? !this.tabList.getValue() : !this.aboveHead.getValue()) {
            return null;
        }

        return NickDetectorModule.hasNickSkin(profile)
                ? Component.literal("[nick] ").withStyle(ChatFormatting.YELLOW)
                : null;
    }

    private static Set<String> loadNickSkins() {
        var skins = new HashSet<String>();

        try (var stream = NickDetectorModule.class.getResourceAsStream(NickDetectorModule.NICK_SKINS_PATH)) {
            if (stream == null) {
                NickDetectorModule.LOGGER.warn("Nick skin list not found; nick detection will not work");
                return Set.of();
            }

            var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            for (var line : content.split("\n")) {
                var trimmed = line.trim();

                if (!trimmed.isEmpty()) {
                    skins.add(trimmed);
                }
            }
        } catch (IOException exception) {
            NickDetectorModule.LOGGER.warn("Failed to load the nick skin list", exception);
        }

        return Set.copyOf(skins);
    }
}
