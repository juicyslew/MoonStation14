package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectDuration;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectrocuteGameTests {
    private ElectrocuteGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void electrocuteUsesShockLedgerAndAuthoritativeStun(GameTestHelper helper) {
        Zombie stand = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        stand.setNoAi(true);
        stand.setNoGravity(true);
        stand.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        EffectSystem effects = EffectSystem.withDefaults();
        EffectContext context = context(helper, stand, 2f);

        require(effects.apply(new EffectData.Electrocute(
                EffectCommonData.DEFAULT, .1f, 3, true, true, 1f), context) == EffectResult.APPLIED,
                "electrocution must apply");
        var ledger = stand.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        float shock = ledger == null ? -1f : ledger.getMap().getOrDefault(DamageKeys.SHOCK, -1f);
        require(ledger != null && ledger.getMap().size() == 1 && shock > 3f && shock < 6f,
                "resistance must reduce the single scaled shock below six: "
                        + (ledger == null ? "null" : ledger.getMap()));
        var stunned = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                .get(ModStatusEffects.createKey("statuseffectstunned")).orElseThrow();
        require(stunned.remainingDurationTicks().orElseThrow() == 2,
                "electrocute time must not be scaled with damage");

        // UPDATE takes the maximum rather than shortening an existing stun;
        // ADD accumulates through the same reducer-owned status key.
        EffectContext normalScale = context(helper, stand, 1f);
        require(effects.apply(new EffectData.Electrocute(
                EffectCommonData.DEFAULT, .01f, 1, true, true, 1f), normalScale)
                        == EffectResult.APPLIED, "shorter refresh must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                        .get(ModStatusEffects.createKey("statuseffectstunned")).orElseThrow()
                        .remainingDurationTicks().orElseThrow() == 2,
                "refresh must not shorten the timer");
        require(effects.apply(new EffectData.Electrocute(
                EffectCommonData.DEFAULT, .1f, 1, false, true, 1f), normalScale)
                        == EffectResult.APPLIED, "non-refresh electrocution must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                        .get(ModStatusEffects.createKey("statuseffectstunned")).orElseThrow()
                        .remainingDurationTicks().orElseThrow() == 4,
                "non-refresh electrocution must accumulate");

        // Stimulant-like removal uses the same authoritative key.
        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                "statuseffectstunned", StatusEffectDuration.finite(.2f),
                StatusEffectOperation.REMOVE, 0f), normalScale) == EffectResult.APPLIED,
                "stimulant-like stunned removal must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                        .get(ModStatusEffects.createKey("statuseffectstunned")).isEmpty(),
                "stimulant-like removal must clear electrocute stun");

        Cow unresisted = helper.spawn(EntityType.COW, new BlockPos(5, 1, 1));
        require(effects.apply(new EffectData.Electrocute(
                EffectCommonData.DEFAULT, 0f, 3, true, true, 1f),
                context(helper, unresisted, 2f)) == EffectResult.APPLIED,
                "unresisted electrocution must apply");
        var exactLedger = unresisted.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        float exactShock = exactLedger == null
                ? -1f : exactLedger.getMap().getOrDefault(DamageKeys.SHOCK, -1f);
        require(Math.abs(exactShock - 6f) <= 0.0001f,
                "unresisted path must record exactly six typed shock: "
                        + (exactLedger == null ? "null" : exactLedger.getMap()));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void insulationPolicyAndUnsupportedTargetsDoNotMutate(GameTestHelper helper) {
        EffectSystem effects = EffectSystem.withDefaults();
        ArmorStand insulatedRequest = spawn(helper, 1);
        List<String> warnings = new ArrayList<>();
        ElectrocutionSystem.resetWarnings();
        ElectrocutionSystem.setWarningSink(warnings::add);
        try {
            for (int i = 0; i < 2; i++) {
                require(effects.apply(new EffectData.Electrocute(
                        EffectCommonData.DEFAULT, 1f, 5, true, false, 1f),
                        context(helper, insulatedRequest, 1f)) == EffectResult.SKIPPED_UNSUPPORTED,
                        "non-bypass electrocution must be rejected without a conductivity model");
            }
            require(warnings.size() == 1,
                    "effective non-bypass requests must warn exactly once: " + warnings);
            require(!insulatedRequest.hasData(ModDataAttachments.DAMAGE.get())
                            && !insulatedRequest.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                    "insulation rejection must happen before all mutation");
        } finally {
            ElectrocutionSystem.resetWarningSink();
            ElectrocutionSystem.resetWarnings();
        }

        Entity item = helper.spawn(EntityType.ITEM, new BlockPos(4, 1, 1));
        require(effects.apply(new EffectData.Electrocute(
                EffectCommonData.DEFAULT, 1f, 5, true, true, 1f),
                context(helper, item, 1f)) == EffectResult.SKIPPED_UNSUPPORTED,
                "nonliving electrocution targets must be quiet unsupported");
        require(!item.hasData(ModDataAttachments.DAMAGE.get())
                        && !item.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "unsupported targets must not receive attachments");
        helper.succeed();
    }

    private static ArmorStand spawn(GameTestHelper helper, int x) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(x, 1, 1));
        stand.setNoGravity(true);
        return stand;
    }

    private static EffectContext context(GameTestHelper helper, Entity entity, float scale) {
        return new EffectContext(helper.getLevel(), entity, scale, RandomSource.create(42L),
                ConditionContext.unavailable(), EffectCause.MANUAL);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
