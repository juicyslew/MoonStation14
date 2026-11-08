package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-mini-lightning"})
@PrefixGameTestTemplate(false)
public final class TeslaMiniLightningGameTests {
    private TeslaMiniLightningGameTests() { }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }

    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="tesla_mini_lightning", timeoutTicks=400)
    public static void registeredMiniEmitsAuthoredLightningIntoLiveRod(GameTestHelper h) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,
                new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+8,origin.getY()+8,origin.getZ()+8));
        BlockPos rodPos=origin.east(2);
        var miniRef=new TeslaEnergyBallEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean() { if(miniRef[0]!=null&&!miniRef[0].isRemoved()) miniRef[0].discard(); level.setBlock(rodPos,Blocks.AIR.defaultBlockState(),3); lease.close(); }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean();}
        });
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{
            level.setBlock(rodPos,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3);
            var rod=(TeslaMachineBlockEntity)level.getBlockEntity(rodPos);
            var rodProfile=ModTeslaProfiles.machines(level).get(ModTeslaProfiles.ROD_ID);
            check(rod!=null&&rodProfile.target().priority()==3&&rodProfile.target().probability()==1&&rodProfile.target().resistance()==10,
                    "registered authored rod is guaranteed priority-3 target with resistance 10");
            var mini=ModEntities.TESLA_ENERGY_BALL.get().create(level);
            check(mini!=null&&mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID),"registered inherited mini profile resolves"); miniRef[0]=mini;
            check(mini.profileId().equals(ModTeslaProfiles.MINI_ID)&&mini.energy()==0,"resolved mini identity and empty energy");
            mini.moveTo(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5);
            check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(mini.position(),mini.position()).inflate(25))
                            .stream().noneMatch(other->other!=mini&&!other.isMini()),
                    "isolated mini has no live main Tesla source within its 25-block acquisition neighborhood");
            check(mini.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(rodPos))==2.0,"rod center is exactly two blocks from mini source point");
            check(level.addFreshEntity(mini),"mini enters ordinary entity ticking");
            var profile=ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID);
            int firstShot=(int)Math.ceil(profile.shooter().maximumIntervalSeconds()*20);
            check(firstShot>10,"authored mini first-shot deadline exceeds initial observation window");
            h.runAfterDelay(firstShot-2,()->{
                check(mini.lightningShotAttempts()==0&&!rod.hitActive()&&!level.getBlockState(rodPos).getValue(TeslaMachineBlock.HIT),
                        "no mini shot attempt or rod strike before authored maximum first-shot deadline");
                h.runAfterDelay(7,()->{
                    check(mini.lightningShotAttempts()>=1&&mini.lightningBoltsEmitted()>=1&&mini.lastLightningShotTick()>=0,
                            "mini emitted authored lightning; attempts="+mini.lightningShotAttempts()+" bolts="+mini.lightningBoltsEmitted()+" lastTick="+mini.lastLightningShotTick()+" position="+mini.position()+" profile="+mini.profileId()+" shooter="+profile.shooter()+" lightning="+profile.lightning()+" rod="+rodPos);
                    check(rod.hitActive()&&level.getBlockState(rodPos).getValue(TeslaMachineBlock.HIT),"mini authored strike reaches live rod");
                    check(mini.isAlive()&&mini.isMini()&&mini.energy()==0,"mini remains alive with no energy growth");
                    check(rod.energyJoules()==0,"grounding rod accepts strike without storing energy");
                    check(level.getEntitiesOfClass(TeslaEnergyBallEntity.class,new net.minecraft.world.phys.AABB(origin).inflate(3)).size()==1,"mini creates no main entity");
                    h.succeed();
                });
            });
        },origin,origin.offset(7,7,7));
    }
}
