package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** Native Space-vs-ordinary-ambient regression coverage for atmosphere firelock preflight. */
@GameTestTags({"space_firelock_preflight", "atmosphere", "firelock"})
@PrefixGameTestTemplate(false)
public final class SpaceFirelockPreflightGameTests {
    private static Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum, oldSpace;
    private SpaceFirelockPreflightGameTests() { }

    @BeforeBatch(batch="space_firelock_preflight")
    public static void configure(ServerLevel level) {
        var service=AtmosphereService.INSTANCE; oldVacuum=service.vacuumDimensionsForTest(); oldSpace=service.spaceDimensionsForTest();
        service.configureAtServerStart(Set.of(level.dimension()), Set.of(level.dimension()));
    }
    @AfterBatch(batch="space_firelock_preflight")
    public static void restore(ServerLevel level) { AtmosphereService.INSTANCE.configureAtServerStart(oldVacuum,oldSpace); }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="space_firelock_preflight",timeoutTicks=300)
    public static void finitePatchFirelockClosesBeforeSpaceTransfer(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); BlockPos root=helper.absolutePos(new BlockPos(4,1,4));
        BlockPos inside=root.north(), outside=root.south();
        List<BlockPos> cells=List.of(inside,root,root.above(),outside);
        for(BlockPos p:cells) level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        for(BlockPos cell:cells) for(var direction:net.minecraft.core.Direction.values()) {
            BlockPos wall=cell.relative(direction);
            if(!cells.contains(wall)) level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        }
        // The side boundary beyond the finite chamber is opened only after ownership is claimed.
        level.setBlock(outside.south(),Blocks.STONE.defaultBlockState(),3);
        // A two-cell open firelock is an actual member of the bounded finite room patch.
        var lower=ModBlocks.FIRELOCK.get().defaultBlockState(); level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("lower firelock BE missing");return;}
        door.initializeFromResolvedPrototype();door.refreshProjection();
        var service=AtmosphereService.INSTANCE;
        if(!service.initializeEnclosedFiniteMixtureForTrustedSetup(level,cells,new GasMixture(java.util.Map.of(),293.15))){
            helper.fail("fixture failed to establish finite ownership while all boundaries were enclosed"); return;
        }
        if(!service.addGas(level,inside,GasType.NITROGEN,80,293.15)
                ||!service.addGas(level,outside,GasType.NITROGEN,40,293.15)) {helper.fail("finite gas seeds rejected");return;}
        double protectedBefore=service.sample(level,inside).orElseThrow().moles(GasType.NITROGEN);
        double exposedBefore=service.sample(level,outside).orElseThrow().moles(GasType.NITROGEN);
        level.setBlock(outside.south(),Blocks.AIR.defaultBlockState(),3);
        service.topologyChanged(level,outside.south());
        service.activateCellAndNeighbors(level,inside);
        int[] clock={0};
        for(int i=0;i<260;i++) service.tick(level,++clock[0]);
        if(door.state().phase()!=AirlockState.DoorPhase.CLOSING && door.state().phase()!=AirlockState.DoorPhase.CLOSING_PASSABLE
                && door.state().phase()!=AirlockState.DoorPhase.CLOSED) {helper.fail("scheduler never considered open firelock on finite Space patch: "+door.state());return;}
        if(level.getBlockState(root).getValue(FirelockBlock.OPEN)||level.getBlockState(root.above()).getValue(FirelockBlock.OPEN)) {helper.fail("live preflight did not project both doorway cells closed");return;}
        double protectedAfter=service.sample(level,inside).orElseThrow().moles(GasType.NITROGEN);
        if(protectedAfter<protectedBefore-1e-6) {helper.fail("stale Space snapshot transferred protected-side gas before the firelock seal: "+protectedBefore+" -> "+protectedAfter);return;}
        double exposedAfter=service.sample(level,outside).orElseThrow().moles(GasType.NITROGEN);
        if(!(exposedAfter<exposedBefore-1e-6)) {helper.fail("fresh post-close Space patch did not continue exposed-side depressurization: "+exposedBefore+" -> "+exposedAfter);return;}
        if(door.state().powered()) {helper.fail("Space emergency close must not depend on receiver power");return;}
        helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="space_firelock_preflight",timeoutTicks=300)
    public static void obstructedSpaceCloseVetoKeepsDoorOpenAndFlowContinues(GameTestHelper helper) {
        Fixture f=finiteLeakingDoor(helper); var service=AtmosphereService.INSTANCE;
        var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(f.level);
        if(actor==null){helper.fail("obstruction actor unavailable");return;}
        actor.setNoAi(true);actor.setNoGravity(true);actor.setPos(f.root.getX()+.5,f.root.getY(),f.root.getZ()+.5);
        f.level.addFreshEntity(actor);
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(actor,f.level);
        service.activateCellAndNeighbors(f.level,f.inside); int[] clock={0};
        for(int i=0;i<260;i++)service.tick(f.level,++clock[0]);
        if(f.door.state().phase()!=AirlockState.DoorPhase.OPEN
                ||!f.level.getBlockState(f.root).getValue(FirelockBlock.OPEN)
                ||!f.level.getBlockState(f.root.above()).getValue(FirelockBlock.OPEN)){
            helper.fail("obstructed emergency close must veto without sealing either doorway cell: "+f.door.state());return;
        }
        double exposedAfter=service.sample(f.level,f.outside).orElseThrow().moles(GasType.NITROGEN);
        if(!(exposedAfter<f.exposedBefore-1e-6)){helper.fail("failed close must continue actual Space flow: "+f.exposedBefore+" -> "+exposedAfter);return;}
        actor.discard();helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="space_firelock_preflight",timeoutTicks=300)
    public static void ordinaryAmbientExposureFlowsWithoutEmergencyClosing(GameTestHelper helper) {
        var service=AtmosphereService.INSTANCE;var level=helper.getLevel();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> vacuum=service.vacuumDimensionsForTest();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> spaces=service.spaceDimensionsForTest();
        Fixture f;
        try {
            service.configureAtServerStart(vacuum,Set.of());
            f=finiteLeakingDoor(helper);
            service.activateCellAndNeighbors(level,f.inside);int[] clock={0};
            for(int i=0;i<260;i++)service.tick(level,++clock[0]);
            if(f.door.state().phase()!=AirlockState.DoorPhase.OPEN
                    ||!level.getBlockState(f.root).getValue(FirelockBlock.OPEN)
                    ||!level.getBlockState(f.root.above()).getValue(FirelockBlock.OPEN)){
                helper.fail("non-Space ordinary ambient must not trigger emergency closure: "+f.door.state());return;
            }
            double exposedAfter=service.sample(level,f.outside).orElseThrow().moles(GasType.NITROGEN);
            if(!(exposedAfter<f.exposedBefore-1e-6)){helper.fail("ordinary ambient kernel did not flow gas through the open firelock: "+f.exposedBefore+" -> "+exposedAfter);return;}
            helper.succeed();
        } finally { service.configureAtServerStart(vacuum,spaces); }
    }

    private static Fixture finiteLeakingDoor(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(4,1,4));
        BlockPos inside=root.north(),outside=root.south();List<BlockPos> cells=List.of(inside,root,root.above(),outside);
        for(BlockPos cell:cells)level.setBlock(cell,Blocks.AIR.defaultBlockState(),3);
        for(BlockPos cell:cells)for(var direction:net.minecraft.core.Direction.values()){
            BlockPos wall=cell.relative(direction);if(!cells.contains(wall))level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        }
        level.setBlock(outside.south(),Blocks.STONE.defaultBlockState(),3);
        var lower=ModBlocks.FIRELOCK.get().defaultBlockState();level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door))throw new IllegalStateException("lower firelock BE missing");
        door.initializeFromResolvedPrototype();door.refreshProjection();var service=AtmosphereService.INSTANCE;
        if(!service.initializeEnclosedFiniteMixtureForTrustedSetup(level,cells,new GasMixture(java.util.Map.of(),293.15)))
            throw new IllegalStateException("finite room ownership setup failed");
        if(!service.addGas(level,inside,GasType.NITROGEN,80,293.15)||!service.addGas(level,outside,GasType.NITROGEN,40,293.15))
            throw new IllegalStateException("finite gas seed rejected");
        double exposedBefore=service.sample(level,outside).orElseThrow().moles(GasType.NITROGEN);
        level.setBlock(outside.south(),Blocks.AIR.defaultBlockState(),3);service.topologyChanged(level,outside.south());
        return new Fixture(level,root,inside,outside,door,exposedBefore);
    }
    private record Fixture(ServerLevel level,BlockPos root,BlockPos inside,BlockPos outside,FirelockBlockEntity door,double exposedBefore) { }
}
