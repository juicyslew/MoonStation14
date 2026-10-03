package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.EnumMap;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereAmbientBreachGameTests {
    private static final int LIMIT = 40;
    private static final double TEMP = 1000.0;

    private AtmosphereAmbientBreachGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ambient_breach", timeoutTicks = 120)
    public static void nominalAmbientRoomDrainsAndStopsAfterReseal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (AtmosphereService.INSTANCE.isEnabled())
            throw new GameTestAssertException("isolated ambient breach test requires global atmosphere disabled");
        // 2x2x2 room (8 cells), fully enclosed; its east wall opens directly into the template's open sky.
        shell(helper, true);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of());
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos far = helper.absolutePos(new BlockPos(3, 2, 3));
        int due = classify(service, level, helper);
        for (int x = 2; x <= 3; x++) for (int y = 1; y <= 2; y++) for (int z = 2; z <= 3; z++)
            if (!service.addGas(level, helper.absolutePos(new BlockPos(x, y, z)), GasType.NITROGEN, 40, TEMP))
                throw new GameTestAssertException("nominal room finite seed rejected");
        GasMixture init = service.sample(level, near).orElseThrow();
        double initialP = init.pressureKpa(1.0), initialMoles = total(service, level, helper);
        double initialEnergy = energy(service, level, helper);
        EnumMap<GasType, Double> initialSpecies = inventory(service, level, helper);
        for (int x=2;x<=3;x++) for(int y=1;y<=2;y++) for(int z=2;z<=3;z++)
            if (!service.diagnosticCell(level, helper.absolutePos(new BlockPos(x,y,z))).status().equals("finite"))
                throw new GameTestAssertException("room seed must be finitely owned before breach");
        double ambientP = service.diagnosticCell(level, near).ambient().pressureKpa(1.0);
        if (initialP < ambientP) throw new GameTestAssertException("fixture must be over ambient");

        // Opening all four east-face cells gives a direct exterior path; verify service ownership, not geometry alone.
        for (int y = 1; y <= 2; y++) for (int z = 2; z <= 3; z++)
            helper.setBlock(new BlockPos(4, y, z), Blocks.AIR);
        service.topologyChanged(level, helper.absolutePos(new BlockPos(4, 1, 2)));
        int passes = 0;
        double previous = initialP;
        AtmosphereService.DiagnosticTiming openTiming = null;
        while (passes < LIMIT && service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0) <= 0.0) {
            service.tick(level, (long) (due + ++passes) * AtmosphereService.TICK_CADENCE);
            openTiming = service.diagnosticTiming(level);
            GasMixture current = service.sample(level, near).orElseThrow();
            if (current.pressureKpa(1.0) < ambientP * 0.90)
                throw new GameTestAssertException("warm room drained below ambient safety floor");
            previous = current.pressureKpa(1.0);
        }
        double exported = service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        if (!(exported > 0) || !(previous < initialP))
            throw new GameTestAssertException("open face did not export gas and reduce pressure; passes=" + passes
                    + ", initialP=" + initialP + "kPa, lastP=" + previous + "kPa, ambientP=" + ambientP
                    + "kPa, exported=" + exported + "mol, ledger=" + service.boundaryGasLedger(level)
                    + ", openStatus=" + service.diagnosticCell(level, helper.absolutePos(new BlockPos(4, 1, 2))).status()
                    + ", timing=" + openTiming + ", near=" + describe(service, level, near)
                    + ", far=" + describe(service, level, far));
        if (!service.diagnosticCell(level, helper.absolutePos(new BlockPos(4, 1, 2))).status().equals("exterior"))
            throw new GameTestAssertException("exporting boundary must have loaded diagnostic exterior proof");
        double afterBreach = total(service, level, helper);
        double energyAfterBreach = energy(service, level, helper);
        assertClose(initialMoles, afterBreach + exported, 1e-5, "nitrogen boundary ledger");
        assertClose(initialEnergy, energyAfterBreach + service.boundaryEnergyLedger(level), 1e-4, "energy boundary ledger");
        for (GasType type : GasType.values()) assertClose(initialSpecies.get(type),
                inventory(service, level, helper).get(type) + service.boundaryGasLedger(level).getOrDefault(type, 0.0),
                1e-5, "species boundary ledger " + type);

        for (int y = 1; y <= 2; y++) for (int z = 2; z <= 3; z++) helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        service.topologyChanged(level, helper.absolutePos(new BlockPos(4, 1, 2)));
        int settle = 0;
        while (settle < 12 && service.diagnosticCell(level, helper.absolutePos(new BlockPos(4, 1, 2))).status().equals("unknown")) {
            service.tick(level, (long) (due + passes + ++settle) * AtmosphereService.TICK_CADENCE);
        }
        double sealedMoles = total(service, level, helper), sealedEnergy = energy(service, level, helper);
        double ledgerAtSeal = service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0);
        for (int i = 1; i <= 5; i++) service.tick(level, (long) (due + passes + settle + i) * AtmosphereService.TICK_CADENCE);
        assertClose(sealedMoles, total(service, level, helper), 1e-6, "post-reseal nitrogen");
        assertClose(sealedEnergy, energy(service, level, helper), 1e-4, "post-reseal energy");
        assertClose(ledgerAtSeal, service.boundaryGasLedger(level).getOrDefault(GasType.NITROGEN, 0.0), 1e-8,
                "post-reseal export ledger");
        var timing = service.diagnosticTiming(level);
        MoonStation14.LOGGER.info("Ambient breach GameTest: passes=" + passes + ", resealSettle="
                + settle + ", initialP=" + initialP + "kPa, finalP=" + previous + "kPa, near/far="
                + service.sample(level, near).orElseThrow().pressureKpa(1.0) + "/"
                + service.sample(level, far).orElseThrow().pressureKpa(1.0) + "kPa, exported=" + exported
                + "mol, openDiagnostic=" + openTiming + ", resealDiagnostic=" + timing);
        helper.succeed();
    }

    private static void shell(GameTestHelper h, boolean roof) {
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            if (roof) h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 4; x++) { h.setBlock(new BlockPos(x, y, 1), Blocks.STONE); h.setBlock(new BlockPos(x, y, 4), Blocks.STONE); }
            for (int z = 1; z <= 4; z++) { h.setBlock(new BlockPos(1, y, z), Blocks.STONE); h.setBlock(new BlockPos(4, y, z), Blocks.STONE); }
        }
        for (int x = 2; x <= 3; x++) for (int y = 1; y <= 2; y++) for (int z = 2; z <= 3; z++) h.setBlock(new BlockPos(x,y,z), Blocks.AIR);
    }
    private static int classify(AtmosphereService s, ServerLevel l, GameTestHelper h) {
        for (int pass = 1; pass <= 30; pass++) {
            boolean done = true;
            for (int x=2;x<=3;x++) for(int y=1;y<=2;y++) for(int z=2;z<=3;z++) {
                BlockPos p=h.absolutePos(new BlockPos(x,y,z));
                if (s.sample(l,p).isEmpty()) done=false;
            }
            if (done) return pass - 1;
            s.tick(l, (long) pass * AtmosphereService.TICK_CADENCE);
        }
        throw new GameTestAssertException("sealed room ownership classification timed out");
    }
    private static void requireExterior(AtmosphereService s, ServerLevel l, BlockPos p) {
        for (int i=1;i<=20;i++) { if (s.diagnosticCell(l,p).status().equals("exterior")) return; s.tick(l,(long)i*AtmosphereService.TICK_CADENCE); }
        throw new GameTestAssertException("breach face was not proven exterior");
    }
    private static double total(AtmosphereService s, ServerLevel l, GameTestHelper h) {
        double n=0; for(int x=2;x<=3;x++)for(int y=1;y<=2;y++)for(int z=2;z<=3;z++) n+=s.sample(l,h.absolutePos(new BlockPos(x,y,z))).orElseThrow().moles(GasType.NITROGEN); return n;
    }
    private static double energy(AtmosphereService s, ServerLevel l, GameTestHelper h) {
        double e=0; for(int x=2;x<=3;x++)for(int y=1;y<=2;y++)for(int z=2;z<=3;z++) e+=s.sample(l,h.absolutePos(new BlockPos(x,y,z))).orElseThrow().thermalEnergy(); return e;
    }
    private static String describe(AtmosphereService s, ServerLevel l, BlockPos p) {
        GasMixture gas=s.sample(l,p).orElseThrow();
        return p + "[p="+gas.pressureKpa(1.0)+"kPa,n="+gas.totalMoles()+"mol,T="+gas.temperatureKelvin()+"K]";
    }
    private static EnumMap<GasType, Double> inventory(AtmosphereService s, ServerLevel l, GameTestHelper h) {
        EnumMap<GasType, Double> result = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) result.put(type, 0.0);
        for(int x=2;x<=3;x++)for(int y=1;y<=2;y++)for(int z=2;z<=3;z++) {
            GasMixture mixture=s.sample(l,h.absolutePos(new BlockPos(x,y,z))).orElseThrow();
            for(GasType type:GasType.values()) result.merge(type, mixture.moles(type), Double::sum);
        }
        return result;
    }
    private static void assertClose(double a,double b,double t,String what) { if(Math.abs(a-b)>t) throw new GameTestAssertException(what+" mismatch: "+a+" vs "+b); }
}
