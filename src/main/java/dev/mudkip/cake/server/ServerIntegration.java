package dev.mudkip.cake.server;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerIntegration {
    private final String id;
    private final String name;
    private final List<String> addresses;

    public ServerIntegration(String id, String name, String... addresses) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Server integration id cannot be blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Server integration " + id + " needs a name");
        }

        if (addresses.length == 0) {
            throw new IllegalArgumentException("Server integration " + id + " needs at least one address");
        }

        this.id = id;
        this.name = name;
        this.addresses = ServerIntegration.normalizeAll(id, addresses);
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public List<String> getAddresses() {
        return this.addresses;
    }

    public boolean isCurrent() {
        return ServerSession.isOn(this);
    }

    public boolean matches(String address) {
        var host = ServerIntegration.normalize(address);

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

    static @Nullable String normalize(String address) {
        if (address == null) {
            return null;
        }

        var value = address.trim().toLowerCase(Locale.ROOT);

        if (value.startsWith("*.")) {
            value = value.substring(2);
        }

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

    private static List<String> normalizeAll(String id, String[] addresses) {
        var normalized = new ArrayList<String>(addresses.length);

        for (var address : addresses) {
            var host = ServerIntegration.normalize(address);

            if (host == null) {
                throw new IllegalArgumentException("Invalid address for server integration " + id + ": " + address);
            }

            normalized.add(host);
        }

        return List.copyOf(normalized);
    }
}
