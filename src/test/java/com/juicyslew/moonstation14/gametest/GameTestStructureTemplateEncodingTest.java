package com.juicyslew.moonstation14.gametest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTestStructureTemplateEncodingTest {
    @Test
    void powerStructureSizesUseTheListEncodingConsumedByStructureTemplate() throws Exception {
        assertSize("empty.nbt", 16, 10, 16);
        assertSize("power_live_chain_clear.nbt", 9, 4, 10);
        assertSize("power_overload_clear.nbt", 13, 4, 16);
        assertSize("disposal_clear.nbt", 32, 8, 32);
        assertSize("disposal_power_clear.nbt", 48, 8, 48);
    }

    private static void assertSize(String file, int x, int y, int z) throws Exception {
        String resource = "/data/moonstation14/structure/" + file;
        CompoundTag root;
        try (InputStream input = GameTestStructureTemplateEncodingTest.class.getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Missing structure fixture " + resource);
            root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        }
        assertTrue(root.contains("size", Tag.TAG_LIST), file + " size must be an NBT list");
        ListTag size = root.getList("size", Tag.TAG_INT);
        assertEquals(3, size.size(), file + " size dimensions");
        assertEquals(x, size.getInt(0));
        assertEquals(y, size.getInt(1));
        assertEquals(z, size.getInt(2));
        assertTrue(x > 0 && y > 0 && z > 0);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, encoded);
        CompoundTag roundTripped = NbtIo.readCompressed(
                new java.io.ByteArrayInputStream(encoded.toByteArray()), NbtAccounter.unlimitedHeap());
        assertEquals(root, roundTripped, file + " complete NBT survives NbtIo round-trip");
    }
}
