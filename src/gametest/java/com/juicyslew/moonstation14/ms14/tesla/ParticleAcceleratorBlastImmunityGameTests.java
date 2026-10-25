package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestTags({"tesla", "explosion"})
@PrefixGameTestTemplate(false)
public final class ParticleAcceleratorBlastImmunityGameTests {
    private static final BlastPlanner.Proposal PROPOSAL = new BlastPlanner.Proposal(120, 3, 10);
    private static final Block[] PARTS = {ModBlocks.PA_CONTROL_BOX.get(), ModBlocks.PA_POWER_BOX.get(),
            ModBlocks.PA_FUEL_CHAMBER.get(), ModBlocks.PA_END_CAP.get(), ModBlocks.PA_EMITTER_PORT.get(),
            ModBlocks.PA_EMITTER_FORE.get(), ModBlocks.PA_EMITTER_STARBOARD.get()};
    private ParticleAcceleratorBlastImmunityGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=20)
    public static void finishedPaPartsResistMutationWithoutBlockingBlast(GameTestHelper h) {
        ServerLevel level = (ServerLevel) h.getLevel();
        BlockPos source = h.absolutePos(new BlockPos(2, 2, 2));
        for (int i = 0; i < PARTS.length; i++) {
            BlockPos pa = source.offset(i * 12 + 1, 0, 0);
            BlockPos behind = pa.east();
            level.setBlock(pa, PARTS[i].defaultBlockState(), 3);
            level.setBlock(behind, Blocks.STONE.defaultBlockState(), 3);
            var paEntity = level.getBlockEntity(pa);
            check(paEntity != null, "PA part has block entity: " + PARTS[i]);
            BlockPos blastSource = pa.west();
            var result = ServerExplosionService.execute(level, blastSource, ModExplosions.DEFAULT_ID, PROPOSAL);
            check(result.status() == ServerExplosionService.Status.APPLIED,
                    "legitimate finite blast applied for " + PARTS[i] + ": " + result);
            check(result.breached() > 0 && result.mutated() > 0,
                    "planner breaches cells and executor mutates ordinary terrain: " + result);
            check(level.getBlockState(pa).is(PARTS[i]) && level.getBlockEntity(pa) == paEntity,
                    "PA part and BE preserved by runtime immunity consumer: " + PARTS[i]);
            check(level.getBlockState(behind).isAir(), "terrain behind PA part is breached: " + PARTS[i]);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=20)
    public static void finishedPaPartsAreNotDirectTeslaTargetsAndEmitterControlIsNotImmune(GameTestHelper h) {
        ServerLevel level = (ServerLevel) h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2, 2, 2));
        for (int i = 0; i < PARTS.length; i++) {
            BlockPos pos = origin.offset(i + 1, 0, 0);
            level.setBlock(pos, PARTS[i].defaultBlockState(), 3);
            var be = level.getBlockEntity(pos);
            check(be != null && TeslaLightningSystem.resolveBlockTarget(level, be) == null,
                    "finished PA block is not a direct Tesla target: " + PARTS[i]);
        }
        BlockPos emitter = origin.north();
        level.setBlock(emitter, ModBlocks.EMITTER.get().defaultBlockState(), 3);
        var emitterEntity = level.getBlockEntity(emitter);
        check(emitterEntity != null && TeslaLightningSystem.resolveBlockTarget(level, emitterEntity) != null,
                "supported emitter remains a direct Tesla target");
        check(!level.getBlockState(emitter).is(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BLOCK,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "blast_damage_immune"))),
                "emitter is not added to blast immunity");
        h.succeed();
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new GameTestAssertException(message);
    }
}
