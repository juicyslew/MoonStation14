package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla"})
@PrefixGameTestTemplate(false)
public final class TeslaTargetEligibilityGameTests {
    private TeslaTargetEligibilityGameTests() { }
    private static void check(boolean valid, String message) { if (!valid) throw new GameTestAssertException(message); }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=20)
    public static void directTargetsRequireExplicitSupportedCounterpartProfile(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(3, 3, 3));
        var candidates = java.util.Map.of(
                origin, ModBlocks.TESLA_COIL.get(), origin.east(), ModBlocks.TESLA_GROUNDING_ROD.get(),
                origin.east(2), ModBlocks.EMITTER.get(), origin.east(3), ModBlocks.CHEM_MASTER.get(),
                origin.east(4), ModBlocks.APC.get(), origin.east(5), ModBlocks.TESLA_GENERATOR.get(),
                origin.east(6), Blocks.STONE);
        candidates.forEach((pos, block) -> level.setBlock(pos, block.defaultBlockState(), 3));
        h.runAfterDelay(1, () -> {
            for (int i = 0; i < 5; i++) {
                var pos = origin.east(i);
                check(TeslaLightningSystem.resolveBlockTarget(level, level.getBlockEntity(pos)) != null,
                        "supported target resolves: " + level.getBlockState(pos));
            }
            for (int i : new int[]{5, 6}) {
                var pos = origin.east(i);
                check(TeslaLightningSystem.resolveBlockTarget(level, level.getBlockEntity(pos)) == null,
                        "unsupported direct target is excluded: " + level.getBlockState(pos));
            }
            for (String id : new String[]{"pa_control_box", "pa_power_box", "pa_fuel_chamber",
                    "pa_end_cap", "pa_emitter_port", "pa_emitter_fore", "pa_emitter_starboard"}) {
                var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", id));
                check(block != net.minecraft.world.level.block.Blocks.AIR, "registered finished PA block: " + id);
                check(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTag(
                                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "blast_damage_immune")))
                                .map(tag -> tag.contains(block.builtInRegistryHolder())).orElse(false),
                        "finished PA block is blast immune: " + id);
            }
            candidates.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=20)
    public static void directStructuralDamageUsesResolvedThresholdAndSalvage(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        BlockPos coilPos = h.absolutePos(new BlockPos(3, 3, 3));
        BlockPos rodPos = coilPos.east();
        BlockPos emitterPos = coilPos.east(2);
        level.setBlock(coilPos, ModBlocks.TESLA_COIL.get().defaultBlockState(), 3);
        level.setBlock(rodPos, ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(), 3);
        level.setBlock(emitterPos, ModBlocks.EMITTER.get().defaultBlockState(), 3);
        var coil = (TeslaMachineBlockEntity) level.getBlockEntity(coilPos);
        var rod = (TeslaMachineBlockEntity) level.getBlockEntity(rodPos);
        var coilProfile = coil.profile(); var rodProfile = rod.profile();
        check(TeslaLightningSystem.resolveBlockTarget(level, coil) != null
                && TeslaLightningSystem.resolveBlockTarget(level, rod) != null, "coil/rod selected by explicit resolved mappings");
        MachineryIntegrity.add(coil, coilProfile.destructionThreshold() - 1);
        check(coil.receiveLightningStrike(), "coil receives strike and retains charge behavior");
        check(coil.energyJoules() == 5_000_000, "the same strike charges the coil once");
        check(coil.structuralDamage() == coilProfile.destructionThreshold(), "one direct conductor strike applies one structural unit");
        check(level.getBlockState(coilPos).isAir(), "coil is removed at resolved structural threshold");
        check(TeslaLightningSystem.resolveBlockTarget(level, coil) == null, "removed coil cannot remain a selected target");
        int coilSteel = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(coilPos).inflate(2)).stream()
                .filter(item -> item.getItem().is(com.juicyslew.moonstation14.item.ModItems.STEEL.get()))
                .mapToInt(item -> item.getItem().getCount()).sum();
        check(coilSteel >= 2 && coilSteel <= 4, "coil yields resolved two-to-four steel salvage");
        check(level.getBlockEntity(rodPos) == rod, "adjacent rod remains");
        check(rod.receiveLightningStrike(), "rod receives strike");
        check(rod.structuralDamage() == 0, "zero-damage rod strike remains unworn");
        check(rod.structuralDamage() == 0 && !level.getBlockState(rodPos).isAir(), "rod authored zero damage does not wear");
        var emitter = level.getBlockEntity(emitterPos);
        var emitterProfile = ModTeslaProfiles.machines(level).get(ModTeslaProfiles.profileForBlock(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(emitterPos).getBlock())));
        var emitterTarget = TeslaLightningSystem.resolveBlockTarget(level, emitter);
        MachineryIntegrity.add(emitter, emitterProfile.destructionThreshold() - 1);
        TeslaLightningSystem.applyBlockTargetResponse(level, emitterTarget);
        check(level.getBlockState(emitterPos).isAir(), "emitter is removed at resolved threshold");
        check(MachineryIntegrity.damage(emitter) == emitterProfile.destructionThreshold(),
                "structural threshold is committed before the enabled incidental explosion executes");
        int steel = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(emitterPos).inflate(2)).stream()
                .filter(item -> item.getItem().is(com.juicyslew.moonstation14.item.ModItems.STEEL.get()))
                .mapToInt(item -> item.getItem().getCount()).sum();
        check(steel >= 5, "emitter produces authored five steel salvage");
        h.succeed();
    }
}
