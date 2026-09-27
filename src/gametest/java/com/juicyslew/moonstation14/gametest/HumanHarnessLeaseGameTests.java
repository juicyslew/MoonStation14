package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Optional;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HumanHarnessLeaseGameTests {
    private HumanHarnessLeaseGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void leasesOnlyExplicitlyConfiguredHumanVillagers(GameTestHelper helper) {
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 6; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 4, 2));
        Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 4, 2));
        Villager unmarked = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 4, 4));
        first.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        second.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        // Explicit fixture configuration is not proof of authenticated caller authority.

        first.setDeltaMovement(.25d, 0d, 0d);
        second.setDeltaMovement(.25d, 0d, 0d);
        unmarked.setDeltaMovement(.25d, 0d, 0d);
        double firstX = first.getX();
        Vec3 unmarkedStart = unmarked.position();
        double unmarkedX = unmarkedStart.x;
        Optional<GroundedHarnessLease> acquired = GroundedHarnessLease.tryAcquire(first);
        require(acquired.isPresent(), "explicitly marked, bound HUMAN Villager must acquire a lease");
        GroundedHarnessLease lease = acquired.orElseThrow();
        require(first.isNoAi(), "lease must pause AI when prior noAI was false");
        require(((MindControlledMob) first).moonstation14$isMovementOwned(),
                "lease must set the synchronized movement ownership marker");
        require(GroundedHarnessLease.tryAcquire(first).isEmpty(), "an already owned body cannot be acquired again");
        require(GroundedHarnessLease.tryAcquire(first).isEmpty(), "a second acquisition cannot take an owned body");
        require(GroundedHarnessLease.tryAcquire(unmarked).isEmpty(), "unmarked Villager cannot acquire a lease");

        helper.runAfterDelay(3, () -> {
            require(Math.abs(first.getX() - firstX) < 1e-9d,
                    "owned body must not receive vanilla travel while leased");
            // Invoke vanilla travel explicitly as the control; the empty template may not settle the
            // spawned control mob onto its floor before the scheduled tick window.
            unmarked.travel(Vec3.ZERO);
            require(unmarked.getX() > unmarkedX,
                    "unmarked Villager must retain ordinary vanilla movement; start=" + unmarkedStart
                            + ", end=" + unmarked.position() + ", deltaMovement=" + unmarked.getDeltaMovement()
                            + ", isNoAi=" + unmarked.isNoAi() + ", isImmobile=" + immobileState(unmarked)
                            + ", ownsHarnessMovement="
                            + ((MindControlledMob) unmarked).moonstation14$isMovementOwned()
                            + ", onGround=" + unmarked.onGround() + ", boundingBox=" + unmarked.getBoundingBox()
                            + ", noCollision=" + unmarked.level().noCollision(unmarked, unmarked.getBoundingBox()));
            lease.close();
            lease.close();
            require(!first.isNoAi(), "close must restore prior noAI=false");
            require(!((MindControlledMob) first).moonstation14$isMovementOwned(),
                    "close must clear the synchronized ownership marker");
            first.setDeltaMovement(.25d, 0d, 0d);
            double resumedX = first.getX();
            helper.runAfterDelay(2, () -> {
                require(first.getX() > resumedX, "vanilla movement must resume after lease close");

                second.setNoAi(true);
                Optional<GroundedHarnessLease> noAiLease = GroundedHarnessLease.tryAcquire(second);
                require(noAiLease.isPresent(), "second explicitly marked HUMAN candidate can acquire after release");
                noAiLease.orElseThrow().close();
                require(second.isNoAi(), "close must preserve prior noAI=true");
                require(!((MindControlledMob) second).moonstation14$isMovementOwned(),
                        "second lease close must clear its ownership marker");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void genericLeaseAcceptsPigAndVillagerButNotUnmarkedCow(GameTestHelper helper) {
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 6; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        Mob villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 4, 2));
        Mob pig = helper.spawn(EntityType.PIG, new BlockPos(5, 4, 2));
        Mob cow = helper.spawn(EntityType.COW, new BlockPos(3, 4, 4));
        require(villager instanceof MindControlledMob && pig instanceof MindControlledMob
                        && cow instanceof MindControlledMob,
                "all mobs expose the same synced owner marker interface");
        require(!((MindControlledMob) pig).moonstation14$isMovementOwned(), "pig owner marker defaults false");
        require(!((MindControlledMob) cow).moonstation14$isMovementOwned(), "cow owner marker defaults false");
        require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolve(pig).isPresent(),
                "real Pig must be automatically enrolled in its distinct character profile");
        require(!com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolve(pig).orElseThrow()
                        .equals(com.juicyslew.moonstation14.ms14.character.ModCharacters.require(helper.getLevel(),
                                com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID)),
                "Pig profile must differ from HUMAN profile");
        for (Mob mob : new Mob[]{villager, pig}) {
            mob.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        }
        require(GroundedHarnessLease.tryAcquire(cow).isEmpty(), "unmarked Cow must remain inert");
        for (Mob mob : new Mob[]{villager, pig}) {
            boolean priorNoAi = mob.isNoAi();
            var lease = GroundedHarnessLease.tryAcquire(mob).orElseThrow();
            require(mob.isNoAi() && ((MindControlledMob) mob).moonstation14$isMovementOwned(),
                    "same lease implementation must pause and mark each grounded mob");
            require(GroundedHarnessLease.tryAcquire(mob).isEmpty(), "duplicate ownership must be rejected");
            lease.close();
            lease.close();
            require(mob.isNoAi() == priorNoAi && !((MindControlledMob) mob).moonstation14$isMovementOwned(),
                    "lease close must be idempotent, restore prior noAI, and clear the shared marker");
        }
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static String immobileState(LivingEntity entity) {
        try {
            Method method = LivingEntity.class.getDeclaredMethod("isImmobile");
            method.setAccessible(true);
            return String.valueOf(method.invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "unavailable (" + exception + ")";
        }
    }
}
