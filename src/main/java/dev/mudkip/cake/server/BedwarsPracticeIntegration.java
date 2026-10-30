package dev.mudkip.cake.server;

public final class BedwarsPracticeIntegration {
    public static final ServerIntegration INSTANCE = new ServerIntegration(
            "bedwarspractice",
            "Bedwars Practice",
            "bedwarspractice.club",
            "bedwarspractice.net",
            "voxyl.net"
    );

    private BedwarsPracticeIntegration() {

    }
}
