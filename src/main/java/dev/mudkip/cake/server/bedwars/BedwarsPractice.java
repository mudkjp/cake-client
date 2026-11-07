package dev.mudkip.cake.server.bedwars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.mudkip.cake.server.ServerIntegration;
import dev.mudkip.cake.server.ServerSession;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public final class BedwarsPractice {
    public static final ServerIntegration INTEGRATION = ServerIntegration.of(
            "bedwarspractice",
            "Bedwars Practice",
            "bedwarspractice.club",
            "bedwarspractice.net",
            "voxyl.net"
    );
    private static final long REQUEST_INTERVAL = 15_000L;
    private static final long REQUEST_DEBOUNCE = 1_000L;
    private static final long COUNTDOWN_TRIGGER_INTERVAL = 10_000L;
    private static final long GAME_START_TRIGGER_INTERVAL = 2_000L;
    private static final Pattern CONNECTED = Pattern.compile(
            "Successfully connected to (bwp-(lobby|game)-\\d+)"
    );
    private static final Pattern GAME_UUID = Pattern.compile("You have joined with a new game! UUID: (\\S+)");
    private static final Pattern RAW_UUID = Pattern.compile(
            "([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})"
    );
    private static final Pattern COUNTDOWN = Pattern.compile("(?:Server is now FULL! )?Game (?:is )?starting in \\d+ seconds!(?: Leaving will cancel this\\.)?");
    private static final Pattern STARTED = Pattern.compile("Game (?:has )?started!");
    private static final Pattern ROUND = Pattern.compile("Game (\\d+)/(\\d+)");
    private static final Pattern RESULT = Pattern.compile("(?m)^\\s*Game (Won|Lost)!\\s*$");
    private static final Pattern WINNING_TEAM = Pattern.compile("The winning team is ([A-Za-z]+)");
    private static final Pattern WINNERS = Pattern.compile("(?m)^\\s*Winners?\\s*-\\s*(.+?)\\s*$");
    private static final Pattern SCORE = Pattern.compile(
            "(?m)^\\s*(\\d+)(?:st|nd|rd|th)\\s*-\\s*(.+?)\\s*-\\s*(-?\\d+)\\s*$"
    );
    private static final Pattern PLACEMENT = Pattern.compile("(?m)^\\s*(\\d+)(?:st|nd|rd|th)\\s*\\(([^)]*)\\)\\s*-\\s*(.+?)\\s*$");
    private static final Pattern TOP = Pattern.compile("Top #(\\d+)");
    private static final Pattern TECHNIQUE_TIME = Pattern.compile("in (\\d+):(\\d+)\\.(\\d+) seconds!");
    private static final Pattern GAME_OVER_WINNER = Pattern.compile("(?m)^\\s*Winner:\\s*(.+?)\\s*$");

    private final Minecraft client;
    private final ServerSession serverSession;
    private final ServerIntegration integration;
    private final List<BedwarsPracticeListener> listeners;
    private @Nullable BedwarsPracticeGame game;
    private @Nullable ClientLevel lastLevel;
    private @Nullable ClientPacketListener lastConnection;
    private @Nullable UUID gameId;
    private @Nullable String server;
    private State state;
    private boolean active;
    private boolean queued;
    private boolean privateGame;
    private boolean awaitingBanner;
    private boolean manual;
    private boolean autoRequest;
    private long lastRequest;
    private long lastCountdownTrigger;
    private long lastGameStartTrigger;

    public BedwarsPractice(Minecraft client, ServerSession serverSession) {
        this.client = client;
        this.serverSession = serverSession;
        this.integration = BedwarsPractice.INTEGRATION;
        this.listeners = new CopyOnWriteArrayList<>();
        this.state = State.NONE;
    }

    public void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::allowGameMessage);
        ClientSendMessageEvents.COMMAND.register(this::onCommand);
    }

    public void addListener(BedwarsPracticeListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(BedwarsPracticeListener listener) {
        this.listeners.remove(listener);
    }

    public @Nullable BedwarsPracticeGame getGame() {
        return this.active ? this.game : null;
    }

    public @Nullable String getMode() {
        var current = this.getGame();
        return current == null ? null : current.mode();
    }

    public void tick() {
        var level = this.client.level;
        var connection = this.client.getConnection();
        var worldChanged = level != this.lastLevel || connection != this.lastConnection;
        this.lastLevel = level;
        this.lastConnection = connection;

        if (!this.serverSession.isOn(this.integration)) {
            if (this.active) {
                this.leaveGame();
                this.fireServerLeave();
                this.reset();
                this.active = false;
            }

            return;
        }

        if (this.listeners.isEmpty()) {
            if (this.active) {
                this.reset();
                this.active = false;
            }

            return;
        }

        if (!this.active) {
            this.reset();
            this.active = true;
        }

        if (worldChanged) {
            this.onWorldChange();
        }

        if (this.manual) {
            return;
        }

        var now = Util.getMillis();

        if (now - this.lastRequest < BedwarsPractice.REQUEST_INTERVAL) {
            return;
        }

        this.lastRequest = now;
        this.request();
    }

    private boolean allowGameMessage(Component message, boolean overlay) {
        if (overlay || !this.active || !this.serverSession.isOn(this.integration)) {
            return true;
        }

        var raw = message.getString();
        var data = BedwarsPractice.parseLocraw(raw);

        if (data != null) {
            this.apply(data);
            return this.manual;
        }

        this.handle(BedwarsPractice.normalize(raw));
        return true;
    }

    private void handle(String text) {
        var message = text.trim();

        if (message.isEmpty()) {
            return;
        }

        if (this.handleConnection(message)) {
            return;
        }

        if (this.handlePrivate(message)) {
            return;
        }

        if (this.handleGameId(message)) {
            return;
        }

        if (this.handleLoading(message)) {
            return;
        }

        if (this.handleRound(message)) {
            return;
        }

        if (this.handleQueue(message)) {
            return;
        }

        if (this.handleCountdown(message)) {
            return;
        }

        if (this.handleStart(message)) {
            return;
        }

        if (this.handleResult(text)) {
            return;
        }

        if (this.handleRoundEnd(text)) {
            return;
        }

        if (this.handleGameFinished(text)) {
            return;
        }

        if (this.handleTechnique(text)) {
            return;
        }

        if (this.handleGameOver(text)) {
            return;
        }

        this.handleBanner(text);
    }

    private boolean handleConnection(String message) {
        var matcher = BedwarsPractice.CONNECTED.matcher(message);

        if (!matcher.matches()) {
            return false;
        }

        this.server = matcher.group(1);
        var kind = "game".equals(matcher.group(2))
                ? BedwarsPracticeEvents.ServerKind.GAME
                : BedwarsPracticeEvents.ServerKind.LOBBY;

        if (kind == BedwarsPracticeEvents.ServerKind.GAME) {
            this.leaveGame();
            this.game = new BedwarsPracticeGame(null, null, null, this.privateGame, this.gameId, 0, 0);
            this.state = State.WAITING;
            this.awaitingBanner = false;
            this.updateQueue(false);
            this.fireServerJoin(kind);
            this.fireGameJoin();
            this.requestNow();
        } else {
            this.leaveGame();
            this.game = null;
            this.gameId = null;
            this.privateGame = false;
            this.state = State.LOBBY;
            this.awaitingBanner = false;
            this.updateQueue(false);
            this.fireServerJoin(kind);
        }

        return true;
    }

    private boolean handlePrivate(String message) {
        if (!message.equals("Joined server for private game...") && !message.equals("Starting private game!")) {
            return false;
        }

        this.privateGame = true;
        return true;
    }

    private boolean handleGameId(String message) {
        var matcher = BedwarsPractice.GAME_UUID.matcher(message);

        if (!matcher.matches()) {
            matcher = BedwarsPractice.RAW_UUID.matcher(message);

            if (!matcher.matches()) {
                return false;
            }
        }

        try {
            this.gameId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException ignored) {
            return true;
        }

        if (this.game != null) {
            this.game = this.game.withId(this.gameId);
        }

        return true;
    }

    private boolean handleLoading(String message) {
        if (!message.equals("Loading map...")) {
            return false;
        }

        this.awaitingBanner = this.game != null;
        return true;
    }

    private boolean handleQueue(String message) {
        if (message.equals("You have been added to the queue!") || message.equals("You have been added to the queue.")) {
            this.updateQueue(true);
            return true;
        }

        if (message.equals("You have been removed from the queue!") || message.equals("Moving you to game server!")) {
            this.updateQueue(false);
            return true;
        }

        return false;
    }

    private boolean handleRound(String message) {
        var matcher = BedwarsPractice.ROUND.matcher(message);

        if (!matcher.matches()) {
            return false;
        }

        if (this.state == State.ENDED) {
            this.beginRematch();
        }

        if (this.game != null) {
            this.game = this.game.withRound(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
        }

        return true;
    }

    private boolean handleCountdown(String message) {
        if (!BedwarsPractice.COUNTDOWN.matcher(message).matches()) {
            return false;
        }

        if (this.state == State.ENDED) {
            this.beginRematch();
        }

        if (this.state == State.WAITING) {
            this.state = State.STARTING;
        }

        var now = Util.getMillis();

        if (now - this.lastCountdownTrigger > BedwarsPractice.COUNTDOWN_TRIGGER_INTERVAL) {
            this.lastCountdownTrigger = now;
            this.triggerUpdate(now);
        }

        return true;
    }

    private boolean handleStart(String message) {
        if (!BedwarsPractice.STARTED.matcher(message).matches()) {
            return false;
        }

        if (this.state == State.ENDED) {
            this.beginRematch();
        }

        var now = Util.getMillis();

        if (now - this.lastGameStartTrigger > BedwarsPractice.GAME_START_TRIGGER_INTERVAL) {
            this.lastGameStartTrigger = now;
            this.triggerUpdate(now);
        }

        if (this.game == null) {
            return true;
        }

        if (this.state != State.WAITING && this.state != State.STARTING) {
            return true;
        }

        this.state = State.PLAYING;
        this.awaitingBanner = false;
        this.fireGameStart();
        return true;
    }

    private boolean handleResult(String text) {
        var matcher = BedwarsPractice.RESULT.matcher(text);

        if (!matcher.find()) {
            return false;
        }

        var current = this.game;

        if (current == null || this.state == State.ENDED) {
            return true;
        }

        var result = this.parseResult(text, "Won".equals(matcher.group(1)));
        this.state = State.ENDED;
        this.fireGameEnd(current, result);
        return true;
    }

    private boolean handleRoundEnd(String text) {
        if (!text.contains("Game Complete!")) {
            return false;
        }

        var current = this.game;

        if (current == null || !current.isPartyGame() || this.state != State.PLAYING) {
            return true;
        }

        var result = this.parsePlacements(text, false);
        this.state = State.WAITING;
        this.fireRoundEnd(current, result);
        return true;
    }

    private boolean handleGameFinished(String text) {
        if (!text.contains("Game Finished!")) {
            return false;
        }

        var current = this.game;

        if (current == null || this.state == State.ENDED) {
            return true;
        }

        var result = this.parsePlacements(text, true);
        this.state = State.ENDED;
        this.fireGameEnd(current, result);
        return true;
    }

    private boolean handleTechnique(String text) {
        if (!text.contains("Technique Beaten!") && !text.contains("Technique Failed!")) {
            return false;
        }

        var current = this.game;

        if (current == null) {
            return true;
        }

        var duration = BedwarsPractice.parseDuration(text);
        var personalBest = text.contains("Personal Best!");
        this.fireTechniqueComplete(current, duration, personalBest);
        return true;
    }

    private boolean handleGameOver(String text) {
        if (!text.contains("Game Over")) {
            return false;
        }

        var current = this.game;

        if (current == null || this.state == State.ENDED) {
            return true;
        }

        var matcher = BedwarsPractice.GAME_OVER_WINNER.matcher(text);
        var winners = new ArrayList<String>();

        if (matcher.find()) {
            winners.add(BedwarsPractice.stripRank(matcher.group(1)));
        }

        var local = this.client.getUser().getName();
        var won = winners.stream().anyMatch(name -> name.equalsIgnoreCase(local));
        var outcome = winners.isEmpty()
                ? BedwarsPracticeResult.Outcome.UNKNOWN
                : won ? BedwarsPracticeResult.Outcome.WIN : BedwarsPracticeResult.Outcome.LOSS;
        var result = new BedwarsPracticeResult(
                outcome,
                null,
                winners,
                List.of(),
                0
        );
        this.state = State.ENDED;
        this.fireGameEnd(current, result);
        return true;
    }

    private boolean handleBanner(String text) {
        if (!this.awaitingBanner || this.game == null) {
            return false;
        }

        if (this.state != State.WAITING && this.state != State.STARTING) {
            return false;
        }

        var title = BedwarsPractice.bannerTitle(text);

        if (title == null) {
            return false;
        }

        this.awaitingBanner = false;

        if (this.game.mode() == null) {
            this.game = this.game.withMode(title, null);
            this.fireGameModeChange(null);
        }

        return true;
    }

    private void beginRematch() {
        var current = this.game;

        if (current == null) {
            return;
        }

        this.game = new BedwarsPracticeGame(
                current.mode(),
                current.modeId(),
                current.map(),
                this.privateGame,
                null,
                0,
                0
        );
        this.state = State.WAITING;
        this.fireGameJoin();
    }

    private void apply(LocrawData data) {
        if (!"practice".equalsIgnoreCase(data.serverType()) || this.state == State.LOBBY) {
            return;
        }

        var mode = data.gameType() != null && !data.gameType().isBlank()
                ? BedwarsPracticeModes.name(data.gameType())
                : data.techniqueType() != null && !data.techniqueType().isBlank()
                        ? BedwarsPracticeModes.displayName(data.techniqueType())
                        : null;
        var map = data.gameMap() == null || data.gameMap().isBlank()
                ? null
                : BedwarsPracticeModes.displayName(data.gameMap());
        var modeId = data.gameType() != null && !data.gameType().isBlank()
                ? data.gameType()
                : data.techniqueType();
        var current = this.game;

        if (current == null) {
            if (mode == null || data.serverName() == null || !data.serverName().startsWith("bwp-game-")) {
                return;
            }

            this.game = new BedwarsPracticeGame(mode, modeId, map, this.privateGame, this.gameId, 0, 0);
            this.state = State.WAITING;
            this.fireGameJoin();
            this.fireGameModeChange(null);
            return;
        }

        var updated = current;

        if (mode != null && !mode.equals(current.mode())) {
            updated = updated.withMode(mode, modeId);
        }

        if (map != null && !map.equals(current.map())) {
            updated = updated.withMap(map);
        }

        if (updated == current) {
            return;
        }

        var previous = current.mode();
        this.game = updated;

        if (!Objects.equals(previous, updated.mode())) {
            this.fireGameModeChange(previous);
        }
    }

    private void requestNow() {
        if (this.manual) {
            return;
        }

        var now = Util.getMillis();

        if (now - this.lastRequest < BedwarsPractice.REQUEST_DEBOUNCE) {
            return;
        }

        this.lastRequest = now;
        this.request();
    }

    private void onWorldChange() {
        if (this.client.level == null || !this.active || !this.serverSession.isOn(this.integration)) {
            return;
        }

        this.requestNow();
    }

    private void triggerUpdate(long now) {
        if (!this.active || this.manual || now - this.lastRequest < BedwarsPractice.REQUEST_DEBOUNCE) {
            return;
        }

        this.lastRequest = now;
        this.request();
    }

    private void request() {
        var connection = this.client.getConnection();

        if (connection == null) {
            return;
        }

        this.autoRequest = true;

        try {
            connection.sendCommand("locraw");
        } catch (Exception ignored) {
        } finally {
            this.autoRequest = false;
        }
    }

    private void onCommand(String command) {
        if (this.autoRequest || !this.active || !this.serverSession.isOn(this.integration)) {
            return;
        }

        var value = command.trim();

        if (value.startsWith("/")) {
            value = value.substring(1);
        }

        if (value.equals("locraw") || value.startsWith("locraw ")) {
            this.manual = true;
        }
    }

    private BedwarsPracticeResult parseResult(String text, boolean won) {
        var teamMatcher = BedwarsPractice.WINNING_TEAM.matcher(text);
        var team = teamMatcher.find() ? teamMatcher.group(1) : null;
        var winners = new ArrayList<String>();
        var winnerMatcher = BedwarsPractice.WINNERS.matcher(text);

        while (winnerMatcher.find()) {
            for (var name : winnerMatcher.group(1).split(",")) {
                var cleaned = BedwarsPractice.stripRank(name);

                if (!cleaned.isEmpty()) {
                    winners.add(cleaned);
                }
            }
        }

        var scores = new ArrayList<BedwarsPracticeResult.Score>();
        var scoreMatcher = BedwarsPractice.SCORE.matcher(text);

        while (scoreMatcher.find()) {
            scores.add(new BedwarsPracticeResult.Score(
                    BedwarsPractice.stripRank(scoreMatcher.group(2)),
                    Integer.parseInt(scoreMatcher.group(3))
            ));
        }

        return this.createResult(
                won ? BedwarsPracticeResult.Outcome.WIN : BedwarsPracticeResult.Outcome.LOSS,
                team,
                winners,
                scores,
                0
        );
    }

    private BedwarsPracticeResult parsePlacements(String text, boolean finished) {
        var matcher = BedwarsPractice.PLACEMENT.matcher(text);
        var winners = new ArrayList<String>();
        var scores = new ArrayList<BedwarsPracticeResult.Score>();
        var placement = 0;
        var local = this.client.getUser().getName();

        while (matcher.find()) {
            var place = Integer.parseInt(matcher.group(1));
            var name = BedwarsPractice.stripRank(matcher.group(3));
            var value = 0;

            for (var index = 0; index < matcher.group(2).length(); index++) {
                if (matcher.group(2).charAt(index) == '✫') {
                    value++;
                }
            }

            scores.add(new BedwarsPracticeResult.Score(name, value));

            if (place == 1) {
                winners.add(name);
            }

            if (name.equalsIgnoreCase(local)) {
                placement = place;
            }
        }

        if (finished) {
            var topMatcher = BedwarsPractice.TOP.matcher(text);

            if (topMatcher.find()) {
                placement = Integer.parseInt(topMatcher.group(1));
            }
        }

        var outcome = placement == 0
                ? BedwarsPracticeResult.Outcome.UNKNOWN
                : placement == 1 ? BedwarsPracticeResult.Outcome.WIN : BedwarsPracticeResult.Outcome.LOSS;

        return new BedwarsPracticeResult(outcome, null, winners, scores, placement);
    }

    private BedwarsPracticeResult createResult(
            BedwarsPracticeResult.Outcome outcome,
            @Nullable String winningTeam,
            List<String> winners,
            List<BedwarsPracticeResult.Score> scores,
            int placement
    ) {
        if (placement == 0) {
            var local = this.client.getUser().getName();

            for (var index = 0; index < scores.size(); index++) {
                if (scores.get(index).name().equalsIgnoreCase(local)) {
                    placement = index + 1;
                    break;
                }
            }
        }

        return new BedwarsPracticeResult(outcome, winningTeam, winners, scores, placement);
    }

    private void updateQueue(boolean queued) {
        if (this.queued == queued) {
            return;
        }

        this.queued = queued;
        var event = new BedwarsPracticeEvents.QueueChange(queued);

        for (var listener : this.listeners) {
            listener.onQueueChange(event);
        }
    }

    private void leaveGame() {
        var current = this.game;

        if (current == null) {
            return;
        }

        this.game = null;

        if (this.state == State.ENDED) {
            return;
        }

        var event = new BedwarsPracticeEvents.GameLeave(current);

        for (var listener : this.listeners) {
            listener.onGameLeave(event);
        }
    }

    private void fireServerJoin(BedwarsPracticeEvents.ServerKind kind) {
        var event = new BedwarsPracticeEvents.ServerJoin(this.server, kind);

        for (var listener : this.listeners) {
            listener.onServerJoin(event);
        }
    }

    private void fireServerLeave() {
        var event = new BedwarsPracticeEvents.ServerLeave(this.server);

        for (var listener : this.listeners) {
            listener.onServerLeave(event);
        }
    }

    private void fireGameJoin() {
        var current = this.game;

        if (current == null) {
            return;
        }

        var event = new BedwarsPracticeEvents.GameJoin(current);

        for (var listener : this.listeners) {
            listener.onGameJoin(event);
        }
    }

    private void fireGameModeChange(@Nullable String previousMode) {
        var current = this.game;

        if (current == null) {
            return;
        }

        var event = new BedwarsPracticeEvents.GameModeChange(current, previousMode);

        for (var listener : this.listeners) {
            listener.onGameModeChange(event);
        }
    }

    private void fireGameStart() {
        var current = this.game;

        if (current == null) {
            return;
        }

        var event = new BedwarsPracticeEvents.GameStart(current);

        for (var listener : this.listeners) {
            listener.onGameStart(event);
        }
    }

    private void fireRoundEnd(BedwarsPracticeGame current, BedwarsPracticeResult result) {
        var event = new BedwarsPracticeEvents.RoundEnd(current, result);

        for (var listener : this.listeners) {
            listener.onRoundEnd(event);
        }
    }

    private void fireTechniqueComplete(
            BedwarsPracticeGame current,
            @Nullable Duration duration,
            boolean personalBest
    ) {
        var event = new BedwarsPracticeEvents.TechniqueComplete(current, duration, personalBest);

        for (var listener : this.listeners) {
            listener.onTechniqueComplete(event);
        }
    }

    private void fireGameEnd(BedwarsPracticeGame current, BedwarsPracticeResult result) {
        var event = new BedwarsPracticeEvents.GameEnd(current, result);

        for (var listener : this.listeners) {
            listener.onGameEnd(event);
        }
    }

    private void reset() {
        this.game = null;
        this.gameId = null;
        this.server = null;
        this.state = State.NONE;
        this.queued = false;
        this.privateGame = false;
        this.awaitingBanner = false;
        this.manual = false;
        this.autoRequest = false;
        this.lastRequest = 0L;
        this.lastCountdownTrigger = 0L;
        this.lastGameStartTrigger = 0L;
    }

    private static String normalize(String raw) {
        var text = ChatFormatting.stripFormatting(raw);
        var builder = new StringBuilder(text.length());

        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);

            if (character == '\u200B'
                    || character == '\u200C'
                    || character == '\u200D'
                    || character == '\uFEFF'
                    || character == '\u205A') {
                continue;
            }

            builder.append(character);
        }

        return builder.toString().replace("\\n", "\n");
    }

    private static @Nullable LocrawData parseLocraw(String message) {
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
            var serverType = BedwarsPractice.string(json, "server_type");

            if (serverType == null) {
                return null;
            }

            return new LocrawData(
                    serverType,
                    BedwarsPractice.string(json, "server_name"),
                    BedwarsPractice.string(json, "game_type"),
                    BedwarsPractice.string(json, "technique_type"),
                    BedwarsPractice.string(json, "game_map")
            );
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

    private static @Nullable String bannerTitle(String text) {
        if (!text.contains("\n")) {
            return null;
        }

        for (var line : text.split("\n")) {
            var value = BedwarsPractice.trimDecoration(line);

            if (value.isEmpty() || BedwarsPractice.isDecoration(value)) {
                continue;
            }

            if (value.startsWith("Welcome to the")
                    || value.contains("STAFF")
                    || value.contains("TOURNAMENT")
                    || value.contains("NameMC")
                    || value.startsWith("Online players")
                    || value.startsWith("Listing all friends")
                    || value.startsWith("Party >")
                    || value.startsWith("FRIEND >")
                    || value.startsWith("Game ")
                    || value.startsWith("Technique ")) {
                return null;
            }

            return value;
        }

        return null;
    }

    private static String trimDecoration(String line) {
        var start = 0;
        var end = line.length();

        while (start < end && (line.charAt(start) == '⁚' || Character.isWhitespace(line.charAt(start)))) {
            start++;
        }

        while (end > start && (line.charAt(end - 1) == '⁚' || Character.isWhitespace(line.charAt(end - 1)))) {
            end--;
        }

        return line.substring(start, end);
    }

    private static boolean isDecoration(String value) {
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);

            if (character != '-'
                    && character != '='
                    && character != '_'
                    && character != '⁚'
                    && !Character.isWhitespace(character)) {
                return false;
            }
        }

        return true;
    }

    private static String stripRank(String name) {
        var value = name.trim();

        while (value.startsWith("[")) {
            var end = value.indexOf(']');

            if (end < 0) {
                break;
            }

            value = value.substring(end + 1).trim();
        }

        return value;
    }

    private static @Nullable Duration parseDuration(String text) {
        var matcher = BedwarsPractice.TECHNIQUE_TIME.matcher(text);

        if (!matcher.find()) {
            return null;
        }

        var minutes = Long.parseLong(matcher.group(1));
        var seconds = Long.parseLong(matcher.group(2));
        var fraction = matcher.group(3);
        var millis = switch (fraction.length()) {
            case 1 -> Long.parseLong(fraction) * 100L;
            case 2 -> Long.parseLong(fraction) * 10L;
            default -> Long.parseLong(fraction.substring(0, 3));
        };

        return Duration.ofMinutes(minutes).plusSeconds(seconds).plusMillis(millis);
    }

    private record LocrawData(
            String serverType,
            @Nullable String serverName,
            @Nullable String gameType,
            @Nullable String techniqueType,
            @Nullable String gameMap
    ) {
    }

    private enum State {
        NONE,
        LOBBY,
        WAITING,
        STARTING,
        PLAYING,
        ENDED
    }
}
