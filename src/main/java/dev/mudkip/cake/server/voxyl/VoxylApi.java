package dev.mudkip.cake.server.voxyl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoxylApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoxylApi.class);
    private static final String STATS_URL = "https://api.voxyl.net/player/stats/overall/";
    private static final int UNKNOWN = -1;
    private static final long TTL_MILLIS = 5L * 60L * 1000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10L))
            .build();
    private static final Gson GSON = new Gson();

    private final Map<UUID, CachedLevel> levels;
    private final Set<UUID> pending;

    public VoxylApi() {
        this.levels = new ConcurrentHashMap<>();
        this.pending = ConcurrentHashMap.newKeySet();
    }

    public @Nullable Integer getLevel(UUID uuid) {
        var cached = this.levels.get(uuid);
        return cached != null && cached.level() >= 0 ? cached.level() : null;
    }

    public void updateLevel(UUID uuid, int level) {
        var cached = this.levels.get(uuid);

        if (cached != null && cached.level() == level) {
            return;
        }

        this.levels.put(uuid, new CachedLevel(level, System.currentTimeMillis()));
    }

    public boolean requestLevel(UUID uuid, String apiKey) {
        var key = apiKey.trim();

        if (key.isEmpty()) {
            return false;
        }

        var cached = this.levels.get(uuid);

        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < VoxylApi.TTL_MILLIS) {
            return false;
        }

        if (!this.pending.add(uuid)) {
            return false;
        }

        HttpRequest request;

        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(VoxylApi.STATS_URL + uuid + "?api=" + key))
                    .timeout(Duration.ofSeconds(10L))
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            this.pending.remove(uuid);
            return false;
        }

        VoxylApi.HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    this.levels.put(uuid, new CachedLevel(VoxylApi.parseLevel(response, throwable), System.currentTimeMillis()));
                    this.pending.remove(uuid);
                });

        return true;
    }

    private static int parseLevel(HttpResponse<String> response, Throwable throwable) {
        if (throwable != null || response == null || response.statusCode() != 200) {
            return VoxylApi.UNKNOWN;
        }

        try {
            var json = VoxylApi.GSON.fromJson(response.body(), JsonObject.class);

            if (json != null && json.has("success") && json.get("success").getAsBoolean() && json.has("level")) {
                return json.get("level").getAsInt();
            }
        } catch (Exception exception) {
            VoxylApi.LOGGER.warn("Failed to parse the Voxyl API response", exception);
        }

        return VoxylApi.UNKNOWN;
    }

    private record CachedLevel(int level, long fetchedAt) {
    }
}
