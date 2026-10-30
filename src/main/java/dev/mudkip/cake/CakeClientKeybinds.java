package dev.mudkip.cake;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class CakeClientKeybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("cake", "main")
    );

    private CakeClientKeybinds() {
    }

    public static KeyMapping.Category getCategory() {
        return CakeClientKeybinds.CATEGORY;
    }
}
