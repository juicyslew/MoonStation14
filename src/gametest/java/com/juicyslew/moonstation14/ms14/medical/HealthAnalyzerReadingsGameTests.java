package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureAttachment;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestTags({"health-analyzer", "prototype", "physiology", "medical-state"})
@PrefixGameTestTemplate(false)
public final class HealthAnalyzerReadingsGameTests {
    private HealthAnalyzerReadingsGameTests() { }

    @GameTestTags({"health-analyzer", "prototype"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void resolvedRangePoliciesUseLiveHumanAndPigWorldPositions(GameTestHelper helper) {
        var level = helper.getLevel();
        var analyzer = ModMedicalItems.require(ModMedicalItems.catalog(level),
                ResourceLocation.parse("moonstation14:handheld_health_analyzer"));
        var scan = analyzer.healthAnalyzer().orElseThrow();
        var ui = analyzer.ui().orElseThrow();
        require(scan.scanRange() == 3.5f && ui.range() == 3f, "resolved scan is 3.5 blocks; UI retention is independently 3 blocks");
        require(scan.scanDelay() == .8f && scan.updateInterval() == 1f && !analyzer.toggle().orElseThrow().onUse(),
                "scanner timing and toggle composition remain unchanged");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level);
        Pig patient = EntityType.PIG.create(level);
        require(body != null && patient != null, "Human body and Pig patient created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        patient.setNoAi(true); patient.setNoGravity(true); patient.setPos(body.getX() + 3.25, body.getY(), body.getZ());
        require(level.addFreshEntity(body) && level.addFreshEntity(patient), "native bodies added");
        require(HealthAnalyzerLifecycle.inResolvedRange(body.position(), patient.position(), scan.scanRange()),
                "live scan predicate admits patient at 3.25 blocks");
        patient.setPos(body.getX() + 2.75, body.getY(), body.getZ());
        require(HealthAnalyzerLifecycle.inUiRange(body.position(), patient.position(), ui.range()),
                "live physical-root UI predicate admits root at 2.75 blocks");
        patient.setPos(body.getX() + 3.1, body.getY(), body.getZ());
        require(!HealthAnalyzerLifecycle.inUiRange(body.position(), patient.position(), ui.range()),
                "live physical-root UI predicate rejects root at 3.1 blocks");
        patient.setPos(body.getX() + 3.25, body.getY(), body.getZ());
        patient.setPos(body.getX() + 3.6, body.getY(), body.getZ());
        require(!HealthAnalyzerLifecycle.inResolvedRange(body.position(), patient.position(), scan.scanRange()),
                "live scan predicate rejects patient at 3.6 blocks");
        require(!HealthAnalyzerLifecycle.inUiRange(body.position(), patient.position(), ui.range()),
                "live physical-root UI predicate rejects root at 3.1 blocks");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void readsLivePhysiologyAndTypedMobStateWithoutCreatingDamage(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "Human fixture enrolled");
        body.setData(ModDataAttachments.BODY_TEMPERATURE.get(),
                new BodyTemperatureAttachment(new BodyTemperatureComponent(315.25)));
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(
                new MobStateData(MobStateComponent.State.CRITICAL, 1)));
        float healthBefore = body.getHealth();
        var snapshot = read(body);
        require(snapshot.species().equals("Human"), "species comes from resolved humanoid profile");
        require(snapshot.temperatureKelvin().orElseThrow() == (float)315.25, "live temperature attachment read");
        require(snapshot.state().equals("Critical"), "typed Critical state read");
        require(snapshot.damage().isEmpty() && body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) == null,
                "missing damage stays empty without importing vanilla baseline");
        require(body.getHealth() == healthBefore, "reading does not project native health");
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(
                new MobStateData(MobStateComponent.State.DEAD, 2)));
        var dead = read(body);
        require(dead.state().equals("Dead") && dead.unrevivable(), "default non-revivable dead state alert");
        body.removeData(ModDataAttachments.BODY_TEMPERATURE.get());
        var missing = read(body);
        require(missing.temperatureKelvin().isEmpty(), "missing temperature remains unknown");
        require(missing.species().equals("Human"), "species remains prototype-owned");
        helper.succeed();
    }

    private static HealthAnalyzerSnapshot read(Villager body) {
        return HealthAnalyzerReadings.read(UUID.randomUUID(), 1, UUID.randomUUID(), body, 2, true, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
