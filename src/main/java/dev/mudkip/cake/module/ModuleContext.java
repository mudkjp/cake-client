package dev.mudkip.cake.module;

import dev.mudkip.cake.server.ServerMappings;
import dev.mudkip.cake.server.ServerSession;
import net.minecraft.client.Minecraft;

public record ModuleContext(Minecraft client, ServerSession serverSession, ServerMappings serverMappings) {
}
