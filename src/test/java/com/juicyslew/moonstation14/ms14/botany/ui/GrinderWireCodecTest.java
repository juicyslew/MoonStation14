package com.juicyslew.moonstation14.ms14.botany.ui;

import com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GrinderWireCodecTest {
    @Test void controlPreservesAppendedJuiceOrdinalAndRoundTrips() {
        var control = new GrinderControl(UUID.randomUUID(), UUID.randomUUID(), 12,
                GrinderControl.Operation.JUICE, -1);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        GrinderWireCodec.writeControl(buffer, control);
        assertEquals(control, GrinderWireCodec.readControl(buffer));
        assertEquals(0, GrinderControl.Operation.GRIND.ordinal());
        assertEquals(4, GrinderControl.Operation.CLOSE.ordinal());
        assertEquals(5, GrinderControl.Operation.JUICE.ordinal());
    }

    @Test void snapshotCarriesServerEligibilityAndActiveMode() {
        var snapshot = new GrinderSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2,
                ResourceLocation.withDefaultNamespace("overworld"), new BlockPos(1, 2, 3), 8, 4,
                List.of(new GrinderSnapshot.Slot(-1, 4, null, ItemStack.EMPTY)), true, true, 10, 80,
                false, true, GrinderProgram.Operation.JUICE, true, null);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        GrinderWireCodec.writeSnapshot(buffer, snapshot);
        var decoded = GrinderWireCodec.readSnapshot(buffer);
        assertEquals(snapshot.canGrind(), decoded.canGrind());
        assertEquals(snapshot.canJuice(), decoded.canJuice());
        assertEquals(GrinderProgram.Operation.JUICE, decoded.activeOperation());
        assertTrue(decoded.active());
    }
}
