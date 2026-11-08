package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/** Shared precedence and attachment owner for genetics-bearing item carriers. */
public final class GeneticCarriers {
    public record SeedPacket(GeneticValue genetics, SeedProfile profile, Float healthOverride) { }
    private GeneticCarriers() { }
    public static GeneticValue resolve(ItemStack stack, Supplier<GeneticValue> fallback) {
        PlantSeedReference reference = stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get());
        if (reference != null) return reference.current();
        GeneticValue snapshot = stack.get(ModDataComponents.GENETIC_VALUE.get());
        return snapshot != null ? snapshot : fallback.get();
    }
    public static void attach(ItemStack stack, PlantSeedReference reference) {
        stack.set(ModDataComponents.GENETIC_VALUE.get(), reference.current());
        stack.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), reference);
    }
    /** Resolve composed seed-bearing items; genetics on produce/swabs are not seed-carrier authority. */
    public static SeedPacket resolveSeedPacket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        SeedItemProfile itemProfile = ModSeedItemProfiles.serverLookup(itemId);
        if (itemProfile == null) return null;
        GeneticValue carried = resolve(stack, () -> null);
        if (carried != null) {
            SeedProfile profile = carried.profile();
            var override = stack.get(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get());
            return new SeedPacket(carried, profile, override == null ? null : override.health());
        }
        ResourceLocation seedId = itemProfile.seedId();
        SeedProfile profile = seedId == null ? null : ModSeedProfiles.serverLookup(seedId);
        if (profile == null) return null;
        var override = stack.get(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get());
        return new SeedPacket(new GeneticValue(seedId, profile), profile, override == null ? null : override.health());
    }
    /** Constructs one registered packet while retaining the exact seed-reference alias. */
    public static ItemStack createSeedPacket(PlantSeedReference reference, Float healthOverride) {
        if (reference == null || reference.current() == null) return ItemStack.EMPTY;
        SeedProfile profile = reference.current().profile();
        if (profile == null || profile.packet() == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(profile.packet());
        if (item == null || item == net.minecraft.world.item.Items.AIR
                || !profile.packet().equals(BuiltInRegistries.ITEM.getKey(item))) return ItemStack.EMPTY;
        var packet = new ItemStack(item);
        attach(packet, reference);
        if (healthOverride != null)
            packet.set(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get(), new SeedPacketHealthOverride(healthOverride));
        return packet;
    }
    /** Botany's carrier commit owns sharing; callers invoke only after publication/consumption succeeds. */
    static void markShared(PlantSeedReference reference) { if (reference != null) reference.unique(false); }
}
