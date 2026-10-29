package dev.mudkip.cake.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;

@Mixin(HttpUtil.class)
public abstract class FingerprintHttpUtilMixin {
    @WrapOperation(
            method = "downloadFile",
            at = @At(value = "INVOKE", target = "Ljava/net/HttpURLConnection;getInputStream()Ljava/io/InputStream;")
    )
    private static InputStream cake$blockLocalAddresses(
            HttpURLConnection connection,
            Operation<InputStream> original,
            @Local(argsOnly = true) Proxy proxy,
            @Local(argsOnly = true) Map<String, String> headers,
            @Local LocalRef<HttpURLConnection> connectionRef
    ) throws IOException {
        if (!FingerprintModule.shouldBlockLocalPackUrls() || FingerprintModule.isLocalServer()) {
            return original.call(connection);
        }

        if (FingerprintModule.isLocalAddress(connection.getURL().getHost())) {
            throw new IllegalStateException("Blocked a resource pack download from a local address");
        }

        connection.setInstanceFollowRedirects(false);

        var status = connection.getResponseCode();
        var redirects = 0;

        while (connection.getHeaderField("Location") != null && ((status > 299 && status < 304) || status == 307)) {
            if (++redirects > 20) {
                throw new ProtocolException("Server redirected too many times (20)");
            }

            URL next;

            try {
                next = new URL(connection.getHeaderField("Location"));
            } catch (MalformedURLException exception) {
                next = new URL(connection.getURL(), connection.getHeaderField("Location"));
            }

            if (!connection.getURL().getProtocol().equalsIgnoreCase(next.getProtocol())) {
                break;
            }

            if (FingerprintModule.isLocalAddress(next.getHost())) {
                throw new IllegalStateException("Blocked a resource pack redirect to a local address");
            }

            connection = (HttpURLConnection) next.openConnection(proxy);
            connection.setInstanceFollowRedirects(false);

            for (var header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            status = connection.getResponseCode();
        }

        connectionRef.set(connection);
        return original.call(connection);
    }
}
