package dev.mudkip.cake.module;

import dev.mudkip.cake.module.setting.MessageSetting;
import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class HideRepetitiveMessagesModule extends ClientModule {
    private static final Pattern HIGHLIGHT = Pattern.compile(
            "/[A-Za-z]+|(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?:/[^\\s!.,)]*)?"
    );

    private final List<Filter> filters;

    public HideRepetitiveMessagesModule(ModuleContext context) {
        super(
                ModuleDefinition.of("hiderepetitivemessages", ModuleCategory.MISC)
                        .withIntegration(BedwarsPractice.INTEGRATION),
                context
        );
        this.filters = new ArrayList<>();
        this.registerTips();
        this.registerWarnings();
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::allowGameMessage);
    }

    private boolean allowGameMessage(Component message, boolean overlay) {
        if (overlay || !this.isEnabled()) {
            return true;
        }

        var text = message.getString().trim();

        for (var filter : this.filters) {
            if (filter.pattern().matcher(text).matches()) {
                return !filter.setting().getValue();
            }
        }

        return true;
    }

    private void registerTips() {
        this.tip("tools", "You can change your tool positions with /tools for better practice!");
        this.tip("report", "If you believe a player is hacking use /report to report them!");
        this.tip("rank", "If you enjoy the server consider buying a rank! store.bedwarspractice.club");
        this.tip("compass", "Join a game from listed from the compass!");
        this.tip("discord", "Join the discord server for updates! discord.gg/TUeUnkqXnY");
        this.tip(
                "twitter",
                "Keep up with the twitter account for updates and giveaways at twitter.com/bedwarspractice"
        );
        this.tip("leave", "Leave your current session using /leave.");
        this.tip("losses", "Server doesn't track losses. Feel free to practice on a bad day to warm up.");
        this.tip("hotbar", "Use /hotbar to re-arrange your hotbar.");
        this.tip(
                "profile",
                "Kills, beds broken, and wins are all added to your profile. Accessible via /stats."
        );
        this.tip("rejoin", "If you are disconnected from your game, use /rejoin to reconnect.");
        this.tip("hotbar_save", "Re-arrange your hotbar and it will automatically save.");
        this.tip(
                "beta_sumo",
                "If you enjoy block sumo, but dislike camping, considering playing beta block sumo."
        );
        this.tip("showfailed", "Use /showfailed to see when you fail a sprint hit!");
        this.tip(
                "gold_plate",
                "Standing on the centre gold plate for more than 20 seconds will result in an instant win."
        );
        this.tip("op_items", "OP items spawn on the centre gold plate periodically.");
        this.tip("sprinthits", "Use /sprinthits to see when you fail a sprint hit!");
        this.tip(
                "energy_sphere",
                "Energy sphere give a good amount of charge, but beware its destruction of wool blocks!"
        );
        this.tip(
                "air_columns",
                "Air columns can be used to launch yourself very high up. Plus they give you jump boost!"
        );
        this.tip(
                "snow_effect",
                "Jumping over a snow effect when it explodes will launch you very high up!"
        );
        this.tip(
                "chatpref",
                "Can avoid same players in a requeued game with 'avoid same players' option in /chatpref."
        );
        this.tip("tier9", "Tier 9 gives godly weapons to use against your enemies.");
        this.tip("particles", "You can tell a players powerup level from their particles.");
        this.tip("death_columns", "Try to hit your enemies into death columns and shadow columns");
        this.tip("level6", "Powerup level at VI will give you high tier items");
        this.tip(
                "level_gain",
                "Powerup level can increase with kills, energy spheres and hitting other players."
        );
        this.tip(
                "event",
                "Every minute or so, a cataclysmic event happens, use it to your advantage!"
        );
        this.tip("fireballs", "Fireballs are useful to prevent camping.");
        this.tip(
                "powerup_sphere",
                "If two enemy players are on the same item powerup sphere, the sphere will wait until one leaves."
        );
        this.tip(
                "report_player",
                "If a player is cheating, you can use /report <player> <reason> to report them!"
        );
        this.tip("team_left", "If a team has left for more than a minute, their bed will be destroyed.");
        this.tip("tower", "Players who tower up too high, may leave their bed too accessible.");
        this.tip("clutch", "Clutch on the side of players to catch them unaware.");
        this.tip("sumohelp", "If you are confused by this game, use /sumohelp.");
        this.tip("hypixel_sync", "Sync your items with hypixel by clicking the glowstone in the menu.");
        this.tip(
                "spheres",
                "Standing in spheres and eliminating players are the best ways to increase powerup level."
        );
        this.tip("lives", "Lives are rewarded at IV, VII, XI, and XV.");
        this.tip(
                "invincibility",
                "Invincibility runs out early when you hit another player. Avoid spawn camping by not hitting the other player too early."
        );
        this.tip("bow_interrupt", "Blocks can be used to interrupt a player's bow shot.");
        this.tip("regeneration", "Use the high regeneration perk to walk into death columns.");
        this.tip(
                "announcements",
                "You have unread announcements! Right click the book in your inventory to mark them as read."
        );
        this.tip(
                "diagonal_bridge",
                "It is recommended to practice diagonal bridging to complete the last segment of the course as fast as possible."
        );
        this.tip(
                "obstacle_jump",
                "When placing blocks on the side of an obstacle, consider jumping before placing a block - continuing the momentum."
        );
        this.tip(
                "enderpearls",
                "Try to avoid shooting enderpearls in front of player, opt instead to teleport next to or behind players."
        );
        this.tip(
                "same_powerup",
                "The same powerup is given to all those with the same level. This reduces luck."
        );
        this.tip("bonus_effects", "There are a variety of different bonus effects to spice up gameplay.");
        this.tip("bow_momentum", "Jumping allows for quicker additional momentum after releasing the bow.");
        this.tip("higher_powerups", "Higher powerup levels gives access to more randomly given powerups.");
    }

    private void registerWarnings() {
        this.warning("report", "If a player is cheating, please use /report to report them!");
        this.warning("crossteam", "Crossteaming is not allowed on this gamemode. Offenders may receive a ban.");
        this.warning("bridge_edge", "You can't start bridging here! Try placing near the andesite edge.");
        this.warning("place_permission", "You do not have permission to place blocks!");
        this.warning("place_important", "You cannot place blocks near important areas!");
        this.warning("place_outside", "You cannot place outside the map!");
        this.warning("go_far", "You cannot go this far out!");
        this.warning("go_world", "You cannot go outside the world!");
        this.warning("leave_map", "You cannot leave the map");
        this.warning("place_next", "You cannot place next to this block!");
    }

    private void tip(String id, String text) {
        this.filter(
                "tip_" + id,
                HideRepetitiveMessagesModule.tip(HideRepetitiveMessagesModule.styled(text, ChatFormatting.GRAY)),
                HideRepetitiveMessagesModule.exact("(!) " + text)
        );
    }

    private void warning(String id, String text) {
        this.filter(
                "warn_" + id,
                HideRepetitiveMessagesModule.warning(text),
                HideRepetitiveMessagesModule.exact(text)
        );
    }

    private void filter(String id, Component label, Pattern pattern) {
        var setting = this.messageSetting(id, label, true);
        this.filters.add(new Filter(setting, pattern));
    }

    private static Pattern exact(String text) {
        return Pattern.compile("^" + Pattern.quote(text) + "$");
    }

    private static Component tip(Component body) {
        return Component.literal("(!) ").withStyle(ChatFormatting.RED).append(body);
    }

    private static Component warning(String text) {
        return HideRepetitiveMessagesModule.styled(text, ChatFormatting.RED);
    }

    private static Component styled(String text, ChatFormatting base) {
        MutableComponent component = Component.empty();
        var matcher = HideRepetitiveMessagesModule.HIGHLIGHT.matcher(text);
        var index = 0;

        while (matcher.find()) {
            if (matcher.start() > index) {
                component.append(Component.literal(text.substring(index, matcher.start())).withStyle(base));
            }

            component.append(Component.literal(matcher.group()).withStyle(ChatFormatting.GREEN));
            index = matcher.end();
        }

        if (index < text.length()) {
            component.append(Component.literal(text.substring(index)).withStyle(base));
        }

        return component;
    }

    private record Filter(MessageSetting setting, Pattern pattern) {
    }
}
