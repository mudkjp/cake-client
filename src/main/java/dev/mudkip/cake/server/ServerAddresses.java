package dev.mudkip.cake.server;

import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public final class ServerAddresses {
    private ServerAddresses() {
    }

    public static @Nullable String normalize(@Nullable String address) {
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

    public static boolean isLocalAddress(@Nullable String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        if (host.startsWith("[")) {
            var end = host.indexOf(']');
            host = end > 0 ? host.substring(1, end) : host.substring(1);
        } else {
            var colon = host.lastIndexOf(':');

            if (colon > 0 && host.indexOf(':') == colon) {
                host = host.substring(0, colon);
            }
        }

        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
            }
        } catch (UnknownHostException exception) {
            return false;
        }

        return false;
    }
}
