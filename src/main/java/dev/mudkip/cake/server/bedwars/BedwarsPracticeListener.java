package dev.mudkip.cake.server.bedwars;

public interface BedwarsPracticeListener {
    default void onServerJoin(BedwarsPracticeEvents.ServerJoin event) {

    }

    default void onServerLeave(BedwarsPracticeEvents.ServerLeave event) {

    }

    default void onQueueChange(BedwarsPracticeEvents.QueueChange event) {

    }

    default void onGameJoin(BedwarsPracticeEvents.GameJoin event) {

    }

    default void onGameModeChange(BedwarsPracticeEvents.GameModeChange event) {

    }

    default void onGameStart(BedwarsPracticeEvents.GameStart event) {

    }

    default void onRoundEnd(BedwarsPracticeEvents.RoundEnd event) {

    }

    default void onTechniqueComplete(BedwarsPracticeEvents.TechniqueComplete event) {

    }

    default void onGameEnd(BedwarsPracticeEvents.GameEnd event) {

    }

    default void onGameLeave(BedwarsPracticeEvents.GameLeave event) {

    }
}
