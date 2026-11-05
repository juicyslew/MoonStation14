package com.juicyslew.moonstation14.ms14.eva.fixture;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the real registered owner command from its normal command source. */
@PrefixGameTestTemplate(false)
@GameTestTags("eva_fixture")
public final class EvaFixtureCommandGameTests {
    private EvaFixtureCommandGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=240)
    public static void oneBuildCommandCreatesEquippedOxygenReadyVacuumRoom(GameTestHelper helper) {
        var level=(net.minecraft.server.level.ServerLevel)helper.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"eva-fixture-build");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                try { EvaFixtureCommands.forgetFixtureSiteForTest(level.getServer(),fixture.player().getUUID(),level.dimension()); fixture.close(); }
                catch(Throwable t) { if(info.getError()!=null)info.getError().addSuppressed(t);else info.fail(t); }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){cleanup(i);}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){cleanup(i);}
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){cleanup(a);}
        });
        helper.runAtTickTime(2,()->{
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body=active.body();
                var clear=helper.absolutePos(new net.minecraft.core.BlockPos(8,20,8));
                body.teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
                body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                var before=body.blockPosition();
                var origin=before.offset(3,0,-EvaFixturePlan.DEPTH/2);
                int result=level.getServer().getCommands().getDispatcher().execute("ms14 eva_fixture build",fixture.player().createCommandSourceStack().withPermission(2));
                require(result==1,"registered build command returned success (not NOT READY)");
                require(body.level()==level && body.blockPosition().equals(origin.offset(3,1,3)),"same authorized body entered fixture interior");
                var room=AtmosphereService.INSTANCE.readAtmosphere(level,origin.offset(3,2,3)).orElse(null);
                require(room!=null&&room.status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE&&room.mixture().totalMoles()==0&&room.mixture().pressureKpa()==0,
                        "command seeded known finite zero-gas vacuum");
                for(var p:EvaFixturePlan.lightingPositions(origin)) require(level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.SEA_LANTERN),"roof light remains sealed");
                var hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(hands!=null&&hands.compatible(body),"canonical body hands remain valid");
                var worn=com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body);
                require(worn.stream().anyMatch(e->e.slotId().equals("head")&&e.stack().is(com.juicyslew.moonstation14.item.ModEquipmentItems.EVA_HELMET.get())),"helmet is actually worn in resolved head slot");
                require(worn.stream().anyMatch(e->e.slotId().equals("outerClothing")&&e.stack().is(com.juicyslew.moonstation14.item.ModEquipmentItems.EVA_SUIT.get())),"EVA suit is actually worn in resolved outer slot");
                var mask=worn.stream().filter(e->e.stack().is(com.juicyslew.moonstation14.item.ModEquipmentItems.BREATHING_MASK.get())).findFirst().orElse(null);
                require(mask!=null&&!com.juicyslew.moonstation14.ms14.equipment.BreathToolItemState.isLowered(mask.stack()),"breathing mask is worn and raised");
                var identity=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(body).orElseThrow();
                var barotrauma=identity.component(com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent.class).orElseThrow();
                var protection=com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection.pressure(worn,barotrauma);
                require(protection.isPresent(),"actual worn gear supplies resolved pressure protection");
                var selected=com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElse(null);
                require(selected!=null&&selected.contents().mixture().totalMoles()>0&&com.juicyslew.moonstation14.ms14.lung.InternalsSystem.status(body).working(),"exact selected finite oxygen tank is connected and working");
                String tankToken=selected.token();double tankMoles=selected.contents().mixture().totalMoles();
                require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.toggle(body,tankToken),"real internals toggle disconnects selected tank");
                require(!com.juicyslew.moonstation14.ms14.lung.InternalsSystem.status(body).connected(),"internals off state is observable");
                require(AtmosphereService.INSTANCE.readAtmosphere(level,origin.offset(3,2,3)).map(s->s.mixture().totalMoles()==0).orElse(false),"toggle does not refill or alter vacuum room");
                require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.toggle(body,tankToken)&&com.juicyslew.moonstation14.ms14.lung.InternalsSystem.status(body).working(),"real internals toggle reconnects same source");
                require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).map(s->s.contents().mixture().totalMoles()==tankMoles).orElse(false),"toggle does not refill tank");
                for(String removed:java.util.List.of("initialize","enter","kit")){
                    boolean absent=false;try{level.getServer().getCommands().getDispatcher().execute("ms14 eva_fixture "+removed,fixture.player().createCommandSourceStack().withPermission(2));}
                    catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){absent=true;}
                    require(absent,"obsolete setup command is absent: "+removed);
                }
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MS14_EVA_FIXTURE_COMMAND_PASS test=oneBuildCommandCreatesEquippedOxygenReadyVacuumRoom command=ms14_eva_fixture_build result={} finite=true vacuum=true equipped=true internalsConnected=true entered=true roofLights=true",result);
                helper.succeed();
            } catch(Throwable failure) { try { EvaFixtureCommands.forgetFixtureSiteForTest(level.getServer(),fixture.player().getUUID(),level.dimension());fixture.close(); }catch(Throwable cleanup){failure.addSuppressed(cleanup);} throw failure instanceof RuntimeException r?r:new GameTestAssertException(failure.toString()); }
        });
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
