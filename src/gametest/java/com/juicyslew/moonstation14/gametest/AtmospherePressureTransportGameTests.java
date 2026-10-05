package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/** Headless service integration fixture for real, sealed-room pressure transport. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere"})
public final class AtmospherePressureTransportGameTests {
    private AtmospherePressureTransportGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "pressure_transport", timeoutTicks = 2400, templateNamespace = "moonstation14")
    public static void largeSealedRoomCommitsPressureTransport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (AtmosphereService.INSTANCE.isEnabled())
            throw new GameTestAssertException("pressure fixture requires global atmosphere disabled");
        int x0 = 1, x1 = 11, y0 = 1, y1 = 11, z0 = 1, z1 = 11;
        for (int x = x0 - 1; x <= x1; x++) for (int z = z0 - 1; z <= z1; z++) {
            helper.setBlock(new BlockPos(x, y0 - 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, y1, z), Blocks.STONE);
        }
        for (int y = y0; y < y1; y++) for (int z = z0 - 1; z <= z1; z++) {
            helper.setBlock(new BlockPos(x0 - 1, y, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x1, y, z), Blocks.STONE);
        }
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            helper.setBlock(new BlockPos(x, y, z0 - 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, y, z1), Blocks.STONE);
        }
        for (int y=y0; y<y1; y++) for(int z=z0; z<z1; z++) for(int x=x0; x<x1; x++)
            helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos source = helper.absolutePos(new BlockPos(x0, y0, z0));
        for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) for (int x = x0; x < x1; x++)
            service.activateCellAndNeighbors(level, helper.absolutePos(new BlockPos(x, y, z)));
        int classifiedTicks = 0;
        while (classifiedTicks < 1800) {
            boolean ready = true;
            for (int y = y0; y < y1 && ready; y++) for (int z = z0; z < z1 && ready; z++)
                for (int x = x0; x < x1; x++) if (!service.diagnosticCell(level,
                        helper.absolutePos(new BlockPos(x, y, z))).status().equals("finite")) { ready = false; break; }
            if (ready) break;
            service.tick(level, ++classifiedTicks);
        }
        for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) for (int x = x0; x < x1; x++) {
            BlockPos p=helper.absolutePos(new BlockPos(x,y,z));
            if(!service.diagnosticCell(level,p).status().equals("finite"))
                throw new GameTestAssertException("finite room classification timed out at "+p+" status="
                        +service.diagnosticCell(level,p).status()+" timing="+service.diagnosticTiming(level));
        }
        for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) for (int x = x0; x < x1; x++) {
            BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
            if (!service.addGas(level, p, GasType.NITROGEN,
                    40,
                    x == x0 && y == y0 && z == z0 ? 5000 : 293))
                throw new GameTestAssertException("failed to seed finite room cell " + p);
        }
        double[] initial = pressureExtrema(service, level, helper, x0, x1, y0, y1, z0, z1);
        if (initial[1] - initial[0] < 1000) throw new GameTestAssertException("fixture did not create pressure pulse");
        double initialN2=inventory(service,level,helper,x0,x1,y0,y1,z0,z1,false);
        double initialEnergy=inventory(service,level,helper,x0,x1,y0,y1,z0,z1,true);
        double initialMaxN2=maxCellInventory(service,level,helper,x0,x1,y0,y1,z0,z1,false);
        double initialMaxEnergy=maxCellInventory(service,level,helper,x0,x1,y0,y1,z0,z1,true);
        AtmosphereRuntimeTelemetry.Snapshot operationStart=service.diagnosticRuntime(level);
        if(operationStart==null) throw new GameTestAssertException("missing runtime operation counters before pressure window");
        double initialSpan=initial[1]-initial[0];
        double[] bestBeforeGroups={initialSpan};
        boolean[] pressureStageWork={false};
        boolean[] writerActive={false};
        int[] writerWrites={0}; long[] lastWriteTick={-1};
        double[] realizedProducerN2={0}, realizedProducerEnergy={0}, producerN2Ulps={0}, producerEnergyUlps={0};
        double[] maxProducerN2={initialMaxN2}, maxProducerEnergy={initialMaxEnergy};
        helper.startSequence().thenExecuteFor(1200, () -> {
            service.tick(level, level.getGameTime());
            var diagnostic = service.diagnosticPressure(level);
            var runtime=service.diagnosticRuntime(level);
            if(runtime!=null && "MONSTERMOS".equals(runtime.stage())) {
                double[] now=pressureExtrema(service,level,helper,x0,x1,y0,y1,z0,z1);
                bestBeforeGroups[0]=Math.min(bestBeforeGroups[0],now[1]-now[0]);
                if(diagnostic!=null && diagnostic.faceTransfers()>0
                        && bestBeforeGroups[0] < initialSpan*.5) pressureStageWork[0]=true;
            }
            // GROUPS is the later averaging stage: require the pressure-stage commit to have
            // made whole-room progress before this stage can mask an ineffective kernel.
            if(runtime!=null && "GROUPS".equals(runtime.stage()) && pressureStageWork[0]) writerActive[0]=true;
            // Inject only after the service callback, i.e. between callbacks, so the source is
            // continuously changing without replaying a stale pressure snapshot.
            long gameTick=helper.getTick();
            if(writerActive[0] && gameTick%20==0 && gameTick!=lastWriteTick[0]) {
                GasMixture beforeWrite=service.sample(level,source).orElseThrow();
                if(!service.addGas(level,source,GasType.NITROGEN,.1,5000))
                    throw new GameTestAssertException("continuous source write rejected");
                GasMixture afterWrite=service.sample(level,source).orElseThrow();
                realizedProducerN2[0]+=afterWrite.moles(GasType.NITROGEN)-beforeWrite.moles(GasType.NITROGEN);
                realizedProducerEnergy[0]+=afterWrite.thermalEnergy()-beforeWrite.thermalEnergy();
                producerN2Ulps[0]+=Math.ulp((float)beforeWrite.moles(GasType.NITROGEN))
                        +Math.ulp((float)afterWrite.moles(GasType.NITROGEN));
                producerEnergyUlps[0]+=Math.ulp((float)beforeWrite.thermalEnergy())
                        +Math.ulp((float)afterWrite.thermalEnergy());
                maxProducerN2[0]=Math.max(maxProducerN2[0],afterWrite.moles(GasType.NITROGEN));
                maxProducerEnergy[0]=Math.max(maxProducerEnergy[0],afterWrite.thermalEnergy());
                writerWrites[0]++; lastWriteTick[0]=gameTick;
            }
        }).thenExecute(() -> {
            double[] now = pressureExtrema(service, level, helper, x0, x1, y0, y1, z0, z1);
        if (!pressureStageWork[0]) throw new GameTestAssertException("Monstermos pressure stage failed to halve "
                + "the whole-room pressure span before GROUPS; bestSpan=" + bestBeforeGroups[0] + ", finalSpan="
                + (now[1] - now[0]) + ", pressure=" + service.diagnosticPressure(level) + ", runtime="
                + service.diagnosticRuntime(level) + ", timing=" + service.diagnosticTiming(level));
            if(writerWrites[0]<10) throw new GameTestAssertException("continuous source did not sustain expected writes: "+writerWrites[0]);
            double expectedN2=initialN2+realizedProducerN2[0];
            double expectedEnergy=initialEnergy+realizedProducerEnergy[0];
            double n2=inventory(service,level,helper,x0,x1,y0,y1,z0,z1,false);
            double energy=inventory(service,level,helper,x0,x1,y0,y1,z0,z1,true);
            AtmosphereRuntimeTelemetry.Snapshot operationEnd=service.diagnosticRuntime(level);
            if(operationEnd==null) throw new GameTestAssertException("missing runtime operation counters after pressure window");
            long pressureFaces=operationEnd.pressureFaceTransfers()-operationStart.pressureFaceTransfers();
            long lindaPairs=operationEnd.lindaSharedPairs()-operationStart.lindaSharedPairs();
            long groupedMembers=operationEnd.groupAveragedMembers()-operationStart.groupAveragedMembers();
            long actualOperations=pressureFaces+lindaPairs+groupedMembers;
            double finalMaxN2=maxCellInventory(service,level,helper,x0,x1,y0,y1,z0,z1,false);
            double finalMaxEnergy=maxCellInventory(service,level,helper,x0,x1,y0,y1,z0,z1,true);
            // A receiver may temporarily merge one packet with itself: 2x the greatest cell
            // source/output magnitude bounds every operand. Each face has at most eight half-ulp
            // species roundings (4 ulps); cleanup can independently drop packet and remainder.
            double maxSpeciesOperand=2*Math.max(initialMaxN2,Math.max(maxProducerN2[0],finalMaxN2));
            double n2TransportBound=actualOperations*4*Math.ulp((float)maxSpeciesOperand);
            double cleanupBound=actualOperations*2*GasMixture.GAS_MIN_MOLES;
            double n2Budget=n2TransportBound+cleanupBound+producerN2Ulps[0];
            // Cp formation/reduction (at most nine gas species), weighted temperature products,
            // addition/division and the Cp*T observer fit within 32 half-ulps = 16 ulps per op.
            double maxEnergyOperand=2*Math.max(initialMaxEnergy,Math.max(maxProducerEnergy[0],finalMaxEnergy));
            double energyTransportBound=actualOperations*16*Math.ulp((float)maxEnergyOperand);
            double energyBudget=energyTransportBound+producerEnergyUlps[0];
            MoonStation14.LOGGER.info("PRESSURE_SCALE conservation N2 expected={} actual={} signed={} absolute={} "
                            +"transportBound={} cleanupBound={} producerULP={} energyExpected={} energyActual={} "
                            +"energySigned={} energyAbsolute={} energyTransportBound={} energyProducerULP={} "
                            +"pressureFaces={} lindaPairs={} groupedMembers={} operations={}",
                    expectedN2,n2,n2-expectedN2,Math.abs(n2-expectedN2),n2TransportBound,cleanupBound,producerN2Ulps[0],
                    expectedEnergy,energy,energy-expectedEnergy,Math.abs(energy-expectedEnergy),energyTransportBound,
                    producerEnergyUlps[0],pressureFaces,lindaPairs,groupedMembers,actualOperations);
            if(Math.abs(expectedN2-n2)>n2Budget || Math.abs(expectedEnergy-energy)>energyBudget)
                throw new GameTestAssertException("sealed-room gas/energy accounting drift: N2="+(n2-expectedN2)
                        +", energy="+(energy-expectedEnergy)+", operationBudgets="+n2Budget+"/"+energyBudget
                        +", operations="+actualOperations+" pressure/linda/groups="+pressureFaces+"/"+lindaPairs+"/"+groupedMembers);
            if(!service.boundaryGasLedger(level).isEmpty() || Math.abs(service.boundaryEnergyLedger(level))>1e-9)
                throw new GameTestAssertException("sealed room exported gas or energy");
            var finalRuntime=service.diagnosticRuntime(level);
            if(finalRuntime==null || finalRuntime.completedCycles()==0 || finalRuntime.pendingOwnership()!=0)
                throw new GameTestAssertException("continuous source stalled cycle/ownership progress: "+finalRuntime);
            MoonStation14.LOGGER.info("PRESSURE_SCALE runtime callbacks={} writes={} cycles={} patches={} spanBefore={} "
                            +"bestBeforeGroups={} finalSpan={} pressureDiagnostic={} timing={}",
                    finalRuntime.callbacks(),writerWrites[0],finalRuntime.completedCycles(),finalRuntime.equalizationPatches(),
                    initialSpan,bestBeforeGroups[0],now[1]-now[0],service.diagnosticPressure(level),service.diagnosticTiming(level));
            helper.succeed();
        });
    }

    private static double[] pressureExtrema(AtmosphereService service, ServerLevel level, GameTestHelper helper,
                                             int x0, int x1, int y0, int y1, int z0, int z1) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) for (int x = x0; x < x1; x++) {
            GasMixture gas = service.sample(level, helper.absolutePos(new BlockPos(x, y, z)))
                    .orElseThrow(() -> new GameTestAssertException("room cell is not classified"));
            min = Math.min(min, gas.pressureKpa()); max = Math.max(max, gas.pressureKpa());
        }
        return new double[]{min, max};
    }

    private static double inventory(AtmosphereService service,ServerLevel level,GameTestHelper helper,
                                    int x0,int x1,int y0,int y1,int z0,int z1,boolean energy) {
        double sum=0;
        for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++) {
            GasMixture gas=service.sample(level,helper.absolutePos(new BlockPos(x,y,z)))
                    .orElseThrow(()->new GameTestAssertException("unclassified inventory cell"));
            sum+=energy?gas.thermalEnergy():gas.moles(GasType.NITROGEN);
        }
        return sum;
    }

    private static double maxCellInventory(AtmosphereService service,ServerLevel level,GameTestHelper helper,
                                           int x0,int x1,int y0,int y1,int z0,int z1,boolean energy) {
        double maximum=0;
        for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++) {
            GasMixture gas=service.sample(level,helper.absolutePos(new BlockPos(x,y,z)))
                    .orElseThrow(()->new GameTestAssertException("unclassified inventory cell"));
            maximum=Math.max(maximum,energy?gas.thermalEnergy():gas.moles(GasType.NITROGEN));
        }
        return maximum;
    }
}
