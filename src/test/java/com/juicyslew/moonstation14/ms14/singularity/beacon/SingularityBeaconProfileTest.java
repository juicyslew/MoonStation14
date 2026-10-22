package com.juicyslew.moonstation14.ms14.singularity.beacon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SingularityBeaconProfileTest {
    private static JsonObject json(String path) {
        try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(
                SingularityBeaconProfileTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    @Test void bundledProfileAndHugeItemFootprintResolve() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModSingularityBeacons.TYPE);
        manager.reload(ModSingularityBeacons.TYPE, Map.of(
                ModSingularityBeacons.BASE_ID, json("/data/moonstation14/moonstation14/singularity_beacon/singularity_beacon_base.json"),
                ModSingularityBeacons.DEFAULT_ID, json("/data/moonstation14/moonstation14/singularity_beacon/singularity_beacon.json")));
        var profile = manager.snapshot(ModSingularityBeacons.TYPE).get(ModSingularityBeacons.DEFAULT_ID);
        assertEquals(5000, profile.powerWatts());
        assertEquals(40, profile.attractorPeriodTicks());
        assertEquals(80, profile.attractorRange());
        assertEquals(120, profile.foodEnergy());
        assertEquals(1, profile.foodFactor());
        assertEquals(ModSingularityBeacons.DEFAULT_ID, profile.id());

        PrototypeManager items = new PrototypeManager();
        items.register(ModItemFootprints.TYPE);
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_beacon");
        items.reload(ModItemFootprints.TYPE, Map.of(id,
                json("/data/moonstation14/moonstation14/item_footprint/singularity_beacon.json")));
        ItemFootprintProfile footprint = items.snapshot(ModItemFootprints.TYPE).get(id);
        assertEquals(ItemFootprintProfile.ItemSize.Huge, footprint.itemSize().orElseThrow());
        assertEquals(16, footprint.cells().size());
    }

    @Test void portableBeaconUsesResolvedServerFoodComposition() {
        PrototypeManager source = new PrototypeManager();
        source.register(ModSingularityBeacons.TYPE);
        source.reload(ModSingularityBeacons.TYPE, Map.of(
                ModSingularityBeacons.BASE_ID, json("/data/moonstation14/moonstation14/singularity_beacon/singularity_beacon_base.json"),
                ModSingularityBeacons.DEFAULT_ID, json("/data/moonstation14/moonstation14/singularity_beacon/singularity_beacon.json")));
        var resolved = source.snapshot(ModSingularityBeacons.TYPE).asMap();

        var runtime = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var previous = runtime.snapshot(ModSingularityBeacons.TYPE).asMap();
        try {
            runtime.publishDecoded(ModSingularityBeacons.TYPE, resolved);
            assertTrue(com.juicyslew.moonstation14.ms14.singularity.SingularityFood.class
                    .isAssignableFrom(com.juicyslew.moonstation14.item.custom.SingularityBeaconItem.class));
            var portableFood = ModSingularityBeacons.serverDefaultProfile();
            assertNotNull(portableFood);
            assertEquals(120, portableFood.foodEnergy());
            assertEquals(1, portableFood.foodFactor());
        } finally {
            runtime.publishDecoded(ModSingularityBeacons.TYPE, previous);
        }
    }

    @Test void attractionUsesInverseDistanceAndHasNoHardRangeCutoff() {
        assertEquals(new Vec3(80, 0, 0), SingularityBeaconAttraction.bias(new Vec3(1, 0, 0), Vec3.ZERO, 80));
        assertEquals(new Vec3(8, 0, 0), SingularityBeaconAttraction.bias(new Vec3(10, 0, 0), Vec3.ZERO, 80));
        assertEquals(new Vec3(.8, 0, 0), SingularityBeaconAttraction.bias(new Vec3(100, 0, 0), Vec3.ZERO, 80));
        assertNotEquals(Vec3.ZERO, SingularityBeaconAttraction.bias(new Vec3(1000, 0, 0), Vec3.ZERO, 80));
        assertEquals(Vec3.ZERO, SingularityBeaconAttraction.bias(Vec3.ZERO, Vec3.ZERO, 80));
        assertEquals(Vec3.ZERO, SingularityBeaconAttraction.bias(new Vec3(1, 0, 0), Vec3.ZERO, Double.NaN));
    }
}
