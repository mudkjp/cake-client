package dev.mudkip.cake.module;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class PacketComponentMarker {
    private PacketComponentMarker() {
    }

    public static Component markFromPacket(Component component) {
        if (component.getContents() instanceof PacketComponentAccess access) {
            access.cake$setFromPacket();
        }

        if (component.getContents() instanceof TranslatableContents translatable) {
            for (var argument : translatable.getArgs()) {
                if (argument instanceof Component nested) {
                    PacketComponentMarker.markFromPacket(nested);
                }
            }
        }

        for (var sibling : component.getSiblings()) {
            PacketComponentMarker.markFromPacket(sibling);
        }

        return component;
    }
}
