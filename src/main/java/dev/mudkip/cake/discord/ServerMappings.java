package dev.mudkip.cake.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import dev.mudkip.cake.CakeClient;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

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
import java.util.*;

public final class ServerMappings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final URI SOURCE = URI.create("https://servermappings.lunarclientcdn.com/servers.json");
    private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("cake-servermappings.json");
    private static final long CACHE_AGE = 7L * 24L * 60L * 60L * 1000L;
    private static final long RETRY_DELAY = 5L * 60L * 1000L;
    private static final int CACHE_VERSION = 1;
    private static final Lookup EMPTY = new Lookup(Map.of(), List.of());
    private static volatile Lookup mappings = ServerMappings.EMPTY;
    private static volatile long fetched;
    private static volatile boolean loading;
    private static volatile boolean loaded;
    private static volatile long lastAttempt;

    private ServerMappings() {

    }

    public static void ensureLoaded() {
        var now = System.currentTimeMillis();

        if (loaded && !mappings.exact().isEmpty() && now - fetched < ServerMappings.CACHE_AGE) {
            return;
        }

        if (loading || now - lastAttempt < ServerMappings.RETRY_DELAY) {
            return;
        }

        loading = true;
        lastAttempt = now;

        var thread = new Thread(ServerMappings::load, "Cake Server Mappings");
        thread.setDaemon(true);
        thread.start();
    }

    public static @Nullable Mapping lookup(String address) {
        var host = ServerMappings.normalize(address);

        if (host == null) {
            return null;
        }

        var current = mappings;
        var exact = current.exact().get(host);

        if (exact != null) {
            return new Mapping(exact.name(), exact.logo());
        }

        Entry best = null;
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

    private static void load() {
        try {
            if (!loaded) {
                ServerMappings.readCache();
            }

            if (loaded && !mappings.exact().isEmpty() && System.currentTimeMillis() - fetched < ServerMappings.CACHE_AGE) {
                return;
            }

            var cache = ServerMappings.download();

            if (cache != null) {
                ServerMappings.apply(cache);
                fetched = cache.fetched();
                loaded = true;
                ServerMappings.writeCache(cache);
            }
        } catch (Exception exception) {
            CakeClient.LOGGER.warn("Failed to update ServerMappings", exception);
        } finally {
            loading = false;
        }
    }

    private static void readCache() {
        if (!Files.exists(ServerMappings.CACHE)) {
            return;
        }

        try (var reader = Files.newBufferedReader(ServerMappings.CACHE, StandardCharsets.UTF_8)) {
            var cache = ServerMappings.GSON.fromJson(reader, Cache.class);

            if (cache == null || cache.version() != ServerMappings.CACHE_VERSION) {
                return;
            }

            ServerMappings.apply(cache);
            fetched = cache.fetched();
            loaded = true;
        } catch (Exception exception) {
            CakeClient.LOGGER.warn("Failed to read the ServerMappings cache", exception);
        }
    }

    private static @Nullable Cache download() throws IOException, InterruptedException {
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15L))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var request = HttpRequest.newBuilder(ServerMappings.SOURCE)
                .timeout(Duration.ofSeconds(60L))
                .header("User-Agent", "CakeClient")
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

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
                String name = null;
                String logo128 = null;
                String logo64 = null;
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

    private static void apply(Cache cache) {
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

                var normalized = ServerMappings.normalize(address);

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

        mappings = new Lookup(Map.copyOf(exact), List.copyOf(entries));
    }

    private static void writeCache(Cache cache) {
        try {
            Files.createDirectories(ServerMappings.CACHE.getParent());
            Files.writeString(ServerMappings.CACHE, ServerMappings.GSON.toJson(cache), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CakeClient.LOGGER.warn("Failed to write the ServerMappings cache", exception);
        }
    }

    private static @Nullable String normalize(String address) {
        var value = address.trim().toLowerCase(Locale.ROOT);

        if (value.isEmpty() || value.startsWith("[")) {
            return null;
        }

        var colon = value.indexOf(':');

        if (colon >= 0) {
            if (colon != value.lastIndexOf(':')) {
                return null;
            }

            value = value.substring(0, colon);
        }

        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        return value.isEmpty() ? null : value;
    }

    public record Mapping(String name, @Nullable String logo) {

    }

    private record Cache(int version, long fetched, List<Entry> servers) {

    }

    private record Entry(String name, @Nullable String logo, List<String> addresses) {

    }

    private record Lookup(Map<String, Entry> exact, List<Entry> entries) {

    }
}
