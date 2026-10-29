package dev.mudkip.cake.module.modules;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.PacketComponentAccess;
import dev.mudkip.cake.module.setting.BooleanSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FingerprintModule extends ClientModule {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("cake", "textures/gui/icons/fingerprint.png");
    private static final Set<Identifier> VANILLA_CHANNELS = Set.of(
            BrandPayload.TYPE.id(),
            Identifier.withDefaultNamespace("register"),
            Identifier.withDefaultNamespace("unregister"),
            Identifier.withDefaultNamespace("mco")
    );
    private static volatile Set<String> allowedTranslations = Set.of();
    private static Set<String> pendingTranslations;
    private static volatile String serverAddress;
    private static FingerprintModule instance;

    private final BooleanSetting brand;
    private final BooleanSetting channels;
    private final BooleanSetting translations;
    private final BooleanSetting keybinds;
    private final BooleanSetting packCache;
    private final BooleanSetting localPacks;

    public FingerprintModule() {
        super(
                "fingerprint",
                Component.translatable("cake.module.fingerprint.name"),
                Component.translatable("cake.module.fingerprint.description"),
                ModuleCategory.MISC,
                FingerprintModule.ICON
        );
        this.brand = this.setting(new BooleanSetting(
                "brand",
                Component.translatable("cake.setting.fingerprint.brand.name"),
                Component.translatable("cake.setting.fingerprint.brand.description"),
                true
        ));
        this.channels = this.setting(new BooleanSetting(
                "channels",
                Component.translatable("cake.setting.fingerprint.channels.name"),
                Component.translatable("cake.setting.fingerprint.channels.description"),
                true
        ));
        this.translations = this.setting(new BooleanSetting(
                "translations",
                Component.translatable("cake.setting.fingerprint.translations.name"),
                Component.translatable("cake.setting.fingerprint.translations.description"),
                true
        ));
        this.keybinds = this.setting(new BooleanSetting(
                "keybinds",
                Component.translatable("cake.setting.fingerprint.keybinds.name"),
                Component.translatable("cake.setting.fingerprint.keybinds.description"),
                true
        ));
        this.packCache = this.setting(new BooleanSetting(
                "pack_cache",
                Component.translatable("cake.setting.fingerprint.pack_cache.name"),
                Component.translatable("cake.setting.fingerprint.pack_cache.description"),
                true
        ));
        this.localPacks = this.setting(new BooleanSetting(
                "local_packs",
                Component.translatable("cake.setting.fingerprint.local_packs.name"),
                Component.translatable("cake.setting.fingerprint.local_packs.description"),
                true
        ));
        FingerprintModule.instance = this;
    }

    public static boolean shouldSpoofBrand() {
        var module = FingerprintModule.active();
        return module != null && module.brand.getValue();
    }

    public static boolean shouldBlockPayload(CustomPacketPayload payload) {
        var module = FingerprintModule.active();

        if (module == null || !module.channels.getValue() || !FingerprintModule.isMultiplayer()) {
            return false;
        }

        var id = payload.type().id();

        if (!FingerprintModule.VANILLA_CHANNELS.contains(id)) {
            return true;
        }

        return FingerprintModule.isRegistrationChannel(id);
    }

    public static boolean shouldDropInboundChannel(Identifier id) {
        var module = FingerprintModule.active();
        return module != null
                && module.channels.getValue()
                && FingerprintModule.isMultiplayer()
                && (!FingerprintModule.VANILLA_CHANNELS.contains(id) || FingerprintModule.isRegistrationChannel(id));
    }

    public static boolean shouldMarkPackets() {
        var module = FingerprintModule.active();
        return module != null
                && (module.translations.getValue() || module.keybinds.getValue())
                && FingerprintModule.isMultiplayer();
    }

    public static boolean shouldHideTranslation(boolean fromPacket, String key) {
        var module = FingerprintModule.active();
        return fromPacket
                && module != null
                && module.translations.getValue()
                && FingerprintModule.isMultiplayer()
                && !FingerprintModule.allowedTranslations.contains(key);
    }

    public static boolean shouldHideKeybinds() {
        var module = FingerprintModule.active();
        return module != null && module.keybinds.getValue() && FingerprintModule.isMultiplayer();
    }

    public static boolean shouldIsolatePackCache() {
        var module = FingerprintModule.active();
        return module != null && module.packCache.getValue();
    }

    public static boolean shouldBlockLocalPackUrls() {
        var module = FingerprintModule.active();
        return module != null && module.localPacks.getValue();
    }

    public static Component spoofKeybind(String name) {
        var mapping = KeyMapping.get(name);

        if (mapping == null || !"minecraft".equals(mapping.getCategory().id().getNamespace())) {
            return Component.literal(name);
        }

        return mapping.getDefaultKey().getDisplayName();
    }

    public static Component markFromPacket(Component component) {
        if (component.getContents() instanceof PacketComponentAccess access) {
            access.cake$setFromPacket();
        }

        if (component.getContents() instanceof TranslatableContents translatable) {
            for (var argument : translatable.getArgs()) {
                if (argument instanceof Component nested) {
                    FingerprintModule.markFromPacket(nested);
                }
            }
        }

        for (var sibling : component.getSiblings()) {
            FingerprintModule.markFromPacket(sibling);
        }

        return component;
    }

    public static void beginTranslationScan() {
        FingerprintModule.pendingTranslations = ConcurrentHashMap.newKeySet();
    }

    public static void finishTranslationScan() {
        if (FingerprintModule.pendingTranslations != null) {
            FingerprintModule.allowedTranslations = FingerprintModule.pendingTranslations;
            FingerprintModule.pendingTranslations = null;
        }
    }

    public static void recordTranslationKey(String key) {
        if (FingerprintModule.pendingTranslations != null) {
            FingerprintModule.pendingTranslations.add(key);
        }
    }

    public static boolean isTrustedTranslationPack(String packId) {
        return "vanilla".equals(packId)
                || "programmer_art".equals(packId)
                || "high_contrast".equals(packId)
                || packId.startsWith("server/");
    }

    public static boolean isLocalServer() {
        var minecraft = Minecraft.getInstance();

        if (minecraft == null || minecraft.hasSingleplayerServer()) {
            return true;
        }

        if (FingerprintModule.serverAddress != null) {
            return FingerprintModule.isLocalAddress(FingerprintModule.serverAddress);
        }

        var server = minecraft.getCurrentServer();
        return server != null && FingerprintModule.isLocalAddress(server.ip);
    }

    public static void setServerAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress socket && socket.getAddress() != null) {
            FingerprintModule.serverAddress = socket.getAddress().getHostAddress();
        }
    }

    public static boolean isLocalAddress(String host) {
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

    private static FingerprintModule active() {
        var module = FingerprintModule.instance;
        return module != null && module.isEnabled() ? module : null;
    }

    private static boolean isRegistrationChannel(Identifier id) {
        return "register".equals(id.getPath()) || "unregister".equals(id.getPath());
    }

    private static boolean isMultiplayer() {
        var minecraft = Minecraft.getInstance();
        return minecraft != null && !minecraft.hasSingleplayerServer();
    }
}
