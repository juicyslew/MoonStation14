package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"particle-accelerator", "power"})
@PrefixGameTestTemplate(false)
public final class ParticleAcceleratorAssemblyGameTests {
    private ParticleAcceleratorAssemblyGameTests() { }
    private static void check(boolean ok,String why){if(!ok)throw new GameTestAssertException(why);}
    private static BlockPos abs(GameTestHelper h,BlockPos p){return h.absolutePos(p);}

    @GameTest(templateNamespace="moonstation14",template="empty",timeoutTicks=30)
    public static void registeredSevenPartAssemblyRequiresResolvedRolesAndSeparatesPowerRoles(GameTestHelper h){
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos control=abs(h,new BlockPos(4,2,4));
        var fixture=PAAssemblyFixture.place(level,control,Rotation.NONE);
        var profile=fixture.profile(); var ctl=fixture.control();
        ParticleAcceleratorPartBlockEntity.serverTick(level,control,level.getBlockState(control),ctl);
        check(ctl.assemblyValid(),"registered six physical parts resolve as strict profile assembly");
        check(ctl.apcPowerDemandWatts()==(double)profile.controlPowerAp()&&ctl.mvPowerDemandWatts()==null,"control requests the resolved profile APC draw only");
        var powerPos=control.offset(ParticleAcceleratorAssembly.toWorldVec(profile.parts().get("power").offsetX(),profile.parts().get("power").offsetY(),Rotation.NONE));
        var power=fixture.parts().get("power");
        check(power.mvPowerDemandWatts()!=null&&power.apcPowerDemandWatts()==null,"power box alone is MV consumer");
        ctl.setStrength(ParticleAcceleratorStrength.LEVEL_2);ctl.setEnabled(true);
        check(power.strength()==ParticleAcceleratorStrength.LEVEL_2&&power.enabled(),"valid control propagates authoritative selection to power box");
        check(!ctl.firingEligible(),"APC-ready request without fresh actual MV allocation cannot authorize firing");
        var saved=power.saveWithoutMetadata(level.registryAccess());
        var restored=new ParticleAcceleratorPartBlockEntity(powerPos,level.getBlockState(powerPos));
        restored.loadWithComponents(saved,level.registryAccess());
        check(restored.placementIdentity().equals(power.placementIdentity())&&restored.profileId().equals(power.profileId())
                &&restored.strength()==power.strength()&&restored.enabled(),"identity, profile, and selected state persist");
        level.setBlock(powerPos,ModBlocks.PA_END_CAP.get().defaultBlockState().setValue(ParticleAcceleratorPartBlock.FACING,Direction.WEST),3);
        ParticleAcceleratorPartBlockEntity.serverTick(level,control,level.getBlockState(control),ctl);
        check(!ctl.assemblyValid(),"wrong registered role invalidates assembly");
        h.succeed();
    }

    @GameTestTags("pa-client-sync")
    @GameTest(templateNamespace="moonstation14",template="empty",timeoutTicks=30)
    public static void updateTagReplacesClientRandomIdentityWithServerIdentity(GameTestHelper h){
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos pos=abs(h,new BlockPos(4,2,4));
        level.setBlock(pos,ModBlocks.PA_CONTROL_BOX.get().defaultBlockState(),3);
        var server=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(pos);
        check(server!=null,"registered server part exists");
        server.setStrength(ParticleAcceleratorStrength.LEVEL_1);
        var initial=server.placementIdentity();
        var client=new ParticleAcceleratorPartBlockEntity(pos,level.getBlockState(pos));
        check(!client.placementIdentity().equals(initial),"fresh client identity starts independently random");
        var tag=server.getUpdateTag(level.registryAccess());
        client.handleUpdateTag(tag,level.registryAccess());
        check(client.placementIdentity().equals(initial),"initial update tag installs server identity");
        check(client.profileId().equals(server.profileId())&&client.strength()==server.strength(),"update tag synchronizes resolved profile and presentation state");
        h.succeed();
    }
}
