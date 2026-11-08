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
        assertSize("power_overload_clear.nbt", 16, 13, 18);
        assertSize("apc_debug_fixture.nbt", 12, 4, 12);
        assertSize("power_receiver_tall_clear.nbt", 16, 13, 16);
        assertBotanyExtractorTemplate();
        assertSize("disposal_clear.nbt", 32, 8, 32);
        assertSize("disposal_power_clear.nbt", 48, 8, 48);
        assertSize("atmos_machinery_lab_clear.nbt", 64, 6, 64);
        assertLabWriteEnvelope();
        assertSize("singularity_fixture_clear.nbt", 32, 16, 24);

        assertCoordinatesFit(16, 13, 18,
                new int[][]{{3, 1, 2}, {3, 1, 5}, {12, 0, 15}, {12, 3, 15}});
        assertCoordinatesFit(16, 13, 16,
                new int[][]{{3, 12, 2}, {4, 12, 10}, {4, 12, 11}, {2, 12, 10}, {6, 12, 10}});
        assertCoordinatesFit(9, 4, 10, new int[][]{{3, 1, 2}, {4, 1, 9}});
        assertCoordinatesFit(12, 4, 12, new int[][]{{3, 1, 2}, {5, 1, 7}});
        assertCoordinatesFit(16, 20, 16,
                new int[][]{{2, 12, 10}, {5, 12, 8}, {7, 12, 8}, {5, 13, 8}, {5, 14, 8}, {6, 14, 9}, {6, 14, 10}});
    }

    private static void assertLabWriteEnvelope() {
        var plan = com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var offset = com.juicyslew.moonstation14.ms14.atmos.machine.fixture.AtmosMachineryFixturePlan.VENTILATION_ORIGIN;
        int minX = 0, minY = 0, minZ = 0, maxX = 63, maxY = 5, maxZ = 63;
        // The four native methods clear the full lab envelope.  The builder's
        // primary floor and the crossing's below-host support are Y=0/Y=0.
        assertCoordinatesFit(64, 6, 64, new int[][]{{0,0,0},{63,5,63},{53,0,23}});
        for (var cell : plan.blocks()) assertTranslated(cell.pos(), offset, minX,minY,minZ,maxX,maxY,maxZ);
        for (var pipe : plan.pipes()) assertTranslated(pipe.position(), offset, minX,minY,minZ,maxX,maxY,maxZ);
        for (var device : plan.powerDevices()) assertTranslated(device.position(), offset, minX,minY,minZ,maxX,maxY,maxZ);
        for (var cable : plan.cables()) assertTranslated(cable.host(), offset, minX,minY,minZ,maxX,maxY,maxZ);
        for (var cell : plan.sourceCells()) assertTranslated(cell, offset, minX,minY,minZ,maxX,maxY,maxZ);
        for (var cell : plan.recipientCells()) assertTranslated(cell, offset, minX,minY,minZ,maxX,maxY,maxZ);
        assertTranslated(plan.alarm(), offset, minX,minY,minZ,maxX,maxY,maxZ);
        assertTranslated(plan.scrubber(), offset, minX,minY,minZ,maxX,maxY,maxZ);
        assertTranslated(plan.vent(), offset, minX,minY,minZ,maxX,maxY,maxZ);
    }

    private static void assertTranslated(net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos offset,
                                         int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        var translated = pos.offset(offset);
        assertTrue(translated.getX() >= minX && translated.getX() <= maxX
                && translated.getY() >= minY && translated.getY() <= maxY
                && translated.getZ() >= minZ && translated.getZ() <= maxZ,
                "translated lab write outside reserved bounds: " + translated);
    }

    private static void assertBotanyExtractorTemplate() throws Exception {
        String resource = "/data/moonstation14/structure/botany_extractor_power_clear.nbt";
        CompoundTag root;
        try (InputStream input = GameTestStructureTemplateEncodingTest.class.getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Missing structure fixture " + resource);
            root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        }
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        assertEquals(16 * 20 * 16, blocks.size(), "every owned cell is explicit air");
        assertEquals(3465, root.getInt("DataVersion"), "copied template DataVersion");
        java.util.Set<String> coordinates = new java.util.HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            assertEquals(0, block.getInt("state"), "air palette state at block " + i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            assertEquals(3, pos.size(), "position dimensions at block " + i);
            assertTrue(pos.getInt(0) >= 0 && pos.getInt(0) < 16 && pos.getInt(1) >= 0 && pos.getInt(1) < 20
                    && pos.getInt(2) >= 0 && pos.getInt(2) < 16, "owned bounds at block " + i);
            assertTrue(coordinates.add(pos.getInt(0) + "," + pos.getInt(1) + "," + pos.getInt(2)), "unique owned coordinate at block " + i);
        }
        assertEquals(16 * 20 * 16, coordinates.size(), "complete owned air volume");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, encoded);
        CompoundTag roundTripped = NbtIo.readCompressed(new java.io.ByteArrayInputStream(encoded.toByteArray()), NbtAccounter.unlimitedHeap());
        assertEquals(root, roundTripped, "botany fixture complete NBT round-trip");
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

    private static void assertCoordinatesFit(int sizeX, int sizeY, int sizeZ, int[][] positions) {
        for (int[] pos : positions) {
            assertEquals(3, pos.length);
            assertTrue(pos[0] >= 0 && pos[0] < sizeX && pos[1] >= 0 && pos[1] < sizeY
                            && pos[2] >= 0 && pos[2] < sizeZ,
                    "fixture coordinate " + java.util.Arrays.toString(pos) + " outside "
                            + java.util.Arrays.toString(new int[]{sizeX, sizeY, sizeZ}));
        }
    }
}
