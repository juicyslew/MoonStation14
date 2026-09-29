package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.slip.SlipEvent;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroundedHarnessSlipGameTests {
    private GroundedHarnessSlipGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void ownedHarnessSlipLaunchFollowsMotorVelocity(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 6; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos source = new BlockPos(4, 1, 2);
        helper.setBlock(source, ModBlocks.PUDDLE.get().defaultBlockState());
        fillSpaceLube(helper, source);

        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 2));
        body.setNoAi(true);
        body.setPos(helper.absolutePos(source).getX() - .2d, helper.absolutePos(source).getY(),
                helper.absolutePos(source).getZ() + .5d);
        body.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
        body.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        var lease = GroundedHarnessLease.tryAcquire(body).orElseThrow();

        BlockPos vanillaSource = new BlockPos(3, 1, 4);
        helper.setBlock(vanillaSource, ModBlocks.PUDDLE.get().defaultBlockState());
        fillSpaceLube(helper, vanillaSource);
        Villager unmarked = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 4));
        unmarked.setNoAi(true);
        require(!unmarked.hasData(ModDataAttachments.STATUS_EFFECT.get()), "unmarked actor starts unstunned");

        AtomicInteger ownedEvents = new AtomicInteger();
        AtomicInteger vanillaEvents = new AtomicInteger();
        AtomicReference<SlipEvent> acceptedEvent = new AtomicReference<>();
        java.util.function.Consumer<SlipEvent> listener = event -> {
            if (event.target() == body) {
                ownedEvents.incrementAndGet();
                acceptedEvent.set(event);
            } else if (event.target() == unmarked) vanillaEvents.incrementAndGet();
        };
        require(SlipSystem.addListener(listener), "focused slip observer registration");
        helper.runAfterDelay(29, () -> {
            SlipSystem.removeListener(listener);
            lease.close();
        });

        helper.runAfterDelay(2, () -> {
            try {
                require(body.onGround(), "owned human Villager must be grounded");
                body.setDeltaMovement(5.5d / 20d, 0d, 0d);
                double beforeX = body.position().x;
                var result = new GroundedHarnessWorldStep().step(body, 0, 1000, false, true, -90f);
                require(result.isPresent(), "owned grounded harness step must be accepted");
                require(ownedEvents.get() == 1 && acceptedEvent.get() != null,
                        "accepted endpoint contact must emit exactly one owned slip; count=" + ownedEvents.get()
                                + ", start=" + beforeX + ", end=" + body.position() + ", velocity="
                                + body.getDeltaMovement() + ", grounded=" + body.onGround());
                Vec3 baseline = new Vec3(result.get().velocity().x() / 20d,
                        result.get().velocity().y() / 20d, result.get().velocity().z() / 20d);
                Vec3 actual = body.getDeltaMovement();
                double multiplier = acceptedEvent.get().solution().launchVelocityMultiplier();
                require(multiplier > 1d, "20u Space Lube profile must boost launch velocity");
                require(Math.abs(actual.x - baseline.x * multiplier) < 1.0e-8d
                                && Math.abs(actual.z - baseline.z * multiplier) < 1.0e-8d,
                        "post-step velocity must reflect Space Lube multiplier rather than motor overwrite; baseline="
                                + baseline + ", actual=" + actual + ", multiplier=" + multiplier);
                require(body.position().x > beforeX, "motor step must physically advance into source");

                // Ordinary, unowned Mob admission remains through entityInside exactly once.
                // Cross into the pre-filled source from a dry tile. Teleporting onto
                // the puddle immediately before a short move can miss vanilla's
                // swept block-inside callback altogether.
                unmarked.setDeltaMovement(.3d, 0d, 0d);
                unmarked.move(MoverType.SELF, new Vec3(1d, 0d, 0d));
                require(vanillaEvents.get() == 1 && CharacterControlSystem.isStunned(unmarked),
                        "unmarked Villager must retain ordinary inline puddle admission; events=" + vanillaEvents.get()
                                + ", stunned=" + CharacterControlSystem.isStunned(unmarked)
                                + ", position=" + unmarked.position() + ", grounded=" + unmarked.onGround()
                                + ", puddle=" + helper.getLevel().getBlockState(helper.absolutePos(vanillaSource)));
                SlipSystem.removeListener(listener);
                lease.close();
                helper.succeed();
            } catch (RuntimeException | Error exception) {
                SlipSystem.removeListener(listener);
                lease.close();
                throw exception;
            }
        });
    }

    private static void fillSpaceLube(GameTestHelper helper, BlockPos position) {
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
