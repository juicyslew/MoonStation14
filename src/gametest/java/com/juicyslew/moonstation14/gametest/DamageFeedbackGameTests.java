package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPayload;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.ModDamageFeedback;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestTags({"damage-feedback"})
public final class DamageFeedbackGameTests {
    private DamageFeedbackGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void chemicalDamageCommitsQuietlyWithoutInterruptingTimedOperation(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        require(ModDamageFeedback.server(DamageSystem.Cause.CHEMICAL.profileId()).equals(
                new com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile(false, false, false, false)),
                "resolved chemical profile inherits the quiet defaults");
        startOperation(body, "chemical");
        var dispatched = new AtomicReference<DamageFeedbackPayload>();
        DamageFeedbackPayload.observeDispatch(dispatched::set);
        try {
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.POISON, 5f), 1f, false,
                    DamageSystem.Cause.CHEMICAL) == DamageSystem.Result.APPLIED, "chemical damage applies");
        } finally {
            DamageFeedbackPayload.observeDispatch(null);
        }
        var damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        require(damage != null && damage.getMap().equals(Map.of(DamageKeys.POISON, 5f)),
                "chemical poison damage must commit to the typed ledger");
        require(body.getHealth() == body.getMaxHealth() - 1f, "chemical damage still changes health");
        require(active(body), "quiet chemical damage must not cancel an active damage-sensitive operation");
        require(dispatched.get() != null && !dispatched.get().redFlash() && !dispatched.get().cameraShake(),
                "native hurt presentation dispatch must carry quiet tint and camera flags");
        require(body.hurtTime == 0, "server-side native hurt presentation counter is cleared after packet suppression");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void combatDamagePublishesPhysicalFeedbackAndInterrupts(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        require(ModDamageFeedback.server(DamageSystem.Cause.COMBAT.profileId()).equals(
                new com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile(true, true, true, false)),
                "resolved combat profile inherits physical defaults");
        startOperation(body, "combat");
        var dispatched = new AtomicReference<DamageFeedbackPayload>();
        DamageFeedbackPayload.observeDispatch(dispatched::set);
        try {
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 5f), 1f, false,
                    DamageSystem.Cause.COMBAT) == DamageSystem.Result.APPLIED, "combat damage applies");
        } finally {
            DamageFeedbackPayload.observeDispatch(null);
        }
        require(dispatched.get() != null && dispatched.get().redFlash() && dispatched.get().cameraShake(),
                "combat dispatch must carry physical feedback flags");
        require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(op -> op.phase() == TimedOperation.Phase.CANCELLED),
                "physical combat damage must interrupt the active operation");
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(helper.absolutePos(pos).below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(helper.getLevel());
        require(body != null, "villager created");
        BlockPos absolute = helper.absolutePos(pos);
        body.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(true);
        require(helper.getLevel().addFreshEntity(body), "villager added to test level");
        return body;
    }

    private static void startOperation(LivingEntity body, String key) {
        require(TimedInteractionSystem.start(body, null, 1, "damage-feedback", key, 15,
                true, false, () -> true, () -> TimedInteractionSystem.Completion.STOP)
                == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                "damage-sensitive operation starts");
    }

    private static boolean active(LivingEntity body) {
        return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .anyMatch(op -> op.phase() == TimedOperation.Phase.ACTIVE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
