package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-lightning-beam"})
@PrefixGameTestTemplate(false)
public final class TeslaBeamLifecycleGameTests {
    private TeslaBeamLifecycleGameTests() { }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    private static float shock(LivingEntity entity) {
        return com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(entity)
                .getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.SHOCK, 0f);
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=260)
    public static void liveBeamTicksContactsAndExpiresOnServerClock(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(4,4,4));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,
                new AABB(p).inflate(8));
        var bodyRef=new LivingEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean() { if(bodyRef[0]!=null&&!bodyRef[0].isRemoved())bodyRef[0].discard(); lease.close(); }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            var body=EntityType.VILLAGER.create(level);
            check(body!=null,"registered native villager created"); bodyRef[0]=body;
            body.setNoAi(true); body.setNoGravity(true);
            body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+2);
            check(level.addFreshEntity(body),"villager enters ticking level");
            h.runAfterDelay(1,()->{
                var actor=com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body);
                check(actor.flatMap(c->c.component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class)).isPresent(),
                        "registered target resolves a native stunnable character component");
                float baseline=shock(body);
                var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID).lightning();
                int lifetime=(int)Math.max(1,Math.round(profile.sourceLifetimeSeconds()*20));
                Vec3 from=new Vec3(p.getX()+.5,p.getY()+1,p.getZ()+.5);
                Vec3 to=new Vec3(p.getX()+5.5,p.getY()+1,p.getZ()+.5);
                TeslaLightningBeamHazard.create(level,from,to,profile,null);
                h.runAfterDelay(5,()->{
                    body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+.5);
                    h.runAfterDelay(2,()->{
                        float first=shock(body);
                        check(Math.abs(first-baseline-7f)<.01,"ordinary server event tick shocks newly intersecting target by 7");
                        h.runAfterDelay(5,()->{
                            check(Math.abs(shock(body)-first)<.01,"continued overlap does not repeatedly shock");
                            body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+3);
                            h.runAfterDelay(2,()->{
                                check(Math.abs(shock(body)-first)<.01,"leaving fixed beam adds no shock");
                                body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+.5);
                                h.runAfterDelay(2,()->{
                                    float reentry=shock(body);
                                    check(Math.abs(reentry-first-7f)<.01,"re-entry receives another shock");
                                    body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+3);
                                    h.runAfterDelay(lifetime+5,()->{
                                        body.moveTo(p.getX()+2,p.getY()+.5,p.getZ()+.5);
                                        h.runAfterDelay(3,()->{
                                            check(Math.abs(shock(body)-reentry)<.01,"expired beam does not shock a later entrant");
                                            h.succeed();
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        },p.offset(-8,-8,-8),p.offset(8,8,8));
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=180)
    public static void authoredBeamWidthHitsOffAxisPigButRejectsBeyondWidth(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(4,4,4));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new AABB(p).inflate(12));
        LivingEntity[] bodies=new LivingEntity[2];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(){for(var b:bodies)if(b!=null&&!b.isRemoved())b.discard();lease.close();}
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            for(int i=0;i<2;i++){
                var pig=EntityType.PIG.create(level); check(pig!=null,"native pig created"); bodies[i]=pig;
                pig.setNoAi(true);pig.setNoGravity(true);
                // Keep the out-of-width pig well separated along the beam axis so ordinary
                // pig-pig collision cannot push it into the authored beam corridor.
                pig.moveTo(p.getX()+(i==0?2:4.5),p.getY()+.5,p.getZ()+(i==0?.65:1.0));
                check(level.addFreshEntity(pig),"pig enters ticking level");
            }
            h.runAfterDelay(2,()->{
                var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MAIN_ID).lightning();
                check(Math.abs(profile.beamHalfWidth()-.20)<.0001,"resolved base lightning profile authors .20 half-width");
                for(int i=0;i<2;i++) {
                    double halfWidth=bodies[i].getBoundingBox().getZsize()*.5;
                    double offset=halfWidth+(i==0?.10:.30);
                    bodies[i].moveTo(p.getX()+(i==0?2:4.5),p.getY()+.5,p.getZ()+.5+offset);
                }
                check(bodies[0].position().distanceTo(bodies[1].position())>1.5,"beam-width subjects remain physically separated at sample time");
                Vec3 beamStart=new Vec3(p.getX()+.5,p.getY()+1,p.getZ()+.5),beamEnd=new Vec3(p.getX()+5.5,p.getY()+1,p.getZ()+.5);
                check(!TeslaLightningBeamHazard.intersects(beamStart,beamEnd,bodies[0].getBoundingBox(),0)
                                &&TeslaLightningBeamHazard.intersects(beamStart,beamEnd,bodies[0].getBoundingBox(),profile.beamHalfWidth())
                                &&!TeslaLightningBeamHazard.intersects(beamStart,beamEnd,bodies[1].getBoundingBox(),profile.beamHalfWidth()),
                        "sampled pig boxes prove only the positive subject enters authored beam width");
                float before0=shock(bodies[0]),before1=shock(bodies[1]);
                TeslaLightningSystem.emitLiveBeam(level,beamStart,beamEnd,profile,null);
                h.runAfterDelay(3,()->{
                    check(!TeslaLightningBeamHazard.intersects(beamStart,beamEnd,bodies[0].getBoundingBox(),0)
                                    &&TeslaLightningBeamHazard.intersects(beamStart,beamEnd,bodies[0].getBoundingBox(),profile.beamHalfWidth())
                                    &&!TeslaLightningBeamHazard.intersects(beamStart,beamEnd,bodies[1].getBoundingBox(),profile.beamHalfWidth()),
                            "sampled boxes remain on opposite sides of authored cutoff at impact");
                    check(Math.abs(shock(bodies[0])-before0-7)<.01,"off-axis pig within authored beam width receives normal 7 shock");
                    check(Math.abs(shock(bodies[1])-before1)<.01,"pig beyond authored width remains untouched");
                    h.succeed();
                });
            });
        },p.offset(-8,-8,-8),p.offset(8,8,8));
    }
}
