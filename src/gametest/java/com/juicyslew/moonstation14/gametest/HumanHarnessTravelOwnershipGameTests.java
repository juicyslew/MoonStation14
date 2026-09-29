package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BarotraumaAtmospherePolicy;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HumanHarnessTravelOwnershipGameTests {
    private HumanHarnessTravelOwnershipGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void leasedPigAndVillagerKeepVanillaWalkAnimation(GameTestHelper helper) {
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 6; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        Villager ownedVillager = spawnWithoutPressureTick(helper, EntityType.VILLAGER, new BlockPos(2, 4, 2));
        var ownedPig = spawnWithoutPressureTick(helper, EntityType.PIG, new BlockPos(5, 4, 2));
        Villager vanillaVillager = spawnWithoutPressureTick(helper, EntityType.VILLAGER, new BlockPos(2, 4, 5));
        var vanillaPig = spawnWithoutPressureTick(helper, EntityType.PIG, new BlockPos(5, 4, 5));
        vanillaVillager.setNoAi(true);
        vanillaPig.setNoAi(true);
        for (var body : new net.minecraft.world.entity.Mob[]{ownedVillager, ownedPig}) {
            body.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        }
        helper.runAfterDelay(3, () -> {
            require(ownedVillager.onGround() && ownedPig.onGround(), "bodies must settle on the floor before leasing");
            GroundedHarnessLease villagerLease = GroundedHarnessLease.tryAcquire(ownedVillager).orElseThrow(
                    () -> new GameTestAssertException("enrolled marked Villager must acquire a real harness lease"));
            GroundedHarnessLease pigLease = GroundedHarnessLease.tryAcquire(ownedPig).orElseThrow(
                    () -> new GameTestAssertException("enrolled marked Pig must acquire a real harness lease"));
            helper.runAfterDelay(2, () -> {
                require(ownedVillager.walkAnimation.speed(1f) < 1e-4f
                                && ownedPig.walkAnimation.speed(1f) < 1e-4f,
                        "stationary leased bodies must have near-zero walk animation speed");
                require(vanillaVillager.walkAnimation.speed(1f) < 1e-4f
                                && vanillaPig.walkAnimation.speed(1f) < 1e-4f,
                        "stationary unowned controls must remain unanimated");

                GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
                double villagerZ = ownedVillager.getZ();
                double pigZ = ownedPig.getZ();
                var villagerStep = stepper.step(ownedVillager, 0, 1_000, false, false, 0f);
                var pigStep = stepper.step(ownedPig, 0, 1_000, false, false, 0f);
                require(villagerStep.isPresent() && pigStep.isPresent(),
                        "one grounded harness step must be accepted for each leased character");
                require(ownedVillager.getZ() > villagerZ && ownedPig.getZ() > pigZ,
                        "both harness steps must produce accepted horizontal displacement");
                require(ownedVillager.walkAnimation.speed(1f) > 0f,
                        "owned Villager must animate immediately after its accepted harness movement");
                require(ownedPig.walkAnimation.speed(1f) > 0f,
                        "owned Pig must animate immediately after its accepted harness movement");
                require(vanillaVillager.walkAnimation.speed(1f) < 1e-4f
                                && vanillaPig.walkAnimation.speed(1f) < 1e-4f,
                        "unowned stationary controls must not gain walk animation");
                helper.runAfterDelay(1, () -> {
                    require(ownedVillager.walkAnimation.speed(1f) > 0f,
                            "owned Villager walk animation must survive one natural entity tick");
                    require(ownedPig.walkAnimation.speed(1f) > 0f,
                            "owned Pig walk animation must survive one natural entity tick");
                    require(ownedVillager.walkAnimation.speed(1f) > 0f
                                    && ownedPig.walkAnimation.speed(1f) > 0f,
                            "both leased bodies must retain animation after natural ticking");
                    require(vanillaVillager.walkAnimation.speed(1f) < 1e-4f
                                    && vanillaPig.walkAnimation.speed(1f) < 1e-4f,
                            "stationary unowned controls must remain unanimated after natural ticking");
                    villagerLease.close();
                    pigLease.close();
                    helper.succeed();
                });
            });
        });
    }

    /** Keep the animation observation independent of the real, staggered pressure hazard. */
    private static <T extends Mob> T spawnWithoutPressureTick(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        // A pressure update in the first eight ticks (including the spawn tick) can make a
        // stationary mob play vanilla hurt animation. Retry before any candidate gets a tick.
        for (int attempt = 0; attempt < 80; attempt++) {
            T body = helper.spawn(type, pos);
            if (atmos.isEnabled()) {
                BlockPos eye = BlockPos.containing(body.getEyePosition());
                // Strict sample only: an unknown cell is not vacuum, and an exterior cell
                // cannot be written. Each actor has its own eye cell in this fixture.
                atmos.sample(helper.getLevel(), eye).ifPresent(mixture -> {
                    double pressure = mixture.pressureKpa(1.0); // one gas cell is 1 m^3
                    if (pressure < 30.0) {
                        // At 293.15 K the missing partial pressure corresponds to these moles
                        // in 1 m^3. No global policy or exterior inventory is modified.
                        double moles = (101.325 - pressure) / 101.325 * GasMixture.breathableAir().totalMoles();
                        atmos.addBreathableAir(helper.getLevel(), eye, moles, 293.15);
                    }
                });
            }
            int interval = CharacterIdentitySystem.resolveForHost(body).flatMap(CharacterData::barotrauma)
                    .map(policy -> BarotraumaAtmospherePolicy.CADENCE_TICKS).orElse(0);
            long untilDue = interval == 0 ? Long.MAX_VALUE
                    : Math.floorMod(-(helper.getLevel().getGameTime() + (long) body.getId()), interval);
            if (untilDue >= 8) return body;
            body.discard();
        }
        throw new GameTestAssertException("could not spawn an actor outside the pressure hazard observation window");
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void onlyMarkedVillagerLosesVanillaTravelOwnership(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        Villager owned = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 4, 2));
        Villager vanilla = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 4, 2));
        var ownedPig = helper.spawn(EntityType.PIG, new BlockPos(6, 4, 5));
        MindControlledMob ownerFlag = (MindControlledMob) owned;
        ownerFlag.moonstation14$setMovementOwned(true);
        ownedPig.setNoAi(true);
        ((MindControlledMob) (Object) ownedPig).moonstation14$setMovementOwned(true);
        require(ownerFlag.moonstation14$isMovementOwned(), "server setter must expose the synchronized marker");
        require(!((MindControlledMob) vanilla).moonstation14$isMovementOwned(),
                "new Villager marker must default to false");

        owned.setDeltaMovement(.25d, 0d, 0d);
        ownedPig.setDeltaMovement(.25d, 0d, 0d);
        vanilla.setDeltaMovement(.25d, 0d, 0d);
        double ownedX = owned.getX();
        double ownedPigX = ownedPig.getX();
        double vanillaX = vanilla.getX();
        helper.runAfterDelay(3, () -> {
            require(Math.abs(owned.getX() - ownedX) < 1e-9d,
                    "marked Villager must not receive vanilla travel movement on server ticks");
            require(Math.abs(ownedPig.getX() - ownedPigX) < 1e-9d,
                    "same generic owner marker must cancel vanilla Pig travel");
            require(vanilla.getX() > vanillaX,
                    "unmarked control Villager must retain vanilla travel movement");

            GroundedHarnessWorldStep adapter = new GroundedHarnessWorldStep();
            owned.getPersistentData().putBoolean(com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease.CONFIGURED_MARKER, true);
            double beforeAdapter = owned.getZ();
            var accepted = adapter.step(owned, 0, 1_000, false, false, 0f);
            require(accepted.isPresent(), "marked, enrolled HUMAN Villager must accept a server adapter step");
            require(owned.getZ() > beforeAdapter, "adapter step must move the owned body through real collision resolution");
            require(Math.abs(accepted.get().position().z() - owned.getZ()) < 1e-9,
                    "returned state must report the collision-accepted body position");

            Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(2, 4, 2));
            require(owned.startRiding(boat), "marked Villager must attach to a vanilla boat for passenger rejection check");
            require(owned.isPassenger(), "Villager must be a passenger before explicit step rejection check");
            Vec3 beforePassengerStep = owned.position();
            Vec3 velocityBeforePassengerStep = owned.getDeltaMovement();
            var passengerStep = adapter.step(owned, 0, 1_000, false, false, 0f);
            require(passengerStep.isEmpty(), "explicit harness step must reject a Villager that became a passenger");
            require(owned.position().equals(beforePassengerStep),
                    "rejected passenger step must not move the Villager");
            require(owned.getDeltaMovement().equals(velocityBeforePassengerStep),
                    "rejected passenger step must not change Villager velocity");
            owned.stopRiding();

            double afterAdapter = owned.getX();
            helper.runAfterDelay(3, () -> {
                require(Math.abs(owned.getX() - afterAdapter) < 1e-9d,
                        "marked body must not receive additional vanilla travel between adapter calls");
                finishOwnershipChecks(helper, owned, vanilla, ownerFlag, vanillaX);
            });
        });
    }

    private static void finishOwnershipChecks(GameTestHelper helper, Villager owned, Villager vanilla,
                                              MindControlledMob ownerFlag, double vanillaX) {
        ownerFlag.moonstation14$setMovementOwned(false);
        owned.setDeltaMovement(.25d, 0d, 0d);
        double beforeResume = owned.getX();
        owned.travel(Vec3.ZERO);
        require(owned.getX() > beforeResume, "clearing marker must restore vanilla travel immediately");
        require(vanilla.getX() > vanillaX, "ownership changes must not affect the control Villager");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
