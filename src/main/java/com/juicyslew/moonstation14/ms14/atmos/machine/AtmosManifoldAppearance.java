package com.juicyslew.moonstation14.ms14.atmos.machine;

import java.util.List;

/** Shared ordering for manifold connection bits and their rendered endpoint models. */
public final class AtmosManifoldAppearance {
    public record Endpoint(String portId, String modelSuffix) { }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("north0", "north_primary"),
            new Endpoint("north1", "north_secondary"),
            new Endpoint("north2", "north_tertiary"),
            new Endpoint("south0", "south_primary"),
            new Endpoint("south1", "south_secondary"),
            new Endpoint("south2", "south_tertiary"));

    private AtmosManifoldAppearance() { }

    public static int endpointCount() { return ENDPOINTS.size(); }

    public static int portBit(String portId) {
        for (int bit = 0; bit < ENDPOINTS.size(); bit++)
            if (ENDPOINTS.get(bit).portId().equals(portId)) return bit;
        return -1;
    }

    public static String endpointSuffix(int bit) {
        return bit < 0 || bit >= ENDPOINTS.size() ? null : ENDPOINTS.get(bit).modelSuffix();
    }

    /** Wired model resource paths selected by this exact appearance mask and machine facing. */
    public static List<String> connectedModelPaths(int mask, String facing) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (int bit = 0; bit < endpointCount(); bit++) if ((mask & (1 << bit)) != 0)
            result.add("block/atmos_manifold/" + facing + "_" + endpointSuffix(bit));
        return List.copyOf(result);
    }
}
