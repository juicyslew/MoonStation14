package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.damage.TypedDamageAppliedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only server diagnostics for actual damage sustained during disposal transit. */
public final class DisposalRideDamageDiagnostics {
    private DisposalRideDamageDiagnostics() { }

    /** Keep only actual, finite damage ledger changes; healing and baseline deltas are omitted. */
    static Map<String, Float> positiveDamage(Map<String, Float> delta) {
        Map<String, Float> result = new LinkedHashMap<>();
        delta.forEach((kind, amount) -> {
            if (kind != null && amount != null && Float.isFinite(amount) && amount > 0f)
                result.put(kind, amount);
        });
        return Map.copyOf(result);
    }

    public static void onTypedDamage(TypedDamageAppliedEvent event) {
        if (event == null || event.entity() == null) return;
        LivingEntity body = event.entity();
        if (!(body.level() instanceof ServerLevel level) || level.isClientSide) return;
        Map<String, Float> damage = positiveDamage(event.delta());
        if (damage.isEmpty()) return;

        ContainedBodyOwner owner = BodyContainmentService.forLevel(level).ownerOf(body).orElse(null);
        if (owner == null || owner.level() != level || !owner.isValid()
                || owner.stage() != ContainedBodyOwner.Stage.TRANSIT) return;

        String air = "unknown";
        var sample = owner.airSource().flatMap(source -> source.sample());
        if (sample.isPresent()) {
            var exposure = sample.orElseThrow();
            var mixture = exposure.mixture();
            air = String.format(java.util.Locale.ROOT,
                    "pressureKPa=%.3f volumeM3=%.6f O2mol=%.6f CO2mol=%.6f temperatureK=%.2f",
                    mixture.pressureKpa(exposure.volumeCubicMeters()), exposure.volumeCubicMeters(),
                    mixture.moles(GasType.OXYGEN), mixture.moles(GasType.CARBON_DIOXIDE), mixture.temperatureKelvin());
        }
        String ownerId = owner.identity().toString();
        String position = owner.position().toString();
        MoonStation14.LOGGER.info("Disposal ride damage body={} owner={} stage={} position={} tick={} damage={} hp={}/{} air={}",
                body.getUUID(), ownerId, owner.stage(), position, level.getGameTime(), damage,
                body.getHealth(), body.getMaxHealth(), air);
    }
}

