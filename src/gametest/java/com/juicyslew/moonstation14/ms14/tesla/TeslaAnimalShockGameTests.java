package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;

@GameTestTags({"tesla", "tesla-animal-shock"})
@PrefixGameTestTemplate(false)
public final class TeslaAnimalShockGameTests {
    private TeslaAnimalShockGameTests() { }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_animal_shock",timeoutTicks=400)
    public static void pigReceivesTypedShockFromResolvedMiniLightning(GameTestHelper h) {
        directBolt(h, net.minecraft.world.entity.EntityType.PIG.create((net.minecraft.server.level.ServerLevel)h.getLevel()));
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_animal_shock",timeoutTicks=400)
    public static void mouseReceivesTypedShockFromResolvedMiniLightning(GameTestHelper h) {
        directBolt(h, ModEntities.MOUSE.get().create((net.minecraft.server.level.ServerLevel)h.getLevel()));
    }

    private static void directBolt(GameTestHelper h, LivingEntity animal) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos o=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new AABB(o.getX()-1,o.getY(),o.getZ()-1,o.getX()+8,o.getY()+8,o.getZ()+8));
        check(animal!=null,"registered animal entity constructs");
        if (animal instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        animal.setNoGravity(true); animal.moveTo(o.getX()+2.5,o.getY()+.5,o.getZ()+.5);
        GroundedHarnessLease[] movementLease={null};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){if(movementLease[0]!=null)movementLease[0].close();if(!animal.isRemoved())animal.discard();lease.close();}public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            check(level.addFreshEntity(animal),"registered animal enters live level after fixture readiness");
            if(animal instanceof com.juicyslew.moonstation14.entities.MouseMob mouse) movementLease[0]=GroundedHarnessLease.tryAcquireRoleTarget(mouse).orElseThrow(()->new GameTestAssertException("mouse role-target movement lease unavailable"));
            var catalog=com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(level);
            var resolved=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(animal,catalog);
            check(resolved.isPresent(),"animal resolves through live character catalog");
            check(resolved.get().component(com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent.class).isPresent(),"resolved inherited composition has LightningTarget");
            check(resolved.get().component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class).isEmpty(),"animal resolved composition lacks optional Stunnable capability");
            float before=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(animal).getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID);
            check(profile!=null,"resolved mini profile exists");
            var source=new Vec3(o.getX()+.5,o.getY()+.5,o.getZ()+.5);
            int fired=TeslaLightningSystem.shoot(level,source,profile.shooter(),profile.lightning(),new java.util.Random(17));
            float after=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(animal).getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            check(fired>0&&Math.abs(after-before-7f)<.01,"resolved mini bolt target discovery applies seven typed shock damage; fired="+fired+" shock="+before+"->"+after+" pos="+animal.position()+" box="+animal.getBoundingBox());
            animal.discard(); h.succeed();
        },o,o.offset(7,7,7));
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_animal_shock",timeoutTicks=400)
    public static void registeredMiniNaturalShotShocksPig(GameTestHelper h) {
        naturalShot(h, false);
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_animal_shock",timeoutTicks=400)
    public static void registeredMiniNaturalShotShocksMouse(GameTestHelper h) {
        naturalShot(h, true);
    }

    private static void naturalShot(GameTestHelper h, boolean mouse) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel(); BlockPos o=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new AABB(o.getX()-1,o.getY(),o.getZ()-1,o.getX()+8,o.getY()+8,o.getZ()+8));
        var animal=mouse?ModEntities.MOUSE.get().create(level):net.minecraft.world.entity.EntityType.PIG.create(level); var ball=ModEntities.TESLA_ENERGY_BALL.get().create(level);
        check(animal!=null&&ball!=null&&ball.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"registered pig and resolved mini construct");
        if(animal instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);animal.setNoGravity(true);animal.moveTo(o.getX()+2.5,o.getY()+.5,o.getZ()+.5);
        ball.setNoGravity(true);ball.moveTo(o.getX()+.5,o.getY()+.5,o.getZ()+.5);
        GroundedHarnessLease[] movementLease={null};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){if(movementLease[0]!=null)movementLease[0].close();if(!animal.isRemoved())animal.discard();if(!ball.isRemoved())ball.discard();lease.close();}public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            check(level.addFreshEntity(animal)&&level.addFreshEntity(ball),"pig and mini enter entity ticking after fixture readiness");
            if(animal instanceof com.juicyslew.moonstation14.entities.MouseMob mouseBody) movementLease[0]=GroundedHarnessLease.tryAcquireRoleTarget(mouseBody).orElseThrow(()->new GameTestAssertException("mouse role-target movement lease unavailable"));
            var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID);
            check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new AABB(ball.position(),ball.position()).inflate(25)).stream().noneMatch(other->other!=ball&&!other.isMini()),"isolated mini has no nearby main source");
            var resolved=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(animal,com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(level));
            check(resolved.isPresent()&&resolved.get().component(com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent.class).isPresent(),"natural-shot animal resolves LightningTarget composition");
            check(resolved.get().component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class).isEmpty(),"natural-shot animal lacks Stunnable");
            float before=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(animal).getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            int wait=(int)Math.ceil(profile.shooter().maximumIntervalSeconds()*20);
            h.runAfterDelay(wait+3,()->{float after=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(animal).getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);check(ball.lightningBoltsEmitted()>0&&Math.abs(after-before-7)<.01,"authored mini first shot discovers "+(mouse?"mouse":"pig")+" and records seven shock damage; bolts="+ball.lightningBoltsEmitted()+" shock="+before+"->"+after+" pos="+animal.position()+" box="+animal.getBoundingBox());animal.discard();ball.discard();h.succeed();});
        },o,o.offset(7,7,7));
    }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_animal_shock",timeoutTicks=400)
    public static void delayedBeamEntryShocksPig(GameTestHelper h) { beamEntry(h, false); }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="tesla_animal_shock",timeoutTicks=400)
    public static void delayedBeamEntryShocksMouse(GameTestHelper h) { beamEntry(h, true); }

    private static void beamEntry(GameTestHelper h, boolean mouse) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel(); BlockPos o=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new AABB(o.getX()-1,o.getY(),o.getZ()-1,o.getX()+8,o.getY()+8,o.getZ()+8));
        var animal=mouse?ModEntities.MOUSE.get().create(level):net.minecraft.world.entity.EntityType.PIG.create(level);
        check(animal!=null,"registered animal constructs"); if(animal instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(!(animal instanceof com.juicyslew.moonstation14.entities.MouseMob));animal.setNoGravity(true);
         animal.moveTo(o.getX()+2.5,o.getY()+.5,o.getZ()+3.5); check(level.addFreshEntity(animal),"animal enters live level");
         GroundedHarnessLease[] movementLease={null};
         h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(){if(movementLease[0]!=null)movementLease[0].close();if(!animal.isRemoved())animal.discard();lease.close();}public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}});
         com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
             if(animal instanceof com.juicyslew.moonstation14.entities.MouseMob mouseBody) movementLease[0]=GroundedHarnessLease.tryAcquireRoleTarget(mouseBody).orElseThrow(()->new GameTestAssertException("mouse role-target movement lease unavailable for "+mouseBody));
            var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID);
            var resolved=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(animal,com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(level));
            check(resolved.isPresent()&&resolved.get().component(com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent.class).isPresent(),"beam animal has resolved LightningTarget");
            check(resolved.get().component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class).isEmpty(),"beam animal lacks Stunnable");
            float before=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(animal).getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);
            double beamY=animal.getBoundingBox().getCenter().y;
            Vec3 from=new Vec3(o.getX()+.5,beamY,o.getZ()+.5), to=new Vec3(o.getX()+5.5,beamY,o.getZ()+.5);
             TeslaLightningBeamHazard.create(level,from,to,profile.lightning(),null);
            h.runAfterDelay(1,()->{
                animal.moveTo(o.getX()+2.5,o.getY()+.5,o.getZ()+.5);
                h.runAfterDelay(2,()->{float after=com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(animal).getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK,0f);check(Math.abs(after-before-7)<.01,"delayed fixed-beam entry records seven typed shock on "+(mouse?"mouse":"pig"));animal.discard();h.succeed();});
            });
        },o,o.offset(7,7,7));
    }
}
