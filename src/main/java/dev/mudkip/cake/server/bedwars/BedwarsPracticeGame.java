package dev.mudkip.cake.server.bedwars;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record BedwarsPracticeGame(
        @Nullable String mode,
        @Nullable String modeId,
        @Nullable String map,
        boolean privateGame,
        @Nullable UUID id,
        int round,
        int totalRounds
) {
    public boolean isPartyGame() {
        return this.totalRounds > 1;
    }

    public BedwarsPracticeGame withMode(@Nullable String mode, @Nullable String modeId) {
        return new BedwarsPracticeGame(
                mode,
                modeId,
                this.map,
                this.privateGame,
                this.id,
                this.round,
                this.totalRounds
        );
    }

    public BedwarsPracticeGame withMap(@Nullable String map) {
        return new BedwarsPracticeGame(
                this.mode,
                this.modeId,
                map,
                this.privateGame,
                this.id,
                this.round,
                this.totalRounds
        );
    }

    public BedwarsPracticeGame withId(@Nullable UUID id) {
        return new BedwarsPracticeGame(
                this.mode,
                this.modeId,
                this.map,
                this.privateGame,
                id,
                this.round,
                this.totalRounds
        );
    }

    public BedwarsPracticeGame withRound(int round, int totalRounds) {
        return new BedwarsPracticeGame(
                this.mode,
                this.modeId,
                this.map,
                this.privateGame,
                this.id,
                round,
                totalRounds
        );
    }
}
