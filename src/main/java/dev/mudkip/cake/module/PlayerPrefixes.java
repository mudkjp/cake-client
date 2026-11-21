package dev.mudkip.cake.module;

import com.mojang.authlib.GameProfile;
import dev.mudkip.cake.MinecraftClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class PlayerPrefixes {
    private PlayerPrefixes() {
    }

    public static Component decorate(Component original, GameProfile profile, boolean tabList) {
        var prefix = PlayerPrefixes.prefix(profile, tabList);
        var suffix = PlayerPrefixes.badgeSuffix(profile);

        if (prefix == null && suffix == null) {
            return original;
        }

        var result = Component.empty();

        if (prefix != null) {
            result.append(prefix);
        }

        result.append(original);

        if (suffix != null) {
            result.append(suffix);
        }

        return result;
    }

    private static @Nullable Component prefix(GameProfile profile, boolean tabList) {
        var access = (MinecraftClientAccess) Minecraft.getInstance();
        var nickDetector = access.cake$getActiveModule(NickDetectorModule.class);
        var prefix = nickDetector == null ? null : nickDetector.prefix(profile, tabList);

        if (prefix == null) {
            var starLevels = access.cake$getActiveModule(StarLevelsModule.class);
            prefix = starLevels == null ? null : starLevels.prefix(profile, tabList);
        }

        return prefix;
    }

    private static @Nullable Component badgeSuffix(GameProfile profile) {
        //? if modrinth {
        /*return null;
        *///?} else {
        var badge = ((MinecraftClientAccess) Minecraft.getInstance()).cake$getBadgeService();
        return badge == null ? null : badge.suffix(profile);
        //?}
    }
}
