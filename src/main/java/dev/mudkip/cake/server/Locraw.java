package dev.mudkip.cake.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class Locraw {
    private static final long REQUEST_INTERVAL = 15_000L;
    private static final long COUNTDOWN_TRIGGER_INTERVAL = 10_000L;
    private static final long GAME_START_TRIGGER_INTERVAL = 2_000L;
    private static final Pattern GAME_STARTING = Pattern.compile("Game (?:is )?starting in \\d+ seconds!");
    private static final Pattern GAME_STARTED = Pattern.compile("Game(?: has)? started!");
    private static final Pattern CONNECTED = Pattern.compile("Successfully connected to bwp-(?:lobby|game)-\\d+");
    private static final Map<String, String> GAME_NAMES = Map.ofEntries(
            Map.entry("bridgessingle", "1v1 Bed Bridge Fight"),
            Map.entry("bridgesdouble", "2v2 Bed Bridge Fight"),
            Map.entry("bridgesingle", "1v1 Bed Bridge Fight"),
            Map.entry("bridgedouble", "2v2 Bed Bridge Fight"),
            Map.entry("compbridgesingle", "1v1 Competitive Bridge Fight"),
            Map.entry("sumo", "Block Sumo"),
            Map.entry("sumoduelssolo", "1v1 Block Sumo Duels"),
            Map.entry("sumoduelsdouble", "2v2 Block Sumo Duels"),
            Map.entry("betasumo", "Beta Block Sumo"),
            Map.entry("obstaclesingle", "1v1 Obstacles"),
            Map.entry("resourcesingle", "1v1 Resource Collect"),
            Map.entry("resourcedouble", "2v2 Resource Collect"),
            Map.entry("voidsingle", "1v1 Void Fight"),
            Map.entry("voiddouble", "2v2 Void Fight"),
            Map.entry("bedwarsnormalsingle", "1v1 Bedwars Normal"),
            Map.entry("bedwarsnormaldouble", "2v2 Bedwars Normal"),
            Map.entry("bedwarsmegasolo", "Bedwars Mega Solo"),
            Map.entry("bedwarsmegadouble", "Bedwars Mega Doubles"),
            Map.entry("bedwarslatesingle", "1v1 Bedwars Late Game"),
            Map.entry("bedwarslatedouble", "2v2 Bedwars Late Game"),
            Map.entry("bedwarsrushsolo", "1v1 Bedwars Rush Duels"),
            Map.entry("bedwarsrushdouble", "2v2 Bedwars Rush Duels"),
            Map.entry("stickfightsingle", "1v1 Stick Fight"),
            Map.entry("stickfightdouble", "2v2 Stick Fight"),
            Map.entry("flatfightsingle", "1v1 Flat Fight"),
            Map.entry("flatfightdouble", "2v2 Flat Fight"),
            Map.entry("ladderfightsingle", "1v1 Ladder Fight"),
            Map.entry("ladderfightdouble", "2v2 Ladder Fight"),
            Map.entry("partygames", "Party Games"),
            Map.entry("rankedfourspractice", "4v4 Ranked Fours Practice"),
            Map.entry("bowfightsingle", "1v1 Bow Fight"),
            Map.entry("bowfightdouble", "2v2 Bow Fight"),
            Map.entry("pearlfightsingle", "1v1 Pearl Fight"),
            Map.entry("pearlfightdouble", "2v2 Pearl Fight"),
            Map.entry("bedrushsingle", "1v1 Bed Rush"),
            Map.entry("bedrushdouble", "2v2 Bed Rush"),
            Map.entry("miniwarssolo", "Miniwars Solo"),
            Map.entry("miniwarsdouble", "Miniwars Double"),
            Map.entry("bedwalls", "Bedwalls"),
            Map.entry("bedwarseightsolo", "Bedwars Eight Team"),
            Map.entry("bedwarseighttourney", "Bedwars Four Team"),
            Map.entry("fourwaybridgesingle", "Four Way Bridge Fight"),
            Map.entry("groundsingle", "1v1 Ground Fight"),
            Map.entry("grounddouble", "2v2 Ground Fight"),
            Map.entry("resourceoldsingle", "1v1 Old Resource Collect"),
            Map.entry("resourceolddouble", "2v2 Old Resource Collect"),
            Map.entry("wallfightsolo", "Wall Fight Solo")
    );
    private static @Nullable String game;
    private static long lastRequest;
    private static long lastCountdownTrigger;
    private static long lastGameStartTrigger;
    private static boolean manual;
    private static boolean autoRequest;
    private static boolean active;

    private Locraw() {

    }

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(Locraw::allowGameMessage);
        ClientSendMessageEvents.COMMAND.register(Locraw::onCommand);
    }

    public static void tick(Minecraft client, boolean enabled) {
        if (!enabled || !BedwarsPracticeIntegration.INSTANCE.isCurrent()) {
            Locraw.reset();
            return;
        }

        Locraw.active = true;

        if (Locraw.manual) {
            return;
        }

        var now = Util.getMillis();

        if (now - Locraw.lastRequest < Locraw.REQUEST_INTERVAL) {
            return;
        }

        Locraw.lastRequest = now;
        Locraw.request(client);
    }

    public static @Nullable String getGame() {
        return Locraw.active && BedwarsPracticeIntegration.INSTANCE.isCurrent() ? Locraw.game : null;
    }

    public static void reset() {
        Locraw.game = null;
        Locraw.lastRequest = 0L;
        Locraw.lastCountdownTrigger = 0L;
        Locraw.lastGameStartTrigger = 0L;
        Locraw.manual = false;
        Locraw.autoRequest = false;
        Locraw.active = false;
    }

    private static boolean allowGameMessage(Component message, boolean overlay) {
        if (overlay || !Locraw.active || !BedwarsPracticeIntegration.INSTANCE.isCurrent()) {
            return true;
        }

        var messageText = message.getString();
        var text = ChatFormatting.stripFormatting(messageText).trim();

        if (Locraw.CONNECTED.matcher(text).matches()) {
            Locraw.game = null;
            Locraw.lastCountdownTrigger = 0L;
            Locraw.lastGameStartTrigger = 0L;
            return true;
        }

        if (Locraw.GAME_STARTING.matcher(text).matches()) {
            var now = Util.getMillis();

            if (now - Locraw.lastCountdownTrigger > Locraw.COUNTDOWN_TRIGGER_INTERVAL) {
                Locraw.lastCountdownTrigger = now;
                Locraw.triggerUpdate(now);
            }

            return true;
        }

        if (Locraw.GAME_STARTED.matcher(text).matches()) {
            var now = Util.getMillis();

            if (now - Locraw.lastGameStartTrigger > Locraw.GAME_START_TRIGGER_INTERVAL) {
                Locraw.lastGameStartTrigger = now;
                Locraw.triggerUpdate(now);
            }

            return true;
        }

        var data = Locraw.parse(messageText);

        if (data == null) {
            return true;
        }

        Locraw.apply(data);
        return Locraw.manual;
    }

    private static void onCommand(String command) {
        if (Locraw.autoRequest || !BedwarsPracticeIntegration.INSTANCE.isCurrent()) {
            return;
        }

        var value = command.trim();

        if (value.startsWith("/")) {
            value = value.substring(1);
        }

        if (value.equals("locraw") || value.startsWith("locraw ")) {
            Locraw.manual = true;
        }
    }

    private static void triggerUpdate(long now) {
        if (Locraw.manual) {
            return;
        }

        Locraw.lastRequest = now;
        Locraw.request(Minecraft.getInstance());
    }

    private static void request(Minecraft client) {
        var connection = client.getConnection();

        if (connection == null) {
            return;
        }

        Locraw.autoRequest = true;

        try {
            connection.sendCommand("locraw");
        } catch (Exception ignored) {
        } finally {
            Locraw.autoRequest = false;
        }
    }

    private static @Nullable Data parse(String message) {
        var text = message.trim();
        var start = text.indexOf('{');
        var end = text.lastIndexOf('}');

        if (start < 0 || end <= start) {
            return null;
        }

        try {
            var element = JsonParser.parseString(text.substring(start, end + 1));

            if (!element.isJsonObject()) {
                return null;
            }

            var json = element.getAsJsonObject();
            var serverType = Locraw.string(json, "server_type");

            if (serverType == null) {
                return null;
            }

            return new Data(serverType, Locraw.string(json, "game_type"), Locraw.string(json, "technique_type"));
        } catch (JsonParseException ignored) {
            return null;
        }
    }

    private static @Nullable String string(JsonObject json, String key) {
        var element = json.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }

        return element.getAsString();
    }

    private static void apply(Data data) {
        if (!"practice".equalsIgnoreCase(data.serverType())) {
            Locraw.game = null;
            return;
        }

        var name = data.gameType() != null && !data.gameType().isBlank()
                ? Locraw.gameName(data.gameType())
                : data.techniqueType() != null && !data.techniqueType().isBlank()
                        ? Locraw.displayName(data.techniqueType())
                        : null;

        Locraw.game = name == null || name.isBlank() ? null : name;
    }

    private static String gameName(String value) {
        var key = value.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(" ", "")
                .replace("-", "");
        var mapped = Locraw.GAME_NAMES.get(key);

        if (mapped == null) {
            mapped = Locraw.closestGameName(key);
        }

        return mapped != null ? mapped : Locraw.displayName(value);
    }

    private static @Nullable String closestGameName(String key) {
        String best = null;
        var bestDistance = 2;

        for (var entry : Locraw.GAME_NAMES.entrySet()) {
            var distance = Locraw.distance(key, entry.getKey());

            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getValue();
            } else if (distance == bestDistance && best != null && !best.equals(entry.getValue())) {
                best = null;
            }
        }

        return best;
    }

    private static int distance(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) {
            return 2;
        }

        var previous = new int[right.length() + 1];
        var current = new int[right.length() + 1];

        for (var index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }

        for (var leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;

            for (var rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                var cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }

            var swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }

    private static String displayName(String value) {
        var text = value.replace('_', ' ').trim();
        var builder = new StringBuilder(text.length() + 4);

        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);

            if (index > 0 && Character.isUpperCase(character)) {
                var previous = text.charAt(index - 1);
                var next = index + 1 < text.length() ? text.charAt(index + 1) : 0;

                if (Character.isLowerCase(previous) || (Character.isUpperCase(previous) && Character.isLowerCase(next))) {
                    builder.append(' ');
                }
            }

            builder.append(character);
        }

        if (!builder.isEmpty()) {
            builder.setCharAt(0, Character.toUpperCase(builder.charAt(0)));
        }

        return builder.toString();
    }

    private record Data(String serverType, @Nullable String gameType, @Nullable String techniqueType) {

    }
}
