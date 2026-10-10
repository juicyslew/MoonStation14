package com.juicyslew.moonstation14.ms14.stripping;

import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.Unpooled;

class StripProtocolTest {
    @Test void hiddenOccupiedProjectionCannotCarryPrivateItemPresentation() {
        assertDoesNotThrow(()->new StripViewSnapshot.Slot(StripSlotRef.Kind.EQUIPMENT,"pocket1",true,true,null));
        assertDoesNotThrow(()->new StripViewSnapshot.Slot(StripSlotRef.Kind.EQUIPMENT,"pocket1",false,true,null));
        assertThrows(IllegalArgumentException.class,()->new StripViewSnapshot.Slot(
                StripSlotRef.Kind.EQUIPMENT,"pocket1",true,true,ItemPresentation.fromStack(new ItemStack(Items.STICK))));
    }

    @Test void packetIntentsBindBothActorAndTargetRevisionsWithoutClientItemTokens() {
        UUID target=UUID.randomUUID();
        var action=new StripSlotAction(3,8,12,target,14,StripSlotRef.Kind.EQUIPMENT,"pocket1");
        assertEquals(12,action.actorRevision());
        assertEquals(14,action.targetRevision());
        assertEquals("pocket1",action.slot());
        assertEquals(StripViewRequest.Operation.OPEN,new StripViewRequest(StripViewRequest.Operation.OPEN,3,8,target,14).operation());
    }

    @Test void unavailableSnapshotCannotAdvertiseActionOrPrivateSlots() {
        var snapshot=new StripViewSnapshot(1,2,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),7,19,
                null,List.of(),StripViewSnapshot.Status.UNAVAILABLE,false);
        assertFalse(snapshot.canAct());
        assertTrue(snapshot.slots().isEmpty());
    }

    @Test void wireSnapshotCarriesRevisionAndHiddenOccupancyWithoutAppearance() {
        UUID target=UUID.randomUUID();
        var snapshot=new StripViewSnapshot(2,9,target,ResourceLocation.withDefaultNamespace("overworld"),17,23,
                "moonstation14:human",List.of(new StripViewSnapshot.Slot(StripSlotRef.Kind.EQUIPMENT,"pocket1",true,true,null)),
                StripViewSnapshot.Status.AVAILABLE,true);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            StripViewSnapshot.STREAM_CODEC.encode(buffer,snapshot);
            var decoded=StripViewSnapshot.STREAM_CODEC.decode(buffer);
            assertEquals(23,decoded.targetRevision());
            assertTrue(decoded.slots().getFirst().occupied());
            assertTrue(decoded.slots().getFirst().hidden());
            assertNull(decoded.slots().getFirst().item());
        } finally { buffer.release(); }
    }
}
