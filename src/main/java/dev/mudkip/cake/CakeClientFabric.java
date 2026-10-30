package dev.mudkip.cake;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public final class CakeClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new CakeClient(Minecraft.getInstance()).initialize();
    }
}
