package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native registered-player entry coverage; this fixture is not connected-client or render evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-machine-controls"})
public final class AtmosMachineControlsGameTests {
    private AtmosMachineControlsGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void registeredEmptyHandTogglesValveThroughOrdinaryBlockUse(GameTestHelper h) {
        var fixture = RegisteredServerPlayerFixture.register(h.getLevel(), "atmos-controls-valve-v1");
        BlockPos local = new BlockPos(8, 2, 8), pos = h.absolutePos(local);
        var lease = FixtureChunkTickets.acquire(h, new net.minecraft.world.phys.AABB(pos.getX()-2,pos.getY()-1,pos.getZ()-3,pos.getX()+3,pos.getY()+5,pos.getZ()+2));
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> { if (closed.compareAndSet(false,true)) { clearOwned(h.getLevel(),pos); fixture.close(); try { lease.close(); } catch (Exception ignored) { } } };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
        });
        FixtureChunkTickets.awaitEntityTicking(h, lease, 80, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                for (int x=-1;x<=1;x++) for (int z=-2;z<=0;z++) h.getLevel().setBlock(pos.offset(x,-1,z), Blocks.STONE.defaultBlockState(),3);
                h.setBlock(local, ModBlocks.GAS_VALVE.get());
                h.runAfterDelay(2, () -> {
                    try {
                        var be = (AtmosMachineBlockEntity) h.getLevel().getBlockEntity(pos);
                        var body = owner.body(); body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);
                        var query = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(),2));
                        String hand = query.hands().getFirst().id();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body,h.getLevel(),pos);
                        var request = new BodyBlockUseRequest(owner.epoch(),1,query.revision(),hand,null,pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                        var valveSounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
                        drainValveSounds(fixture, valveSounds);
                        var ports=be.gasPorts();require(ports.size()==2,"resolved valve declares two topology ports");
                        var start=ports.getFirst().key(pos);
                        require(be.settingsSnapshot().valveOpen().orElseThrow(), "resolved SS14 valve default begins open");
                        require(com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent.discover(h.getLevel(),start).orElseThrow().nodes().size()==2,"open valve connects its two gas ports");
                        require(AuthenticatedBlockUseDriver.use(fixture.player(),request) == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE, "registered admitted empty-hand USE reaches valve activation");
                        drainValveSounds(fixture, valveSounds);
                        require(valveSounds.size() == 1 && valveSounds.getFirst().getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.GAS_VALVE_TOGGLE.get(),
                                "accepted direct activation emits exactly one registered valve cue");
                        require(!be.settingsSnapshot().valveOpen().orElseThrow(), "valid activation closes valve once");
                        require(com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent.discover(h.getLevel(),start).orElseThrow().nodes().size()==1,"closed valve separates the two gas ports");
                        require(AuthenticatedBlockUseDriver.use(fixture.player(),request) == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.DENIED
                                &&!be.settingsSnapshot().valveOpen().orElseThrow(),"replayed block-use sequence cannot retoggle valve");
                        drainValveSounds(fixture, valveSounds);
                        require(valveSounds.size() == 1, "replayed activation emits no duplicate cue");
                        var refreshed=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(),3));
                        var again=new BodyBlockUseRequest(owner.epoch(),2,refreshed.revision(),hand,null,pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                        require(AuthenticatedBlockUseDriver.use(fixture.player(),again)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE
                                &&be.settingsSnapshot().valveOpen().orElseThrow(),"fresh admitted sequence reopens valve");
                        drainValveSounds(fixture, valveSounds);
                        require(valveSounds.size() == 2, "second accepted toggle emits one additional cue");
                        require(com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent.discover(h.getLevel(),start).orElseThrow().nodes().size()==2,"reopened valve restores connected topology");
                        require(AtmosMachineMenuService.session(fixture.player(),pos)==null, "manual valve activation does not open a settings session");
                        h.succeed();
                    } catch(Throwable e) { cleanup.run(); h.fail("Atmos valve admitted-use fixture: "+e); }
                });
            } catch(Throwable e) { cleanup.run(); h.fail("Atmos registered-player readiness: "+e); }
        },pos);
    }

    private static void drainValveSounds(RegisteredServerPlayerFixture fixture,
            java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                    && sound.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.GAS_VALVE_TOGGLE.get()) sounds.add(sound);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 120)
    public static void registeredEmptyHandFilterMenuEnforcesRevisionAndRetainedLineOfSight(GameTestHelper h) {
        var fixture=RegisteredServerPlayerFixture.register(h.getLevel(),"atmos-filter-ui-v1");
        BlockPos local=new BlockPos(8,2,8), pos=h.absolutePos(local);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(pos.getX()-2,pos.getY()-1,pos.getZ()-3,pos.getX()+3,pos.getY()+5,pos.getZ()+2));
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true)){clearOwned(h.getLevel(),pos);fixture.close();try{lease.close();}catch(Exception ignored){}}};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){cleanup.run();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){cleanup.run();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){cleanup.run();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,90,()->{try{
            var owner=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            for(int x=-1;x<=1;x++)for(int z=-2;z<=0;z++)h.getLevel().setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            h.setBlock(local,ModBlocks.GAS_FILTER.get());
            h.runAfterDelay(2,()->{try{
                var be=(AtmosMachineBlockEntity)h.getLevel().getBlockEntity(pos);
                var body=owner.body();body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);
                var query=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(),2));
                String hand=query.hands().getFirst().id();var hit=RegisteredPlayerReadyDriver.aimAt(body,h.getLevel(),pos);
                var use=new BodyBlockUseRequest(owner.epoch(),1,query.revision(),hand,null,pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),use)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE,"ordinary registered empty-hand activation opens unpowered filter workspace");
                var menu=AtmosMachineMenuService.session(fixture.player(),pos);
                require(menu!=null,"dispatcher installed exact server-owned atmos session");
                var refreshed=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(),3));
                require(refreshed.reason()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,
                        "ordinary hand query remains available alongside nonmodal workspace");
                require(!(fixture.player().containerMenu instanceof AtmosMachineMenu)&&menu.session()!=null&&menu.stillValid(fixture.player()),
                        "workspace has an independent server session and does not own/replace active container");
                var first=new AtmosMachineControlRequest(menu.menuId(),menu.session(),1,be.settingsRevision(),com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Operation.SET_FILTER_GAS,false,0,com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA.ordinal());
                var ack=AtmosMachineMenuService.handle(fixture.player(),first);
                require(ack.accepted()&&ack.requestId()==1&&ack.settings().filterGas().orElseThrow()==com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA
                        &&ack.controls().filterGas()&&ack.controls().kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,
                        "accepted setting returns correlated ACK, current settings, and resolved filter capabilities");
                require(be.settingsRevision()==1,"accepted request changes revision exactly once");
                var replay=AtmosMachineMenuService.handle(fixture.player(),first);
                require(!replay.accepted()&&be.settingsRevision()==1,"replayed request cannot mutate");
                var stale=new AtmosMachineControlRequest(menu.menuId(),menu.session(),2,0,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Operation.CLEAR_FILTER_GAS,false,0,0);
                var staleAck=AtmosMachineMenuService.handle(fixture.player(),stale);
                require(!staleAck.accepted()&&staleAck.requestId()==2&&staleAck.settings().revision()==1&&staleAck.settings().filterGas().orElseThrow()==com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,"stale revision rejects but returns current authoritative snapshot and ACK");
                var clear=new AtmosMachineControlRequest(menu.menuId(),menu.session(),3,1,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Operation.CLEAR_FILTER_GAS,false,0,0);
                var clearAck=AtmosMachineMenuService.handle(fixture.player(),clear);
                require(clearAck.accepted()&&clearAck.settings().filterGas().isEmpty()&&be.settingsRevision()==2,"next fresh request clears filter selection exactly once");
                for(int i=0;i<10;i++)menu.broadcastChanges();
                Object packet;AtmosMachineControlSnapshot periodic=null;
                while((packet=fixture.pollOutbound())!=null)if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AtmosMachineControlSnapshot snapshot)periodic=snapshot;
                require(periodic!=null&&periodic.requestId()==3,"periodic broadcast preserves last accepted ACK rather than resetting to -1");
                // Put opaque collision between the body eye and target; exact retained session must fail closed.
                h.getLevel().setBlock(pos.offset(0,0,-1),Blocks.STONE.defaultBlockState(),3);
                require(!menu.stillValid(fixture.player()),"retained session invalidates when LOS is blocked");
                var blocked=new AtmosMachineControlRequest(menu.menuId(),menu.session(),4,2,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Operation.CLEAR_FILTER_GAS,false,0,0);
                require(!AtmosMachineMenuService.handle(fixture.player(),blocked).accepted()&&be.settingsRevision()==2,"blocked retained menu cannot mutate");
                h.getLevel().setBlock(pos.offset(0,0,-1),Blocks.AIR.defaultBlockState(),3);
                h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(pos,ModBlocks.GAS_FILTER.get().defaultBlockState(),3);
                var replacement=(AtmosMachineBlockEntity)h.getLevel().getBlockEntity(pos);
                require(replacement!=be&&!menu.stillValid(fixture.player()),"replaced target BE invalidates exact retained menu");
                AtmosMachineMenuService.close(fixture.player(),new AtmosMachineSessionClose(menu.session()));
                require(AtmosMachineMenuService.session(fixture.player(),pos)==null,"dismissed workspace session is no longer authoritative");
                var afterClose=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(),4));
                require(afterClose.reason()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,
                        "ordinary hand query remains available after workspace close");
                h.succeed();
            }catch(Throwable e){cleanup.run();h.fail("Atmos filter UI admission: "+e);}});
        }catch(Throwable e){cleanup.run();h.fail("Atmos filter UI readiness: "+e);}},pos);
    }
    public static void clearOwned(net.minecraft.server.level.ServerLevel level,BlockPos pos){
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        for(int x=-1;x<=1;x++)for(int z=-2;z<=0;z++)level.setBlock(pos.offset(x,-1,z),Blocks.AIR.defaultBlockState(),3);
    }
    public static void require(boolean value,String message) { if(!value) throw new GameTestAssertException(message); }
}
