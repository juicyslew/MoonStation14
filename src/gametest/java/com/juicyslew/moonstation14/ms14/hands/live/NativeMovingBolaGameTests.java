package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native regression for a target entering a bola's swept path during its ordinary entity tick. */
@PrefixGameTestTemplate(false)
@GameTestTags({"thrown-combat", "portable-throw"})
public final class NativeMovingBolaGameTests {
    private NativeMovingBolaGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void bolaHitsTargetMovingIntoSweep(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "MovingBola");
        var bolaRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var throwerRef = new net.minecraft.world.entity.LivingEntity[1];
        var targetRef = new Villager[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                Throwable failure = null;
                try { if (bolaRef[0] != null && !bolaRef[0].isRemoved()) bolaRef[0].discard(); } catch (Throwable t) { failure = t; }
                try { if (targetRef[0] != null && !targetRef[0].isRemoved()) targetRef[0].discard(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                try { if (throwerRef[0] != null && !throwerRef[0].isRemoved()) throwerRef[0].discard(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
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
                var thrower = human.body(); throwerRef[0] = thrower;
                if (!(thrower instanceof Mob mob)) throw new GameTestAssertException("registered character is not a mob");
                mob.setNoAi(true);
                var floor = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
                for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++)
                    level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                thrower.setPos(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
                thrower.setDeltaMovement(Vec3.ZERO);

                var target = EntityType.VILLAGER.create(level);
                if (target == null) throw new GameTestAssertException("villager creation failed");
                targetRef[0] = target;
                // Keep native living-entity travel enabled: NoAI suppresses the travel path
                // that consumes externally supplied velocity on mobs.
                target.setNoAi(false);
                target.getBrain().removeAllBehaviors();
                target.setPos(floor.getX() + 3.5, floor.getY(), floor.getZ() + 2.5);
                if (!com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(
                        target, level, com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID))
                    throw new GameTestAssertException("villager human identity enrollment failed");
                if (!level.addFreshEntity(target)) throw new GameTestAssertException("target publication failed");
                target.getNavigation().stop();
                target.setDeltaMovement(0, 0, -.2);

                var bola = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                        target.position().add(0, .8, -1),
                        new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOLA.get()));
                bolaRef[0] = bola;
                double sourceZ = target.getBoundingBox().minZ - bola.getBbWidth() * .5 - .05;
                var sourceStart = new Vec3(target.getX(), target.getY() + .8, sourceZ);
                bola.setPos(sourceStart);
                if (!level.addFreshEntity(bola)) throw new GameTestAssertException("bola publication failed");
                bola.armThrownCombat(thrower.getUUID());
                if (!bola.activeThrownCombat() || !bola.thrownByBody().filter(thrower.getUUID()::equals).isPresent())
                    throw new GameTestAssertException("published bola did not arm for the thrower: active=" + bola.activeThrownCombat()
                            + " source=" + bola.thrownByBody());
                bola.launch(new Vec3(0, 0, .75), 0f);

                var originalTargetZ = target.getZ();
                var initialTargetBounds = target.getBoundingBox();
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                    try {
                        double targetMove = originalTargetZ - target.getZ();
                        if (targetMove < .05 || targetMove > .35)
                            throw new GameTestAssertException("target did not move by ordinary velocity before bola contact: move=" + targetMove
                                    + " noAi=" + target.isNoAi() + " onGround=" + target.onGround()
                                    + " velocity=" + target.getDeltaMovement() + " position=" + target.position());
                        var current = target.getBoundingBox();
                        var oldExpanded = initialTargetBounds.inflate(bola.getBbWidth() / 2.0);
                        var currentExpanded = current.inflate(bola.getBbWidth() / 2.0);
                        if (oldExpanded.contains(sourceStart) || !currentExpanded.contains(sourceStart))
                            throw new GameTestAssertException("fixture did not establish outside-old/inside-current target geometry");
                        var attachment = bola.attachment();
                        if (attachment.isEmpty() || attachment.orElseThrow().targetEntityId() != target.getId()
                                || attachment.orElseThrow().kind() != com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.ENSNARED)
                            throw new GameTestAssertException("bola did not ensnare the moving target; attachment=" + attachment
                                    + " active=" + bola.activeThrownCombat() + " source=" + bola.position()
                                    + " velocity=" + bola.getDeltaMovement() + " targetBounds=" + current);
                        if (com.juicyslew.moonstation14.ms14.combat.throwing.EnsnaringSystem.ensnares(target).size() != 1)
                            throw new GameTestAssertException("expected exactly one ensnare on target");
                        helper.succeed();
                    } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
                });
            } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new GameTestAssertException(t.toString()); }
        });
    }
}
