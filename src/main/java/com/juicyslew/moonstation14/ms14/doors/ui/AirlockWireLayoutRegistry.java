package com.juicyslew.moonstation14.ms14.doors.ui;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Server-lifetime SS14-style function/color assignment, shared by prototype layout ID. */
public final class AirlockWireLayoutRegistry {
    public record WireAppearance(int rowId, int argb, String letter, boolean cut) { }
    private record Row(int rowId, int argb, String letter, AirlockState.Wire function) { }
    private record Layout(Map<Integer, Row> byId, List<Row> rows) { }

    private static final List<Integer> COLORS = List.of(
            0xFFFF0000, 0xFF0000FF, 0xFF32CD32, 0xFFFFA500, 0xFFA52A2A, 0xFFFFD700,
            0xFF808080, 0xFF00FFFF, 0xFF000080, 0xFF800080, 0xFFFFC0CB, 0xFFFF00FF);
    private static final List<String> LETTERS = List.of("α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ");

    private final Random random;
    private final Map<String, Layout> layouts = new HashMap<>();

    public AirlockWireLayoutRegistry() { this(new Random()); }
    AirlockWireLayoutRegistry(Random random) { this.random = random; }

    public synchronized List<WireAppearance> describe(String layoutId, Collection<String> supportedActions,
                                                        AirlockState state) {
        if (state == null) return List.of();
        var layout = layouts.computeIfAbsent(requireLayoutId(layoutId), ignored -> create(supportedActions));
        var result = new ArrayList<WireAppearance>(layout.rows().size());
        for (var row : layout.rows()) result.add(new WireAppearance(row.rowId(), row.argb(), row.letter(), isCut(state, row.function())));
        return List.copyOf(result);
    }

    /** Resolve only an ID actually assigned in this cached layout; null is an invalid selector. */
    public synchronized AirlockState.Wire resolve(String layoutId, int rowId) {
        if (rowId < 0 || layoutId == null) return null;
        Layout layout = layouts.get(layoutId);
        Row row = layout == null ? null : layout.byId().get(rowId);
        return row == null ? null : row.function();
    }

    public synchronized void clear() { layouts.clear(); }

    private Layout create(Collection<String> actions) {
        if (actions == null || actions.size() != AirlockState.Wire.values().length)
            throw new IllegalArgumentException("airlock layout must define every supported wire slot");
        var wires = new ArrayList<AirlockState.Wire>();
        // Prototype action sets may be hash-backed; normalize before the randomized layout assignment.
        for (String action : actions.stream().sorted().toList()) {
            AirlockState.Wire wire = wire(action);
            if (wire == null || wires.contains(wire)) throw new IllegalArgumentException("invalid airlock wire action");
            wires.add(wire);
        }
        Collections.shuffle(wires, random);
        var colors = new ArrayList<>(COLORS);
        Collections.shuffle(colors, random);
        var letters = new ArrayList<>(LETTERS);
        Collections.shuffle(letters, random);
        var byId = new HashMap<Integer, Row>();
        var rows = new ArrayList<Row>(wires.size());
        for (int i = 0; i < wires.size(); i++) {
            Row row = new Row(i, colors.get(i), letters.get(i), wires.get(i));
            byId.put(i, row);
            rows.add(row);
        }
        return new Layout(Map.copyOf(byId), List.copyOf(rows));
    }

    private static AirlockState.Wire wire(String action) {
        if (action == null) return null;
        return switch (action) {
            case "power_one" -> AirlockState.Wire.POWER_ONE;
            case "power_two" -> AirlockState.Wire.POWER_TWO;
            case "log" -> AirlockState.Wire.LOG;
            case "bolt" -> AirlockState.Wire.BOLT;
            case "bolt_light" -> AirlockState.Wire.BOLT_LIGHT;
            case "timing" -> AirlockState.Wire.TIMING;
            case "safety" -> AirlockState.Wire.SAFETY;
            case "ai_interact" -> AirlockState.Wire.AI_INTERACT;
            default -> null;
        };
    }

    private static String requireLayoutId(String value) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException("invalid airlock wire layout ID");
        return value;
    }

    private static boolean isCut(AirlockState s, AirlockState.Wire wire) {
        return switch (wire) {
            case POWER_ONE -> s.powerWireOneCut();
            case POWER_TWO -> s.powerWireTwoCut();
            case BOLT -> s.boltWireCut();
            case BOLT_LIGHT -> s.boltLightWireCut();
            case TIMING -> s.timingWireCut();
            case SAFETY -> s.safetyWireCut();
            case LOG -> s.logWireCut();
            case AI_INTERACT -> s.aiInteractWireCut();
        };
    }
}
