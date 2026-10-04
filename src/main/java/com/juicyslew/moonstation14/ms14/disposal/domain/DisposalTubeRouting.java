package com.juicyslew.moonstation14.ms14.disposal.domain;

import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import java.util.*;
import java.util.regex.Pattern;

/** Pure routing rules. Directions are expressed in world grid coordinates; incoming is the side entered from. */
public final class DisposalTubeRouting {
    private DisposalTubeRouting() { }
    public static Set<DisposalDirection> connections(DisposalEntityData.Tube tube) {
        return connections(tube, DisposalOrientation.identity());
    }
    public static Set<DisposalDirection> connections(DisposalEntityData.Tube tube, DisposalOrientation frame) {
        EnumSet<DisposalDirection> out = EnumSet.noneOf(DisposalDirection.class);
        for (DisposalDirection d : tube.topology().directions()) out.add(DisposalDirection.fromMC(frame.world(d.rotate(tube.rotationQuarters()))));
        return Collections.unmodifiableSet(out);
    }
    public static Set<DisposalDirection> worldConnections(DisposalEntityData.Tube tube, DisposalOrientation frame) { return connections(tube, frame); }
    public static boolean mutuallyConnected(DisposalEntityData.Tube a, boolean aAnchored, DisposalDirection towardB,
                                             DisposalEntityData.Tube b, boolean bAnchored) {
        return aAnchored && bAnchored && connections(a).contains(towardB)
                && connections(b).contains(towardB.opposite());
    }
    public static DisposalDirection selectNextDirection(DisposalEntityData.Tube t, DisposalDirection incoming,
                                                           Set<String> tags, RandomGenerator random) {
        return selectNextDirection(t, DisposalOrientation.identity(), incoming, tags, random);
    }
    public static DisposalDirection selectNextDirection(DisposalEntityData.Tube t, DisposalOrientation frame,
                                                           DisposalDirection incoming, Set<String> tags, RandomGenerator random) {
        List<DisposalDirection> dirs = t.topology().directions().stream().map(d -> d.rotate(t.rotationQuarters())).toList();
        List<DisposalDirection> world = dirs.stream().map(d -> DisposalDirection.fromMC(frame.world(d))).toList();
        DisposalDirection forward = world.get(0);
        return switch (t.kind()) {
            case TRUNK -> incoming == null ? forward : null;
            case TAGGER -> incoming == forward ? world.get(1) : forward;
            case ROUTER -> overlaps(tags, t.routerTags()) ? world.get(1) : forward;
            case STRAIGHT -> incoming == null ? forward : (incoming == forward ? world.get(1) : forward);
            case BEND -> incoming == null ? forward : (incoming == forward ? world.get(1) : forward);
            case THREE_WAY, THREE_WAY_FLIPPED, Y_JUNCTION, X_JUNCTION ->
                    incoming == null || incoming == forward ? world.subList(1, world.size()).get(random.nextInt(world.size()-1)) : forward;
        };
    }
    private static boolean overlaps(Set<String> a, Set<String> b) { return a != null && b != null && a.stream().anyMatch(b::contains); }
    public static boolean validTagExpression(String expression) {
        return expression != null && Pattern.matches("^[a-zA-Z0-9, ]*$", expression);
    }
    public static boolean validTaggerInput(String tag) { return tag != null && Pattern.matches("^[a-zA-Z0-9 ]*$", tag); }
    /** Applies tagger payload behavior; router uses exact tag membership after server-side expression validation. */
    public static Set<String> appendTag(Set<String> current, String tag) {
        var result = new LinkedHashSet<>(current);
        result.add(tag);
        return Collections.unmodifiableSet(result);
    }
    @FunctionalInterface public interface RandomGenerator { int nextInt(int bound); }
}
