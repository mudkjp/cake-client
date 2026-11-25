package dev.mudkip.cake.module;

import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import dev.mudkip.cake.server.bedwars.BedwarsPracticeEvents;
import dev.mudkip.cake.server.bedwars.BedwarsPracticeGame;
import dev.mudkip.cake.server.bedwars.BedwarsPracticeListener;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

public final class BlockSumoTimerModule extends HudModule implements BedwarsPracticeListener {
    private static final long ITEM_INTERVAL_MILLIS = 25_000L;
    private static final long ITEM_START_DELAY_MILLIS = 23_000L;
    private static final long OP_ITEM_INTERVAL_MILLIS = 30_000L;
    private static final long OP_ITEM_START_DELAY_MILLIS = 9_000L;
    private static final long OP_ITEM_WARNING_MILLIS = 5_000L;
    private static final Pattern GAME_STARTED = Pattern.compile("^Game (?:has )?started!$");
    private static final Pattern ITEM_SPAWN = Pattern.compile(
            "^All players have rec(?:ei|ie)ved (?:the )?.+ powerup!$"
    );
    private static final Pattern OP_ITEM_WARNING = Pattern.compile(
            "^An OP [Ii]tem is spawning on the gold block in 5 seconds!$"
    );

    private final BedwarsPractice bedwarsPractice;
    private long nextItem;
    private long nextOpItem;
    private boolean itemTracked;
    private boolean opItemTracked;

    public BlockSumoTimerModule(ModuleContext context) {
        super(
                ModuleDefinition.of("blocksumotimer", ModuleCategory.MISC)
                        .withIntegration(BedwarsPractice.INTEGRATION),
                context
        );
        this.bedwarsPractice = context.bedwarsPractice();
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::allowGameMessage);
    }

    @Override
    public String getText() {
        if (!BlockSumoTimerModule.isBlockSumo(this.bedwarsPractice.getGame())) {
            return "";
        }

        var now = Util.getMillis();
        this.advanceTimers(now);
        var builder = new StringBuilder();

        if (this.itemTracked) {
            BlockSumoTimerModule.append(builder, "Next Item", this.nextItem - now);
        }

        if (this.opItemTracked) {
            BlockSumoTimerModule.append(builder, "Next OP Item", this.nextOpItem - now);
        }

        return builder.toString();
    }

    @Override
    protected void onEnable() {
        this.bedwarsPractice.addListener(this);
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.bedwarsPractice.removeListener(this);
        this.reset();
    }

    @Override
    protected void onTick() {
        this.advanceTimers(Util.getMillis());
    }

    @Override
    public void onServerJoin(BedwarsPracticeEvents.ServerJoin event) {
        this.reset();
    }

    @Override
    public void onGameModeChange(BedwarsPracticeEvents.GameModeChange event) {
        if (!BlockSumoTimerModule.isBlockSumo(event.game())) {
            this.reset();
        }
    }

    @Override
    public void onGameStart(BedwarsPracticeEvents.GameStart event) {
        this.startTimers();
    }

    @Override
    public void onGameEnd(BedwarsPracticeEvents.GameEnd event) {
        this.reset();
    }

    @Override
    public void onGameLeave(BedwarsPracticeEvents.GameLeave event) {
        this.reset();
    }

    @Override
    public void onServerLeave(BedwarsPracticeEvents.ServerLeave event) {
        this.reset();
    }

    private boolean allowGameMessage(Component message, boolean overlay) {
        if (overlay || !this.isEnabled()) {
            return true;
        }

        var text = message.getString().trim();

        if (BlockSumoTimerModule.GAME_STARTED.matcher(text).matches()) {
            this.startTimers();
            return true;
        }

        if (BlockSumoTimerModule.OP_ITEM_WARNING.matcher(text).matches()) {
            this.nextOpItem = Util.getMillis() + BlockSumoTimerModule.OP_ITEM_WARNING_MILLIS;
            this.opItemTracked = true;
            return true;
        }

        if (BlockSumoTimerModule.ITEM_SPAWN.matcher(text).matches()) {
            this.nextItem = Util.getMillis() + BlockSumoTimerModule.ITEM_INTERVAL_MILLIS;
            this.itemTracked = true;
        }

        return true;
    }

    private void advanceTimers(long now) {
        if (this.itemTracked) {
            this.nextItem = BlockSumoTimerModule.advance(this.nextItem, now, BlockSumoTimerModule.ITEM_INTERVAL_MILLIS);
        }

        if (this.opItemTracked) {
            this.nextOpItem = BlockSumoTimerModule.advance(
                    this.nextOpItem,
                    now,
                    BlockSumoTimerModule.OP_ITEM_INTERVAL_MILLIS
            );
        }
    }

    private void startTimers() {
        var now = Util.getMillis();
        this.nextItem = now + BlockSumoTimerModule.ITEM_START_DELAY_MILLIS;
        this.nextOpItem = now + BlockSumoTimerModule.OP_ITEM_START_DELAY_MILLIS;
        this.itemTracked = true;
        this.opItemTracked = true;
    }

    private void reset() {
        this.nextItem = 0L;
        this.nextOpItem = 0L;
        this.itemTracked = false;
        this.opItemTracked = false;
    }

    private static long advance(long target, long now, long interval) {
        if (target > now) {
            return target;
        }

        var cycles = (now - target) / interval + 1L;
        return target + cycles * interval;
    }

    private static void append(StringBuilder builder, String label, long remaining) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }

        var seconds = Math.max(0L, (remaining + 999L) / 1000L);
        builder.append(label)
                .append(": ")
                .append(seconds)
                .append(seconds == 1L ? " second" : " seconds");
    }

    private static boolean isBlockSumo(@Nullable BedwarsPracticeGame game) {
        return game != null
                && ("sumo".equalsIgnoreCase(game.modeId()) || "Block Sumo".equalsIgnoreCase(game.mode()));
    }
}
