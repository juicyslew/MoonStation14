package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization.PortSeed;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlsGameTests;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineMenu;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineMenuService;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-machinery-runtime"})
public final class AtmosMixerRuntimeGameTests {
    private AtmosMixerRuntimeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void registeredMixerMixesFiniteInputsAtConfiguredRatio(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var fixture = RegisteredServerPlayerFixture.register(level, "atmos-mixer-ui-v1");
        var lease = FixtureChunkTickets.acquire(helper, new net.minecraft.world.phys.AABB(pos.getX()-2,pos.getY()-1,pos.getZ()-3,pos.getX()+3,pos.getY()+5,pos.getZ()+2));
        var block = ModBlocks.GAS_MIXER.get();
        helper.setBlock(local, block.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || !machine.profileIdentity().equals("gas_mixer"))
                    throw new IllegalStateException("registered mixer host missing");
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = owner.body(); body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);
                var query = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(),2));
                var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                var use = new BodyBlockUseRequest(owner.epoch(),1,query.revision(),query.hands().getFirst().id(),null,pos,hit.getDirection(),false,BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
                AtmosMachineControlsGameTests.require(AuthenticatedBlockUseDriver.use(fixture.player(),use)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE,"registered ordinary activation opens mixer workspace");
                var menu=AtmosMachineMenuService.session(fixture.player(),pos);
                AtmosMachineControlsGameTests.require(menu!=null&&!(fixture.player().containerMenu instanceof AtmosMachineMenu),"mixer session is server-owned and nonmodal");
                var request=new AtmosMachineControlRequest(menu.menuId(),menu.session(),1,machine.settingsRevision(),AtmosMachineSettings.Operation.SET_MIXER_FRACTION_A,false,.25,0);
                var ack=AtmosMachineMenuService.handle(fixture.player(),request);
                AtmosMachineControlsGameTests.require(ack.accepted()&&Math.abs(ack.settings().mixerFractionA().orElseThrow()-.25)<1e-8,"authenticated menu control sets mixer fraction to 25 percent");
                var enableRequest=new AtmosMachineControlRequest(menu.menuId(),menu.session(),2,ack.settings().revision(),AtmosMachineSettings.Operation.SET_ENABLED,true,0,0);
                var enableAck=AtmosMachineMenuService.handle(fixture.player(),enableRequest);
                AtmosMachineControlsGameTests.require(enableAck.accepted()&&enableAck.settings().enabled().orElse(false),"authenticated menu control enables mixer");
                var inventory = PipeGasInventorySavedData.forMutation(level);
                var seeds = new java.util.ArrayList<PortSeed>();
                for (var port : machine.gasPorts()) {
                    if (port.id().equals("inlet"))
                        seeds.add(new PortSeed(pos, port.id(), port.incarnation(),
                                new GasMixture(Map.of(GasType.OXYGEN, 100d), 293.15, port.volume())));
                    else if (port.id().equals("filter"))
                        seeds.add(new PortSeed(pos, port.id(), port.incarnation(),
                                new GasMixture(Map.of(GasType.NITROGEN, 100d), 293.15, port.volume())));
                }
                var initialization = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seeds);
                if (!initialization.successful())
                    throw new IllegalStateException("mixer port gas initialization failed: " + initialization.failure() + " " + initialization.detail());
                var outletPort = machine.gasPorts().stream().filter(port -> port.id().equals("outlet")).findFirst().orElseThrow();
                var seededSnapshot = inventory.snapshot();
                var vacuumOutlet = seededSnapshot.nodeShare(outletPort.key(pos));
                double canonicalVacuumTemperature = (double) (float) 293.15;
                if (vacuumOutlet.totalMoles() != 0 || vacuumOutlet.temperatureKelvin() != canonicalVacuumTemperature
                        || vacuumOutlet.volumeCubicMeters() != (double) (float) outletPort.volume())
                    throw new IllegalStateException("mixer outlet did not remain canonical vacuum after finite input initialization");
                var inletPort = machine.gasPorts().stream().filter(port -> port.id().equals("inlet")).findFirst().orElseThrow();
                var filterPort = machine.gasPorts().stream().filter(port -> port.id().equals("filter")).findFirst().orElseThrow();
                var inletNetwork = seededSnapshot.network(inletPort.key(pos)).orElseThrow();
                var filterNetwork = seededSnapshot.network(filterPort.key(pos)).orElseThrow();
                var outletNetwork = seededSnapshot.network(outletPort.key(pos)).orElseThrow();
                if (inletNetwork.id().equals(filterNetwork.id()) || inletNetwork.id().equals(outletNetwork.id())
                        || filterNetwork.id().equals(outletNetwork.id()))
                    throw new IllegalStateException("mixer ports did not resolve to three independent networks");
                var initial = inventory.snapshot();
                double initialEnergy = 0;
                double initialMoles = 0;
                for (var port : machine.gasPorts()) {
                    var entry = initial.get(port.key(pos)).orElseThrow();
                    var share=initial.nodeShare(entry.node()); initialEnergy += share.thermalEnergy();
                    initialMoles += share.totalMoles();
                }
                final double conservedInitialEnergy = initialEnergy;
                final double conservedInitialMoles = initialMoles;
                helper.runAfterDelay(15, () -> {
                    try {
                        var liveInventory=inventory.snapshot();
                        var inlet = liveInventory.nodeShare(machine.gasPorts().stream().filter(p->p.id().equals("inlet")).findFirst().orElseThrow().key(pos));
                        var second = liveInventory.nodeShare(machine.gasPorts().stream().filter(p->p.id().equals("filter")).findFirst().orElseThrow().key(pos));
                        var outlet = liveInventory.nodeShare(machine.gasPorts().stream().filter(p->p.id().equals("outlet")).findFirst().orElseThrow().key(pos));
                        if (outlet.moles(GasType.OXYGEN) <= 0 || outlet.moles(GasType.NITROGEN) <= 0
                         || Math.abs(outlet.moles(GasType.OXYGEN) / outlet.totalMoles() - .25) > 1e-5
                         || Math.abs(outlet.moles(GasType.NITROGEN) / outlet.totalMoles() - .75) > 1e-5
                                 || inlet.totalMoles() >= 100 || second.totalMoles() >= 100
                                 )
                            throw new IllegalStateException("live mixer did not consume finite inputs at the configured ratio");
                        double target = machine.settingsSnapshot().targetPressureKpa().orElseThrow();
                        double outletPressure = outlet.pressureKpa(machine.gasPorts().stream().filter(port -> port.id().equals("outlet")).findFirst().orElseThrow().volume());
                        double inletPressure = inlet.pressureKpa(machine.gasPorts().stream().filter(port -> port.id().equals("inlet")).findFirst().orElseThrow().volume());
                        double secondPressure = second.pressureKpa(machine.gasPorts().stream().filter(port -> port.id().equals("filter")).findFirst().orElseThrow().volume());
                        if (Math.abs(outletPressure - target) > 1e-3 || inlet.totalMoles() <= 0 || second.totalMoles() <= 0
                                || inletPressure <= target || secondPressure <= target)
                            throw new IllegalStateException("mixer did not reach target with nondepleted overstocked inputs");
                        var after = inventory.snapshot();
                        double afterEnergy = 0, afterMoles = 0;
                        for (var port : machine.gasPorts()) {
                            var entry = after.get(port.key(pos)).orElseThrow();
                            var share=after.nodeShare(entry.node()); afterEnergy += share.thermalEnergy();
                            afterMoles += share.totalMoles();
                        }
                        for (GasType gas : GasType.values()) {
                            double beforeSpecies = 0, afterSpecies = 0;
                            for (var port : machine.gasPorts()) {
                                beforeSpecies += initial.nodeShare(port.key(pos)).moles(gas);
                                afterSpecies += after.nodeShare(port.key(pos)).moles(gas);
                            }
                            if (!PipeGasLedger.conservedSpecies(beforeSpecies, afterSpecies))
                                throw new IllegalStateException("mixer violated finite inventory species conservation");
                        }
                        if (!PipeGasLedger.conservedEnergy(conservedInitialEnergy, afterEnergy))
                            throw new IllegalStateException("mixer violated finite inventory species/thermal conservation");
                        var once = outlet;
                        helper.runAfterDelay(24, () -> {
                        try {
                        var twice = inventory.snapshot().nodeShare(machine.gasPorts().stream().filter(p->p.id().equals("outlet")).findFirst().orElseThrow().key(pos));
                        if (Math.abs(twice.totalMoles()-once.totalMoles())>1e-5)
                            throw new IllegalStateException("mixer continued transferring after reaching its target outlet composition");
                        var conservedTwice=inventory.snapshot(); double moles=0,energy=0;
                         for(var port:machine.gasPorts()){var entry=conservedTwice.get(port.key(pos)).orElseThrow();var share=conservedTwice.nodeShare(entry.node());moles+=share.totalMoles();energy+=share.thermalEnergy();}
                        for (GasType gas : GasType.values()) {
                            double beforeSpecies = 0, afterSpecies = 0;
                             for (var port : machine.gasPorts()) { beforeSpecies += initial.nodeShare(port.key(pos)).moles(gas); afterSpecies += conservedTwice.nodeShare(port.key(pos)).moles(gas); }
                            if (!PipeGasLedger.conservedSpecies(beforeSpecies, afterSpecies)) throw new IllegalStateException("mixer violated species conservation after target-stop interval");
                        }
                        if(!PipeGasLedger.conservedEnergy(conservedInitialEnergy, energy))
                            throw new IllegalStateException("mixer violated conservation after target-stop interval");
                        helper.setBlock(local, Blocks.AIR);
                        fixture.close();
                        lease.close();
                        helper.succeed();
                        } catch (RuntimeException | Error failure) {
                            if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                            fixture.close(); lease.close(); throw failure;
                        }
                        });
                        return;
                    } catch (RuntimeException | Error failure) {
                        if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                        fixture.close();
                        lease.close();
                        throw failure;
                    }
                });
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                fixture.close();
                lease.close();
                throw failure;
            }
        });
    }
}
