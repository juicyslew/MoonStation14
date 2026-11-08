package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelData;
import com.juicyslew.moonstation14.ms14.hands.live.TeslaRepairHandGameTestAccess;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered server-player runtime path; exercises ordinary admitted block use, not a synthetic admission handshake. */
@PrefixGameTestTemplate(false)
@GameTestTags({"tesla", "tesla-repair"})
public final class TeslaMachineRepairGameTests {
    private TeslaMachineRepairGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=120)
    public static void admittedRepairCompletesAtOneSecondAndSpendsFuelAfterPassiveBurn(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"tesla-repair-complete-v1");
        var lease=lease(h); var cleanup=cleanup(h,fixture,lease);
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{},h.absolutePos(new BlockPos(8,2,8)));
        h.runAtTickTime(2,()->{
            try {
                var owner=RegisteredPlayerReadyDriver.readyAndQuery(fixture); ServerLevel level=h.getLevel();
                BlockPos pos=h.absolutePos(new BlockPos(8,2,8)); platform(level,pos); level.setBlock(pos,ModBlocks.TESLA_COIL.get().defaultBlockState(),3);
                h.runAfterDelay(2,()->{
                    try {
                        var machine=(TeslaMachineBlockEntity)level.getBlockEntity(pos); var profile=machine.profile();
                        require(profile!=null&&profile.repair()!=null&&profile.repair().durationSeconds()==1.0&&profile.repair().fuelCost()==5,"use existing resolved coil repair catalog profile");
                        require(machine.receiveLightningStrike()&&machine.structuralDamage()>0,"coil damaged through actual machine integrity owner");
                        var body=owner.body(); body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);
                        require(level.isPositionEntityTicking(BlockPos.containing(body.position())),"registered body remains entity-ticking inside shared fixture lease");
                        LiveHands hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); String hand=hands.activeHand();
                        var catalog=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverTools();
                        ToolPrototypeData tool=catalog.get(com.juicyslew.moonstation14.ms14.construction.tool.ModTools.WELDER);
                        ItemStack welder=new ItemStack(ModItems.WELDER.get()); var fuel=tool.welder().orElseThrow();
                        var initialFuel=new WelderFuelData(100,true,level.getServer().overworld().getGameTime()); welder.set(ModDataComponents.WELDER_FUEL.get(),initialFuel);
                        TeslaRepairHandGameTestAccess.hold(body,hand,"tesla-repair-welder",welder);
                        var query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(owner.epoch(),2)); var held=query.hands().stream().filter(x->x.id().equals(hand)).findFirst().orElseThrow();
                        var hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos); var request=new BodyBlockUseRequest(owner.epoch(),1,query.revision(),hand,held.token(),pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                        var admission=BodyHandRequestService.inspectBlockUse(fixture.player(),request).admission();
                         require(admission!=null&&TeslaMachineRepairUse.begin(fixture.player(),request,admission),"registered-player admitted USE begins repair handler");
                         h.runAfterDelay(5,()->{
                             var current=hands(body); var currentFuel=current.stackCopy(hand).orElseThrow().get(ModDataComponents.WELDER_FUEL.get());
                             require(hasActive(body)&&current!=admission.hands()
                                     &&current.token(hand).filter(request.expectedToken()::equals).isPresent()
                                     &&current.revision()>=request.expectedRevision()&&currentFuel.fuel()<100,
                                     "normal owner fuel tick republishes immutable hands while preserving logical hand identity and active repair");
                             body.setYRot(body.getYRot()+90); body.setYHeadRot(body.getYHeadRot()+90);
                         });
                         h.runAfterDelay(19,()->require(machine.structuralDamage()>0&&hasActive(body),
                                 "repair must remain active before deadline; "+diagnostic(level,fixture.player(),body,request,admission,machine,pos,hands.revision(),welder,tool)));
                        h.runAfterDelay(21,()->{
                             try { require(machine.structuralDamage()==0,"deadline fully clears structural damage; "+diagnostic(level,fixture.player(),body,request,admission,machine,pos,hands.revision(),welder,tool));
                                double after=hands(body).stackCopy(hand).orElseThrow().get(ModDataComponents.WELDER_FUEL.get()).fuel();
                                 double expected=initialFuel.tick(fuel,level.getServer().overworld().getGameTime()).fuel()-5;
                                 require(Math.abs(after-expected)<1e-6,"completion spends exactly 5 after fuel-state passive burn projection; expected="+expected+" after="+after);
                                h.succeed();
                            } catch(Throwable e){h.fail("Tesla repair completion: "+e);} finally {fixture.close();}
                        });
                    } catch(Throwable e){fixture.close();h.fail("Tesla repair fixture: "+e);}
                });
            } catch(Throwable e){fixture.close();h.fail("Tesla player readiness: "+e);}
        });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=120)
    public static void litInsufficientAndUnlitWeldersAreRejectedWithoutMutation(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"tesla-repair-denied-v1");
        var lease=lease(h); cleanup(h,fixture,lease);
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{},h.absolutePos(new BlockPos(8,2,8)));
        h.runAtTickTime(2,()->{ try {
            var owner=RegisteredPlayerReadyDriver.readyAndQuery(fixture); ServerLevel level=h.getLevel();
            BlockPos pos=h.absolutePos(new BlockPos(8,2,8)); platform(level,pos); level.setBlock(pos,ModBlocks.TESLA_COIL.get().defaultBlockState(),3);
            h.runAfterDelay(2,()->{ try {
                var machine=(TeslaMachineBlockEntity)level.getBlockEntity(pos); machine.receiveLightningStrike(); int damage=machine.structuralDamage();
                var body=owner.body(); body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);
                var tool=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverTools().get(com.juicyslew.moonstation14.ms14.construction.tool.ModTools.WELDER);
                var composition=tool.welder().orElseThrow(); LiveHands hands=hands(body); String hand=hands.activeHand();
                for(int i=0;i<2;i++) {
                    boolean unlit=i==1; double fuel=unlit?Math.max(5,composition.initialFuel()):4;
                    ItemStack welder=new ItemStack(ModItems.WELDER.get()); welder.set(ModDataComponents.WELDER_FUEL.get(),new WelderFuelData(fuel,!unlit,level.getServer().overworld().getGameTime()));
                    TeslaRepairHandGameTestAccess.hold(body,hand,"tesla-repair-denied-"+i,welder);
                    var query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(owner.epoch(),2+i)); var held=query.hands().stream().filter(x->x.id().equals(hand)).findFirst().orElseThrow();
                    var hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos); var req=new BodyBlockUseRequest(owner.epoch(),1+i,query.revision(),hand,held.token(),pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                    var admission=BodyHandRequestService.inspectBlockUse(fixture.player(),req).admission();
                    require(admission!=null&&!TeslaMachineRepairUse.begin(fixture.player(),req,admission),"welder with " +(unlit?"unlit fuel":"lit insufficient fuel")+" is rejected at begin");
                    var after=hands(body).stackCopy(hand).orElseThrow().get(ModDataComponents.WELDER_FUEL.get());
                    require(after.fuel()==fuel&&after.lit()==!unlit,"rejected begin does not spend fuel or change ignition state");
                    require(machine.structuralDamage()==damage,"rejected use leaves damaged coil unchanged");
                    require(!hasActive(body),"rejected use creates no active timed operation");
                }
                h.succeed();
            } catch(Throwable e){h.fail("Tesla repair denial: "+e);} finally {fixture.close();} });
        } catch(Throwable e){fixture.close();h.fail("Tesla player readiness: "+e);} });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=120)
    public static void movingBodyAfterAdmissionCancelsBeforeRepairOrCompletionCharge(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"tesla-repair-interrupt-v1");
        var lease=lease(h); cleanup(h,fixture,lease);
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->{},h.absolutePos(new BlockPos(8,2,8)));
        h.runAtTickTime(2,()->{ try {
            var owner=RegisteredPlayerReadyDriver.readyAndQuery(fixture); ServerLevel level=h.getLevel();
            BlockPos pos=h.absolutePos(new BlockPos(8,2,8)); platform(level,pos); level.setBlock(pos,ModBlocks.TESLA_COIL.get().defaultBlockState(),3);
            h.runAfterDelay(2,()->{ try {
                var machine=(TeslaMachineBlockEntity)level.getBlockEntity(pos); machine.receiveLightningStrike(); int damage=machine.structuralDamage();
            var body=owner.body(); body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5); LiveHands hands=hands(body); String hand=hands.activeHand();
                var tool=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverTools().get(com.juicyslew.moonstation14.ms14.construction.tool.ModTools.WELDER);
                var composition=tool.welder().orElseThrow(); ItemStack welder=new ItemStack(ModItems.WELDER.get());
                double initial=100; var initialFuel=new WelderFuelData(initial,true,level.getServer().overworld().getGameTime());
                welder.set(ModDataComponents.WELDER_FUEL.get(),initialFuel);
                TeslaRepairHandGameTestAccess.hold(body,hand,"tesla-repair-interrupt-welder",welder);
                var query=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(owner.epoch(),2)); var held=query.hands().stream().filter(x->x.id().equals(hand)).findFirst().orElseThrow();
                var hit=RegisteredPlayerReadyDriver.aimAt(body,level,pos); var req=new BodyBlockUseRequest(owner.epoch(),1,query.revision(),hand,held.token(),pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                var admission=BodyHandRequestService.inspectBlockUse(fixture.player(),req).admission();
                require(admission!=null&&TeslaMachineRepairUse.begin(fixture.player(),req,admission),"registered admitted use starts repair");
                h.runAfterDelay(5,()->{ body.setPos(body.getX()+1,body.getY(),body.getZ()); });
                h.runAfterDelay(30,()->{ try {
                    require(machine.structuralDamage()==damage,"movement interruption preserves coil damage");
                    var state=hands(body).stackCopy(hand).orElseThrow().get(ModDataComponents.WELDER_FUEL.get());
                    double expected=initialFuel.tick(composition,level.getServer().overworld().getGameTime()).fuel();
                    require(Math.abs(state.fuel()-expected)<1e-6,"cancellation matches passive-burn projection only; expected="+expected+" fuel="+state.fuel());
                    require(state.fuel()>initial-5,"no five-unit completion charge");
                    require(!hasActive(body),"interrupted repair timer is no longer active"); h.succeed();
                } catch(Throwable e){h.fail("Tesla repair interruption: "+e);} finally {fixture.close();} });
            } catch(Throwable e){fixture.close();h.fail("Tesla interruption setup: "+e);} });
        } catch(Throwable e){fixture.close();h.fail("Tesla player readiness: "+e);} });
    }

    private static LiveHands hands(net.minecraft.world.entity.LivingEntity body){return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());}
    private static com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.Lease lease(GameTestHelper h){
        BlockPos origin=h.absolutePos(new BlockPos(8,2,8));
        return com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-2,origin.getY()-1,origin.getZ()-4,origin.getX()+3,origin.getY()+5,origin.getZ()+2));
    }
    private static void platform(ServerLevel level,BlockPos coil){
        for(int x=-2;x<=2;x++) for(int z=-4;z<=1;z++) {
            BlockPos ground=coil.offset(x,-1,z); if(!ground.equals(coil)) level.setBlock(ground,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        }
    }
    private static AutoCloseable cleanup(GameTestHelper h,RegisteredServerPlayerFixture fixture,AutoCloseable lease){
        var closed=new java.util.concurrent.atomic.AtomicBoolean(); Runnable close=()->{if(closed.compareAndSet(false,true)){fixture.close();try{lease.close();}catch(Exception ignored){}}};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close.run();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close.run();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){close.run();}});
        return ()->close.run();
    }
    private static boolean hasActive(net.minecraft.world.entity.LivingEntity body){var state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());return state!=null&&state.operations().stream().anyMatch(op->op.phase()==TimedOperation.Phase.ACTIVE);}
    private static String diagnostic(ServerLevel level,net.minecraft.server.level.ServerPlayer actor,
            net.minecraft.world.entity.LivingEntity body,BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission,TeslaMachineBlockEntity machine,BlockPos pos,
            long admittedRevision,ItemStack initial,ToolPrototypeData tool){
        LiveHands current=hands(body); ItemStack held=current==null?null:current.stackCopy(request.handId()).orElse(null);
        var fuel=held==null?null:held.get(ModDataComponents.WELDER_FUEL.get());
        var initialFuel=initial.get(ModDataComponents.WELDER_FUEL.get());
        boolean target=BodyBlockUseTargetProbe.probe(level,body,pos,request.face())!=null;
        var operationState=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return "tick="+level.getServer().overworld().getGameTime()+", damage="+machine.structuralDamage()
                +", ops="+(operationState==null?null:operationState.operations())+", bodyDelta="+body.position().subtract(pos.getX()+.5,pos.getY(),pos.getZ()-1.5)
                +", onGround="+body.onGround()+", velocity="+body.getDeltaMovement()+", alive="+body.isAlive()
                +", registered="+(actor.getServer().getPlayerList().getPlayer(actor.getUUID())==actor)
                +", authority="+HandActorAuthority.revalidate(actor,admission.authority())
                +", canAct="+com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                +", handsReplacedByOwnerTick="+(current!=admission.hands())+", compatible="+(current!=null&&current.compatible(body))
                +", revisions="+admittedRevision+"/"+(current==null?-1:current.revision())
                +", token="+(current!=null&&current.token(request.handId()).filter(request.expectedToken()::equals).isPresent())
                +", held="+held+", initialFuel="+initialFuel+", currentFuel="+fuel
                +", revisionContinuity="+(current!=null&&com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService.revisionContinuity(request.expectedRevision(),current.revision(),com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService.passiveFuelChanged(initial,held)))
                +", passiveSame="+com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService.sameStackOrPassiveFuel(initial,held)
                +", fuelSpendable="+com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService.canSpendFuel(held,tool,5,level.getServer().overworld().getGameTime())
                +", targetProbe="+target+", machineSame="+(level.getBlockEntity(pos)==machine)+", hasActive="+hasActive(body);
    }
    private static void require(boolean value,String message){if(!value)throw new GameTestAssertException(message);}
}
