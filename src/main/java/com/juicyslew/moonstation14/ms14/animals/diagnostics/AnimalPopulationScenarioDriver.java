package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.entities.AnimalMobRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs one bounded population profile only for the configured authenticated, committed owner. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class AnimalPopulationScenarioDriver {
    private static final Map<MinecraftServer, Run> RUNS = new IdentityHashMap<>();
    private static final com.juicyslew.moonstation14.ms14.mobs.GroundNpcRoutePlanner SUPPORT = new com.juicyslew.moonstation14.ms14.mobs.GroundNpcRoutePlanner();
    private static final int BASELINE_TICKS = 100, POPULATION_TICKS = 500;
    private static final class Run {
        final UUID playerId, bodyId; final long epoch; final ServerLevel level; BlockPos origin; Vec3 settlingPosition, center; float heading; double forwardDistance;
        final FixtureSettling settling=new FixtureSettling();
        AnimalPopulationFixture.Handle fixture;
        String phase = "baseline"; int ticks; final AnimalPopulationSpec spec; final int expected; boolean captureAttempted, settledGround;
        final PopulationCaptureProtocol sustained = new PopulationCaptureProtocol();
        Run(ServerPlayer p, LifecycleCharacterSessionControl.ActiveCharacterBody body) { playerId=p.getUUID(); bodyId=body.body().getUUID(); epoch=body.epoch(); level=(ServerLevel)p.level(); settling.seed(body.body().position()); spec=AnimalPopulationSpec.automated(AnimalPopulationDiagnostic.scenario()); expected=spec.count(); }
    }
    private AnimalPopulationScenarioDriver() { }

    static void pre(MinecraftServer server) {
        if (RUNS.containsKey(server)) return;
        String configured = System.getProperty("ms14.animalPopulationDiagnosticPlayer", "");
        if (configured.isBlank()) return;
        ServerPlayer player = server.getPlayerList().getPlayerByName(configured);
        if (player == null || player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        var owned = LifecycleCharacterSessionControl.committedActorSnapshot(player).orElse(null);
        if (owned == null) return;
        Run run = new Run(player, owned);
        RUNS.put(server, run);
        run.phase="settling";
    }

    static void post(MinecraftServer server) {
        Run r=RUNS.get(server); if(r==null)return;
        ServerPlayer p=server.getPlayerList().getPlayer(r.playerId);
        var now=p==null?null:LifecycleCharacterSessionControl.committedActorSnapshot(p).orElse(null);
        if (r.phase.equals("complete") || r.phase.equals("aborted")) return;
        if(now==null||now.epoch()!=r.epoch||!now.body().getUUID().equals(r.bodyId)) { r.phase="aborted"; MoonStation14.LOGGER.warn("Animal population diagnostic aborted: committed body ownership changed"); return; }
        if (r.phase.equals("settling")) {
            Mob body=now.body(); Vec3 pos=body.position();
            var result=r.settling.observe(body.onGround() && SUPPORT.hasSupportedSurface(body,pos),pos);
            if(result==FixtureSettling.Result.ABORT) { abort(r,"body did not settle on supported surface within 40 ticks; bodyPos="+pos+" onGround="+body.onGround()+" scenario="+AnimalPopulationDiagnostic.scenario()); return; }
            if(result!=FixtureSettling.Result.READY) return;
            r.settlingPosition=pos;
            r.settledGround=body.onGround();
            r.heading=body.getYRot();
            r.ticks=0; r.phase="waiting_fixture";
        }
        if (AnimalPopulationDiagnostic.sustained() && PopulationCaptureProtocol.PHASES.contains(r.phase)) {
            Vec3 pos = now.body().position();
            double distance = Math.sqrt(Math.pow(pos.x-r.center.x, 2) + Math.pow(pos.z-r.center.z, 2));
            r.sustained.bodySample(distance);
        }
        if (r.phase.equals("waiting_fixture")) {
            var candidates=FixtureSettling.candidates(r.settlingPosition,r.heading);
            List<String> failures=new ArrayList<>(); int[] loaded={0};
            var chosen=FixtureSettling.firstPassing(candidates,candidate -> {
                if(!chunksLoaded(r,candidate.origin())) return false;
                loaded[0]++;
                String failure=layoutFailure(r,candidate.origin());
                if(failure!=null) { if(failures.size()<3) failures.add(failure); return false; }
                return true;
            });
            if(chosen==null) {
                if(loaded[0]==candidates.size()) { abort(r,"no safe loaded fixture candidate count="+candidates.size()+" failures="+failures+" bodyPos="+r.settlingPosition+" yaw="+r.heading+" scenario="+AnimalPopulationDiagnostic.scenario()); return; }
                if(++r.ticks>=100) abort(r,"fixture candidates remained unloaded/unsafe within wait; loaded="+loaded[0]+" candidates="+candidates.size()+" failures="+failures+" bodyPos="+r.settlingPosition+" yaw="+r.heading+" scenario="+AnimalPopulationDiagnostic.scenario());
                return;
            }
            r.origin=chosen.origin(); r.center=chosen.center(); r.forwardDistance=chosen.distance();
            if(!layout(r)){abort(r,"fixture layout failed at "+r.origin);return;}
            MoonStation14.LOGGER.info("MS14_ANIMAL_FIXTURE_READY scenario={} bodyPos={} yaw={} origin={} center={} forwardDistance={} expectedCount={}", AnimalPopulationDiagnostic.scenario().name().toLowerCase(java.util.Locale.ROOT),r.settlingPosition,r.heading,r.origin,r.center,r.forwardDistance,r.expected);
            r.phase="baseline"; r.ticks=0; AnimalPopulationDiagnostic.markPhase("baseline", 0); return;
        }
        r.ticks++;
        if(r.phase.equals("baseline") && r.ticks>=BASELINE_TICKS) {
            if (!spawn(r) || liveCount(r)!=r.expected) { r.phase="aborted"; MoonStation14.LOGGER.error("Animal population diagnostic spawn failed expected={} actual={}",r.expected,liveCount(r)); return; }
            r.phase="population"; r.ticks=0; AnimalPopulationDiagnostic.markPhase("population", liveCount(r));
        } else if (AnimalPopulationDiagnostic.sustained() && PopulationCaptureProtocol.PHASES.contains(r.phase) && PopulationCaptureProtocol.phaseCompleteAt(r.ticks)) {
            if (!phaseCoverage(r)) { int actual=liveCount(r); String reason="sustained phase coverage requirements not observed: "+r.phase; AnimalPopulationCapture.writePhaseFailure(AnimalPopulationDiagnostic.scenario().name().toLowerCase(java.util.Locale.ROOT),r.phase,r.ticks,r.expected,actual,r.sustained,reason); abort(r, reason); return; }
            finishSustainedPhase(r, p, now.body());
        } else if(r.phase.equals("population") && r.ticks>= (AnimalPopulationDiagnostic.sustained() ? 0 : POPULATION_TICKS)) {
            if (AnimalPopulationDiagnostic.sustained()) {
                r.phase = PopulationCaptureProtocol.PHASES.get(0); r.ticks = 0; resetMetrics(r, now.body());
                AnimalPopulationDiagnostic.markPhase("population", liveCount(r));
                markSustained(r); instruct(p, r.phase); return;
            }
            completeRun(r, liveCount(r));
        }
    }

    /** Invoked only after the existing committed character world-step accepted its intent. */
    public static void observeAcceptedInput(ServerPlayer player, Mob body, long epoch, boolean jumpRequest,
                                            Vec3 before, boolean groundedBefore, Vec3 after) {
        if (!AnimalPopulationDiagnostic.sustained()) return;
        MinecraftServer server = player.getServer(); Run r = server == null ? null : RUNS.get(server);
        if (r == null || !PopulationCaptureProtocol.PHASES.contains(r.phase)
                || !PopulationCaptureProtocol.sameOwner(r.playerId, player.getUUID(), r.bodyId, body.getUUID(), r.epoch, epoch)) return;
        r.sustained.acceptedStep(jumpRequest, groundedBefore, before, after);
    }

    private static void resetMetrics(Run r, Mob body) { r.sustained.reset(); }
    private static void markSustained(Run r) { AnimalPopulationDiagnostic.markPhase(r.phase, liveCount(r), r.ticks, r.sustained.acceptedDistance(), r.sustained.groundedJumps(), r.sustained.upwardSamples(), r.sustained.maxDistance(), r.sustained.acceptedInputSamples(), r.sustained.nearSamples(), r.sustained.farSamples()); }
    private static boolean phaseCoverage(Run r) {
        return r.sustained.covered(r.phase, liveCount(r), r.expected);
    }
    private static void finishSustainedPhase(Run r, ServerPlayer p, Mob body) {
        markSustained(r);
        String next = PopulationCaptureProtocol.next(r.phase);
        if (next.equals("complete")) {
            completeRun(r, liveCount(r));
            return;
        }
        r.phase=next; r.ticks=0; resetMetrics(r, body); markSustained(r); instruct(p, r.phase);
    }
    /** Shared stationary/sustained completion contract: terminal phase, log handshake, one capture. */
    private static void completeRun(Run r, int count) {
        String scenario = AnimalPopulationDiagnostic.scenario().name().toLowerCase(java.util.Locale.ROOT);
        if (!PopulationCaptureProtocol.validCompletion(scenario, count, r.expected)) {
            abort(r, "completion population mismatch scenario=" + scenario + " expected=" + r.expected + " actual=" + count);
            return;
        }
        r.phase="complete";
        AnimalPopulationDiagnostic.markPhase("complete", count);
        MoonStation14.LOGGER.info("MS14_ANIMAL_POPULATION_COMPLETE scenario={} animalCount={}", scenario, count);
        if (!r.captureAttempted) { r.captureAttempted=true; AnimalPopulationCapture.captureServer(count); }
    }
    private static void instruct(ServerPlayer player, String phase) {
        String text = switch (phase) {
            case "population_idle" -> "Population diagnostic: remain near the animals and avoid movement for 60 seconds.";
            case "population_move" -> "Population diagnostic: walk naturally around the nearby test area for 60 seconds.";
            case "population_jump" -> "Population diagnostic: repeatedly jump using normal controls for 60 seconds.";
            case "population_away" -> "Population diagnostic: walk at least 64 blocks away for 60 seconds.";
            case "population_return" -> "Population diagnostic: return within 32 blocks of the population and revisit for 60 seconds.";
            default -> "Population diagnostic phase: " + phase;
        };
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
    }

    static String phase(MinecraftServer s) { Run r=RUNS.get(s); return r==null?"waiting":r.phase; }
    static int count(MinecraftServer s) { Run r=RUNS.get(s); return r==null?0:liveCount(r); }
    static void sampleFlyerPoses(MinecraftServer server, long gameTick) {
        if (!AnimalPopulationDiagnostic.flyerPosesEnabled()) return;
        Run r=RUNS.get(server);
        if (r==null || !r.phase.equals("population") || r.fixture==null
                || !AnimalPopulationDiagnostic.allowFlyerPoseSample(gameTick, 0)) return;
        int sampled=0;
        for (UUID id:r.fixture.animalIds()) {
            if (!AnimalPopulationDiagnostic.allowFlyerPoseSample(gameTick, sampled)) break;
            var entity=r.level.getEntity(id);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive() || living.isRemoved()
                    || com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.capability(living).isEmpty()) continue;
            AnimalPopulationDiagnostic.recordFlyerPose(id, r.level.dimension().location().toString(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString(), r.level.getGameTime(),
                    living.getY(), living.onGround(), com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(living).name(),
                    com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.isActivePoweredFlight(living), r.origin.getY()+1);
            sampled++;
        }
    }
    private static int liveCount(Run r) { int n=0; if(r.fixture!=null) for(UUID id:r.fixture.animalIds()) { var e=r.level.getEntity(id); if(e!=null&&!e.isRemoved())n++; } return n; }
    private static boolean chunksLoaded(Run r,BlockPos origin) {
        return AnimalPopulationFixture.chunksLoaded(r.level,origin,16);
    }
    private static String layoutFailure(Run r,BlockPos origin) {
        return AnimalPopulationFixture.preflight(r.level,origin,r.spec);
    }
    private static String failure(Run r,BlockPos pos,String cause) {
        var state=r.level.getBlockState(pos);
        return "fixture preflight "+cause+" pos="+pos+" state="+state+" fluid="+r.level.getFluidState(pos)+" neededHeadroom=4 actualBodyPos="+r.settlingPosition+" onGround="+r.settledGround+" expectedScenario="+AnimalPopulationDiagnostic.scenario()+" loaded="+r.level.hasChunkAt(pos);
    }
    private static boolean layout(Run r) {
        var prepared=AnimalPopulationFixture.prepare(r.level,r.origin,r.spec);
        r.fixture=prepared.handle();
        if(r.fixture==null)return false;
        return true;
    }
    private static void abort(Run run, String reason) {
        run.phase="aborted";
        MoonStation14.LOGGER.warn("Animal population diagnostic aborted: {}",reason);
    }
    private static boolean spawn(Run r) {
        return r.fixture!=null&&AnimalPopulationFixture.spawn(r.fixture)==null;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { RUNS.remove(e.getServer()); }
}
