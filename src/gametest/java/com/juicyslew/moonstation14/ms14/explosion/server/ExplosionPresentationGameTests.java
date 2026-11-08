package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@GameTestTags({"explosion-presentation"})
@PrefixGameTestTemplate(false)
public final class ExplosionPresentationGameTests {
    private ExplosionPresentationGameTests() { }

    // The controlled camera is placed 16 blocks from the blast; keep it inside this 36x5x8 fixture.
    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", timeoutTicks=80)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining"})
    public static void acceptedExecutionAndGrenadeWrapperPresentOnce(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        RegisteredServerPlayerFixture fixture=RegisteredServerPlayerFixture.register(level,"explosion-cues");
        var leaveListener=new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<EntityLeaveLevelEvent>>();
        var firstLeave=new java.util.concurrent.atomic.AtomicReference<String>();
        var cleanup=cleanup(h,fixture,leaveListener);
        h.runAtTickTime(2,()->{ try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var player=fixture.player();
            BlockPos source=h.absolutePos(new BlockPos(4,2,4));
            var camera=active.body();
            var bodyId=camera.getUUID();
            java.util.function.Consumer<EntityLeaveLevelEvent> observer=event->{
                if(event.getEntity()==camera && event.getEntity().getUUID().equals(bodyId)) firstLeave.compareAndSet(null,
                        leaveSummary(camera,event.getLevel()));
            };
            NeoForge.EVENT_BUS.addListener(observer); leaveListener.set(observer);
            camera.setPos(source.getX()+16.5,source.getY()+.5,source.getZ()+.5);
            player.setPos(source.getX()+128.5,source.getY()+.5,source.getZ()+.5);
            player.setCamera(camera);
            var bounds=h.getBounds();
            var bodyBounds=camera.getBoundingBox();
            h.assertTrue(bounds.contains(new net.minecraft.world.phys.Vec3(bodyBounds.minX,bodyBounds.minY,bodyBounds.minZ))
                            &&bounds.contains(new net.minecraft.world.phys.Vec3(bodyBounds.maxX,bodyBounds.maxY,bodyBounds.maxZ)),
                    "controlled camera bounding box fits within the fixture bounds");
            h.assertTrue(bounds.contains(new net.minecraft.world.phys.Vec3(source.getX()+.5,source.getY()+.5,source.getZ()+.5)),
                    "explosion source is within the fixture bounds");
            h.assertTrue(camera.distanceToSqr(source.getX()+.5,source.getY()+.5,source.getZ()+.5)==256,
                    "controlled camera is sixteen blocks from blast");
            h.assertTrue(player.distanceToSqr(source.getX()+.5,source.getY()+.5,source.getZ()+.5)>32*32,
                    "native carrier is outside vanilla particle broadcast range");
            drain(fixture);
            var result=ServerExplosionService.execute(level,source,ModExplosions.DEFAULT_ID,new BlastPlanner.Proposal(120,3,10));
            String afterGeneric=timeline(camera,level);
            h.assertTrue(result.status()==ServerExplosionService.Status.APPLIED,"real generic execution accepted: "+result);
            verify(h,fixture,source);
            drain(fixture);
            var unknown=ServerExplosionService.execute(level,source,net.minecraft.resources.ResourceLocation.withDefaultNamespace("missing_explosion"),new BlastPlanner.Proposal(120,3,10));
            h.assertTrue(unknown.status()==ServerExplosionService.Status.UNKNOWN_PROTOTYPE,"unknown prototype rejected");
            assertNoMatching(fixture,source,level.dimension().location());
            drain(fixture);
            h.assertTrue(GrenadeDetonationService.detonate(level,source)==GrenadeDetonationService.Outcome.QUEUED,"fixed grenade wrapper admitted to queue");
            h.runAfterDelay(2,()->{
                try {
                    if(!live(camera,level)) throw new IllegalStateException("body unavailable before delayed verify");
                    verify(h,fixture,source);
                    h.succeed();
                } catch(Throwable failure) { h.fail(failureMessage("queued grenade presentation",firstLeave.get(),afterGeneric,camera,level,failure)); }
                finally { try { cleanup.close(); } catch(Exception ignored) { } }
            });
        } catch(Throwable failure) {
            h.fail(failureMessage("explosion presentation fixture",firstLeave.get(),"n/a",null,level,failure));
            try { cleanup.close(); } catch(Exception ignored) { }
        }});
    }

    private static void verify(GameTestHelper h,RegisteredServerPlayerFixture fixture,BlockPos origin) {
        int explosion=0,flame=0,smoke=0,sound=0;
        Object packet;
        while((packet=fixture.pollOutbound())!=null) {
            if(packet instanceof ClientboundLevelParticlesPacket particles
                    && Math.abs(particles.getX()-(origin.getX()+.5))<.01
                    && Math.abs(particles.getY()-(origin.getY()+.5))<.01
                    && Math.abs(particles.getZ()-(origin.getZ()+.5))<.01) {
                if(particles.getParticle().getType()==net.minecraft.core.particles.ParticleTypes.EXPLOSION) { explosion++; h.assertTrue(particles.getCount()==1,"explosion count one"); }
                if(particles.getParticle().getType()==net.minecraft.core.particles.ParticleTypes.FLAME) { flame++; h.assertTrue(particles.getCount()==16,"flame count sixteen"); }
                if(particles.getParticle().getType()==net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE) { smoke++; h.assertTrue(particles.getCount()==12,"smoke count twelve"); }
            }
            if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof GrenadeSoundPayload cue
                    && matches(cue,origin,fixture.player().level().dimension().location())) sound++;
        }
        h.assertTrue(explosion==1&&flame==1&&smoke==1,"one packet each explosion/flame/smoke at source; got "+explosion+"/"+flame+"/"+smoke);
        h.assertTrue(sound==1,"exactly one nearby grenade sound payload; got "+sound);
    }
    private static void assertNoMatching(RegisteredServerPlayerFixture fixture,BlockPos origin,net.minecraft.resources.ResourceLocation dimension) {
        Object packet;
        while((packet=fixture.pollOutbound())!=null) {
            if(packet instanceof ClientboundLevelParticlesPacket p && Math.abs(p.getX()-(origin.getX()+.5))<.01
                    && Math.abs(p.getY()-(origin.getY()+.5))<.01&&Math.abs(p.getZ()-(origin.getZ()+.5))<.01)
                throw new net.minecraft.gametest.framework.GameTestAssertException("rejected execution emitted source particles");
            if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof GrenadeSoundPayload cue&&matches(cue,origin,dimension))
                throw new net.minecraft.gametest.framework.GameTestAssertException("rejected execution emitted source sound");
        }
    }
    private static boolean matches(GrenadeSoundPayload cue,BlockPos origin,net.minecraft.resources.ResourceLocation dimension) {
        return cue.dimension().equals(dimension)&&cue.mode()==GrenadeSoundPayload.Mode.NEAR
                &&Math.abs(cue.x()-(origin.getX()+.5))<.01&&Math.abs(cue.y()-(origin.getY()+.5))<.01&&Math.abs(cue.z()-(origin.getZ()+.5))<.01;
    }
    private static AutoCloseable cleanup(GameTestHelper h,RegisteredServerPlayerFixture fixture,java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<EntityLeaveLevelEvent>> listener) {
        var closed=new java.util.concurrent.atomic.AtomicBoolean(); Runnable close=()->{if(closed.compareAndSet(false,true)){var l=listener.getAndSet(null);if(l!=null)NeoForge.EVENT_BUS.unregister(l);fixture.close();}};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close.run();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close.run();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){close.run();}});
        return close::run;
    }
    private static void drain(RegisteredServerPlayerFixture fixture) { while(fixture.pollOutbound()!=null) { } }
    private static boolean live(net.minecraft.world.entity.Entity e,ServerLevel level) { return e!=null&&!e.isRemoved()&&e.isAlive()&&e.isAddedToLevel()&&e.level()==level&&level.getEntity(e.getUUID())==e&&!e.isPassenger(); }
    private static String state(net.minecraft.world.entity.Entity e,ServerLevel level) { return e==null?"body=null":"live="+live(e,level)+" rem="+e.isRemoved()+" alive="+e.isAlive()+" added="+e.isAddedToLevel()+" sameLevel="+(e.level()==level)+" mapped="+(level.getEntity(e.getUUID())==e)+" passenger="+e.isPassenger()+" hp="+(e instanceof net.minecraft.world.entity.LivingEntity living?living.getHealth():-1)+" reason="+e.getRemovalReason()+" pos="+e.position(); }
    private static String leaveSummary(net.minecraft.world.entity.Entity e,net.minecraft.world.level.Level level) { return clip("leave="+e.getRemovalReason()+",hp="+(e instanceof net.minecraft.world.entity.LivingEntity living?living.getHealth():-1)+",via="+shortStack(),120); }
    private static String timeline(net.minecraft.world.entity.Entity e,ServerLevel level) { return "g:"+live(e,level)+"/r"+e.isRemoved()+"/a"+e.isAlive()+"/map"+(level.getEntity(e.getUUID())==e); }
    private static String failureMessage(String phase,String leave,String after,net.minecraft.world.entity.Entity body,ServerLevel level,Throwable failure) { String path=leave==null?"none":leave;String result=phase+" "+clip(path,120)+" g="+after+" now="+clip(state(body,level),220)+" err="+failure.getClass().getSimpleName();return clip(result,480); }
    private static String clip(String value,int max) { return value.length()<=max?value:value.substring(0,max); }
    private static String shortStack() { var frames=Thread.currentThread().getStackTrace();var out=new StringBuilder();int added=0;for(var frame:frames){String c=frame.getClassName();if(c.equals(ExplosionPresentationGameTests.class.getName())||c.startsWith("net.neoforged.bus.")||c.startsWith("java."))continue;if(added++==3)break;if(out.length()>0)out.append("<-");String name=c.substring(c.lastIndexOf('.')+1);out.append(name).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());}return out.length()==0?"origin-unknown":clip(out.toString(),65); }
}
