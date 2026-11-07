package dev.mudkip.cake.server.bedwars;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record BedwarsPracticeResult(
        Outcome outcome,
        @Nullable String winningTeam,
        List<String> winners,
        List<Score> scores,
        int placement
) {
    public BedwarsPracticeResult {
        winners = List.copyOf(winners);
        scores = List.copyOf(scores);
    }

    public boolean isWin() {
        return this.outcome == Outcome.WIN;
    }

    public boolean isLoss() {
        return this.outcome == Outcome.LOSS;
    }

    public @Nullable Score getScore(String player) {
        for (var score : this.scores) {
            if (score.name().equalsIgnoreCase(player)) {
                return score;
            }
        }

        return null;
    }

    public enum Outcome {
        WIN,
        LOSS,
        DRAW,
        COMPLETE,
        UNKNOWN
    }

    public record Score(String name, int value) {
    }
}
