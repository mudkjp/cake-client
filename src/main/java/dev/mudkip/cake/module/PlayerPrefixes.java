package dev.mudkip.cake.module;

import com.mojang.authlib.GameProfile;
import dev.mudkip.cake.MinecraftClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class PlayerPrefixes {
    private PlayerPrefixes() {
    }

    public static @Nullable Component aboveHead(GameProfile profile) {
        return PlayerPrefixes.prefix(profile, false);
    }

    public static @Nullable Component tabList(GameProfile profile) {
        return PlayerPrefixes.prefix(profile, true);
    }

    private static @Nullable Component prefix(GameProfile profile, boolean tabList) {
        var access = (MinecraftClientAccess) Minecraft.getInstance();
        var nickDetector = access.cake$getActiveModule(NickDetectorModule.class);

        if (nickDetector != null) {
            var prefix = nickDetector.prefix(profile, tabList);

            if (prefix != null) {
                return prefix;
            }
        }

        var starLevels = access.cake$getActiveModule(StarLevelsModule.class);
        return starLevels == null ? null : starLevels.prefix(profile, tabList);
    }
}
