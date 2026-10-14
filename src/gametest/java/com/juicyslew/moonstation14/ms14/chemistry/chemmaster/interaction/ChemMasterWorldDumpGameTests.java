package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlock;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.jug.ModJugs;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Trusted service fixtures only; these do not exercise authenticated packet transport. */
@GameTestTags({"chemmaster-dump", "power", "feature-readiness"})
@PrefixGameTestTemplate(false)
public final class ChemMasterWorldDumpGameTests {
    private ChemMasterWorldDumpGameTests() { }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=30)
    public static void chemMasterLoadedNominationDoesNotRequireGaze(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos target=h.absolutePos(new BlockPos(4,1,2));
        level.setBlock(target,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
        var machine=(ChemMasterBlockEntity)level.getBlockEntity(target);
        h.assertTrue(machine!=null && machine.isLoadedValid(),"ChemMaster BE is live");
        h.assertTrue(ChemMasterDumpService.nominatesChemMaster(level,target),
                "release nomination resolves loaded ChemMaster without APC type filter or gaze requirement");
        BlockPos barrier=target.offset(0,1,1);
        level.setBlock(barrier,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(ChemMasterDumpService.nominatesChemMaster(level,target),
                "client target nomination is not gaze/LOS authority; shared transaction validates access");
        BlockPos unloaded=null;
        for (int chunkOffset=32;chunkOffset<256&&unloaded==null;chunkOffset+=16) {
            BlockPos candidate=target.offset(chunkOffset,0,0);
            if (level.getChunkSource().getChunkNow(candidate.getX()>>4,candidate.getZ()>>4)==null) unloaded=candidate;
        }
        h.assertTrue(unloaded!=null && level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,
                "fixture found an actually unloaded chunk without loading it");
        h.assertTrue(!ChemMasterDumpService.nominatesChemMaster(level,unloaded)
                        && level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,
                "unknown target chunk is denied without forcing a load");
        var actor=spawnStableActor(h,h.absolutePos(new BlockPos(4,1,4)));
        var source=LooseItemEntity.create(level,h.absolutePos(new BlockPos(4,1,4)).getCenter(),jugWith(ModReagents.createKey("water"),1));
        level.addFreshEntity(source);
        h.assertTrue(ChemMasterWorldDump.transferOwned(actor,
                        new ChemMasterWorldDump.ExpectedSource(source.getUUID(),source.getItem()),unloaded,
                        java.util.UUID.randomUUID(),0,0,()->true)==ChemMasterWorldDump.Result.DENIED
                        &&level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,
                "transaction target resolution denies unloaded chunk without loading it");
        h.succeed();
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=30)
    public static void wholeMixtureCommitsInertlyToUnpoweredMachine(GameTestHelper h) {
        ServerLevel l=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(4,1,4));
        var body=spawnStableActor(h,origin);
        BlockPos machinePos=origin.offset(1,0,0);
        l.setBlock(machinePos,ModBlocks.CHEM_MASTER.get().defaultBlockState().setValue(ChemMasterBlock.FACING,Direction.SOUTH),3);
        var be=(ChemMasterBlockEntity)l.getBlockEntity(machinePos);
        var chlorine=ModReagents.createKey("chlorine"); var sodium=ModReagents.createKey("sodium");
        ItemStack jug=new ItemStack(ModItems.JUG.get()); jug.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(chlorine,50f,sodium,50f)));
        var item=LooseItemEntity.create(l,origin.getCenter(),jug); l.addFreshEntity(item);
        var before=be.buffer().snapshot(); var rev=be.bufferRevision(); var state=be.stateRevision();
        var expected=new ChemMasterWorldDump.ExpectedSource(item.getUUID(),jug);
        var result=ChemMasterWorldDump.transferOwned(body,expected,machinePos,be.placementIdentity(),state,rev,()->true);
        h.assertTrue(result==ChemMasterWorldDump.Result.SUCCESS,"whole mixture accepted without power gate");
        h.assertTrue(be.buffer().quantities().getOrDefault(chlorine,0L)==5000&&be.buffer().quantities().getOrDefault(sodium,0L)==5000,
                "all species transferred exactly, no target reaction");
        h.assertTrue(item.getItem().get(ModDataComponents.REAGENT.get())==null&&item.getItem().getCount()==1,"empty jug remains as same world item");
        h.assertTrue(be.inputSlot().snapshot().stack().isEmpty()&&be.bufferRevision()==rev+1&&be.stateRevision()==state+1,"input unchanged and buffer revisions advance");
        h.assertTrue(before.contents().centContents().isEmpty(),"started empty"); h.succeed();
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=80)
    public static void authenticatedDumpBeginCancelReleaseAndDuplicateConserveSource(GameTestHelper h) {
        RegisteredServerPlayerFixture fixture=RegisteredServerPlayerFixture.register((ServerLevel)h.getLevel(),"readiness-chem-dump");
        h.runAtTickTime(2, () -> { try {
            var owner=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            ServerLevel level=(ServerLevel)h.getLevel();
            var body=owner.body();
            BlockPos machinePos=BlockPos.containing(body.getX()+1,body.getY(),body.getZ());
            level.setBlock(machinePos,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
            var machine=(ChemMasterBlockEntity)level.getBlockEntity(machinePos);
            h.assertTrue(machine!=null&&machine.isLoadedValid(),"real target ChemMaster is loaded");
            body.setPos(machinePos.getX()-1.1,machinePos.getY(),machinePos.getZ()+.5);
            var water=ModReagents.createKey("water");
            ItemStack jug=jugWith(water,2);
            var source=LooseItemEntity.create(level,body.position().add(0,.2,0),jug); level.addFreshEntity(source);
            var dimension=level.dimension().location();
            var cancelled=java.util.UUID.randomUUID();
            var beginCancel=new ChemMasterDumpRequest(cancelled,ChemMasterDumpRequest.Phase.BEGIN,owner.epoch(),1,
                    source.getUUID(),dimension,java.util.Optional.empty());
            ChemMasterDumpService.handle(fixture.player(),beginCancel);
            requireDumpAck(fixture,cancelled,ChemMasterDumpRequest.Phase.BEGIN,true);
            ChemMasterDumpService.handle(fixture.player(),new ChemMasterDumpRequest(cancelled,ChemMasterDumpRequest.Phase.CANCEL,
                    owner.epoch(),2,source.getUUID(),dimension,java.util.Optional.empty()));
            requireDumpAck(fixture,cancelled,ChemMasterDumpRequest.Phase.CANCEL,true);
            h.assertTrue(source.isAlive()&&source.getItem().get(ModDataComponents.REAGENT.get()).contents()
                    .getOrDefault(water,0f)==2f&&machine.buffer().snapshot().contents().centContents().isEmpty(),
                    "owned cancel leaves loose jug and ChemMaster buffer unchanged");
            var gesture=java.util.UUID.randomUUID();
            ChemMasterDumpService.handle(fixture.player(),new ChemMasterDumpRequest(gesture,ChemMasterDumpRequest.Phase.BEGIN,
                    owner.epoch(),3,source.getUUID(),dimension,java.util.Optional.empty()));
            requireDumpAck(fixture,gesture,ChemMasterDumpRequest.Phase.BEGIN,true);
            long slotRevision=machine.inputSlot().snapshot().revision();
            long bufferRevision=machine.bufferRevision();
            var release=new ChemMasterDumpRequest(gesture,ChemMasterDumpRequest.Phase.RELEASE,owner.epoch(),4,
                    source.getUUID(),dimension,java.util.Optional.of(machinePos));
            ChemMasterDumpService.handle(fixture.player(),release);
            requireDumpAck(fixture,gesture,ChemMasterDumpRequest.Phase.RELEASE,true);
            h.assertTrue(machine.buffer().quantities().getOrDefault(water,0L)==200
                    &&machine.bufferRevision()==bufferRevision+1&&machine.inputSlot().snapshot().revision()==slotRevision
                    &&source.isAlive()&&source.getItem().get(ModDataComponents.REAGENT.get())==null,
                    "authenticated release transfers exact 2u once, keeps jug entity and input slot unchanged");
            ChemMasterDumpService.handle(fixture.player(),new ChemMasterDumpRequest(gesture,ChemMasterDumpRequest.Phase.RELEASE,
                    owner.epoch(),5,source.getUUID(),dimension,java.util.Optional.of(machinePos)));
            requireDumpAck(fixture,gesture,ChemMasterDumpRequest.Phase.RELEASE,false);
            h.assertTrue(machine.buffer().quantities().getOrDefault(water,0L)==200&&machine.bufferRevision()==bufferRevision+1,
                    "duplicate release cannot consume/add source again");
            h.succeed();
        } catch (RuntimeException|Error failure) { throw failure; }
        finally { fixture.close(); } });
    }

    private static void requireDumpAck(RegisteredServerPlayerFixture fixture,java.util.UUID gesture,
            ChemMasterDumpRequest.Phase phase,boolean accepted) {
        Object packet;
        while((packet=fixture.pollOutbound())!=null) {
            if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof ChemMasterDumpResult result && result.gestureId().equals(gesture)
                    && result.phase()==phase) {
                if(result.accepted()!=accepted) throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "unexpected dump acknowledgement phase="+phase+" accepted="+result.accepted()+" reason="+result.reason());
                return;
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("missing dump acknowledgement phase="+phase);
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=30)
    public static void staleSourceAndMachineExpectationsDoNotMutate(GameTestHelper h) {
        ServerLevel l=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(4,1,4)); var body=spawnStableActor(h,origin);
        BlockPos pos=origin.offset(1,0,0); l.setBlock(pos,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
        var be=(ChemMasterBlockEntity)l.getBlockEntity(pos); ItemStack jug=new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(ModReagents.createKey("water"),1f)));
        var item=LooseItemEntity.create(l,origin.getCenter(),jug);l.addFreshEntity(item);
        var before=be.buffer().snapshot(); var expected=new ChemMasterWorldDump.ExpectedSource(item.getUUID(),jug);
        item.setItem(new ItemStack(ModItems.JUG.get()));
        h.assertTrue(ChemMasterWorldDump.transferOwned(body,expected,pos,be.placementIdentity(),be.stateRevision(),be.bufferRevision(),()->true)==ChemMasterWorldDump.Result.DENIED
                &&be.buffer().snapshot().equals(before),"changed expected source denied without buffer change");
        item.setItem(jug.copy()); var replacement=java.util.UUID.randomUUID();
        h.assertTrue(ChemMasterWorldDump.transferOwned(body,expected,pos,replacement,be.stateRevision(),be.bufferRevision(),()->true)==ChemMasterWorldDump.Result.DENIED
                &&be.buffer().snapshot().equals(before)&&item.getItem().get(ModDataComponents.REAGENT.get())!=null,"replaced-machine identity denied without mutation");
        h.succeed();
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=30)
    public static void rangeAndColliderLosAreEnforced(GameTestHelper h) {
        ServerLevel l=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(4,1,4)); var body=spawnStableActor(h,origin);
        body.setPos(origin.getX()+0.7,origin.getY(),origin.getZ()+0.6);
        BlockPos pos=origin.offset(1,0,1);l.setBlock(pos,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
        var be=(ChemMasterBlockEntity)l.getBlockEntity(pos);ItemStack jug=new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(ModReagents.createKey("water"),1f)));
        var item=LooseItemEntity.create(l,origin.getCenter(),jug);l.addFreshEntity(item);
        var source=new ChemMasterWorldDump.ExpectedSource(item.getUUID(),jug);
        var positive=ChemMasterWorldDump.transferOwned(body,source,pos,be.placementIdentity(),be.stateRevision(),be.bufferRevision(),()->true);
        h.assertTrue(positive==ChemMasterWorldDump.Result.SUCCESS,"valid in-range unpowered setup is a positive control");
        item.setItem(jug.copy());
        var reachable=LooseItemEntity.create(l,origin.offset(4,0,0).getCenter(),jug);l.addFreshEntity(reachable);
        var reachableExpected=new ChemMasterWorldDump.ExpectedSource(reachable.getUUID(),jug);
        h.assertTrue(ChemMasterWorldDump.transferOwned(body,reachableExpected,pos,be.placementIdentity(),be.stateRevision(),be.bufferRevision(),()->true)
                ==ChemMasterWorldDump.Result.SUCCESS,"source at ordinary pickup reach is accepted without requiring gaze nomination");
        reachable.setItem(jug.copy());
        var snapshot=be.buffer().snapshot();
        l.setBlock(origin.offset(1,0,0),Blocks.STONE.defaultBlockState(),3);
        l.setBlock(origin.offset(1,1,0),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(ChemMasterWorldDump.transferOwned(body,source,pos,be.placementIdentity(),be.stateRevision(),be.bufferRevision(),()->true)==ChemMasterWorldDump.Result.DENIED
                &&be.buffer().snapshot().equals(snapshot),"solid collider between actor and target prevents transfer");
        l.setBlock(origin.offset(1,0,0),Blocks.AIR.defaultBlockState(),3);
        l.setBlock(origin.offset(1,1,0),Blocks.AIR.defaultBlockState(),3);
        var far=LooseItemEntity.create(l,origin.offset(8,0,0).getCenter(),jug);l.addFreshEntity(far);
        var farExpected=new ChemMasterWorldDump.ExpectedSource(far.getUUID(),jug);
        h.assertTrue(ChemMasterWorldDump.transferOwned(body,farExpected,pos,be.placementIdentity(),be.stateRevision(),be.bufferRevision(),()->true)==ChemMasterWorldDump.Result.DENIED
                &&be.buffer().snapshot().equals(snapshot),"source beyond ordinary pickup reach denied");
        BlockPos farMachine=origin.offset(8,0,0);l.setBlock(farMachine,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
        var farBe=(ChemMasterBlockEntity)l.getBlockEntity(farMachine);
        h.assertTrue(ChemMasterWorldDump.transferOwned(body,source,farMachine,farBe.placementIdentity(),farBe.stateRevision(),farBe.bufferRevision(),()->true)==ChemMasterWorldDump.Result.DENIED
                &&farBe.buffer().snapshot().contents().centContents().isEmpty(),"target beyond ordinary pickup reach denied");h.succeed();
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=30)
    public static void commitRevalidationAndNumericOverflowDoNotDrain(GameTestHelper h) {
        ServerLevel l=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(4,1,4));var body=spawnStableActor(h,origin);
        BlockPos pos=origin.offset(1,0,0);l.setBlock(pos,ModBlocks.CHEM_MASTER.get().defaultBlockState(),3);
        var be=(ChemMasterBlockEntity)l.getBlockEntity(pos);var water=ModReagents.createKey("water");
        var overflow=LooseItemEntity.create(l,origin.getCenter(),jugWith(water,1));l.addFreshEntity(overflow);
        var machineBefore=be.buffer().snapshot();
        // Seed just below numeric maximum, then stage a source which cannot be represented alongside it.
        var nearMax=ReagentComponent.fromCents(Map.of(water,com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.MAX_CENTS-50));
        var source=new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(new ReagentComponent(Map.of(water,1f)));
        var sourceBefore=new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(new ReagentComponent(Map.of(water,1f)));
        var buffer=new com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer(new com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer.Snapshot(nearMax,java.util.List.of(water)));
        h.assertTrue(!buffer.addWholeMixture(source)&&buffer.snapshot().contents().equals(nearMax)&&source.equals(sourceBefore),
                "overflow rejects whole staged mixture without changing source or buffer");
        var expected=new ChemMasterWorldDump.ExpectedSource(overflow.getUUID(),overflow.getItem());
        final int[] validations={0};
        var result=ChemMasterWorldDump.transferOwned(body,expected,pos,be.placementIdentity(),be.stateRevision(),be.bufferRevision(),()->{
            if(++validations[0]==2) overflow.setItem(jugWith(water,2));
            return true;
        });
        h.assertTrue(validations[0]==2,"valid transaction reached both authority validation phases");
        h.assertTrue(result==ChemMasterWorldDump.Result.DENIED&&be.buffer().snapshot().equals(machineBefore)
                &&overflow.getItem().get(ModDataComponents.REAGENT.get()).contents().getOrDefault(water,0f)==2f,
                "source changed during conditional commit prevents both transaction writes");h.succeed();
    }
    private static ItemStack jugWith(net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> key,float amount){
        ItemStack stack=new ItemStack(ModItems.JUG.get());stack.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of(key,amount)));return stack;
    }
    private static net.minecraft.world.entity.npc.Villager spawnStableActor(GameTestHelper h,BlockPos origin) {
        var actor=h.spawn(EntityType.VILLAGER,new BlockPos(4,1,4));
        actor.setNoAi(true); actor.setNoGravity(true);
        actor.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
        return actor;
    }
}
