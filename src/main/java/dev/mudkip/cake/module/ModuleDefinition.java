package dev.mudkip.cake.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mudkip.cake.server.ServerIntegration;
import org.jspecify.annotations.Nullable;

public record ModuleDefinition(
        String id,
        ModuleCategory category,
        @Nullable ServerIntegration integration,
        InputConstants.Type keyType,
        int defaultKey
) {
    public static ModuleDefinition of(String id, ModuleCategory category) {
        return new ModuleDefinition(
                id,
                category,
                null,
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue()
        );
    }

    public ModuleDefinition withIntegration(ServerIntegration integration) {
        return new ModuleDefinition(this.id, this.category, integration, this.keyType, this.defaultKey);
    }

    public ModuleDefinition withKey(InputConstants.Type keyType, int defaultKey) {
        return new ModuleDefinition(this.id, this.category, this.integration, keyType, defaultKey);
    }
}
