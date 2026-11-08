package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunRuntimeStateTest {
    @Test void codecAndRegistryFriendlyStreamRoundTrip() {
        var state = new GunRuntimeState(31, 31, 1.5f, 47);
        assertEquals(state, GunRuntimeState.CODEC.parse(NbtOps.INSTANCE, GunRuntimeState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow()).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.EMPTY);
        GunRuntimeState.STREAM_CODEC.encode(buffer, state);
        assertEquals(state, GunRuntimeState.STREAM_CODEC.decode(buffer));
    }
    @Test void initialComponentDefaultsAreZero() {
        assertEquals(new GunRuntimeState(0, 0, 0), GunRuntimeState.INITIAL);
        assertEquals(0, GunRuntimeState.INITIAL.nextUseTick());
    }
}
