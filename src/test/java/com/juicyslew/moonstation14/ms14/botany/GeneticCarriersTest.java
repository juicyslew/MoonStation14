package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class GeneticCarriersTest {
    private static GeneticValue value(String species) {
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", species);
        return new GeneticValue(id, new SeedProfile(id, id, species,
                new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12),
                new SeedProfile.Lifecycle(3,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),
                Map.of(), Map.of(), Map.of(), List.of(), SeedProfile.Scream.DISABLED, "test-"+species+"-name"));
    }
    @Test void referencePrecedesStaleSnapshotAndFallbackIsLazy() {
        var stack = new ItemStack(Items.WHEAT_SEEDS);
        var current = value("current");
        var reference = new PlantSeedReference(current, false);
        stack.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), reference);
        stack.set(ModDataComponents.GENETIC_VALUE.get(), value("stale"));
        var called = new AtomicBoolean();
        assertSame(current, GeneticCarriers.resolve(stack, () -> { called.set(true); return value("fallback"); }));
        assertFalse(called.get());
        var copy = stack.copy();
        assertSame(reference, copy.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
    }
    @Test void snapshotThenLazyFallbackUsedWhenReferenceAbsent() {
        var stack = new ItemStack(Items.WHEAT_SEEDS);
        var snapshot = value("snapshot");
        stack.set(ModDataComponents.GENETIC_VALUE.get(), snapshot);
        assertSame(snapshot, GeneticCarriers.resolve(stack, () -> fail("fallback should not run")));
        stack.remove(ModDataComponents.GENETIC_VALUE.get());
        var fallback = value("fallback");
        assertSame(fallback, GeneticCarriers.resolve(stack, () -> fallback));
    }

    @Test void seedPacketEligibilityRequiresActualPacketItemAndUsesCurrentReference() {
        withCatalogs(Map.of(id("testseed"), profile("testseed", ResourceLocation.withDefaultNamespace("wheat_seeds"))),
                Map.of(ResourceLocation.withDefaultNamespace("wheat_seeds"), new SeedItemProfile(id("testseed"))), () -> {
                    var stack = new ItemStack(Items.WHEAT_SEEDS);
                    var current = new GeneticValue(id("current"), profile("current", ResourceLocation.withDefaultNamespace("potato")));
                    stack.set(ModDataComponents.GENETIC_VALUE.get(), value("stale"));
                    stack.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), new PlantSeedReference(current, false));
                    assertSame(current, GeneticCarriers.resolveSeedPacket(stack).genetics());
                    var wrongItem = new ItemStack(Items.POTATO);
                    wrongItem.set(ModDataComponents.GENETIC_VALUE.get(), current);
                    assertNull(GeneticCarriers.resolveSeedPacket(wrongItem), "carried genes cannot authorize an unprofiled item");
                    var fallback = GeneticCarriers.resolveSeedPacket(new ItemStack(Items.WHEAT_SEEDS));
                    assertEquals(id("testseed"), fallback.genetics().species());

                    var produce = new ItemStack(Items.WHEAT);
                    produce.set(ModDataComponents.GENETIC_VALUE.get(), current);
                    assertNull(GeneticCarriers.resolveSeedPacket(produce), "genetics on produce do not qualify it as a seed-bearing item");
                    var swab = new ItemStack(Items.STICK);
                    swab.set(ModDataComponents.GENETIC_VALUE.get(), current);
                    assertNull(GeneticCarriers.resolveSeedPacket(swab));

                    var noDefault = new ItemStack(Items.WHEAT_SEEDS);
                    noDefault.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), new PlantSeedReference(current, false));
                    assertSame(current, GeneticCarriers.resolveSeedPacket(noDefault).genetics(), "carried seed is authoritative despite missing default");
                    var catalogUnavailable = new ItemStack(Items.WHEAT_SEEDS);
                    catalogUnavailable.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), new PlantSeedReference(current, false));
                    withCatalogs(Map.of(), null, () -> assertSame(current, GeneticCarriers.resolveSeedPacket(catalogUnavailable).genetics()));
                    var nullDefault = new ItemStack(Items.WHEAT_SEEDS);
                    installItemProfiles(Map.of(ResourceLocation.withDefaultNamespace("wheat_seeds"), new SeedItemProfile(null)));
                    nullDefault.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), new PlantSeedReference(current, false));
                    assertSame(current, GeneticCarriers.resolveSeedPacket(nullDefault).genetics());
                    assertNull(GeneticCarriers.resolveSeedPacket(new ItemStack(Items.WHEAT_SEEDS)), "null default without carried genetics is denied");
                });
    }

    @Test void composedSeedItemDefaultIsExplicitAndCarriedSpeciesOverridesIt() {
        var packetId = ResourceLocation.withDefaultNamespace("wheat_seeds");
        var profileA = profile("seed_a", packetId);
        var profileB = profile("seed_b", packetId);
        var map = new java.util.LinkedHashMap<ResourceLocation, SeedProfile>();
        map.put(id("seed_b"), profileB); // Deliberately before A: catalog iteration must not choose the default.
        map.put(id("seed_a"), profileA);
        withCatalogs(map, Map.of(packetId, new SeedItemProfile(id("seed_a"))), () -> {
            var plain = GeneticCarriers.resolveSeedPacket(new ItemStack(Items.WHEAT_SEEDS));
            assertEquals(id("seed_a"), plain.genetics().species());
            assertEquals(profileA, plain.profile());
            var carried = new ItemStack(Items.WHEAT_SEEDS);
            var currentB = new GeneticValue(id("seed_b"), profileB);
            carried.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), new PlantSeedReference(currentB, false));
            assertSame(currentB, GeneticCarriers.resolveSeedPacket(carried).genetics());
        });
    }

    @Test void packetHealthOverrideCodecAndConstructionPreserveReferenceAndPresence() {
        var override = new SeedPacketHealthOverride(-2.5f);
        var json = SeedPacketHealthOverride.CODEC.encodeStart(JsonOps.INSTANCE, override).getOrThrow();
        assertEquals(-2.5f, SeedPacketHealthOverride.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().health());
        var nbt = SeedPacketHealthOverride.CODEC.encodeStart(NbtOps.INSTANCE, override).getOrThrow();
        assertEquals(-2.5f, SeedPacketHealthOverride.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().health());
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        SeedPacketHealthOverride.STREAM_CODEC.encode(buffer, override);
        assertEquals(-2.5f, SeedPacketHealthOverride.STREAM_CODEC.decode(buffer).health());

        var profile = profile("factory", ResourceLocation.withDefaultNamespace("wheat_seeds"));
        var genes = new GeneticValue(ResourceLocation.fromNamespaceAndPath("test", "factory"), profile);
        var ref = new PlantSeedReference(genes, true);
        var packet = GeneticCarriers.createSeedPacket(ref, null);
        assertEquals(Items.WHEAT_SEEDS, packet.getItem());
        assertSame(ref, packet.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
        assertNull(packet.get(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get()));
        withCatalogs(Map.of(id("factory"), profile), Map.of(ResourceLocation.withDefaultNamespace("wheat_seeds"), new SeedItemProfile(id("factory"))), () -> {
        var modified = GeneticCarriers.createSeedPacket(ref, -2.5f);
        assertEquals(-2.5f, GeneticCarriers.resolveSeedPacket(modified).healthOverride());
        var unmodified = GeneticCarriers.createSeedPacket(ref, null);
        assertNull(GeneticCarriers.resolveSeedPacket(unmodified).healthOverride());
        var produce = new ItemStack(Items.WHEAT);
        produce.set(ModDataComponents.GENETIC_VALUE.get(), genes);
        produce.set(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get(), override);
        assertNull(GeneticCarriers.resolveSeedPacket(produce));
        var swab = new ItemStack(Items.STICK);
        swab.set(ModDataComponents.GENETIC_VALUE.get(), genes);
        assertNull(GeneticCarriers.resolveSeedPacket(swab));
        var invalid = profile("not_registered_packet", ResourceLocation.fromNamespaceAndPath("test", "not_a_registered_item"));
        var invalidRef = new PlantSeedReference(new GeneticValue(ResourceLocation.fromNamespaceAndPath("test", "invalid"), invalid), true);
        assertTrue(GeneticCarriers.createSeedPacket(invalidRef, null).isEmpty());
        });
    }

    private static SeedProfile profile(String species, net.minecraft.resources.ResourceLocation packet) {
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", species);
        return new SeedProfile(packet, id, species,
                new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12),
                new SeedProfile.Lifecycle(3,20,21,22,23,24,SeedProfile.Harvest.SELF_HARVEST,false,true),
                Map.of(), Map.of(), Map.of(), List.of(), SeedProfile.Scream.DISABLED, "test-"+species+"-name");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }

    private static void withCatalogs(Map<ResourceLocation, SeedProfile> seeds,
                                     Map<ResourceLocation, SeedItemProfile> itemProfiles, Runnable test) {
        var manager = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var previousSeeds = manager.snapshot(ModSeedProfiles.TYPE);
        var previousItems = manager.snapshot(ModSeedItemProfiles.TYPE);
        try {
            manager.reload(ModSeedProfiles.TYPE, encode(seeds, ModSeedProfiles.TYPE.codec()));
            if (itemProfiles != null) installItemProfiles(itemProfiles);
            test.run();
        } finally {
            manager.reload(ModSeedProfiles.TYPE, encode(previousSeeds.asMap(), ModSeedProfiles.TYPE.codec()));
            manager.reload(ModSeedItemProfiles.TYPE, encode(previousItems.asMap(), ModSeedItemProfiles.TYPE.codec()));
        }
    }
    private static void installItemProfiles(Map<ResourceLocation, SeedItemProfile> entries) {
        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager().reload(
                ModSeedItemProfiles.TYPE, encode(entries, ModSeedItemProfiles.TYPE.codec()));
    }
    private static <T> Map<ResourceLocation, com.google.gson.JsonObject> encode(Map<ResourceLocation, T> values,
            com.mojang.serialization.Codec<T> codec) {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        values.forEach((key, value) -> raw.put(key, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject()));
        return raw;
    }
}
