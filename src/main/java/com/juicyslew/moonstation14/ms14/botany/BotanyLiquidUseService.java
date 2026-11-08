package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Adapter from authenticated ordinary tray use to the shared held-liquid transaction owner. */
public final class BotanyLiquidUseService {
    private BotanyLiquidUseService() { }

    public static BodyLiquidInteraction.Result water(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                                       String hand, String token, long handRevision,
                                                       HydroponicsTrayBlockEntity tray) {
        if (actor == null || authority == null || tray == null || !(tray.getLevel() instanceof ServerLevel level)
                || !tray.isLoadedValid() || level.getBlockEntity(tray.getBlockPos()) != tray) return BodyLiquidInteraction.Result.DENIED;
        TrayProfile profile = tray.resolvedTrayProfile();
        if (profile == null || profile.soilCapacityCents() <= 0 || profile.refillMaximumCents() <= 0)
            return BodyLiquidInteraction.Result.DENIED;
        var placement = tray.placementIdentity();
        var plant = tray.plantState();
        long plantRevision = plant == null ? -1 : plant.revision();
        long[] provenRevision = { plantRevision };
        ReagentAttachment soil = tray.soilSolution();
        ReagentAttachment expected = new ReagentAttachment(soil.toComponent());
        BodyLiquidInteraction.WorldSolutionRecipient recipient = new BodyLiquidInteraction.WorldSolutionRecipient() {
            @Override public ReagentAttachment solutionSnapshot() { return new ReagentAttachment(tray.soilSolution().toComponent()); }
            @Override public int capacityCents() { return profile.soilCapacityCents(); }
            @Override public int refillMaximumCents() { return profile.refillMaximumCents(); }
            @Override public boolean validPosition() {
                return tray.isLoadedValid() && tray.getLevel() == level && level.getBlockEntity(tray.getBlockPos()) == tray
                        && tray.placementIdentity().equals(placement) && tray.plantState() == plant
                        && (plant == null ? provenRevision[0] == -1 : plant.revision() == provenRevision[0]);
            }
            @Override public boolean matches(ReagentAttachment value) {
                return validPosition() && tray.soilSolution().equals(value);
            }
            @Override public boolean replace(ReagentAttachment before, ReagentAttachment replacement) {
                if (!validPosition() || !matches(before)) return false;
                // Implemented by the tray owner in the paired BE change; CAS includes placement,
                // plant/revision proof and the exact detached solution snapshot.
                boolean committed = tray.tryReplaceSoil(placement, plant, provenRevision[0], before, replacement);
                if (committed && plant != null) provenRevision[0] = plant.revision();
                return committed;
            }
            @Override public net.minecraft.world.phys.Vec3 position() { return net.minecraft.world.phys.Vec3.atCenterOf(tray.getBlockPos()); }
        };
        if (!recipient.matches(expected)) return BodyLiquidInteraction.Result.DENIED;
        BodyLiquidInteraction.Result result = BodyLiquidInteraction.transferWorldRecipient(actor, authority, hand, token, handRevision, recipient);
        if (result == BodyLiquidInteraction.Result.SUCCESS && profile.wateringSound().isPresent()) {
            var cue = profile.wateringSound().orElseThrow();
            float pitch = 1f + (level.random.nextFloat() * 2f - 1f) * cue.pitchVariation();
            ControlledSoundDispatch.emit(level, cue.soundResourceId(), tray.getBlockPos().getX() + .5,
                    tray.getBlockPos().getY() + .5, tray.getBlockPos().getZ() + .5,
                    (float) Math.pow(10.0, cue.volumeDb() / 20.0), pitch);
        }
        return result;
    }
}
