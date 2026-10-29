package dev.mudkip.cake;

import net.fabricmc.api.ClientModInitializer;

public final class CakeClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CakeClient.init();
    }
}
