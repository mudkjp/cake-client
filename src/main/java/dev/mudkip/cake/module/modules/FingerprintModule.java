package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleContext;
import dev.mudkip.cake.module.ModuleDefinition;
import dev.mudkip.cake.module.setting.BooleanSetting;
import dev.mudkip.cake.server.ServerAddresses;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FingerprintModule extends ClientModule {
    private static final Set<Identifier> VANILLA_CHANNELS = Set.of(
            BrandPayload.TYPE.id(),
            Identifier.withDefaultNamespace("register"),
            Identifier.withDefaultNamespace("unregister"),
            Identifier.withDefaultNamespace("mco")
    );

    private final BooleanSetting brand;
    private final BooleanSetting channels;
    private final BooleanSetting translations;
    private final BooleanSetting keybinds;
    private final BooleanSetting packCache;
    private final BooleanSetting localPacks;
    private volatile Set<String> allowedTranslations;
    private @Nullable Set<String> pendingTranslations;
    private volatile @Nullable String serverAddress;

    public FingerprintModule(ModuleContext context) {
        super(ModuleDefinition.of("fingerprint", ModuleCategory.MISC), context);
        this.brand = this.booleanSetting("brand", true);
        this.channels = this.booleanSetting("channels", true);
        this.translations = this.booleanSetting("translations", true);
        this.keybinds = this.booleanSetting("keybinds", true);
        this.packCache = this.booleanSetting("pack_cache", true);
        this.localPacks = this.booleanSetting("local_packs", true);
        this.allowedTranslations = Set.of();
    }

    public static boolean isTrustedTranslationPack(String packId) {
        return "vanilla".equals(packId)
                || "programmer_art".equals(packId)
                || "high_contrast".equals(packId)
                || packId.startsWith("server/");
    }

    public boolean shouldSpoofBrand() {
        return this.brand.getValue();
    }

    public boolean shouldBlockPayload(CustomPacketPayload payload) {
        if (!this.channels.getValue() || !this.isMultiplayer()) {
            return false;
        }

        var id = payload.type().id();
        return !FingerprintModule.VANILLA_CHANNELS.contains(id) || FingerprintModule.isRegistrationChannel(id);
    }

    public boolean shouldDropInboundChannel(Identifier id) {
        return this.channels.getValue()
                && this.isMultiplayer()
                && (!FingerprintModule.VANILLA_CHANNELS.contains(id) || FingerprintModule.isRegistrationChannel(id));
    }

    public boolean shouldHideTranslation(boolean fromPacket, String key) {
        return fromPacket
                && this.translations.getValue()
                && this.isMultiplayer()
                && !this.allowedTranslations.contains(key);
    }

    public boolean shouldHideKeybinds() {
        return this.keybinds.getValue() && this.isMultiplayer();
    }

    public boolean shouldMarkPackets() {
        return (this.translations.getValue() || this.keybinds.getValue()) && this.isMultiplayer();
    }

    public boolean shouldIsolatePackCache() {
        return this.packCache.getValue();
    }

    public boolean shouldBlockLocalPackUrls() {
        return this.localPacks.getValue();
    }

    public Component spoofKeybind(String name) {
        var mapping = KeyMapping.get(name);

        if (mapping == null || !"minecraft".equals(mapping.getCategory().id().getNamespace())) {
            return Component.literal(name);
        }

        return mapping.getDefaultKey().getDisplayName();
    }

    public void beginTranslationScan() {
        this.pendingTranslations = ConcurrentHashMap.newKeySet();
    }

    public void finishTranslationScan() {
        if (this.pendingTranslations != null) {
            this.allowedTranslations = this.pendingTranslations;
            this.pendingTranslations = null;
        }
    }

    public void recordTranslationKey(String key) {
        if (this.pendingTranslations != null) {
            this.pendingTranslations.add(key);
        }
    }

    public void setServerAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress socket && socket.getAddress() != null) {
            this.serverAddress = socket.getAddress().getHostAddress();
        }
    }

    public boolean isLocalServer() {
        if (this.client.hasSingleplayerServer()) {
            return true;
        }

        if (this.serverAddress != null) {
            return ServerAddresses.isLocalAddress(this.serverAddress);
        }

        var server = this.client.getCurrentServer();
        return server != null && ServerAddresses.isLocalAddress(server.ip);
    }

    private boolean isMultiplayer() {
        return !this.client.hasSingleplayerServer();
    }

    private static boolean isRegistrationChannel(Identifier id) {
        return "register".equals(id.getPath()) || "unregister".equals(id.getPath());
    }
}
