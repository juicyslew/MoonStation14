package com.juicyslew.moonstation14.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaSystem;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolutionException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
    public static void boundPigReadsCommittedIsolatedComponentSnapshots(GameTestHelper helper) {
        var level = helper.getLevel();
        var vacuum = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        var identity = pig.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        require(identity != null && identity.isBound(), "pig must already be bound by the join adapter");
        ResourceLocation pigId = identity.characterId();
        require(pigId.equals(ResourceLocation.parse("moonstation14:pig")), "pig must retain its real identity");
        var global = ModCharacters.catalog(level);
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        require(!BarotraumaSystem.tickIfDue(pig, -(long) pig.getId(), vacuum,
                        manager.snapshot(ModCharacters.CHARACTER_TYPE)) && !pig.hasData(ModDataAttachments.DAMAGE.get()),
                "bound identity absent from snapshot must remain inert without enrollment");
        ResourceLocation baseId = ResourceLocation.parse("moonstation14:pressure_test_base");
        var inherited = pressureCandidate(baseId, pigId, "", "");
        var initialToken = manager.stage(Map.of(ModCharacters.CHARACTER_TYPE, inherited));
        require(manager.snapshot(ModCharacters.CHARACTER_TYPE).get(pigId) == null,
                "staging must not publish the candidate");
        require(manager.commit(initialToken), "initial isolated commit must succeed");
        var first = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        require(first.get(baseId) == null && first.get(pigId).component(BarotraumaComponent.class).isPresent(),
                "abstract parent must not be published, concrete child must inherit component");

        BlockPos sky = new BlockPos(2, level.getMaxBuildHeight() - 10, 2);
        level.getChunk(sky);
        pig.setPos(sky.getX() + 0.5, sky.getY() - pig.getEyeHeight(), sky.getZ() + 0.5);
        var sample = vacuum.sample(level, BlockPos.containing(pig.getEyePosition()));
        require(sample.isPresent() && sample.orElseThrow().pressureKpa(1) == 0,
                "isolated enabled service must prove strict exterior vacuum");
        long due = -(long) pig.getId();
        require(!pig.hasData(ModDataAttachments.DAMAGE.get()), "initial pressure read must not enroll damage");
        require(BarotraumaSystem.tickIfDue(pig, due, vacuum, first), "inherited pressure hit must apply");
        require(typedBlunt(pig) == 0.6f, "inherited .15 spec must produce one .6 typed hit");

        ResourceLocation componentFreeBaseId = ResourceLocation.parse("moonstation14:pressure_test_component_free_base");
        var removedToken = manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                pressureCandidate(componentFreeBaseId, pigId, "\"components\":[]", "")));
        require(manager.snapshot(ModCharacters.CHARACTER_TYPE) == first, "stage must retain published snapshot");
        require(manager.commit(removedToken), "component-free parent commit must succeed");
        var removed = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        require(removed != first && removed.get(pigId).component(BarotraumaComponent.class).isEmpty(),
                "same pig identity must inherit no pressure policy from component-free parent");
        require(first.get(pigId).component(BarotraumaComponent.class).isPresent(),
                "previous snapshot remains immutable");
        require(!BarotraumaSystem.tickIfDue(pig, due, vacuum, removed) && typedBlunt(pig) == 0.6f,
                "component-free snapshot cannot apply another hit or damage state");

        var changedToken = manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                pressureCandidate(baseId, pigId, "", ",\"components\":[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.25}}}]")));
        require(manager.commit(changedToken), "override commit must succeed");
        var changed = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        require(changed != removed && removed.get(pigId).component(BarotraumaComponent.class).isEmpty(),
                "old component-free snapshot must stay immutable");
        require(BarotraumaSystem.tickIfDue(pig, due, vacuum, changed), "changed component must hit live pig");
        require(Math.abs(typedBlunt(pig) - 1.6f) < 0.001f
                        && pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().size() == 1,
                "override .25 must add exactly 1.0 typed blunt, not a second pressure policy");

        try {
            manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                    pressureCandidate(baseId, pigId, "",
                            ",\"components\":[{\"type\":\"Barotrauma\",\"remove\":true}]")));
            throw new GameTestAssertException("unsupported removal field must fail staging");
        } catch (PrototypeResolutionException expected) {
            require(expected.getMessage().contains("$.components[0].remove")
                            && expected.getMessage().contains("unknown field"),
                    "removal must fail as an unknown component field");
            require(manager.snapshot(ModCharacters.CHARACTER_TYPE) == changed && !manager.hasStagedReload(),
                    "invalid candidate must not replace published snapshot");
        }
        require(ModCharacters.catalog(level) == global, "isolated manager must not mutate production catalog");
        helper.succeed();
    }

    private static Map<ResourceLocation, JsonObject> pressureCandidate(ResourceLocation baseId, ResourceLocation pigId,
                                                                          String baseComponents, String childTail) {
        String base = "{\"abstract\":true,"
                + (baseComponents.isEmpty() ? "\"components\":[{\"type\":\"Barotrauma\","
                + "\"damage\":{\"types\":{\"blunt\":0.15}},\"maxDamage\":200}]" : baseComponents) + "}";
        String child = "{\"parent\":\"" + baseId + "\",\"host_entity_types\":[\"minecraft:pig\"]"
                + childTail + "}";
        return Map.of(baseId, JsonParser.parseString(base).getAsJsonObject(),
                pigId, JsonParser.parseString(child).getAsJsonObject());
    }

    private static float typedBlunt(Pig pig) {
        return pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()).getMap().getOrDefault("blunt", 0f);
    }

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
