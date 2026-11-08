package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression: a thrower is protected during spawn overlap, but can be hit on a later return pass. */
@PrefixGameTestTemplate(false)
@GameTestTags({"thrown-combat", "thrown-embedding"})
public final class NativeReturningThrowerSelfHitGameTests {
    private NativeReturningThrowerSelfHitGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void returningThrowCanHitItsThrowerAfterClearance(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "ReturnSelfHit");
        var itemRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                Throwable failure = null;
                try { if (itemRef[0] != null && !itemRef[0].isRemoved()) itemRef[0].discard(); } catch (Throwable t) { failure = t; }
                try { if (bodyRef[0] != null && !bodyRef[0].isRemoved()) bodyRef[0].discard(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                try { fixture.close(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                if (failure != null) { if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
        });
        RegisteredPlayerReadyDriver.awaitCharacterReady(helper, fixture, 40, human -> {
            try {
                var body = human.body(); bodyRef[0] = body;
                if (!(body instanceof net.minecraft.world.entity.Mob mob)) throw new GameTestAssertException("registered character is not a mob");
                mob.setNoAi(true);
                var floor = helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4));
                for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                body.setPos(floor.getX() + 2.5, floor.getY(), floor.getZ() + 2.5);
                body.setDeltaMovement(Vec3.ZERO); body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var item = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, 1, 0),
                        new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.THROWING_KNIFE.get()));
                itemRef[0] = item;
                if (!level.addFreshEntity(item)) throw new GameTestAssertException("knife publication failed");
                item.armThrownCombat(body.getUUID());
                var health = body.getHealth();
                item.setPos(body.position().add(0, 1, 0)); item.setDeltaMovement(Vec3.ZERO);
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                    try {
                        if (Math.abs(body.getHealth() - health) > .001f || typed(body) != 0f)
                            throw new GameTestAssertException("spawn-overlapping throw damaged its thrower");
                        // Clearance occurs outside the local candidate query, then the next phase is a real swept return.
                        item.setPos(body.position().add(0, 1, 2.5)); item.setDeltaMovement(Vec3.ZERO);
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                            try {
                                 item.setPos(body.position().add(0, 1, 1.2));
                                 item.setDeltaMovement(new Vec3(0, 0, -1.5));
                                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                                    if (typed(body) <= 0f || body.getHealth() >= health)
                                        throw new GameTestAssertException("returning knife did not apply typed piercing/slash self-damage");
                                    if (item.attachment().isEmpty() || item.attachment().orElseThrow().targetEntityId() != body.getId())
                                        throw new GameTestAssertException("returning knife did not embed in its thrower");
                                    helper.succeed();
                                });
                            } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
                        });
                    } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
                });
            } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
        });
    }

    private static float typed(net.minecraft.world.entity.LivingEntity body) {
        var damage = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        return damage == null ? 0f : damage.getMap().getOrDefault(
                com.juicyslew.moonstation14.ms14.damage.DamageKeys.SLASH, 0f)
                + damage.getMap().getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.PIERCING, 0f);
    }
}
