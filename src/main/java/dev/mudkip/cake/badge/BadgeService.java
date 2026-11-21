//? if !modrinth {
package dev.mudkip.cake.badge;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.mudkip.cake.server.ServerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class BadgeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeService.class);
    private static final String BACKEND = "wss://api.cake.mudkip.dev/ws/presence";
    private static final Component CAKE_BADGE = Component.empty()
            .append(Component.literal(" "))
            .append(Component.object(
                    new AtlasSprite(
                            Identifier.fromNamespaceAndPath("minecraft", "items"),
                            Identifier.fromNamespaceAndPath("minecraft", "item/cake")
                    ),
                    Component.literal("[cake]")
            ).withStyle(style -> style.withoutShadow()));

    private final Minecraft client;
    private final ServerSession serverSession;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final AtomicReference<Set<String>> users;
    private volatile @Nullable WebSocket socket;
    private volatile @Nullable String server;
    private volatile boolean connecting;
    private volatile long generation;
    private volatile long appliedRevision = -1;
    private volatile long reconnectAt;
    private volatile int reconnectAttempts;

    public BadgeService(Minecraft client, ServerSession serverSession) {
        this.client = client;
        this.serverSession = serverSession;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "cake-badge");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(this.executor)
                .build();
        this.users = new AtomicReference<>(Set.of());
    }

    public @Nullable Component suffix(GameProfile profile) {
        var current = this.users.get();
        var name = profile.name();

        if (!current.contains(profile.id().toString())
                && (name == null || !current.contains(name.toLowerCase(Locale.ROOT)))) {
            return null;
        }

        return BadgeService.CAKE_BADGE;
    }

    public void tick() {
        var current = this.serverSession.getAddress();

        if (current == null) {
            if (this.server != null || this.socket != null) {
                this.server = null;
                this.appliedRevision = -1;
                this.users.set(Set.of());
                this.close();
            }

            return;
        }

        if (!current.equals(this.server)) {
            this.server = current;
            this.appliedRevision = -1;
            this.users.set(Set.of());
            this.reconnectAttempts = 0;
            this.reconnectAt = 0;
            this.close();
            this.connect(current);
            return;
        }

        if (this.socket == null
                && !this.connecting
                && System.currentTimeMillis() >= this.reconnectAt) {
            this.connect(current);
        }
    }

    public void shutdown() {
        this.server = null;
        this.appliedRevision = -1;
        this.users.set(Set.of());
        this.close();
        this.httpClient.close();
        this.executor.shutdownNow();
    }

    private void connect(String server) {
        if (this.connecting) {
            return;
        }

        this.connecting = true;

        var user = this.client.getUser();
        var generation = ++this.generation;
        var url = BadgeService.BACKEND
                + "?server=" + BadgeService.encode(server)
                + "&username=" + BadgeService.encode(user.getName())
                + "&uuid=" + user.getProfileId();
        var listener = new Listener(server, generation);

        try {
            this.httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(url), listener)
                    .whenComplete((webSocket, throwable) -> {
                        if (throwable != null) {
                            this.handleFailure(throwable, generation);
                        }
                    });
        } catch (RuntimeException exception) {
            this.handleFailure(exception, generation);
        }
    }

    private void close() {
        this.connecting = false;
        this.generation++;

        var current = this.socket;
        this.socket = null;

        if (current == null) {
            return;
        }

        try {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (RuntimeException exception) {
            current.abort();
        }
    }

    private void handleFailure(Throwable throwable, long generation) {
        if (generation != this.generation) {
            return;
        }

        this.connecting = false;
        BadgeService.LOGGER.debug("Badge connection failed", throwable);
        this.scheduleReconnect();
    }

    private void handleClosed(WebSocket webSocket) {
        if (this.socket != webSocket) {
            return;
        }

        this.socket = null;
        this.connecting = false;
        this.appliedRevision = -1;
        this.users.set(Set.of());
        this.scheduleReconnect();
    }

    private void handleError(WebSocket webSocket, Throwable throwable, long generation) {
        if (generation != this.generation) {
            return;
        }

        this.connecting = false;

        if (this.socket == webSocket) {
            this.socket = null;
            this.appliedRevision = -1;
            this.users.set(Set.of());
        }

        this.handleFailure(throwable, generation);
    }

    private void handleMessage(String raw) {
        try {
            var root = JsonParser.parseString(raw).getAsJsonObject();
            var type = root.get("type");

            if (type == null
                    || (!"hello".equals(type.getAsString()) && !"presence".equals(type.getAsString()))) {
                return;
            }

            var snapshot = root.getAsJsonObject("snapshot");

            if (snapshot == null) {
                return;
            }

            var userList = snapshot.getAsJsonArray("users");
            var revision = snapshot.get("revision");

            if (userList == null || revision == null || !revision.isJsonPrimitive()) {
                return;
            }

            var revisionValue = revision.getAsLong();

            if (revisionValue < this.appliedRevision) {
                return;
            }

            this.appliedRevision = revisionValue;

            var found = new HashSet<String>();

            for (var element : userList) {
                if (!element.isJsonObject()) {
                    continue;
                }

                var user = element.getAsJsonObject();
                var uuid = user.get("uuid");
                var username = user.get("username");

                if (uuid != null && uuid.isJsonPrimitive()) {
                    found.add(uuid.getAsString().toLowerCase(Locale.ROOT));
                }

                if (username != null && username.isJsonPrimitive()) {
                    found.add(username.getAsString().toLowerCase(Locale.ROOT));
                }
            }

            this.users.set(Set.copyOf(found));
        } catch (RuntimeException exception) {
            BadgeService.LOGGER.debug("Ignoring malformed badge message", exception);
        }
    }

    private void scheduleReconnect() {
        this.reconnectAttempts = Math.min(this.reconnectAttempts + 1, 5);
        this.reconnectAt = System.currentTimeMillis() + (1_000L << this.reconnectAttempts);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private final class Listener implements WebSocket.Listener {
        private final String server;
        private final long generation;
        private final StringBuilder buffer;

        private Listener(String server, long generation) {
            this.server = server;
            this.generation = generation;
            this.buffer = new StringBuilder();
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            if (!this.isCurrent()) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stale");
                return;
            }

            BadgeService.this.socket = webSocket;
            BadgeService.this.connecting = false;
            BadgeService.this.reconnectAttempts = 0;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (this.isCurrent()) {
                this.buffer.append(data);

                if (last) {
                    var message = this.buffer.toString();
                    this.buffer.setLength(0);
                    BadgeService.this.handleMessage(message);
                }
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            BadgeService.this.handleClosed(webSocket);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            BadgeService.this.handleError(webSocket, error, this.generation);
        }

        private boolean isCurrent() {
            return this.generation == BadgeService.this.generation
                    && this.server.equals(BadgeService.this.server);
        }
    }
}
//?}
