package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyTargetActionSnapshotTest {
    private static RegistryFriendlyByteBuf buf() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
    }
    @Test void codecRoundTripsBoundedTypedActionListAndConsumesBuffer() {
        var id = UUID.randomUUID();
        var value = new BodyTargetActionSnapshot(3, 8, 12, "right", "token", id, Component.literal("gun"), List.of(
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.BUTCHER, Component.literal("cut"), true, null),
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.EMPTY_REVOLVER, Component.literal("empty"), false, Component.literal("none")),
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.SPIN_REVOLVER, Component.literal("spin"), true, null)));
        var buffer = buf(); BodyTargetActionSnapshot.STREAM_CODEC.encode(buffer, value);
        assertEquals(value, BodyTargetActionSnapshot.STREAM_CODEC.decode(buffer)); assertFalse(buffer.isReadable());
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionSnapshot(3, 8, 12, "right", "token", id, Component.literal("gun"), List.of(value.actions().get(0), value.actions().get(0))));
        assertThrows(IllegalArgumentException.class, () -> new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.BUTCHER, Component.literal("x"), true, Component.literal("bad")));
    }
}
