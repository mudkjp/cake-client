package dev.mudkip.cake.module;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class PingModule extends HudModule {
    private static final int UPDATE_INTERVAL_TICKS = 20;
    private static final long SAMPLE_TIMEOUT_MILLIS = 10_000L;

    private @Nullable ClientPacketListener connection;
    private volatile int measuredPing = -1;
    private volatile long lastPong;
    private int ticks;

    public PingModule(ModuleContext context) {
        super(ModuleDefinition.of("ping", ModuleCategory.MISC), context, Anchor.TOP_LEFT, 4.0D, 26.0D);
    }

    @Override
    public String getText() {
        var connection = this.client.getConnection();

        if (connection == null || this.client.player == null) {
            return "Ping: --";
        }

        var ping = this.measuredPing;

        if (ping >= 0 && Util.getMillis() - this.lastPong <= PingModule.SAMPLE_TIMEOUT_MILLIS) {
            return "Ping: " + ping + " ms";
        }

        var playerInfo = connection.getPlayerInfo(this.client.player.getUUID());
        return playerInfo == null ? "Ping: --" : "Ping: " + playerInfo.getLatency() + " ms";
    }

    public void recordPong(long time) {
        var now = Util.getMillis();
        var sample = (int) Math.max(0L, now - time);
        var previous = this.measuredPing;
        this.measuredPing = previous < 0 ? sample : (previous * 3 + sample) / 4;
        this.lastPong = now;
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    @Override
    protected void onTick() {
        var connection = this.client.getConnection();

        if (connection == null || this.client.player == null) {
            this.reset();
            return;
        }

        if (connection != this.connection) {
            this.connection = connection;
            this.measuredPing = -1;
            this.ticks = 0;
        }

        if (++this.ticks < PingModule.UPDATE_INTERVAL_TICKS) {
            return;
        }

        this.ticks = 0;

        if (Util.getMillis() - this.lastPong > PingModule.SAMPLE_TIMEOUT_MILLIS) {
            this.measuredPing = -1;
        }

        connection.send(new ServerboundPingRequestPacket(Util.getMillis()));
    }

    private void reset() {
        this.connection = null;
        this.measuredPing = -1;
        this.lastPong = 0L;
        this.ticks = 0;
    }
}
