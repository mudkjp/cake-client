package dev.mudkip.cake.module;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.mudkip.cake.module.modules.FingerprintModule;
import net.minecraft.network.chat.Component;

public record PacketComponentCodec(Codec<Component> delegate) implements Codec<Component> {
    @Override
    public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
        var result = this.delegate.decode(ops, input);

        if (!PacketSource.isDecoding() || !FingerprintModule.shouldMarkPackets()) {
            return result;
        }

        return result.map(pair -> pair.mapFirst(FingerprintModule::markFromPacket));
    }

    @Override
    public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
        return this.delegate.encode(input, ops, prefix);
    }
}
