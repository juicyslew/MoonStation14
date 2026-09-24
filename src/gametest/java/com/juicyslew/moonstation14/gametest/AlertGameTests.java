package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.alert.AlertAttachment;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AlertGameTests {
    private AlertGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void alertApplicationClearAndExpiry(GameTestHelper helper) {
        ArmorStand character = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        EffectSystem effects = EffectSystem.withDefaults();
        require(apply(effects, helper, character, true, 0) == EffectResult.APPLIED, "immediate clear should be applied");
        require(state(character) == null, "clear should not materialize empty state");
        require(apply(effects, helper, character, false, 0) == EffectResult.APPLIED, "persistent show should apply");
        require(state(character) != null && !state(character).isEmpty(), "persistent alert should be attached and synced");
        require(apply(effects, helper, character, true, 1) == EffectResult.APPLIED, "timed clear should show temporarily");
        helper.runAfterDelay(1, () -> {
            require(state(character) != null && !state(character).isEmpty(), "alert must remain before deadline");
            helper.runAfterDelay(20, () -> {
                AlertAttachment current = state(character);
                require(current == null || current.isEmpty(), "timed alert must expire at its deadline");
                helper.succeed();
            });
        });
    }

    private static EffectResult apply(EffectSystem effects, GameTestHelper helper, ArmorStand target,
                                      boolean clear, float time) {
        return effects.apply(new EffectData.AdjustAlert(EffectCommonData.DEFAULT, "toxins", clear, true, time),
                new EffectContext(helper.getLevel(), target, 1f, RandomSource.create(4),
                        ConditionContext.unavailable(), EffectCause.MANUAL));
    }
    private static AlertAttachment state(ArmorStand entity) {
        return entity.getExistingDataOrNull(ModDataAttachments.ALERT.get());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
