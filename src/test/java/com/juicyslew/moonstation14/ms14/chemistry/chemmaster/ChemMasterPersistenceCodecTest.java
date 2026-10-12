package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterPersistenceCodecTest {
    @Test void slotOwnerAndBufferSnapshotSurviveCodecRoundTrip() {
        UUID owner = UUID.randomUUID();
        var ledger = new ItemLocationLedger();
        var slot = new MachineItemSlot(owner, "beakerSlot", stack -> true, ledger);
        var token = new ItemToken(UUID.randomUUID().toString());
        var source = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        assertTrue(ledger.register(token, source).accepted());
        assertTrue(slot.insert(0, ledger.snapshot().revision(), token, source, new ItemStack(Items.STONE)));
        var slotJson = MachineItemSlot.codec().encodeStart(JsonOps.INSTANCE, slot.encode()).getOrThrow();
        var decodedSlot = MachineItemSlot.codec().parse(JsonOps.INSTANCE, slotJson).getOrThrow();
        var restoredSlot = new MachineItemSlot(owner, "beakerSlot", stack -> true, new ItemLocationLedger());
        assertTrue(restoredSlot.restore(decodedSlot));
        assertEquals(owner, restoredSlot.snapshot().owner());
        assertEquals("beakerSlot", restoredSlot.snapshot().slotId());
        assertEquals(token, restoredSlot.snapshot().token());
        assertEquals(1, restoredSlot.snapshot().revision());
        assertEquals(Items.STONE, restoredSlot.snapshot().stack().getItem());

        var buffer = new ChemMasterBuffer();
        var bufferJson = ChemMasterBuffer.SNAPSHOT_CODEC.encodeStart(JsonOps.INSTANCE, buffer.snapshot()).getOrThrow();
        var restoredBuffer = new ChemMasterBuffer(ChemMasterBuffer.SNAPSHOT_CODEC.parse(JsonOps.INSTANCE, bufferJson).getOrThrow());
        assertEquals(buffer.snapshot(), restoredBuffer.snapshot());
    }
}
