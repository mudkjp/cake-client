package dev.mudkip.cake.module;

public final class PacketSource {
    private static final ThreadLocal<Boolean> DECODING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private PacketSource() {
    }

    public static void enter() {
        PacketSource.DECODING.set(Boolean.TRUE);
    }

    public static void exit() {
        PacketSource.DECODING.set(Boolean.FALSE);
    }

    public static boolean isDecoding() {
        return PacketSource.DECODING.get();
    }
}
