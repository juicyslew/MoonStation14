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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatIdentitySavedDataEnrollmentTest {
    @TempDir Path directory;

    @Test void unreadableExistingLedgerMustNotBeReplacedDuringFirstEnrollment() throws Exception {
        Path ledger = directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        Files.writeString(ledger, "corrupt or unreadable save");

        assertThrows(IllegalStateException.class, () -> ChatIdentitySavedData.requireNoUnloadedSave(ledger));
    }

    @Test void firstEnrollmentIdentitySurvivesSaveAndReloadWithoutChangingColorOrName() {
        var key = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        var ledger = new ChatIdentitySavedData();
        var assigned = ledger.allocateCharacter(key);

        var restored = ChatIdentitySavedData.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(assigned, restored.character(key).orElseThrow());
        assertEquals(assigned, restored.allocateCharacter(key));
    }

    @Test void durableEnrollmentRoundTripsThroughVanillaDataWrapper() throws Exception {
        Path file = directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        var key = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        var ledger = new ChatIdentitySavedData();
        var assigned = ledger.allocateCharacterDurably(file, key);

        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertTrue(root.contains("DataVersion", CompoundTag.TAG_INT));
        assertTrue(root.contains("data", CompoundTag.TAG_COMPOUND));
        assertEquals(assigned, ChatIdentitySavedData.load(root.getCompound("data"), null).character(key).orElseThrow());
        assertEquals(assigned, ledger.allocateCharacterDurably(file, key));
        assertFalse(ledger.isDirty());
    }

    @Test void failedAtomicMoveLeavesExistingLedgerUntouchedAndNewIdentityAbsent() throws Exception {
        Path file = directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        var ledger = new ChatIdentitySavedData();
        var original = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        ledger.allocateCharacterDurably(file, original);
        byte[] before = Files.readAllBytes(file);
        var added = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");

        assertThrows(IllegalStateException.class, () -> ledger.allocateCharacterDurably(file, added,
                () -> { throw new IOException("forced failure before rename"); }));
        assertFalse(ledger.isDirty());
        assertTrue(ledger.character(added).isEmpty());
        assertTrue(ChatIdentitySavedData.load(ledger.save(new CompoundTag(), null), null).character(added).isEmpty());
        assertArrayEquals(before, Files.readAllBytes(file));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count(), "failed write must remove its temporary file");
        }
        assertEquals(ledger.character(original).orElseThrow(),
                ChatIdentitySavedData.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"), null)
                        .character(original).orElseThrow());
        var successful = ledger.allocateCharacterDurably(file, added);
        var disk = ChatIdentitySavedData.load(
                NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"), null);
        assertEquals(successful, disk.character(added).orElseThrow());
        assertEquals(successful, ledger.character(added).orElseThrow());
    }

    @Test void dirtyCacheWithLaggingDiskFailsClosedAndKeepsItsDirtyState() throws Exception {
        Path file = directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        var ledger = new ChatIdentitySavedData();
        var original = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        ledger.allocateCharacterDurably(file, original);
        byte[] before = Files.readAllBytes(file);
        var pending = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        var rejected = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        ledger.allocateCharacter(pending);
        assertTrue(ledger.isDirty());
        assertThrows(IllegalStateException.class, () -> ledger.allocateCharacterDurably(file, rejected));
        assertTrue(ledger.isDirty());
        assertTrue(ledger.character(pending).isPresent());
        assertTrue(ledger.character(rejected).isEmpty());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void failedMoveDoesNotClearPreexistingDirtyFlag() throws Exception {
        Path file = directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        var ledger = new ChatIdentitySavedData();
        ledger.allocateCharacterDurably(file, new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main"));
        var rejected = new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main");
        ledger.setDirty();
        assertThrows(IllegalStateException.class, () -> ledger.allocateCharacterDurably(file, rejected,
                () -> { throw new IOException("injected failure"); }));
        assertTrue(ledger.isDirty());
        assertTrue(ledger.character(rejected).isEmpty());
        assertTrue(ChatIdentitySavedData.load(ledger.save(new CompoundTag(), null), null).character(rejected).isEmpty());
    }

    @Test void corruptExistingLedgerIsNeverReplaced() throws Exception {
        Path file = directory.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        byte[] corruption = "invalid ledger".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, corruption);
        var ledger = new ChatIdentitySavedData();
        assertThrows(RuntimeException.class, () -> ledger.allocateCharacterDurably(file,
                new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "main")));
        assertArrayEquals(corruption, Files.readAllBytes(file));
    }
}
