package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small fixture-only immutable witnesses and bounded recent samples for the cargo reconnect reproduction. */
final class CargoReconnectWitnessHistory {
    static final int SAMPLE_LIMIT = 32;
    record Witness(long serverTick, String phase, UUID uuid, UUID originalEntityUuid, UUID currentEntityUuid,
                   String originalReference, String currentReference,
                   String baselineStack, String observedStack, String ledgerLocation, String position,
                   String frameSupport, String priorSample) { }

    private final ArrayDeque<String> recent = new ArrayDeque<>(SAMPLE_LIMIT);
    private final Map<UUID, Witness> departures = new LinkedHashMap<>();
    private final Map<UUID, Witness> firstCargoFailures = new LinkedHashMap<>();
    private Witness firstFailure;

    void sample(String row) {
        if (recent.size() == SAMPLE_LIMIT) recent.removeFirst();
        recent.addLast(row);
    }

    void departure(Witness witness) {
        departures.putIfAbsent(witness.uuid(), witness);
        latch(witness);
    }

    void cargoFailure(Witness witness) {
        firstCargoFailures.putIfAbsent(witness.uuid(), witness);
        latch(witness);
    }

    private void latch(Witness witness) {
        if (firstFailure == null) firstFailure = witness;
    }

    Witness firstFailure() { return firstFailure; }
    Witness departure(UUID uuid) { return departures.get(uuid); }
    Witness cargoFailure(UUID uuid) { return firstCargoFailures.get(uuid); }
    List<String> recent() { return List.copyOf(recent); }
    List<Witness> departures() { return List.copyOf(departures.values()); }
    List<Witness> cargoFailures() { return List.copyOf(firstCargoFailures.values()); }
}
