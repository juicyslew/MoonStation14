package com.juicyslew.moonstation14.ms14.examine;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Explicit provider registry with stable identifier ordering. */
public final class ExamineContributorRegistry {
    @FunctionalInterface public interface AttemptCheck { boolean mayExamine(Entity actor, ExamineTarget target, ServerLevel level); }
    private static final ConcurrentHashMap<String, ExamineContributor> CONTRIBUTORS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AttemptCheck> ATTEMPTS = new ConcurrentHashMap<>();
    private ExamineContributorRegistry() { }
    public static void register(String id, ExamineContributor contributor) {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}") || contributor == null
                || CONTRIBUTORS.putIfAbsent(id, contributor) != null)
            throw new IllegalArgumentException("invalid or duplicate examine contributor");
    }
    public static void registerAttemptCheck(String id, AttemptCheck check) {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}") || check == null
                || ATTEMPTS.putIfAbsent(id, check) != null)
            throw new IllegalArgumentException("invalid or duplicate examine attempt check");
    }
    static List<ExamineContributor> contributors() {
        return CONTRIBUTORS.keySet().stream().sorted().map(CONTRIBUTORS::get).toList();
    }
    static boolean attemptsAllow(Entity actor, ExamineTarget target, ServerLevel level) {
        return ATTEMPTS.keySet().stream().sorted().allMatch(id -> ATTEMPTS.get(id).mayExamine(actor, target, level));
    }
}
