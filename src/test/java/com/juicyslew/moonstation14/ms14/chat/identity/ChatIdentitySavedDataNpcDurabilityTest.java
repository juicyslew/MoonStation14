package com.juicyslew.moonstation14.ms14.chat.identity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatIdentitySavedDataNpcDurabilityTest {
    @TempDir Path directory;

    private Path file() { return directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat"); }

    @Test void firstNpcIsDurableAndRepeatedConfigurationDoesNotWrite() throws Exception {
        var ledger = new ChatIdentitySavedData();
        UUID villager = UUID.randomUUID();
        var assigned = ledger.allocateNpcDurably(file(), villager, "Villager");
        assertEquals("Villager (1)", assigned.name());
        assertEquals(assigned, ChatIdentitySavedData.load(
                NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"), null)
                .npc(villager).orElseThrow());
        byte[] before = Files.readAllBytes(file());
        assertEquals(assigned, ledger.allocateNpcDurably(file(), villager, "Villager",
                () -> { throw new IOException("must not write existing row"); }));
        assertEquals(assigned, ledger.existingNpcDurably(file(), villager, "Villager"));
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertFalse(ledger.isDirty());
        assertEquals("Villager (2)", ledger.allocateNpcDurably(file(), UUID.randomUUID(), "Villager").name());
    }

    @Test void alreadyConfiguredNeverCreatesMissingLedgerOrRow() {
        var ledger = new ChatIdentitySavedData();
        assertThrows(IllegalStateException.class,
                () -> ledger.existingNpcDurably(file(), UUID.randomUUID(), "Villager"));
        assertFalse(Files.exists(file()));
        ledger.allocateNpcDurably(file(), UUID.randomUUID(), "Villager");
        byte[] before;
        try { before = Files.readAllBytes(file()); } catch (IOException failure) { throw new AssertionError(failure); }
        assertThrows(IllegalStateException.class,
                () -> ledger.existingNpcDurably(file(), UUID.randomUUID(), "Villager"));
        try { assertArrayEquals(before, Files.readAllBytes(file())); } catch (IOException failure) { throw new AssertionError(failure); }
    }

    @Test void corruptDiskAndChangedTypeFailClosed() throws Exception {
        var ledger = new ChatIdentitySavedData();
        UUID id = UUID.randomUUID();
        ledger.allocateNpcDurably(file(), id, "Villager");
        assertThrows(IllegalStateException.class, () -> ledger.existingNpcDurably(file(), id, "Zombie"));
        byte[] corrupt = "broken ledger".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file(), corrupt);
        assertThrows(RuntimeException.class, () -> ledger.allocateNpcDurably(file(), UUID.randomUUID(), "Villager"));
        assertThrows(RuntimeException.class, () -> ledger.existingNpcDurably(file(), id, "Villager"));
        assertArrayEquals(corrupt, Files.readAllBytes(file()));
    }

    @Test void failedNpcMoveLeavesPriorDiskUntouchedAndDoesNotConsumeNumber() throws Exception {
        var ledger = new ChatIdentitySavedData();
        UUID original = UUID.randomUUID(), failed = UUID.randomUUID();
        ledger.allocateNpcDurably(file(), original, "Villager");
        byte[] before = Files.readAllBytes(file());
        assertThrows(IllegalStateException.class, () -> ledger.allocateNpcDurably(file(), failed, "Villager",
                () -> { throw new IOException("injected failure"); }));
        assertFalse(ledger.isDirty());
        assertTrue(ledger.npc(failed).isEmpty());
        assertTrue(ChatIdentitySavedData.load(ledger.save(new CompoundTag(), null), null).npc(failed).isEmpty());
        assertThrows(IllegalStateException.class, () -> ledger.existingNpcDurably(file(), failed, "Villager"));
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertTrue(ChatIdentitySavedData.load(
                NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"), null)
                .npc(failed).isEmpty());
        UUID successful = UUID.randomUUID();
        assertEquals("Villager (2)", ledger.allocateNpcDurably(file(), successful, "Villager").name());
        var disk = ChatIdentitySavedData.load(
                NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"), null);
        assertTrue(disk.npc(failed).isEmpty());
        assertEquals(ledger.npc(successful).orElseThrow(), disk.npc(successful).orElseThrow());
    }

    @Test void dirtyNpcCacheWithLaggingDiskFailsClosed() throws Exception {
        var ledger = new ChatIdentitySavedData();
        ledger.allocateNpcDurably(file(), UUID.randomUUID(), "Villager");
        byte[] before = Files.readAllBytes(file());
        UUID pending = UUID.randomUUID(), rejected = UUID.randomUUID();
        ledger.allocateNpc(pending, "Villager");
        assertThrows(IllegalStateException.class, () -> ledger.allocateNpcDurably(file(), rejected, "Villager"));
        assertTrue(ledger.isDirty());
        assertEquals("Villager (2)", ledger.npc(pending).orElseThrow().name());
        assertTrue(ledger.npc(rejected).isEmpty());
        assertArrayEquals(before, Files.readAllBytes(file()));
    }
}
