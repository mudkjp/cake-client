package dev.mudkip.cake.server.bedwars;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public final class BedwarsPracticeEvents {
    private BedwarsPracticeEvents() {

    }

    public record ServerJoin(String server, ServerKind kind) {

    }

    public record ServerLeave(@Nullable String server) {

    }

    public record QueueChange(boolean queued) {

    }

    public record GameJoin(BedwarsPracticeGame game) {

    }

    public record GameModeChange(BedwarsPracticeGame game, @Nullable String previousMode) {

    }

    public record GameStart(BedwarsPracticeGame game) {

    }

    public record RoundEnd(BedwarsPracticeGame game, BedwarsPracticeResult result) {

    }

    public record TechniqueComplete(BedwarsPracticeGame game, @Nullable Duration duration, boolean personalBest) {

    }

    public record GameEnd(BedwarsPracticeGame game, BedwarsPracticeResult result) {

    }

    public record GameLeave(BedwarsPracticeGame game) {

    }

    public enum ServerKind {
        LOBBY,
        GAME
    }
}
