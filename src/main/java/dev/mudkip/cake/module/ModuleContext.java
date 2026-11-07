package dev.mudkip.cake.module;

import dev.mudkip.cake.server.ServerMappings;
import dev.mudkip.cake.server.ServerSession;
import dev.mudkip.cake.server.bedwars.BedwarsPractice;
import net.minecraft.client.Minecraft;

public record ModuleContext(
        Minecraft client,
        ServerSession serverSession,
        ServerMappings serverMappings,
        BedwarsPractice bedwarsPractice
) {
}
