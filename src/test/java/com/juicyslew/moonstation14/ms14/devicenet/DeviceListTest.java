package com.juicyslew.moonstation14.ms14.devicenet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.*;

class DeviceListTest {
    private static final class Owner implements DeviceEndpoint {
        DeviceList list = new DeviceList(List.of("old"), 2, true, false);
        boolean accept = true;
        public DeviceIdentity identity() { return new DeviceIdentity(UUID.randomUUID()); }
        public ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        public BlockPos position() { return BlockPos.ZERO; }
        public DeviceComposition composition() { return null; }
        public boolean available() { return true; }
        public void receive(DevicePayload payload, String sourceAddress) { }
        public DeviceList deviceList() { return list; }
        public boolean mutateDeviceListMembers(DeviceList replacement) {
            if (!accept) return false;
            list = replacement;
            return true;
        }
    }

    @Test
    void serviceMutatesOnlyThroughOwnerAndRejectsOverflowAtomically() {
        Owner owner = new Owner();
        DeviceListService service = new DeviceListService();
        assertFalse(service.apply(owner, DeviceListService.Mutation.add(List.of("next", "third"))));
        assertEquals(List.of("old"), owner.list.addresses());
        assertTrue(service.apply(owner, DeviceListService.Mutation.add(List.of("old", "next"))));
        assertEquals(List.of("old", "next"), owner.list.addresses());
        owner.accept = false;
        assertFalse(service.apply(owner, DeviceListService.Mutation.clear()));
        assertEquals(List.of("old", "next"), owner.list.addresses());
    }

    @Test
    void clearAndRemoveNeedNotResolveMembers() {
        Owner owner = new Owner();
        DeviceListService service = new DeviceListService();
        assertTrue(service.apply(owner, DeviceListService.Mutation.remove("offline-id")));
        assertEquals(List.of("old"), owner.list.addresses());
        assertTrue(service.apply(owner, DeviceListService.Mutation.clear()));
        assertEquals(List.of(), owner.list.addresses());
    }
    @Test
    void upstreamDefaultsAndAllowDenySemantics() {
        assertEquals(32, DeviceList.DEFAULT.capacity());
        assertTrue(DeviceList.DEFAULT.allowList());
        assertFalse(DeviceList.DEFAULT.filterIncoming());
        assertFalse(DeviceList.DEFAULT.permits("device-a", false));
        DeviceList allow = new DeviceList(List.of("device-a"), 32, true, false);
        assertTrue(allow.permits("device-a", false));
        assertFalse(allow.permits("device-b", false));
        DeviceList deny = new DeviceList(List.of("device-a"), 32, false, false);
        assertFalse(deny.permits("device-a", false));
        assertTrue(deny.permits("device-b", false));
    }

    @Test
    void incomingFilterIsOptInAndCapacityIsEnforced() {
        assertTrue(DeviceList.DEFAULT.permits("device-a", true));
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceList(List.of("a", "b"), 1, true, false));
    }
}
