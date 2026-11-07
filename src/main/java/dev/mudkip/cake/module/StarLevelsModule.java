package dev.mudkip.cake.module;

import com.mojang.authlib.GameProfile;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.module.setting.StringSetting;
import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import dev.mudkip.cake.server.voxyl.VoxylApi;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

public final class StarLevelsModule extends ClientModule {
    private static final String STAR = "\u272B";
    private static final String PRESTIGE_STAR = "\u272A";
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("\u00A7.");
    private static final Pattern CHAT_STAR_PATTERN = Pattern.compile("^\\[(\\d+)[" + STAR + PRESTIGE_STAR + "]\\] (?:\\[[^\\]]+\\] )*([A-Za-z0-9_]{1,16}): ");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("Your API key is (\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROTATED_PATTERN = Pattern.compile("Generated new API Key", Pattern.CASE_INSENSITIVE);
    private static final ChatFormatting[] PRESTIGE_COLORS = {
            ChatFormatting.GRAY,
            ChatFormatting.WHITE,
            ChatFormatting.GOLD,
            ChatFormatting.AQUA,
            ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.BLUE,
            ChatFormatting.DARK_PURPLE
    };
    private static final ChatFormatting[] PRESTIGE_1000_COLORS = {
            ChatFormatting.RED,
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.DARK_PURPLE
    };
    private static final ChatFormatting[] PRESTIGE_1100_COLORS = {
            ChatFormatting.GRAY,
            ChatFormatting.WHITE,
            ChatFormatting.WHITE,
            ChatFormatting.WHITE,
            ChatFormatting.WHITE,
            ChatFormatting.GRAY,
            ChatFormatting.GRAY
    };
    private static final ChatFormatting[] PRESTIGE_1200_COLORS = {
            ChatFormatting.GRAY,
            ChatFormatting.YELLOW,
            ChatFormatting.YELLOW,
            ChatFormatting.YELLOW,
            ChatFormatting.YELLOW,
            ChatFormatting.GOLD,
            ChatFormatting.GRAY
    };
    private static final int MAX_FETCH_PER_TICK = 3;
    private static final int JOIN_DELAY_TICKS = 20;

    private final StringSetting apiKey;
    private final BooleanSetting aboveHead;
    private final BooleanSetting tabList;
    private final VoxylApi api;
    private int joinTicks;
    private boolean requested;
    private boolean expectingKey;
    private boolean refetch;

    public StarLevelsModule(ModuleContext context) {
        super(
                ModuleDefinition.of("starlevels", ModuleCategory.RENDER)
                        .withIntegration(BedwarsPractice.INTEGRATION),
                context
        );
        this.apiKey = this.stringSetting("api_key", "", 128);
        this.aboveHead = this.booleanSetting("above_head", true);
        this.tabList = this.booleanSetting("tab_list", true);
        this.api = new VoxylApi();
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::allowGameMessage);
    }

    public @Nullable Component prefix(GameProfile profile, boolean tabList) {
        if (tabList ? !this.tabList.getValue() : !this.aboveHead.getValue()) {
            return null;
        }

        if (NickDetectorModule.hasNickSkin(profile)) {
            return null;
        }

        var level = this.api.getLevel(profile.id());

        if (level == null) {
            return null;
        }

        var prestige = level / 100;

        if (prestige == 10) {
            return StarLevelsModule.gradientPrefix(level, StarLevelsModule.PRESTIGE_1000_COLORS);
        }

        if (prestige == 11) {
            return StarLevelsModule.gradientPrefix(level, StarLevelsModule.PRESTIGE_1100_COLORS);
        }

        if (prestige == 12) {
            return StarLevelsModule.gradientPrefix(level, StarLevelsModule.PRESTIGE_1200_COLORS);
        }

        return Component.literal("[" + level + StarLevelsModule.starSymbol(level) + "] ").withStyle(StarLevelsModule.prestigeColor(level));
    }

    public boolean allowGameMessage(Component message, boolean overlay) {
        if (!this.isEnabled()) {
            return true;
        }

        var text = StarLevelsModule.FORMATTING_PATTERN.matcher(message.getString()).replaceAll("");
        var keyMatcher = StarLevelsModule.API_KEY_PATTERN.matcher(text);

        if (keyMatcher.find()) {
            this.apiKey.setValue(keyMatcher.group(1));

            if (this.expectingKey) {
                this.expectingKey = false;
                return false;
            }

            return true;
        }

        if (StarLevelsModule.ROTATED_PATTERN.matcher(text).find()) {
            this.refetch = true;
        }

        this.handleChatLevel(text);
        return true;
    }

    @Override
    protected void onEnable() {
        this.joinTicks = 0;
        this.requested = false;
        this.expectingKey = false;
        this.refetch = false;
    }

    @Override
    protected void onTick() {
        var connection = this.client.getConnection();

        if (connection == null || this.client.player == null) {
            return;
        }

        if (this.refetch) {
            this.refetch = false;
            this.sendApiGet(connection);
        }

        if (!this.requested && ++this.joinTicks >= StarLevelsModule.JOIN_DELAY_TICKS) {
            this.requested = true;
            this.sendApiGet(connection);
        }

        var issued = 0;

        for (var info : connection.getListedOnlinePlayers()) {
            if (issued >= StarLevelsModule.MAX_FETCH_PER_TICK) {
                break;
            }

            var profile = info.getProfile();

            if (NickDetectorModule.hasNickSkin(profile)) {
                continue;
            }

            if (this.api.requestLevel(profile.id(), this.apiKey.getValue())) {
                issued++;
            }
        }
    }

    private void handleChatLevel(String text) {
        var matcher = StarLevelsModule.CHAT_STAR_PATTERN.matcher(text);

        if (!matcher.find()) {
            return;
        }

        var connection = this.client.getConnection();

        if (connection == null) {
            return;
        }

        var info = connection.getPlayerInfo(matcher.group(2));

        if (info == null || NickDetectorModule.hasNickSkin(info.getProfile())) {
            return;
        }

        this.api.updateLevel(info.getProfile().id(), Integer.parseInt(matcher.group(1)));
    }

    private void sendApiGet(ClientPacketListener connection) {
        this.expectingKey = true;

        try {
            connection.sendCommand("api get");
        } catch (Exception ignored) {
            this.expectingKey = false;
        }
    }

    private static String starSymbol(int level) {
        return level >= 1100 && level < 2100 ? StarLevelsModule.PRESTIGE_STAR : StarLevelsModule.STAR;
    }

    private static ChatFormatting prestigeColor(int level) {
        var prestige = Math.clamp(level / 100, 0, StarLevelsModule.PRESTIGE_COLORS.length - 1);
        return StarLevelsModule.PRESTIGE_COLORS[prestige];
    }

    private static Component gradientPrefix(int level, ChatFormatting[] colors) {
        var body = "[" + level + StarLevelsModule.starSymbol(level) + "]";
        MutableComponent component = Component.empty();

        for (var index = 0; index < body.length(); index++) {
            var color = colors[Math.min(index, colors.length - 1)];
            component.append(Component.literal(String.valueOf(body.charAt(index))).withStyle(color));
        }

        return component.append(Component.literal(" "));
    }
}
