package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.radiation.RadiationService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered-entity integration coverage for resolved energy, movement and persistence. */
@GameTestTags({"singularity-entity", "singularity-horizon"})
@PrefixGameTestTemplate(false)
public final class SingularityEntityGameTests {
    private SingularityEntityGameTests() { }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    @GameTest(template="empty", timeoutTicks=30, templateNamespace="moonstation14")
    public static void registeredSpawnFeedDrainMovementAndSaveLoad(GameTestHelper h) {
        FixtureCleanup cleanup = new FixtureCleanup(h);
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(3,3,3));
        var entity=ModEntities.SINGULARITY.get().create(level);
        if (entity != null) cleanup.own(entity);
        require(entity!=null,"registered singularity type creates entity");
        entity.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        require(level.addFreshEntity(entity),"registered singularity spawns");
        entity.tick();
        var profile=entity.gravityProfile();
        double afterFirstTick=profile.energyAfterSeconds(profile.initialEnergy(),1,.05);
        require(Math.abs(entity.energy()-afterFirstTick)<.001 && entity.singularityLevel()==1,"resolved initial energy and first-tick continuous drain applied");
        entity.feed(25);
        require(Math.abs(entity.energy()-(afterFirstTick+25))<.001 && entity.singularityLevel()==2,"feed updates energy and resolved threshold level");
        entity.drain(5);
        double afterDrain=afterFirstTick+20;
        require(Math.abs(entity.energy()-afterDrain)<.001
                        && entity.singularityLevel()==profile.levelForEnergy(afterDrain),
                "drain updates energy and derives level from resolved threshold");

