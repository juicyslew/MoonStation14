package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.ms14.singularity.containment.ModFieldGenerators;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** One-shot, fixture-owned transition from placed hardware to a powered accelerator. */
public final class CreateSingularityFixtureStartup {
    public enum Phase { NEW, WAITING_CONTAINMENT, ACCELERATOR_STARTED, FAILED }
    private static final long DEADLINE_TICKS = 1200;
    private final ServerLevel level;
    private final SingularityFixtureLayout.Placed placed;
    private final List<EmitterBlockEntity> emitters = new ArrayList<>();
    private final List<FieldGeneratorBlockEntity> fields = new ArrayList<>();
    private final List<RadiationCollectorBlockEntity> collectors = new ArrayList<>();
    private final ParticleAcceleratorPartBlockEntity control;
    private final com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity generator;
    private final long deadline;
    private Phase phase = Phase.NEW;
    private String error;

    public CreateSingularityFixtureStartup(ServerLevel level, SingularityFixtureLayout.Placed placed) {
        this.level = level; this.placed = placed; this.deadline = level.getGameTime() + DEADLINE_TICKS;
        for (BlockPos p : placed.emitters()) { if (!(level.getBlockEntity(p) instanceof EmitterBlockEntity be)) { fail("missing emitter at " + p); break; } emitters.add(be); }
        for (BlockPos p : placed.fieldGenerators()) { if (!(level.getBlockEntity(p) instanceof FieldGeneratorBlockEntity be)) { fail("missing field generator at " + p); break; } fields.add(be); }
        for (BlockPos p : placed.collectors()) { if (!(level.getBlockEntity(p) instanceof RadiationCollectorBlockEntity be)) { fail("missing collector at " + p); break; } collectors.add(be); }
        control = level.getBlockEntity(placed.acceleratorControl()) instanceof ParticleAcceleratorPartBlockEntity be ? be : null;
        generator = level.getBlockEntity(placed.startupGenerator()) instanceof com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity be ? be : null;
        if (control == null || generator == null) fail("missing accelerator control or startup generator");
    }
    public void begin() {
        if (phase != Phase.NEW) return;
        phase = Phase.WAITING_CONTAINMENT;
        for (EmitterBlockEntity be : emitters) { if (!owned(be)) return; if (!be.requestedOn()) be.setRequestedOn(true); }
        for (FieldGeneratorBlockEntity be : fields) { if (!owned(be)) return; if (!be.snapshot().enabled()) be.toggleEnabled(); }
    }
    public void tick() {
        if (phase == Phase.NEW) begin();
        if (phase != Phase.WAITING_CONTAINMENT) return;
        if (level.getGameTime() > deadline) { fail("field, power, or closure deadline expired"); return; }
        for (EmitterBlockEntity be : emitters) if (!owned(be) || !be.firingPowered()) return;
        for (FieldGeneratorBlockEntity be : fields) if (!owned(be) || !be.connectionEligible()) return;
        if (!owned(generator) || !owned(control)) return;
        var profile = com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.catalog(level).get(generator.profileId());
        if (profile == null) { fail("resolved generator profile unavailable"); return; }
        FieldContainmentRuntime.refresh(level);
        Vec3 center = Vec3.atCenterOf(placed.startupGenerator());
        if (profile.failsafeEnabled() && FieldContainmentRuntime.closedVolumes(level).stream().noneMatch(b -> ClosedFieldVolume.containsWithinFaceDistance(b, center, profile.failsafeDistance()))) return;
        control.requestAssemblyRescan();
        if (!control.assemblyValid()) return;
        control.setStrength(ParticleAcceleratorStrength.LEVEL_0);
        control.setEnabled(true);
        if (!control.enabled()) return;
        for (RadiationCollectorBlockEntity be : collectors) {
            if (!owned(be)) return;
            if (!be.enabled() && !be.toggleEnabled()) { fail("collector cooldown or toggle refusal"); return; }
        }
        phase = Phase.ACCELERATOR_STARTED;
    }
    public Phase phase() { return phase; }
    public String error() { return error; }
    /** Original owned startup machine retained for one-shot proof if horizon consumption removes its block. */
    com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity startupGenerator() { return generator; }
    private boolean owned(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (!be.isRemoved() && be.getLevel() == level && level.getBlockEntity(be.getBlockPos()) == be) return true;
        fail("owned machine missing, replaced, or unloaded at " + be.getBlockPos()); return false;
    }
    private void fail(String reason) { if (phase != Phase.FAILED) { phase = Phase.FAILED; error = reason; } }
}
