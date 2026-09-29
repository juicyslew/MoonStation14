package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaSystem;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PressureGameTests {
    private PressureGameTests() { }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void absentComponentDoesNotEnrollOrDamage(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        cow.setNoAi(true);
        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        cow.setPos(sky.getX() + 0.5, sky.getY() - cow.getEyeHeight(), sky.getZ() + 0.5);
        require(vacuum.sample(level, BlockPos.containing(cow.getEyePosition())).isPresent(),
                "absence check requires known vacuum");
        require(!BarotraumaSystem.tickIfDue(cow, -cow.getId(), vacuum)
                        && !cow.hasData(ModDataAttachments.DAMAGE.get()),
                "unmapped host has no component and must not create damage state");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void crossingCeilingAppliesFullHitThenHealingReopens(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        pig.setHealth(100);
        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        pig.setPos(sky.getX() + 0.5, sky.getY() - pig.getEyeHeight(), sky.getZ() + 0.5);
        require(vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).isPresent(),
                "ceiling test requires strict exterior vacuum");
        require(DamageSystem.applyHealthChange(pig, Map.of("blunt", 199.9f), 1f, true)
                        == DamageSystem.Result.APPLIED, "seed matching typed damage");
        float before = pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().get("blunt");
        require(Math.abs(before - 199.9f) < 0.001f, "seed must commit 199.9 matching damage");

        require(BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum), "crossing hit must apply");
        var crossed = pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap();
        require(Math.abs(crossed.get("blunt") - (before + 0.6f)) < 0.001f,
                "the full pig hit must cross the 200 ceiling");
        require(!BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum)
                        && pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().equals(crossed),
                "next due update must stop at current matching damage above ceiling");

        require(DamageSystem.applyHealthChange(pig, Map.of("blunt", -3f), 1f, true)
                        == DamageSystem.Result.APPLIED, "heal matching damage below ceiling");
        require(BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum),
                "healing must reopen pressure damage headroom");
        var resumed = pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap();
        require(Math.abs(resumed.get("blunt") - (crossed.get("blunt") - 2.4f)) < 0.001f,
                "resumed hit must again apply full typed damage");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void missingLedgerAboveCeilingSkipsVacuumHitWithoutImport(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        pig.setHealth(59); // 41 missing health = 205 typed blunt, above the 200 ceiling.
        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        pig.setPos(sky.getX() + 0.5, sky.getY() - pig.getEyeHeight(), sky.getZ() + 0.5);
        require(vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).isPresent()
                        && vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).orElseThrow().pressureKpa(1) == 0,
                "ceiling check requires strict known vacuum");
        require(!pig.hasData(ModDataAttachments.DAMAGE.get()), "vanilla injury must not already have a ledger");
        require(!BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum),
                "missing-health blunt baseline above ceiling must prevent pressure hit");
        require(pig.getHealth() == 59f && !pig.hasData(ModDataAttachments.DAMAGE.get()),
                "precheck must neither damage host nor materialize missing ledger");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void missingLedgerBelowCeilingImportsOnceAndAppliesFullVacuumHit(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        pig.setHealth(61); // 39 missing health = 195 typed blunt, below the 200 ceiling.
        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        pig.setPos(sky.getX() + 0.5, sky.getY() - pig.getEyeHeight(), sky.getZ() + 0.5);
        require(vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).isPresent()
                        && vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).orElseThrow().pressureKpa(1) == 0,
                "below-ceiling check requires strict known vacuum");
        require(!pig.hasData(ModDataAttachments.DAMAGE.get()), "vanilla injury must not already have a ledger");
        require(BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum), "vacuum must apply full pressure hit");
        var first = pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap();
        require(first.size() == 1 && Math.abs(first.getOrDefault("blunt", 0f) - 195.6f) < 0.001f,
                "first hit must import 195 blunt once and add the full 0.6 typed pressure hit");
        require(BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum), "below ceiling permits another full hit");
        var second = pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap();
        require(second.size() == 1 && Math.abs(second.getOrDefault("blunt", 0f) - 196.2f) < 0.001f,
                "next hit must add only pressure damage, not reimport missing health");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", timeoutTicks = 80)
    public static void isolatedStrictKnownAndUnknownPressure(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        var air = AtmosphereService.withVacuumDimensions(Set.of());
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        BlockPos eye = BlockPos.containing(pig.getEyePosition());
        // Covered but unclaimed, then solid: neither is permitted to imply vacuum.
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        for (int y = 1; y <= 2; y++) for (int i = 1; i <= 3; i++) {
            helper.setBlock(new BlockPos(1, y, i), Blocks.STONE);
            helper.setBlock(new BlockPos(3, y, i), Blocks.STONE);
            helper.setBlock(new BlockPos(i, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(i, y, 3), Blocks.STONE);
        }
        require(vacuum.sample(level, eye).isEmpty(), "unclaimed covered cell must be unknown");
        float baseline = pig.getHealth();
        require(!BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum), "unknown must not damage");
        require(pig.getHealth() == baseline && !pig.hasData(ModDataAttachments.DAMAGE.get()),
                "unknown read must not materialize damage state");
        require(!BarotraumaSystem.tickIfDue(pig, 1L - pig.getId(), vacuum),
                "stagger must reject an off-cadence tick");

        // Claim the closed room as finite, using an independent air-configured service.
        for (int i = 1; i <= 12 && air.sample(level, eye).isEmpty(); i++)
            air.tick(level, i * AtmosphereService.TICK_CADENCE);
        require(air.sample(level, eye).isPresent() && air.sample(level, eye).orElseThrow().pressureKpa(1) > 20,
                "sealed finite cell must contain air");
        require(!BarotraumaSystem.tickIfDue(pig, -pig.getId(), air), "ordinary air must be inert");
        level.setBlockAndUpdate(eye, Blocks.STONE.defaultBlockState());
        float beforeSolid = pig.getHealth();
        require(air.sample(level, eye).isEmpty() && !BarotraumaSystem.tickIfDue(pig, -pig.getId(), air)
                        && pig.getHealth() == beforeSolid, "solid eye cell must be inert");

        // Move to a verified sky-exposed vacuum; the strict exterior mixture is zero pressure.
        BlockPos sky = new BlockPos(eye.getX(), level.getMaxBuildHeight() - 10, eye.getZ());
        level.getChunk(sky);
        pig.setPos(sky.getX() + 0.5, sky.getY() - pig.getEyeHeight(), sky.getZ() + 0.5);
        require(vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).isPresent()
                        && vacuum.sample(level, BlockPos.containing(pig.getEyePosition())).orElseThrow().pressureKpa(1) == 0,
                "sky must be proven exterior vacuum");
        float beforeVacuum = pig.getHealth();
        require(BarotraumaSystem.tickIfDue(pig, -pig.getId(), vacuum), "known vacuum must damage");
        require(pig.getHealth() < beforeVacuum, "vacuum low damage must reach health");
        var disabled = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        disabled.onServerStopped();
        float beforeDisabled = pig.getHealth();
        require(!BarotraumaSystem.tickIfDue(pig, -pig.getId(), disabled)
                        && pig.getHealth() == beforeDisabled, "disabled atmosphere is inert");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
