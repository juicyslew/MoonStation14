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

@GameTestTags({"explosion-presentation"})
@PrefixGameTestTemplate(false)
public final class ExplosionPresentationGameTests {
    private ExplosionPresentationGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=80)
    public static void acceptedExecutionAndGrenadeWrapperPresentOnce(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        RegisteredServerPlayerFixture fixture=RegisteredServerPlayerFixture.register(level,"explosion-cues");
        var cleanup=cleanup(h,fixture);
        h.runAtTickTime(2,()->{ try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var player=fixture.player();
            BlockPos source=h.absolutePos(new BlockPos(4,2,4));
            var camera=active.body();
            camera.setPos(source.getX()+16.5,source.getY()+.5,source.getZ()+.5);
            player.setPos(source.getX()+128.5,source.getY()+.5,source.getZ()+.5);
            player.setCamera(camera);
            h.assertTrue(camera.distanceToSqr(source.getX()+.5,source.getY()+.5,source.getZ()+.5)==256,
                    "controlled camera is sixteen blocks from blast");
            h.assertTrue(player.distanceToSqr(source.getX()+.5,source.getY()+.5,source.getZ()+.5)>32*32,
                    "native carrier is outside vanilla particle broadcast range");
            drain(fixture);
            var result=ServerExplosionService.execute(level,source,ModExplosions.DEFAULT_ID,new BlastPlanner.Proposal(120,3,10));
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
                    verify(h,fixture,source);
                    h.succeed();
                } catch(Throwable failure) { h.fail("queued grenade presentation: "+failure); }
                finally { try { cleanup.close(); } catch(Exception ignored) { } }
            });
        } catch(Throwable failure) {
            h.fail("explosion presentation fixture: "+failure);
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
    private static AutoCloseable cleanup(GameTestHelper h,RegisteredServerPlayerFixture fixture) {
        var closed=new java.util.concurrent.atomic.AtomicBoolean(); Runnable close=()->{if(closed.compareAndSet(false,true))fixture.close();};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close.run();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close.run();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){close.run();}});
        return close::run;
    }
    private static void drain(RegisteredServerPlayerFixture fixture) { while(fixture.pollOutbound()!=null) { } }
}
