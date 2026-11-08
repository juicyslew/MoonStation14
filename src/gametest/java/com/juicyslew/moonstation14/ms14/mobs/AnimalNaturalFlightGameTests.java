package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end observations of naturally scheduled NPC flight. */
@PrefixGameTestTemplate(false)
@GameTestTags({"animals_natural_flight", "animals_npc_flight", "animals", "movement"})
public final class AnimalNaturalFlightGameTests {
    private AnimalNaturalFlightGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 650)
    public static void normallyJoinedBatLeavesOpenTopWallEnclosure(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            if (Math.abs(x) == 2 || Math.abs(z) == 2)
                for (int y = 0; y < 2; y++) level.setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        var bat = createBat(level, origin);
        require(bat != null, "normally joined resolved bat");
        observeEscape(helper, bat, origin, 0);
    }

    private static void observeEscape(GameTestHelper h, com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos o, int t) {
        h.runAfterDelay(1, () -> {
            boolean aboveWall = bat.getBoundingBox().minY > o.getY() + 2;
            boolean outside = Math.abs(bat.getX() - (o.getX() + .5)) > 2.5 || Math.abs(bat.getZ() - (o.getZ() + .5)) > 2.5;
            if (aboveWall && outside) { h.succeed(); return; }
            if (t >= 600) throw new GameTestAssertException(context(bat, "bat did not fly above and outside enclosure"));
            observeEscape(h, bat, o, t + 1);
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1450)
    public static void naturallyCyclesWalkingCruisingDescentAndLanding(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var bat = createBat(level, origin);
        require(bat != null, "normally joined resolved bat");
        var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow();
        var flight = resolved.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        var movement = resolved.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).orElseThrow();
        bat.getRandom().setSeed(140014L);
        // Nominal 400 airborne and 160 grounded ticks, each varied by .75–1.25; include the
        // complete maximum grounded wait plus maximum airborne interval and scheduling slack.
        int bound = (int) Math.ceil(flight.npcAirborneTicks() * 1.25 + flight.npcGroundedTicks() * 1.25 + 300);
        double verticalLimit = flight.npcVerticalSpeedMultiplier() * com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousSpeedPerSecond(movement) / 20d + .03;
        var damage = java.util.Map.copyOf(com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(bat));
        observeCycle(helper, bat, origin, bound, verticalLimit, bat.getHealth(), damage, 0, bat.getX(), bat.getZ(), 0, false, 0, null, 0, false, false, false, false, 0, 0);
    }

    private static void observeCycle(GameTestHelper h, com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos o,
            int bound, double verticalLimit, float health, java.util.Map<String, Float> damage, int tick,
            double px, double pz, double travel, boolean walked, int cruiseSamples, net.minecraft.world.phys.Vec3 lastGoal, int goals,
            boolean descended, boolean landingSeen, boolean landed, boolean groundedAfter, int groundedTicks, int touchdowns) {
        h.runAfterDelay(1, () -> {
            var state = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat);
            var snap = NpcBehaviorSystem.debugSnapshot(bat);
            double step = Math.hypot(bat.getX()-px, bat.getZ()-pz);
            double nextTravel = travel;
            boolean ground = bat.onGround() && state == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED;
            boolean nextWalked = walked || (ground && step > .015);
            boolean flying = state == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.FLYING;
            boolean nextDescended = descended || (cruiseSamples >= 2 && flying && bat.getDeltaMovement().y < -.01);
            boolean nextLandingSeen = landingSeen || (nextDescended && state == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.LANDING);
            if (!ground && flying) nextTravel += step;
            var goal = flying && snap != null ? snap.idlePoint() : null;
            int nextGoals = goal != null && (lastGoal == null || goal.distanceToSqr(lastGoal) > .01) ? goals + 1 : goals;
            var nextLastGoal = goal != null ? goal : lastGoal;
            int nextCruise = flying && goal != null && nextTravel >= 3 ? cruiseSamples + 1 : cruiseSamples;
            boolean nextGroundedAfter = groundedAfter || (nextLandingSeen && ground);
            int grounded = nextGroundedAfter && ground ? groundedTicks + 1 : 0;
            int nextTouchdowns = touchdowns + (nextLandingSeen && ground && groundedTicks == 0 ? 1 : 0);
            boolean landedNow = landed || (nextLandingSeen && nextDescended && grounded >= 8);
            require(Math.abs(bat.getDeltaMovement().y) <= verticalLimit, "autonomous vertical speed exceeds resolved flight policy");
            require(bat.getHealth() == health && com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(bat).equals(damage), "powered landing preserves health and typed damage ledger");
            if (nextWalked && nextCruise >= 2 && nextGoals >= 2 && nextDescended && landedNow) { h.succeed(); return; }
            if (nextTouchdowns > 1) throw new GameTestAssertException(context(bat, "repeated touchdown tick="+tick+"/"+bound+" goals="+nextGoals+" touchdowns="+nextTouchdowns));
            if (tick >= bound) throw new GameTestAssertException(context(bat, "cycle incomplete tick="+tick+"/"+bound+" pos="+bat.blockPosition()+" state="+state+" goals="+nextGoals+" cruise="+nextCruise+" walk="+nextWalked+" descend="+nextDescended+" landing="+nextLandingSeen+" grounded="+grounded+" touchdowns="+nextTouchdowns));
            observeCycle(h, bat, o, bound, verticalLimit, health, damage, tick+1, bat.getX(), bat.getZ(), nextTravel, nextWalked, nextCruise, nextLastGoal, nextGoals, nextDescended, nextLandingSeen, landedNow, nextGroundedAfter, grounded, nextTouchdowns);
        });
    }

    private static com.juicyslew.moonstation14.entities.AnimalMob createBat(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        var type = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("bat");
        var bat = type == null ? null : type.get().create(level);
        if (bat == null) return null;
        bat.setPos(origin.getX()+.5, origin.getY(), origin.getZ()+.5);
        if (!level.addFreshEntity(bat)) { bat.discard(); return null; }
        var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElse(null);
        if (resolved == null || resolved.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).isEmpty()
                || resolved.component(com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.class).isEmpty()) { bat.discard(); return null; }
        return bat;
    }
    private static String context(com.juicyslew.moonstation14.entities.AnimalMob bat, String reason) {
        var s = NpcBehaviorSystem.debugSnapshot(bat);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Natural flight GameTest failure: {}; full snapshot={}", reason, s);
        return reason+"; pos="+bat.blockPosition()+" flight="+com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
