package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.movement.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcFlightSteeringTest {
    private static final MovementCollisionResolver OPEN = (p, d, g) ->
            new MovementCollisionResolver.CollisionResult(d, g);

    @Test void legCruisePreferenceVariesAroundResolvedPrototypeHeight() {
        double base = 4;
        assertEquals(3, NpcBehaviorSystem.cruisePreference(base, 0));
        assertEquals(5, NpcBehaviorSystem.cruisePreference(base, 1));
        assertEquals(4, NpcBehaviorSystem.cruisePreference(base, .5));
        assertEquals(3, NpcBehaviorSystem.cruisePreference(base, -1));
        assertEquals(5, NpcBehaviorSystem.cruisePreference(base, 2));
    }

    @Test void idleProjectionIncludesMaximumRandomizedCruisePreference() {
        assertEquals(5.25, NpcBehaviorSystem.cruiseProjectionDrop(4));
        assertEquals(7.75, NpcBehaviorSystem.cruiseProjectionDrop(6));
        assertTrue(NpcBehaviorSystem.cruiseProjectionDrop(4) >= NpcBehaviorSystem.cruisePreference(4, 1) + .25);
    }

    @Test void idleArrivalHoversThroughWaitAndKeepsCruiseForNextAirLeg() {
        var brain = new NpcBehaviorSystem.Brain();
        brain.idlePoint = new Vec3(4, 2, 4);
        brain.activeTask = com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.Task.IDLE;
        brain.idleWait = 30;
        brain.cruiseY = 7.25;
        NpcBehaviorSystem.completeIdleLeg(brain, 100);
        assertNull(brain.idlePoint);
        assertEquals(130, brain.idleUntil);
        assertEquals(7.25, brain.cruiseY);
        // Planning the next reachable idle point leaves the same cruise altitude in force.
        brain.idlePoint = new Vec3(9, 2, 9);
        brain.activeTask = com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.Task.IDLE;
        assertEquals(7.25, brain.cruiseY);
    }

    @Test void productionCommandIsFiniteBoundedAndQuantized() {
        for (double[] heading : new double[][]{{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) {
            var command = NpcBehaviorSystem.flightCommand(new Vec3(heading[0], 0, heading[1]), 1000);
            assertTrue(Float.isFinite(command.yaw()));
            assertTrue(command.yaw() >= -180f && command.yaw() <= 180f);
            assertTrue(command.forward() >= 0 && command.forward() <= 1000);
            assertEquals(0, command.forward() % 1);
        }
        assertEquals(new NpcBehaviorSystem.FlightCommand(0f, 0),
                NpcBehaviorSystem.flightCommand(Vec3.ZERO, 1000));
        assertEquals(new NpcBehaviorSystem.FlightCommand(0f, 0),
                NpcBehaviorSystem.flightCommand(new Vec3(Double.NaN, 0, 1), 1000));
    }

    @Test void airborneVelocityWishMaintainsCruiseAndBrakesNearGoalAcrossHeadings() {
        var policy = CharacterMovementPolicy.fromCharacterData(readBat());
        var motor = new GroundedHarnessMotor(policy);
        var env = new CharacterMovementEnvironment(com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS, 32, 8.4, .98,
                MovementVector.ZERO, 0, 0, OPEN);
        for (double[] goal : new double[][]{{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) {
          for (double impulse : new double[]{0d, .15d, .38d}) {
            MovementVector pos = MovementVector.ZERO;
            MovementVector velocity = new MovementVector(-goal[1] * impulse * 20, 0, goal[0] * impulse * 20);
            for (int tick=0; tick<1400; tick++) {
                double dx=goal[0]-pos.x(), dz=goal[1]-pos.z();
                Vec3 wish=NpcBehaviorSystem.flightHorizontalWish(dx,dz,
                        policy.sprintSpeedPerSecond(),1000);
                var command=NpcBehaviorSystem.flightCommand(wish, GroundedHarnessMotor.INPUT_QUANTIZATION);
                var state=new CharacterMovementState(pos, velocity, false);
                var next=motor.tick(state,0,command.forward(),false,true,command.yaw(),env,false,false,0,true);
                pos=next.position(); velocity=next.velocity();
                if (Math.hypot(dx,dz)<.08 && Math.hypot(velocity.x(),velocity.z())<.12) break;
                if (tick==1399) fail("did not settle for goal="+goal[0]+","+goal[1]+" impulse="+impulse+" pos="+pos+" velocity="+velocity);
            }
          }
        }
    }

    @Test void flightWishKeepsCruiseInputAndReturnsNeutralAtGoal() {
        var cruise=NpcBehaviorSystem.flightHorizontalWish(20,0,4,1000);
        assertEquals(1d,cruise.x());
        assertEquals(0d,cruise.z());
        var near=NpcBehaviorSystem.flightHorizontalWish(.1,0,4,1000);
        assertEquals(.05d,near.x(),1e-9);
        assertEquals(Vec3.ZERO,NpcBehaviorSystem.flightHorizontalWish(0,0,4,1000));
        assertEquals(Vec3.ZERO,NpcBehaviorSystem.flightHorizontalWish(Double.NaN,0,4,1000));
        assertEquals(1d,NpcBehaviorSystem.flightHorizontalWish(.6,0,6,1000,true).x());
        assertEquals(1d,NpcBehaviorSystem.flightHorizontalWish(.6,0,9,1000,true).x());
        assertEquals(.05d,NpcBehaviorSystem.flightHorizontalWish(.1,0,4,1000,false).x(),1e-9);
        assertEquals(Vec3.ZERO,NpcBehaviorSystem.flightHorizontalWish(0,0,4,1000,true));
    }

    private static CharacterData readBat() {
        try {
            var root=java.nio.file.Path.of(System.getProperty("moonstation14.projectDir"),
                    "src/main/resources/data/moonstation14/moonstation14/character");
            var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
            try(var paths=java.nio.file.Files.walk(root)) {
                for(var file:paths.filter(java.nio.file.Files::isRegularFile).filter(p->p.toString().endsWith(".json")).toList()) {
                    var name=root.relativize(file).toString().replace('\\','/');
                    raw.put(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14",name.substring(0,name.length()-5)),
                            JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject());
                }
            }
            var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
            manager.register(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE);
            manager.reload(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE,raw);
            return manager.snapshot(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE)
                    .get(net.minecraft.resources.ResourceLocation.parse("moonstation14:bat"));
        } catch(java.io.IOException e) { throw new AssertionError(e); }
    }
}
