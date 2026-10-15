package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;

import java.util.Map;
@PrefixGameTestTemplate(false)
@GameTestTags({"damage"})
public final class DamageGameTests {
    private DamageGameTests() {
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void zeroAndAbsentHealingDoNotMaterializeDamage(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        EffectContext context = context(helper, stand);
        EffectSystem effects = EffectSystem.withDefaults();
        require(effects.apply(new EffectData.HealthChange(EffectCommonData.DEFAULT, true,
                new DamageSpecifierData(Map.of("blunt", 0f))), context) == EffectResult.APPLIED,
                "zero HealthChange must be applied as a no-op");
        require(effects.apply(new EffectData.HealthChange(EffectCommonData.DEFAULT, true,
                new DamageSpecifierData(Map.of("blunt", -1f))), context) == EffectResult.APPLIED,
                "healing absent damage must be a quiet no-op");
        require(!stand.hasData(ModDataAttachments.DAMAGE.get()),
                "zero and absent healing must not create a damage attachment");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void healthChangeAndEvenHealingProjectTypedState(GameTestHelper helper) {
        Zombie stand = spawnZombie(helper, new BlockPos(1, 2, 1));
        EffectSystem effects = EffectSystem.withDefaults();
        EffectContext context = context(helper, stand);
        require(effects.apply(new EffectData.HealthChange(EffectCommonData.DEFAULT, true,
                        new DamageSpecifierData(Map.of("blunt", 5f, "slash", 5f))), context)
                        == EffectResult.APPLIED,
                "typed damage must apply");
        require(stand.getHealth() == 18f, "ten typed units must project to two health");

        require(effects.apply(new EffectData.EvenHealthChange(EffectCommonData.DEFAULT, true,
                        Map.of("brute", -4f)), context) == EffectResult.APPLIED,
                "even healing must apply");
        var damage = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && damage.getMap().equals(Map.of("blunt", 3f, "slash", 3f)),
                "even healing must redistribute over damaged group members");
        require(stand.getHealth() == 18.8f, "typed healing must project once");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void projectionUsesEntityMaxHealth(GameTestHelper helper) {
        Zombie stand = spawnZombie(helper, new BlockPos(1, 2, 1));
        stand.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        stand.setHealth(40f);
        DamageSystem.DamageApplication application = DamageSystem.applyHealthChangeWithAdmission(stand,
                Map.of(DamageKeys.BLUNT, 50f), 1f, true, DamageSystem.Cause.CHEMICAL);
        require(application.status() == DamageSystem.Result.APPLIED && application.positiveDamageAdmitted(),
                "typed damage must apply to the configured max health");
        require(stand.getHealth() == 30f, "fifty typed damage must remove ten health from max forty");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void canceledNativeHurtLeavesTypedLedgerUnchanged(GameTestHelper helper) {
        Zombie victim = spawnZombie(helper, new BlockPos(1, 2, 1));
        java.util.function.Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (event.getEntity() == victim) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            DamageSystem.DamageApplication application = DamageSystem.applyHealthChangeWithAdmission(victim,
                    Map.of(DamageKeys.RADIATION, 10f), 1f, false, DamageSystem.Cause.CHEMICAL);
            require(application.status() == DamageSystem.Result.APPLIED, "transaction request accepted");
            require(!application.positiveDamageAdmitted(), "canceled native hurt is not admitted");
            var stored = victim.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(stored == null || stored.getMap().isEmpty(), "canceled native hurt does not commit typed ledger");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void externalDamageHealingAndAbsorptionMirrorOnce(GameTestHelper helper) {
        Zombie stand = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        stand.setNoAi(true);
        stand.setNoGravity(true);
        helper.runAfterDelay(1, () -> {
            stand.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2f);
            stand.setAbsorptionAmount(2f);
            require(stand.getAbsorptionAmount() == 2f, "test absorption setup must stick");
            require(stand.hurt(stand.level().damageSources().generic(), 4f),
                    "vanilla damage must be accepted");
            var afterDamage = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(afterDamage != null && afterDamage.getMap().equals(Map.of(DamageKeys.BLUNT, 10f)),
                    "only unabsorbed vanilla damage must enter the ledger: "
                            + (afterDamage == null ? "null" : afterDamage.getMap())
                            + " health=" + stand.getHealth() + " absorption=" + stand.getAbsorptionAmount());
            require(stand.getHealth() == 18f && stand.getAbsorptionAmount() == 0f,
                    "vanilla health and absorption must remain consistent");

            stand.heal(1f);
            var afterHeal = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            require(afterHeal != null && afterHeal.getMap().equals(Map.of(DamageKeys.BLUNT, 5f)),
                    "generic healing must be mirrored exactly once");
            require(stand.getHealth() == 19f, "healing must not double-apply to vanilla health");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void damageDoesNotApplyAutomaticKnockbackButExplicitImpulseDoes(GameTestHelper helper) {
        Zombie victim = spawnZombie(helper, new BlockPos(2, 1, 2));
        Zombie attacker = spawnZombie(helper, new BlockPos(1, 1, 2));
        victim.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        require(victim.hurt(victim.level().damageSources().mobAttack(attacker), 1f),
                "ordinary damage must be accepted");
        require(victim.getDeltaMovement().horizontalDistanceSqr() == 0,
                "damage reaction must not shove the body");

        victim.knockback(1d, 1d, 0d);
        require(victim.getDeltaMovement().horizontalDistanceSqr() > 0,
                "explicit knockback must remain available");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void boundCharacterHarnessDamageDoesNotApplyAutomaticKnockback(GameTestHelper helper) {
        PlayerCharacterHarnessEntity body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(),
                new BlockPos(2, 1, 2));
        require(PlayerCharacterHarnessBinder.bind(body, java.util.UUID.randomUUID(), "main",
                        java.util.UUID.randomUUID(), ModCharacters.HUMAN_ID),
                "test human harness must bind to its resolved human prototype");
        Zombie attacker = spawnZombie(helper, new BlockPos(1, 1, 2));
        body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        require(body.hurt(body.level().damageSources().mobAttack(attacker), 1f),
                "bound character damage must be accepted");
        require(body.getDeltaMovement().horizontalDistanceSqr() == 0,
                "bound character damage must not cause automatic knockback");
        body.knockback(1d, 1d, 0d);
        require(body.getDeltaMovement().horizontalDistanceSqr() > 0,
                "explicit impulse remains available to the bound character");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void lowVanillaHealthImportsBaselineBeforeHealing(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        stand.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        stand.setHealth(30f);

        require(!stand.hasData(ModDataAttachments.DAMAGE.get()), "baseline test must begin without damage data");
        stand.heal(1f);

        var damage = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && damage.getMap().equals(Map.of(DamageKeys.BLUNT, 45f)),
                "low vanilla health must import and heal the missing-health baseline");
        require(stand.getHealth() == 31f, "baseline healing must use the requested health amount once");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void multiTypeVanillaHealingRemovesOneAggregateHealthPoint(GameTestHelper helper) {
        Zombie stand = spawnZombie(helper, new BlockPos(1, 2, 1));
        require(DamageSystem.applyHealthChange(stand, Map.of(DamageKeys.BLUNT, 5f, DamageKeys.SLASH, 10f),
                        1f, true) == DamageSystem.Result.APPLIED, "typed damage must apply");

        stand.heal(1f);

        var damage = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && damage.getMap().equals(Map.of(DamageKeys.BLUNT, 2.5f, DamageKeys.SLASH, 7.5f)),
                "vanilla healing must remove five typed units in aggregate");
        require(stand.getHealth() == 18f, "one vanilla health point must be removed exactly once");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void resistibleAndBypassTypedDamageHaveDifferentOutcomes(GameTestHelper helper) {
        Zombie resistible = spawnZombie(helper, new BlockPos(1, 2, 1));
        resistible.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        require(DamageSystem.applyHealthChange(resistible, Map.of(DamageKeys.BLUNT, 50f), 1f, false)
                        == DamageSystem.Result.APPLIED, "resistible damage must apply");
        var reduced = resistible.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        float reducedDamage = reduced == null ? -1f : reduced.getMap().getOrDefault(DamageKeys.BLUNT, -1f);
        require(reducedDamage > 0f && reducedDamage < 50f,
                "Resistance must reduce the aggregate typed damage: "
                        + (reduced == null ? "null" : reduced.getMap()) + " health=" + resistible.getHealth());
        require(Math.abs(resistible.getHealth() - (20f - reducedDamage / 5f)) < 0.01f,
                "resistible damage must project the reduced health loss");

        Zombie bypass = spawnZombie(helper, new BlockPos(3, 2, 1));
        bypass.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        bypass.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2f);
        bypass.setAbsorptionAmount(2f);
        require(DamageSystem.applyHealthChange(bypass, Map.of(DamageKeys.BLUNT, 50f), 1f, true)
                        == DamageSystem.Result.APPLIED, "bypass damage must apply");
        var unreduced = bypass.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(unreduced != null && unreduced.getMap().equals(Map.of(DamageKeys.BLUNT, 40f)),
                "bypass damage must ignore Resistance while honoring absorption");
        require(bypass.getHealth() == 12f && bypass.getAbsorptionAmount() == 0f,
                "bypass damage must preserve absorption as a consumable buffer");
        require(DamageSystem.applyHealthChange(bypass, Map.of(DamageKeys.BLUNT, 50f), 1f, true)
                        == DamageSystem.Result.APPLIED, "same-tick bypass damage must apply twice");
        var repeated = bypass.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(repeated != null && repeated.getMap().equals(Map.of(DamageKeys.BLUNT, 90f))
                        && bypass.getHealth() == 2f,
                "same-tick bypass damage must not be dropped by cooldown");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void repeatedResistibleDamageIsMirrored(GameTestHelper helper) {
        Zombie stand = spawnZombie(helper, new BlockPos(1, 2, 1));
        require(DamageSystem.applyHealthChange(stand, Map.of(DamageKeys.BLUNT, 5f), 1f, false)
                        == DamageSystem.Result.APPLIED, "first resistible damage must apply");
        require(DamageSystem.applyHealthChange(stand, Map.of(DamageKeys.BLUNT, 5f), 1f, false)
                        == DamageSystem.Result.APPLIED, "second resistible damage must apply");
        var damage = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        float total = damage == null ? 0f : damage.getMap().getOrDefault(DamageKeys.BLUNT, 0f);
        require(total > 5f && stand.getHealth() < 19f,
                "same-tick resistible damage must not be dropped by cooldown: "
                        + (damage == null ? "null" : damage.getMap()) + " health=" + stand.getHealth());
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void rejectedMixedHealthChangeDoesNotApplyHealingHalf(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        require(DamageSystem.applyHealthChange(stand,
                        Map.of(DamageKeys.BLUNT, 5f, DamageKeys.SLASH, -5f), 1f, false)
                        == DamageSystem.Result.APPLIED, "rejected mixed damage must return a handled result");
        require(!stand.hasData(ModDataAttachments.DAMAGE.get()) && stand.getHealth() == stand.getMaxHealth(),
                "a rejected mixed mutation must not commit its negative half");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void reconcileImportsMissingHealthAtNonDefaultMaxHealth(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        stand.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40f);
        stand.setHealth(30f);

        DamageSystem.reconcile(stand);

        var damage = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && damage.getMap().equals(Map.of(DamageKeys.BLUNT, 50f)),
                "reconcile must import missing health using the entity max health");
        require(stand.getHealth() == 30f, "baseline reconciliation must preserve current health");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void fullAbsorptionDoesNotMaterializeDamage(GameTestHelper helper) {
        Zombie stand = spawnZombie(helper, new BlockPos(1, 2, 1));
        stand.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2f);
        stand.setAbsorptionAmount(2f);
        require(stand.hurt(stand.level().damageSources().generic(), 2f),
                "fully absorbed vanilla damage must be accepted");
        require(!stand.hasData(ModDataAttachments.DAMAGE.get()),
                "fully absorbed damage must not create an attachment");
        require(stand.getHealth() == 20f && stand.getAbsorptionAmount() == 0f,
                "full absorption must preserve health and consume absorption");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void bypassLethalDamageUsesTheDeathPath(GameTestHelper helper) {
        Zombie stand = spawnZombie(helper, new BlockPos(1, 2, 1));
        require(DamageSystem.applyHealthChange(stand, Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                        == DamageSystem.Result.APPLIED, "lethal bypass damage must apply");
        require(stand.getHealth() == 0f && stand.isDeadOrDying(),
                "lethal bypass damage must enter the normal death path");
        helper.succeed();
    }

    private static ArmorStand spawn(GameTestHelper helper) {
        return helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos position) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, position);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        return zombie;
    }

    private static EffectContext context(GameTestHelper helper, LivingEntity stand) {
        return new EffectContext(helper.getLevel(), stand, 1f, RandomSource.create(7L),
                ConditionContext.unavailable(), EffectCause.MANUAL);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
