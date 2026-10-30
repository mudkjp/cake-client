package dev.mudkip.cake.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServerMappings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMappings.class);
    private static final URI SOURCE = URI.create("https://servermappings.lunarclientcdn.com/servers.json");
    private static final long CACHE_AGE = 7L * 24L * 60L * 60L * 1000L;
    private static final long RETRY_DELAY = 5L * 60L * 1000L;
    private static final int CACHE_VERSION = 1;
    private static final Lookup EMPTY = new Lookup(Map.of(), List.of());

    private final Path cachePath;
    private final HttpClient httpClient;
    private volatile Lookup mappings;
    private volatile long fetched;
    private volatile boolean loading;
    private volatile boolean loaded;
    private volatile long lastAttempt;

    public ServerMappings(Path cachePath) {
        this.cachePath = cachePath;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15L))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mappings = ServerMappings.EMPTY;
    }

    public void ensureLoaded() {
        var now = System.currentTimeMillis();

        if (this.loaded && !this.mappings.exact().isEmpty() && now - this.fetched < ServerMappings.CACHE_AGE) {
            return;
        }

        if (this.loading || now - this.lastAttempt < ServerMappings.RETRY_DELAY) {
            return;
        }

        this.loading = true;
        this.lastAttempt = now;

        var thread = new Thread(this::load, "Cake Server Mappings");
        thread.setDaemon(true);
        thread.start();
    }

    public @Nullable Mapping lookup(String address) {
        var host = ServerAddresses.normalize(address);

        if (host == null) {
            return null;
        }

        var current = this.mappings;
        var exact = current.exact().get(host);

        if (exact != null) {
            return new Mapping(exact.name(), exact.logo());
        }

        @Nullable Entry best = null;
        var bestLength = 0;

        for (var entry : current.entries()) {
            for (var mapped : entry.addresses()) {
                if (mapped.length() > bestLength && host.endsWith("." + mapped)) {
                    best = entry;
                    bestLength = mapped.length();
                }
            }
        }

        return best == null ? null : new Mapping(best.name(), best.logo());
    }

    private void load() {
        try {
            if (!this.loaded) {
                this.readCache();
            }

            if (this.loaded && !this.mappings.exact().isEmpty() && System.currentTimeMillis() - this.fetched < ServerMappings.CACHE_AGE) {
                return;
            }

            var cache = this.download();

            if (cache != null) {
                this.apply(cache);
                this.fetched = cache.fetched();
                this.loaded = true;
                this.writeCache(cache);
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to update ServerMappings", exception);
        } finally {
            this.loading = false;
        }
    }

    private void readCache() {
        if (!Files.exists(this.cachePath)) {
            return;
        }

        try (var reader = Files.newBufferedReader(this.cachePath, StandardCharsets.UTF_8)) {
            var cache = ServerMappings.GSON.fromJson(reader, Cache.class);

            if (cache == null || cache.version() != ServerMappings.CACHE_VERSION) {
                return;
            }

            this.apply(cache);
            this.fetched = cache.fetched();
            this.loaded = true;
        } catch (Exception exception) {
            LOGGER.warn("Failed to read the ServerMappings cache", exception);
        }
    }

    private @Nullable Cache download() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(ServerMappings.SOURCE)
                .timeout(Duration.ofSeconds(60L))
                .header("User-Agent", "CakeClient")
                .GET()
                .build();
        var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            response.body().close();
            return null;
        }

        var servers = new ArrayList<Entry>();

        try (var stream = response.body();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             var json = new JsonReader(reader)) {
            json.beginArray();

            while (json.hasNext()) {
                @Nullable String name = null;
                @Nullable String logo128 = null;
                @Nullable String logo64 = null;
                List<String> addresses = List.of();
                var inactive = false;

                json.beginObject();

                while (json.hasNext()) {
                    var field = json.nextName();

                    switch (field) {
                        case "name" -> name = json.nextString();
                        case "addresses" -> {
                            var values = new ArrayList<String>();
                            json.beginArray();

                            while (json.hasNext()) {
                                values.add(json.nextString());
                            }

                            json.endArray();
                            addresses = values;
                        }
                        case "images" -> {
                            json.beginObject();

                            while (json.hasNext()) {
                                var imageField = json.nextName();

                                switch (imageField) {
                                    case "logo-128" -> logo128 = json.nextString();
                                    case "logo-64" -> logo64 = json.nextString();
                                    default -> json.skipValue();
                                }
                            }

                            json.endObject();
                        }
                        case "inactive" -> inactive = json.nextBoolean();
                        default -> json.skipValue();
                    }
                }

                json.endObject();

                var logo = logo128 != null ? logo128 : logo64;

                if (!inactive && name != null && !addresses.isEmpty()) {
                    servers.add(new Entry(name, logo, List.copyOf(addresses)));
                }
            }

            json.endArray();
        }

        return new Cache(ServerMappings.CACHE_VERSION, System.currentTimeMillis(), List.copyOf(servers));
    }

    private void apply(Cache cache) {
        var servers = cache.servers();

        if (servers == null) {
            return;
        }

        var exact = new HashMap<String, Entry>();
        var entries = new ArrayList<Entry>();

        for (var entry : servers) {
            if (entry == null || entry.name() == null || entry.addresses() == null) {
                continue;
            }

            var addresses = new ArrayList<String>();

            for (var address : entry.addresses()) {
                if (address == null) {
                    continue;
                }

                var normalized = ServerAddresses.normalize(address);

                if (normalized != null) {
                    addresses.add(normalized);
                }
            }

            if (!addresses.isEmpty()) {
                var normalizedEntry = new Entry(entry.name(), entry.logo(), List.copyOf(addresses));
                entries.add(normalizedEntry);

                for (var address : addresses) {
                    exact.putIfAbsent(address, normalizedEntry);
                }
            }
        }

        this.mappings = new Lookup(Map.copyOf(exact), List.copyOf(entries));
    }

    private void writeCache(Cache cache) {
        try {
            Files.createDirectories(this.cachePath.getParent());
            Files.writeString(this.cachePath, ServerMappings.GSON.toJson(cache), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Failed to write the ServerMappings cache", exception);
        }
    }

    public record Mapping(String name, @Nullable String logo) {
    }

    private record Cache(int version, long fetched, @Nullable List<Entry> servers) {
    }

    private record Entry(String name, @Nullable String logo, List<String> addresses) {
    }

    private record Lookup(Map<String, Entry> exact, List<Entry> entries) {
    }
}
