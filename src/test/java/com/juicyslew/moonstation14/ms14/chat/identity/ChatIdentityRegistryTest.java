package com.juicyslew.moonstation14.ms14.chat.identity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatIdentityRegistryTest {
    private static ChatIdentityRegistry.CharacterKey key(int i) {
        return new ChatIdentityRegistry.CharacterKey(new UUID(0, i), "profile");
    }

    @Test void allocationCollisionAndBoundedExhaustion() {
        ChatIdentityRegistry registry = new ChatIdentityRegistry();
        var names = new HashSet<String>();
        for (int i = 0; i < 1024; i++) {
            var entry = registry.allocateCharacter(key(i));
            assertTrue(names.add(entry.name()));
            assertSame(entry, registry.allocateCharacter(key(i)));
            assertTrue(ChatIdentityRegistry.validColor(entry.rgb()));
        }
        assertThrows(IllegalStateException.class, () -> registry.allocateCharacter(key(1024)));
        assertTrue(registry.character(key(1024)).isEmpty());
    }

    @Test void aliasAndImpostorBorrowSavedRgbWithoutChangingActualIdentity() {
        ChatIdentityRegistry registry = new ChatIdentityRegistry();
        var victim = registry.allocateCharacter(key(1));
        var impostor = registry.allocateCharacter(key(2));
        assertEquals(victim.rgb(), registry.colorForPresentedName("§c " + victim.name().toUpperCase() + " ").orElseThrow());
        assertEquals(impostor, registry.character(key(2)).orElseThrow());
        assertTrue(registry.colorForPresentedName("Unknown Stranger").isEmpty());
    }

    @Test void persistentNpcUuidAndTypeNotTransientInteger() {
        ChatIdentityRegistry registry = new ChatIdentityRegistry();
        UUID first = UUID.randomUUID();
        var villager = registry.allocateNpc(first, "Villager");
        assertEquals("Villager (1)", villager.name());
        assertSame(villager, registry.allocateNpc(first, "Villager"));
        assertTrue(ChatIdentityRegistry.validColor(villager.rgb()));
        assertEquals(villager.rgb(), registry.colorForPresentedName("§a VILLAGER (1) ").orElseThrow());
        assertEquals("Villager (2)", registry.allocateNpc(UUID.randomUUID(), "Villager").name());
        assertEquals("Cow (1)", registry.allocateNpc(UUID.randomUUID(), "Cow").name());
        assertThrows(IllegalStateException.class, () -> registry.allocateNpc(first, "Cow"));
        assertThrows(IllegalArgumentException.class, () -> registry.allocateNpc(UUID.randomUUID(), "System"));
    }

    @Test void restartAndMalformedFailClosed() {
        ChatIdentitySavedData data = new ChatIdentitySavedData();
        var actual = data.allocateCharacter(key(1));
        UUID entity = UUID.randomUUID();
        var npc = data.allocateNpc(entity, "Villager");
        CompoundTag encoded = data.save(new CompoundTag(), null);
        var loaded = ChatIdentitySavedData.load(encoded, null);
        assertEquals(actual, loaded.character(key(1)).orElseThrow());
        assertEquals(actual.rgb(), loaded.colorForPresentedName(actual.name()).orElseThrow());
        assertEquals("Villager (1)", loaded.npc(entity).orElseThrow().name());
        assertEquals(npc.rgb(), loaded.npc(entity).orElseThrow().rgb());
        assertEquals(npc.rgb(), loaded.colorForPresentedName("§bVillager (1)").orElseThrow());
        assertEquals("Villager (2)", loaded.allocateNpc(UUID.randomUUID(), "Villager").name());
        assertTrue(loaded.character(key(2)).isEmpty());

        CompoundTag noVersion = encoded.copy();
        noVersion.remove("Version");
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(noVersion, null));
        CompoundTag olderVersion = encoded.copy();
        olderVersion.putInt("Version", 1);
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(olderVersion, null));
        CompoundTag duplicates = encoded.copy();
        ((ListTag) duplicates.get("Characters")).add(((ListTag) duplicates.get("Characters")).get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(duplicates, null));
        CompoundTag duplicateName = encoded.copy();
        CompoundTag second = ((ListTag) duplicateName.get("Characters")).getCompound(0).copy();
        second.putUUID("Account", UUID.randomUUID());
        ((ListTag) duplicateName.get("Characters")).add(second);
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(duplicateName, null));
        CompoundTag duplicateNpcNumber = encoded.copy();
        CompoundTag otherNpc = ((ListTag) duplicateNpcNumber.get("Npcs")).getCompound(0).copy();
        otherNpc.putUUID("Entity", UUID.randomUUID());
        ((ListTag) duplicateNpcNumber.get("Npcs")).add(otherNpc);
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(duplicateNpcNumber, null));
        CompoundTag missingNpcColor = encoded.copy();
        ((ListTag) missingNpcColor.get("Npcs")).getCompound(0).remove("Rgb");
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(missingNpcColor, null));
        CompoundTag corrupt = encoded.copy();
        ((ListTag) corrupt.get("Characters")).getCompound(0).putInt("Rgb", 0x111111);
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(corrupt, null));
        CompoundTag badList = encoded.copy();
        badList.put("Characters", new ListTag());
        badList.putString("Npcs", "corrupt");
        assertThrows(IllegalArgumentException.class, () -> ChatIdentitySavedData.load(badList, null));
    }

    @Test void reservedNamesAndNormalization() {
        assertEquals("ada alder", ChatIdentityRegistry.normalize("§a ADA   ALDER "));
        assertThrows(IllegalArgumentException.class, () -> new ChatIdentityRegistry.CharacterKey(UUID.randomUUID(), "not valid"));
        assertFalse(ChatIdentityRegistry.validColor(0x222222));
        assertFalse(ChatIdentityRegistry.validColor(0xFF000000));
    }

    @Test void failedSavedDataLoadMustNotBeReplaced(@TempDir Path folder) throws IOException {
        Path ledgerFile = folder.resolve(ChatIdentitySavedData.DATA_NAME + ".dat");
        ChatIdentitySavedData.requireNoUnloadedSave(ledgerFile);
        Files.writeString(ledgerFile, "corrupt");
        assertThrows(IllegalStateException.class, () -> ChatIdentitySavedData.requireNoUnloadedSave(ledgerFile));
    }
}
