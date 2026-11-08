package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.ItemStack;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-seed-reference"})
public final class PlantSeedReferenceGameTests {
    private PlantSeedReferenceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14")
    @GameTestTags("botany-seed-reference")
    public static void ensureUniqueDetachesSharedIdentityWithoutChangingCapturedReference(GameTestHelper helper) {
        var original = new PlantSeedReference(new GeneticValue(ModSeedProfiles.seedId("wheat"),
                ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat"))), false);
        var detached = original.ensureUnique();
        require(detached != original && detached.unique(), "clone-on-write creates unique identity");
        require(!original.unique() && !original.equals(detached), "clone-on-write detaches the opaque identity");
        require(original.current().equals(detached.current()), "clone-on-write preserves the immutable genetics value");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14")
    @GameTestTags("botany-seed-reference")
    public static void carrierStackPreservesReferenceIdentity(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var reference = new PlantSeedReference(new GeneticValue(species, ModSeedProfiles.serverLookup(species)), true);
        var stack = new ItemStack(ModItems.WHEAT_SEEDS.get());
        GeneticCarriers.attach(stack, reference);
        require(reference.equals(stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get())),
                "packet carrier retains the attached opaque seed identity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14")
    @GameTestTags("botany-seed-reference")
    public static void referenceCodecRoundTripDecodesDetachedSnapshot(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var original = new PlantSeedReference(new GeneticValue(species, ModSeedProfiles.serverLookup(species)), true);
        var encoded = PlantSeedReference.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, original).getOrThrow();
        var decoded = PlantSeedReference.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        require(original.equals(decoded) && decoded != original && decoded.unique(),
                "codec retains identity and snapshot values but constructs a detached object");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
