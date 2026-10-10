package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.medical.*;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestTags({"medical-treatment", "medical-state", "timed-interaction", "hands", "physiology"})
@PrefixGameTestTemplate(false)
public final class MedicalTreatmentGameTests {
    private MedicalTreatmentGameTests() { }

    @GameTestTags({"medical-treatment", "audio"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void resolvedMedicalSoundPoliciesHaveNativeSoundEvents(GameTestHelper helper) {
        var catalog = ModMedicalItems.catalog(helper.getLevel());
        for (var entry : catalog.values()) {
            var item = entry;
            item.healing().ifPresent(policy -> {
                policy.beginSound().ifPresent(id -> require(BuiltInRegistries.SOUND_EVENT.getOptional(id).isPresent(),
                        "medical begin sound event registered: " + id));
                policy.endSound().ifPresent(id -> require(BuiltInRegistries.SOUND_EVENT.getOptional(id).isPresent(),
                        "medical end sound event registered: " + id));
            });
            item.healthAnalyzer().flatMap(MedicalItemData.HealthAnalyzerPolicy::scanSound).ifPresent(id ->
                    require(BuiltInRegistries.SOUND_EVENT.getOptional(id).isPresent(), "medical scan sound event registered: " + id));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void resolvedMedicalPolicyFeedsTypedDamageConsumer(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "resolved body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "typed body and hands initialized");
        var catalog = ModMedicalItems.catalog(level);
        var ointment = ModMedicalItems.require(catalog, ResourceLocation.parse("moonstation14:ointment")).healing().orElseThrow();
        require(ointment.allowedDamageContainers().contains("biological"), "resolved allowed damage container retained");
        require(DamageSystem.applyHealthChange(body, java.util.Map.of("heat", 5f), 1f, true) == DamageSystem.Result.APPLIED, "typed heat injury applied");
        require(body.getData(ModDataAttachments.DAMAGE.get()).getMap().getOrDefault("heat", 0f) == 5f, "damage ledger records typed injury");
        require(DamageSystem.applyHealthChange(body, ointment.damage(), 1f, false) == DamageSystem.Result.APPLIED, "resolved healing delta applied through damage system");
        require(body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) == null
                || body.getData(ModDataAttachments.DAMAGE.get()).getMap().getOrDefault("heat", 0f) == 0f,
                "typed healing clears only configured damage");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void committedRepeatsRecalculateDurationAndMovementOrigin(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created"); body.setNoAi(true); body.setNoGravity(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(body), "body added");
        AtomicInteger duration = new AtomicInteger(2), completions = new AtomicInteger();
        require(TimedInteractionSystem.start(body, null, 1, "medical-test", "tool:target", 2,
                false, true, 1, duration::get, () -> true, () -> {
                    if (completions.getAndIncrement() == 0) { duration.set(4); return TimedInteractionSystem.Completion.REPEAT; }
                    return TimedInteractionSystem.Completion.STOP;
                }).name().equals("STARTED"), "dynamic-duration action starts");
        helper.runAfterDelay(3, () -> {
            var active = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                    .filter(o -> o.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
            require(active.durationTicks() == 4, "repeat uses newly calculated duration");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> body.setPos(body.getX() + .5, body.getY(), body.getZ()));
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 3, () -> {
                require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(o -> o.id() == active.id() && o.phase() == TimedOperation.Phase.CANCELLED),
                        "movement after repeat is measured from the new repeat origin");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void typedTreatmentDeltaDoesNotReviveDefaultDeadBody(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created"); body.setNoAi(true); body.setNoGravity(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "body enrolled");
        require(BodySystem.reconcile(body), "body medical state resolved");
        require(DamageSystem.applyHealthChange(body, java.util.Map.of("blunt", 205f), 1f, true) == DamageSystem.Result.APPLIED,
                "typed damage reaches resolved dead threshold");
        require(MobStateSystem.isDead(body), "body reaches Dead state");
        var policy = ModMedicalItems.require(ModMedicalItems.catalog(level), ResourceLocation.parse("moonstation14:brutepack"))
                .healing().orElseThrow();
        require(DamageSystem.applyHealthChange(body, policy.damage(), 1f, false) == DamageSystem.Result.APPLIED,
                "resolved typed healing delta applies to retained dead ledger");
        require(MobStateSystem.isDead(body), "ordinary typed healing below the threshold does not revive default Dead");
        require(body.getData(ModDataAttachments.DAMAGE.get()).getMap().getOrDefault("blunt", 0f) == 200f,
                "dead body's authoritative typed ledger is healed without native-health deficit inference");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void gauzeBleedOnlyAndMixedTypedEffectsStayIndependent(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created"); body.setNoAi(true); body.setNoGravity(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "native human body enrolled");
        require(BodySystem.reconcile(body) && com.juicyslew.moonstation14.ms14.blood.BloodSystem.reconcile(body),
                "typed and blood state resolved");
        com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(body).orElseThrow();
        body.setData(ModDataAttachments.BLOOD.get(), new com.juicyslew.moonstation14.ms14.blood.BloodComponent(10, true).toAttachment());
        var gauze = ModMedicalItems.require(ModMedicalItems.catalog(level), ResourceLocation.parse("moonstation14:gauze"))
                .healing().orElseThrow();
        require(gauze.damage().equals(java.util.Map.of("slash", -5f, "piercing", -10f))
                        && gauze.bleedModifier() == -10,
                "resolved gauze has slash/piercing only and negative ten bleed delta");

        require(MedicalTreatmentEffects.apply(body, gauze).changed(), "production treatment effects accept bleed-only gauze");
        require(com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(body).orElseThrow().bleedRate() == 0,
                "bleed-only treatment stops bleeding");

        require(DamageSystem.applyHealthChange(body, java.util.Map.of("blunt", 12f, "slash", 8f, "piercing", 15f, "bloodloss", 7f),
                1f, true) == DamageSystem.Result.APPLIED, "typed mixed injury applied");
        body.setData(ModDataAttachments.BLOOD.get(), new com.juicyslew.moonstation14.ms14.blood.BloodComponent(10, true).toAttachment());
        double bleedBefore = com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(body).orElseThrow().bleedRate();
        require(MedicalTreatmentEffects.apply(body, gauze).changed(), "production gauze consumer applies mixed effects");
        var ledger = body.getData(ModDataAttachments.DAMAGE.get()).getMap();
        require(ledger.getOrDefault("slash", 0f) == 3 && ledger.getOrDefault("piercing", 0f) == 5
                        && ledger.getOrDefault("blunt", 0f) == 12 && ledger.getOrDefault("bloodloss", 0f) == 7,
                "gauze clears only configured slash/piercing damage; blunt remains");
        require(bleedBefore == 10, "mixed injury starts with ten bleed rate");
        require(com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(body).orElseThrow().bleedRate() == 0,
                "gauze stops active bleed independently of blunt and blood-loss ledger damage");
        long now = level.getGameTime();
        for (long tick = now; tick < now + 60; tick++)
            com.juicyslew.moonstation14.ms14.blood.BloodSystem.tickIfDue(body, tick);
        require(com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(body).orElseThrow().bleedRate() == 0,
                "old unchanged typed injuries do not regenerate stopped bleeding during passive updates");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
