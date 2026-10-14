package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"botany", "botany-fixture"})
@PrefixGameTestTemplate(false)
public final class BotanyFixtureCommandGameTests {
    private BotanyFixtureCommandGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=900)
    public static void singleCommandBuildsCompleteFixture(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        RegisteredServerPlayerFixture fixture=RegisteredServerPlayerFixture.register(level,"botany-fixture-command");
        helper.runAtTickTime(2,()->{
          try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            BlockPos clear=helper.absolutePos(new BlockPos(2,22,2));
            BlockPos origin=clear.offset(2,0,2);
            for(int x=(origin.getX()-4)>>4;x<=(origin.getX()+SmallBotanyFixtureLayout.WIDTH-1+4)>>4;x++)
                for(int z=(origin.getZ()-4)>>4;z<=(origin.getZ()+SmallBotanyFixtureLayout.DEPTH-1+4)>>4;z++) level.getChunk(x,z);
            var body=active.body();
            body.teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
            body.setDeltaMovement(Vec3.ZERO);
            var source=capture(fixture.player().createCommandSourceStack().withPermission(2));
            var dispatcher=level.getServer().getCommands().getDispatcher();
             int result=execute(dispatcher,"ms14 botany_fixture",source.stack());
             helper.assertTrue(result==1 && source.feedback().stream().anyMatch(message -> message.contains("setup accepted")),"setup command accepted: "+source.feedback());
             helper.runAfterDelay(30,()->{
                 try {
                   helper.assertTrue(source.feedback().stream().anyMatch(message -> message.contains("Botany fixture ready")),"setup completion feedback: "+source.feedback());
                   helper.assertTrue(source.feedback().stream().anyMatch(message -> message.contains("kit supplies granted")),"completion confirms kit grant through command feedback: "+source.feedback());
                   helper.assertTrue(execute(dispatcher,"ms14 botany_fixture status",source.stack())==1,"status succeeds: "+source.feedback());
                  var tomato=tray(level,SmallBotanyFixtureLayout.normalTray(origin,false));
                  var wheat=tray(level,SmallBotanyFixtureLayout.normalTray(origin,true));
                  var weeds=tray(level,SmallBotanyFixtureLayout.dryTray(origin,false));
                  helper.assertTrue(tomato!=null && tomato.plantState().ready(),"tomato fixture tray is cached ready");
                  helper.assertTrue(wheat!=null && wheat.plantState().ready(),"wheat fixture tray is cached ready");
                  helper.assertTrue(weeds!=null && weeds.plantState().seed()==null && weeds.warningBits()==28,
                          "weedy fixture tray remains empty and has expected warning bits: "+(weeds==null?"missing":weeds.warningBits()));
                  BlockPos sample=SmallBotanyFixtureLayout.sample(origin);
                  var reading=AtmosphereService.INSTANCE.readAtmosphere(level,sample).orElseThrow();
                  double pressure=reading.mixture().pressureKpa(1.0),temperature=reading.mixture().temperatureKelvin();
                   helper.assertTrue(reading.status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE
                          &&Double.isFinite(pressure)&&pressure>=81&&pressure<=121&&Math.abs(temperature-293)<=10,
                           "initialized command room is known finite at expected pressure/temp: "+reading);
                   Vec3 entry=SmallBotanyFixtureLayout.entry(origin);
                   helper.assertTrue(body.isAddedToLevel() && level.getEntity(body.getUUID())==body && body.position().distanceTo(entry) < 1.0,
                            "authorized existing body entered fixture: "+diagnostics(fixture.player(),body,level));
                   BlockPos lampPos=SmallBotanyFixtureLayout.lamp(origin);
                   helper.assertTrue(level.getBlockState(lampPos).is(com.juicyslew.moonstation14.block.ModBlocks.POWER_LAMP.get())
                           &&level.getBlockState(lampPos).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING)==Direction.EAST,
                           "real APC-fed lamp is mounted facing its cable host");
                   for (String obsolete : java.util.List.of("build","initialize","enter","kit","scenarios")) {
                       try { dispatcher.execute("ms14 botany_fixture "+obsolete,source.stack()); throw new GameTestAssertException("obsolete command remains exposed: "+obsolete); }
                       catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
                   }
                     var sourceHv=new CableFaceNode(origin.offset(8,1,-3),Direction.WEST,CableTier.HV);
                     var subHv=new CableFaceNode(origin.offset(8,1,-1),Direction.WEST,CableTier.HV);
                     var subMv=new CableFaceNode(origin.offset(6,1,-1),Direction.EAST,CableTier.MV);
                     var apcMv=new CableFaceNode(origin.offset(6,1,1),Direction.EAST,CableTier.MV);
                     var apcOutput=new CableFaceNode(origin.offset(8,1,1),Direction.WEST,CableTier.APC);
                      var grinderApcHost=new CableFaceNode(origin.offset(8,1,2),Direction.WEST,CableTier.APC);
                      var grinderApcHostAlternate=new CableFaceNode(origin.offset(8,1,3),Direction.WEST,CableTier.APC);
                      var lampApcHost=new CableFaceNode(origin.offset(8,3,2),Direction.WEST,CableTier.APC);
                      var knownPorts=java.util.List.of(sourceHv,subHv,subMv,apcMv,apcOutput,grinderApcHost,grinderApcHostAlternate,lampApcHost);
                     PowerFixtureReadiness.await(helper,level,500,
                              ()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,knownPorts.toArray(CableFaceNode[]::new)),
                              ()->"botany native power topology unavailable; ports="+knownPorts
                                      +", topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)
                                      +", indexedDeviceCount="+PowerRuntime.indexedDeviceCount(level),()->{
                                 try {
                                     BlockPos apcPos=SmallBotanyFixtureLayout.apc(origin);
                                      PowerDeviceBlockEntity apc=(PowerDeviceBlockEntity)level.getBlockEntity(apcPos);
                                      double seededJoules=100_000;
                                      var machine=(ReagentGrinderBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.grinder(origin));
                                      helper.assertTrue(machine!=null && machine.isLoadedValid(),"fixture grinder owner loaded valid");
                                      var ledger=LevelItemLocationLedgers.forLevel(level);
                                      var beaker=new net.minecraft.world.item.ItemStack(ModItems.BEAKER.get());
                                      beaker.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of()));
                                      var wheatStack=ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"),5);
                                      var beakerToken=new ItemToken("botany-fixture-beaker-"+UUID.randomUUID());
                                      var wheatToken=new ItemToken("botany-fixture-wheat-"+UUID.randomUUID());
                                      // Fixture-arranged ownership records model test setup, not player input.
                                      var beakerOrigin=new ItemLocation.BodyHand(UUID.randomUUID(),"left");
                                      var wheatOrigin=new ItemLocation.BodyHand(UUID.randomUUID(),"left");
                                      helper.assertTrue(ledger.register(beakerToken,beakerOrigin).accepted()
                                              &&ledger.register(wheatToken,wheatOrigin).accepted(),"fixture items registered with unique ownership");
                                      var beakerSlot=machine.beakerSlot();
                                      var wheatSlot=machine.chamberSlots().get(0);
                                      helper.assertTrue(beakerSlot.insert(0,ledger.snapshot().revision(),beakerToken,beakerOrigin,beaker),"fixture beaker inserted through slot owner");
                                      helper.assertTrue(wheatSlot.insert(0,ledger.snapshot().revision(),wheatToken,wheatOrigin,wheatStack),"fixture wheat inserted through slot owner");
                                      helper.assertTrue(machine.startTrusted(machine.startProof(),level.getGameTime(),true),"trusted grinder domain start accepted");
                                      PowerFixtureReadiness.await(helper,level,30,
                                              ()->PowerRuntime.processingMeter(level,machine).known()
                                                      &&PowerRuntime.processingMeter(level,machine).deliveredWatts()==750,
                                              ()->"botany grinder lacks actual 750 W allocation: "+PowerRuntime.processingMeter(level,machine),()->{
                                       PowerFixtureReadiness.await(helper,level,30,
                                               ()->apc.energyJoules()>seededJoules
                                                      && PowerRuntime.apcOutputObservation(level,apc).known()
                                                      && PowerRuntime.apcOutputObservation(level,apc).outputWatts()>0
                                                      &&level.getBlockState(SmallBotanyFixtureLayout.lamp(origin)).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)
                                                       &&level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,net.minecraft.core.BlockPos.containing(SmallBotanyFixtureLayout.entry(origin)))>0,
                                              ()->"botany APC did not charge from native power: energy="+apc.energyJoules()
                                                      +", output="+PowerRuntime.apcOutputObservation(level,apc)
                                                      +", lamp="+level.getBlockState(SmallBotanyFixtureLayout.lamp(origin))
                                                       +", entryBlockLight="+level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,net.minecraft.core.BlockPos.containing(SmallBotanyFixtureLayout.entry(origin)))
                                                      +", solve="+PowerRuntime.lastSolveDiagnostic(level),
                                              ()->{ fixture.close(); helper.succeed(); });
                                      });
                                 } catch(RuntimeException | Error failure) {
                                     fixture.close();
                                     throw failure;
                                 }
                             });
                } catch(RuntimeException | Error failure) {
                  fixture.close();
                  throw failure;
                }
              });
          } catch(RuntimeException | Error failure) {
            fixture.close();
            throw failure;
          }
        });
    }

    private static HydroponicsTrayBlockEntity tray(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HydroponicsTrayBlockEntity tray ? tray : null;
    }

    private static String diagnostics(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.entity.LivingEntity body, ServerLevel level) {
        return "player[mode="+player.gameMode.getGameModeForPlayer()+",camera="+player.getCamera().getUUID()+"/body="+body.getUUID()
                +",alive="+player.isAlive()+",passenger="+player.isPassenger()+",menu="+(player.containerMenu==player.inventoryMenu)
                +"] body[alive="+body.isAlive()+",added="+body.isAddedToLevel()+",passenger="+body.isPassenger()
                +",levelEntity="+(level.getEntity(body.getUUID())==body)+"] active="+LifecycleCharacterSessionControl.activeCharacterBody(player).isPresent()
                +" authority="+HandActorAuthority.resolve(player).isPresent();
    }

    private record CapturedSource(net.minecraft.commands.CommandSourceStack stack, java.util.List<String> feedback) { }
    private static CapturedSource capture(net.minecraft.commands.CommandSourceStack original) {
        var messages=new java.util.ArrayList<String>();
        CommandSource wrapper=new CommandSource() {
            @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return new CapturedSource(original.withSource(wrapper),messages);
    }

    private static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                               String command,net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command,source); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("public command syntax failed: "+command+": "+failure.getMessage());
        }
    }
}
