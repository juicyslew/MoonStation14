package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Map;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegFixtureOwnershipGameTests {
    private TegFixtureOwnershipGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", timeoutTicks=100)
    @GameTestTags("teg-fixture-construction")
    public static void ownersAreExactLivePlacementsAndDuplicatesFailClosed(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos localOrigin=new BlockPos(2,1,2);
        BlockPos origin=helper.absolutePos(localOrigin);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(origin).inflate(3));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"fixture ownership hosts entity-ticking",()->{
            BlockPos coreLocal=localOrigin;
            BlockPos machineLocal=localOrigin.east();
            BlockPos minerLocal=localOrigin.south();
            BlockPos ventLocal=localOrigin.south(2);
            BlockPos corePos=helper.absolutePos(coreLocal), machinePos=helper.absolutePos(machineLocal);
            BlockPos minerPos=helper.absolutePos(minerLocal), ventPos=helper.absolutePos(ventLocal);
            helper.setBlock(coreLocal,ModBlocks.TEG_CENTER.get());
            helper.setBlock(machineLocal,ModBlocks.GAS_VOLUME_PUMP.get());
            helper.setBlock(minerLocal,ModBlocks.GAS_MINER_OXYGEN.get());
            helper.setBlock(ventLocal,ModBlocks.GAS_VENT.get());
            var core=(AtmosMachineBlockEntity)level.getBlockEntity(corePos);
            var machine=(AtmosMachineBlockEntity)level.getBlockEntity(machinePos);
            var miner=(GasMinerBlockEntity)level.getBlockEntity(minerPos);
            var vent=(VentilationDeviceBlockEntity)level.getBlockEntity(ventPos);
            require(core!=null&&machine!=null&&miner!=null&&vent!=null,"all fixture hosts placed");
            core.reconcileLoadedPorts(level); machine.reconcileLoadedPorts(level);
            var minerId=miner.placementIdentity(); var ventId=vent.deviceId();
            require(minerId!=null&&ventId!=null,"live miner and vent own real placement identities");

            var base=TegFixturePlacement.machineOwners(List.of(machine),core);
            require(base.equals(Map.of(corePos,core.placementIdentity(),machinePos,machine.placementIdentity())),
                    "legacy overload returns exactly core and ordinary machines");
            var owners=TegFixturePlacement.machineOwners(List.of(machine),core,List.of(miner),List.of(vent));
            require(owners.equals(Map.of(corePos,core.placementIdentity(),machinePos,machine.placementIdentity(),
                            minerPos,minerId,ventPos,ventId)),"extended overload returns exactly actual owner identities");

            expectRefusal(()->TegFixturePlacement.machineOwners(List.of(machine),core,List.of(miner,miner),List.of(vent)),
                    "duplicate miner position refused");
            expectRefusal(()->TegFixturePlacement.machineOwners(List.of(machine),core,List.of(miner),List.of(vent,vent)),
                    "duplicate vent position refused");
            require(miner.placementIdentity().equals(minerId)&&vent.deviceId().equals(ventId),
                    "refused duplicate captures do not alter live identities");

            helper.setBlock(minerLocal,Blocks.AIR);
            expectRefusal(()->TegFixturePlacement.machineOwners(List.of(machine),core,List.of(miner),List.of(vent)),
                    "removed stale miner owner refused");
            helper.setBlock(minerLocal,ModBlocks.GAS_MINER_OXYGEN.get());
            var replacement=(GasMinerBlockEntity)level.getBlockEntity(minerPos);
            require(replacement!=null&&replacement.placementIdentity()!=null&&!replacement.placementIdentity().equals(minerId),
                    "replacement miner has a fresh live identity");
            helper.succeed();
        },origin);
    }

    private static void expectRefusal(Runnable action,String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new GameTestAssertException(message);
    }
    private static void require(boolean value,String message) { if(!value) throw new GameTestAssertException(message); }
}
