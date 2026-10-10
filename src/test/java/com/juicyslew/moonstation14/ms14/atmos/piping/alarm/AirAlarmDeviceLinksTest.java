package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AirAlarmDeviceLinksTest {
    private static CompoundTag item(int x) {
        CompoundTag item = new CompoundTag();
        item.putString("Dimension", "minecraft:overworld");
        item.putInt("X", x);
        item.putInt("Y", 70);
        item.putInt("Z", 0);
        item.putString("Kind", "VENT");
        item.putUUID("DeviceId", UUID.randomUUID());
        return item;
    }

    private static CompoundTag tagged(ListTag list) {
        CompoundTag tag = new CompoundTag();
        tag.put("AirAlarmDeviceLinks", list);
        return tag;
    }

    @Test void savedRoundTripKeepsExactIdentityIncludingDimensionKindAndUuid() {
        ListTag list = new ListTag();
        list.add(item(7));
        AirAlarmDeviceLinks links = new AirAlarmDeviceLinks();
        links.load(tagged(list), 32);
        assertTrue(links.valid());
        var expected = new AirAlarmDeviceLinks.Link(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:overworld")), new BlockPos(7, 70, 0),
                VentilationDeviceConfig.Kind.VENT, list.getCompound(0).getUUID("DeviceId"));
        assertEquals(java.util.List.of(expected), links.snapshot());
        CompoundTag saved = new CompoundTag();
        links.save(saved);
        AirAlarmDeviceLinks restored = new AirAlarmDeviceLinks();
        restored.load(saved, 32);
        assertEquals(links.snapshot(), restored.snapshot());
        assertNotEquals(expected, new AirAlarmDeviceLinks.Link(Level.NETHER, expected.pos(), expected.kind(), expected.deviceId()));
        assertNotEquals(expected, new AirAlarmDeviceLinks.Link(expected.dimension(), expected.pos(),
                VentilationDeviceConfig.Kind.SCRUBBER, expected.deviceId()));
        assertNotEquals(expected, new AirAlarmDeviceLinks.Link(expected.dimension(), expected.pos(),
                expected.kind(), UUID.randomUUID()));
    }

    @Test void duplicatesAndOversizeRejectEntireListAndStayInvalidAfterSave() {
        ListTag atLimit = new ListTag();
        for (int i = 0; i < 32; i++) atLimit.add(item(i));
        AirAlarmDeviceLinks bounded = new AirAlarmDeviceLinks();
        bounded.load(tagged(atLimit), 32);
        assertTrue(bounded.valid());
        assertEquals(32, bounded.snapshot().size());
        for (boolean duplicate : new boolean[] {false, true}) {
            ListTag list = new ListTag();
            int count = duplicate ? 2 : 33;
            for (int i = 0; i < count; i++) list.add(item(duplicate ? 4 : i));
            AirAlarmDeviceLinks links = new AirAlarmDeviceLinks();
            links.load(tagged(list), 32);
            assertFalse(links.valid());
            assertTrue(links.snapshot().isEmpty());
            CompoundTag saved = new CompoundTag();
            links.save(saved);
            AirAlarmDeviceLinks restored = new AirAlarmDeviceLinks();
            restored.load(saved, 32);
            assertFalse(restored.valid());
        }
    }

    @Test void malformedIdentityCannotBecomeAnEmptyValidAllowList() {
        ListTag list = new ListTag();
        CompoundTag malformed = item(1);
        malformed.remove("DeviceId");
        list.add(malformed);
        AirAlarmDeviceLinks links = new AirAlarmDeviceLinks();
        links.load(tagged(list), 32);
        assertFalse(links.valid());
        ListTag wrongType = new ListTag();
        wrongType.add(net.minecraft.nbt.StringTag.valueOf("not a link"));
        links.load(tagged(wrongType), 32);
        assertFalse(links.valid());
    }

    @Test void deviceListViewUsesResolvedPolicyAndCanonicalMembership() {
        ListTag stored = new ListTag();
        stored.add(item(4));
        AirAlarmDeviceLinks links = new AirAlarmDeviceLinks();
        links.load(tagged(stored), 32);
        String id = stored.getCompound(0).getUUID("DeviceId").toString();

        var defaults = links.deviceList(new com.juicyslew.moonstation14.ms14.devicenet.DeviceList.Config(32, true, false));
        assertEquals(java.util.List.of(id), defaults.addresses());
        assertTrue(defaults.permits(id, false));
        assertTrue(defaults.permits("other", true));

        var denyIncoming = links.deviceList(new com.juicyslew.moonstation14.ms14.devicenet.DeviceList.Config(32, false, true));
        assertFalse(denyIncoming.allowList());
        assertTrue(denyIncoming.filterIncoming());
        assertFalse(denyIncoming.permits(id, true));
        assertTrue(denyIncoming.permits("other", true));
        assertFalse(denyIncoming.permits(id, false));
    }
}