        Vec3 before=entity.position();
        boolean walked=false;
        // Delta movement is blocks/second. Each manual tick integrates velocity * .05 seconds.
        for(int i=1;i<=100;i++) {
            Vec3 tickStart=entity.position();
            entity.tick();
            var movementProfile=entity.gravityProfile();
            double speed=entity.getDeltaMovement().length();
            double minSpeed=movementProfile.minSpeedAtLevel(entity.singularityLevel());
            double maxSpeed=movementProfile.maxSpeedAtLevel(entity.singularityLevel());
            require(speed<=maxSpeed+1.0e-6,"velocity does not exceed resolved maximum blocks/second");
            if(speed>1.0e-9) {
                // A field reflection may attenuate velocity, so only enforce the profile minimum
                // before any containment contact is possible. This fixture has no field faces.
                require(speed+1.0e-6>=minSpeed,"free-walk velocity meets resolved minimum blocks/second");
                walked=true;
            }
            require(entity.position().distanceTo(tickStart)<=maxSpeed*.05+1.0e-6,
                    "per-tick displacement is bounded by resolved blocks/second times 0.05 seconds");
            if(walked) break;
        }
        require(walked && entity.position().distanceTo(before)>0,
                "walk begins within the resolved 40-100 tick interval");
        CompoundTag saved=entity.saveWithoutId(new CompoundTag());
        var restored=ModEntities.SINGULARITY.get().create(level);
        if (restored != null) cleanup.own(restored);
        require(restored!=null,"registered singularity reload instance created");
        restored.load(saved);
        require(restored.energy()==entity.energy() && restored.singularityLevel()==entity.singularityLevel()
                        && restored.identity().equals(entity.identity()) && restored.profileId().equals(entity.profileId()),
                "energy, level, UUID and gameplay profile survive save/load");
        restored.setPos(pos.getX()+2,pos.getY()+.5,pos.getZ()+.5);
        entity.drain(1000);
        require(entity.isRemoved(),"zero energy removes singularity");
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h,1,()->{
            require(entity.isRemoved(),"drained entity unloads before restoring saved UUID");
            require(level.addFreshEntity(restored),"restored entity registers with preserved UUID after original unload");
            h.succeed();
        });
    }

    /** Owns only singularities created by this fixture, including instances from failed assertions. */
    private static final class FixtureCleanup {
        private final GameTestHelper helper;
        private final java.util.ArrayList<SingularityEntity> entities = new java.util.ArrayList<>();

        private FixtureCleanup(GameTestHelper helper) {
            this.helper = helper;
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
            });
        }

        private void own(SingularityEntity entity) { entities.add(entity); }

        private void cleanup() {
            for (SingularityEntity entity : entities) {
                RadiationService.INSTANCE.remove(helper.getLevel(), entity.identity());
                entity.discard();
            }
        }
    }

    @GameTest(template="empty", timeoutTicks=10, templateNamespace="moonstation14")
    public static void resolvedProfileRadiusAndGravityScaling(GameTestHelper h) {
        var prototype=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.catalog(h.getLevel())
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","singularity"));
        require(prototype!=null,"resolved gameplay prototype exists");
        require(prototype.eventHorizonRadiusAtLevel(1)==.5 && prototype.gravityRangeAtLevel(1)==4
                        && prototype.gravityRangeAtLevel(5)==12 && prototype.gravityAccelerationAtLevel(5)==50,
                "event horizon and gravity are distinct level-derived radii/acceleration");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20, templateNamespace="moonstation14")
    public static void liveHorizonKillsPhysicalLivingBodyAndFeedsOnce(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 3, 3));
        var singularity = ModEntities.SINGULARITY.get().create(level);
        var body = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        var outside = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        require(singularity != null && body != null && outside != null, "registered singularity and living bodies created");
        singularity.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        body.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        outside.setPos(pos.getX() + 3.5, pos.getY() + .5, pos.getZ() + .5);
        require(level.addFreshEntity(singularity) && level.addFreshEntity(body) && level.addFreshEntity(outside), "runtime entities admitted");
        singularity.tick();
        require(body.isDeadOrDying() || body.isRemoved(), "physical living body touching horizon receives native death");
        require(outside.isAlive(), "living body outside resolved horizon survives");
        double fed = singularity.gravityProfile().initialEnergy() - .05 + singularity.gravityProfile().eventHorizon().entityEnergy();
        require(Math.abs(singularity.energy() - fed) < .01, "confirmed death adds one configured energy after continuous drain");
        singularity.tick();
        require(Math.abs(singularity.energy() - (fed - .05)) < .01, "retained dead corpse cannot feed a second time");
        body.discard();
        outside.discard();
        singularity.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20, templateNamespace="moonstation14")
    public static void horizonUsesConfirmedCharacterBodyDeathHook(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 3, 3));
        var singularity = ModEntities.SINGULARITY.get().create(level);
        var body = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration
                .getEntityType().create(level);
        require(singularity != null && body != null, "registered singularity and character harness created");
        singularity.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        body.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        require(level.addFreshEntity(singularity) && level.addFreshEntity(body), "entities join authoritative server level");
        require(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder
                .bind(body, java.util.UUID.randomUUID(), "horizon-body", java.util.UUID.randomUUID(),
                        com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID),
                "fixture body receives the same exact human binding used by lifecycle death tests");
        singularity.tick();
        require(body.hasConfirmedDeath() && body.isDeadOrDying(),
                "horizon routes character death through accepted native die callback");
        require(!body.isRemoved(), "owned character corpse is retained rather than bypassing lifecycle ownership");
        body.discard();
        singularity.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20, templateNamespace="moonstation14")
    public static void canceledNativeDeathDoesNotFeedSingularity(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 3, 3));
        var singularity = ModEntities.SINGULARITY.get().create(level);
        var body = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        require(singularity != null && body != null, "runtime entities created");
        singularity.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        body.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        require(level.addFreshEntity(singularity) && level.addFreshEntity(body), "entities join level");
        var cancel = new CancelTargetDeath(body);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(cancel);
        try { singularity.tick(); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
        require(cancel.canceled, "fixture canceled the target's native death event");
        require(!(body instanceof com.juicyslew.moonstation14.mixin.LivingEntityDeathStateAccessor deathState
                        && deathState.ms14$isDead()),
                "canceled death never sets native accepted-death state");
        double expected = singularity.gravityProfile().initialEnergy() - .05;
        require(Math.abs(singularity.energy() - expected) < .01, "unaccepted death gives no horizon energy");
        body.discard(); singularity.discard();
        h.succeed();
    }

    private static final class CancelTargetDeath {
        private final net.minecraft.world.entity.LivingEntity target;
        private boolean canceled;
        private CancelTargetDeath(net.minecraft.world.entity.LivingEntity target) { this.target = target; }
        @net.neoforged.bus.api.SubscribeEvent
        public void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
            if (event.getEntity() == target) { event.setCanceled(true); canceled = true; }
        }
    }
}
