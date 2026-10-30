package dev.mudkip.cake.server;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ServerIntegration(String id, String name, List<String> addresses) {
    public ServerIntegration {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Server integration id cannot be blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Server integration " + id + " needs a name");
        }

        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalArgumentException("Server integration " + id + " needs at least one address");
        }

        addresses = normalizeAll(id, addresses);
    }

    public static ServerIntegration of(String id, String name, String... addresses) {
        return new ServerIntegration(id, name, List.of(addresses));
    }

    public boolean matches(@Nullable String address) {
        var host = ServerAddresses.normalize(address);

        if (host == null) {
            return false;
        }

        for (var mapped : this.addresses) {
            if (host.equals(mapped) || host.endsWith("." + mapped)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> normalizeAll(String id, List<String> addresses) {
        var normalized = new ArrayList<String>(addresses.size());

        for (var address : addresses) {
            var host = ServerAddresses.normalize(address);

            if (host == null) {
                throw new IllegalArgumentException("Invalid address for server integration " + id + ": " + address);
            }

            normalized.add(host);
        }

        return List.copyOf(normalized);
    }
}
