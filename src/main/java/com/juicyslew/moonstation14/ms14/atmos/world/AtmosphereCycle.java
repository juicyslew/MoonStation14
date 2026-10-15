package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import java.util.HashSet;
import java.util.Set;

/** Persistent level-local atmosphere pass state. A cycle survives server-tick yields. */
final class AtmosphereCycle {
    enum Stage { REVALIDATE, MONSTERMOS, LINDA, GROUPS, HIGH_PRESSURE, DELTA_PRESSURE,
        HOTSPOTS, PIPE_NET, ATMOS_DEVICES }

    private long identity;
    private int cadencePhase;
    private long completed;
    private int lindaRemaining = -1;
    private boolean monstermosPending;
    private boolean reactionsPending;
    private boolean reactionsStarted;
    private boolean groupsPending;
    private Stage stage;
    private final Set<BlockPos> monstermosHandled = new HashSet<>();
    private final Set<BlockPos> boundaryCells = new HashSet<>();
    private final Map<BlockPos, GasMixture> archive = new LinkedHashMap<>();
    private final Set<BlockPos> lindaSources = new LinkedHashSet<>();
    private final Set<BlockPos> lindaProcessedSources = new LinkedHashSet<>();
    private final Deque<BlockPos> monstermosSeeds = new ArrayDeque<>();
    private final Set<BlockPos> monstermosProcessed = new HashSet<>();
    private boolean monstermosSeedsStarted;
    private final Set<BlockPos> reactionVisited = new HashSet<>();
    private final Set<BlockPos> reactionSources = new LinkedHashSet<>();
    private final Set<BlockPos> deferredReactions = new LinkedHashSet<>();
    private final Map<BlockPos, GasReactionStep.WholeAirResult> wholeAir = new LinkedHashMap<>();

    /** One stage opportunity every 1/15 second; each default chain occupies nine opportunities. */
    boolean beginWhenDue() {
        cadencePhase += 15;
        if (cadencePhase < 20) return false;
        cadencePhase -= 20;
        if (stage != null) return true;
        identity++;
        monstermosHandled.clear();
        boundaryCells.clear();
        archive.clear();
        lindaSources.clear();
        lindaProcessedSources.clear();
        monstermosSeeds.clear();
        monstermosProcessed.clear();
        monstermosSeedsStarted = false;
        reactionVisited.clear();
        deferredReactions.clear();
        wholeAir.clear();
        reactionsPending = false;
        reactionsStarted = false;
        reactionSources.clear();
        groupsPending = false;
        lindaRemaining = -1;
        stage = Stage.REVALIDATE;
        return true;
    }

    Stage stage() { return stage; }
    long identity() { return identity; }
    long completed() { return completed; }
    Set<BlockPos> monstermosHandled() { return monstermosHandled; }
    Set<BlockPos> boundaryCells() { return boundaryCells; }
    Map<BlockPos, GasMixture> archive() { return archive; }
    void startLinda(Set<BlockPos> sources) {
        if (lindaRemaining >= 0) return;
        lindaSources.addAll(sources);
        lindaRemaining = lindaSources.size();
    }
    boolean lindaStarted() { return lindaRemaining >= 0; }
    Set<BlockPos> lindaSources() { return lindaSources; }
    Set<BlockPos> lindaProcessedSources() { return lindaProcessedSources; }
    int lindaRemaining() { return lindaSources.size(); }
    void removeLindaSource(BlockPos source) {
        lindaSources.remove(source);
        lindaRemaining = lindaSources.size();
    }
    void finishLinda() { lindaSources.clear(); lindaRemaining = 0; }
    void monstermosPending(boolean pending) { monstermosPending = pending; }
    void startMonstermos(Set<BlockPos> seeds) {
        if (monstermosSeedsStarted) return;
        monstermosSeedsStarted = true;
        for (BlockPos seed : seeds) monstermosSeeds.addLast(seed.immutable());
    }
    void addMonstermosSeeds(java.util.Collection<BlockPos> seeds) {
        for (BlockPos seed : seeds)
            if (!monstermosProcessed.contains(seed) && !monstermosSeeds.contains(seed)) monstermosSeeds.addLast(seed.immutable());
    }
    boolean monstermosSeedsStarted() { return monstermosSeedsStarted; }
    Deque<BlockPos> monstermosSeeds() { return monstermosSeeds; }
    Set<BlockPos> monstermosProcessed() { return monstermosProcessed; }
    void groupsPending(boolean pending) { groupsPending = pending; }
    void reactionsPending(boolean pending) { reactionsPending = pending; }
    Set<BlockPos> reactionVisited() { return reactionVisited; }
    void startReactions(java.util.Collection<BlockPos> sources) {
        if (reactionsStarted) return;
        reactionsStarted = true;
        reactionSources.addAll(sources);
        reactionsPending = !reactionSources.isEmpty();
    }
    boolean reactionsStarted() { return reactionsStarted; }
    void admitReactionSource(BlockPos pos) {
        if (reactionsStarted && !deferredReactions.contains(pos) && !reactionVisited.contains(pos))
            reactionSources.add(pos.immutable());
        reactionsPending = !reactionSources.isEmpty();
    }
    Set<BlockPos> reactionSources() { return reactionSources; }
    void removeReactionSource(BlockPos pos) { reactionSources.remove(pos); }
    /** A newly-created hotspot cannot join the already-admitted HOTSPOTS pass. */
    void deferNewHotspotReceiver(BlockPos pos) {
        if (stage != Stage.HOTSPOTS) return;
        reactionSources.remove(pos);
        deferReaction(pos);
        reactionsPendingFromSources();
    }
    void reactionsPendingFromSources() { reactionsPending = !reactionSources.isEmpty(); }
    void discardReactionRemainder() { reactionSources.clear(); reactionsPending = false; }
    void deferReaction(BlockPos pos) { deferredReactions.add(pos.immutable()); }
    void finishReactions(java.util.function.Consumer<BlockPos> scheduleNextCycle) {
        deferredReactions.forEach(scheduleNextCycle);
        deferredReactions.clear();
        reactionVisited.clear();
        reactionsPending = false;
        reactionsStarted = false;
        reactionSources.clear();
    }
    Map<BlockPos, GasReactionStep.WholeAirResult> wholeAir() { return wholeAir; }

    void invalidateTopology(BlockPos changed) {
        archive.remove(changed);
        boundaryCells.remove(changed);
        monstermosHandled.remove(changed);
            lindaSources.remove(changed);
            lindaProcessedSources.remove(changed);
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbor = changed.relative(direction);
            archive.remove(neighbor);
            boundaryCells.remove(neighbor);
            monstermosHandled.remove(neighbor);
            lindaSources.remove(neighbor);
            lindaProcessedSources.remove(neighbor);
        }
    }

    void invalidateChunk(ChunkPos chunk) {
        Set<BlockPos> invalid = new HashSet<>();
        for (BlockPos pos : archive.keySet())
            if (new ChunkPos(pos).equals(chunk)) {
                invalid.add(pos);
                for (var direction : net.minecraft.core.Direction.values()) invalid.add(pos.relative(direction));
            }
        invalid.forEach(pos -> {
            archive.remove(pos);
            boundaryCells.remove(pos);
            monstermosHandled.remove(pos);
            lindaSources.remove(pos);
            lindaProcessedSources.remove(pos);
        });
    }

    boolean advance() {
        if (stage == null) return false;
        if (stage == Stage.MONSTERMOS && monstermosPending) return false;
        if (stage == Stage.MONSTERMOS && !monstermosSeeds.isEmpty()) return false;
        if (stage == Stage.LINDA && !lindaSources.isEmpty()) return false;
        if (stage == Stage.GROUPS && groupsPending) return false;
        if (stage == Stage.HOTSPOTS && reactionsPending) return false;
        if (stage == Stage.REVALIDATE) stage = Stage.MONSTERMOS;
        else if (stage == Stage.MONSTERMOS) stage = Stage.LINDA;
        else if (stage == Stage.LINDA) stage = Stage.GROUPS;
        else if (stage == Stage.GROUPS) stage = Stage.HIGH_PRESSURE;
        else if (stage == Stage.HIGH_PRESSURE) stage = Stage.DELTA_PRESSURE;
        else if (stage == Stage.DELTA_PRESSURE) stage = Stage.HOTSPOTS;
        else if (stage == Stage.HOTSPOTS) stage = Stage.PIPE_NET;
        else if (stage == Stage.PIPE_NET) stage = Stage.ATMOS_DEVICES;
        else {
            stage = null;
            completed++;
            monstermosHandled.clear();
            boundaryCells.clear();
            archive.clear();
            lindaSources.clear();
            monstermosSeeds.clear();
            monstermosProcessed.clear();
            reactionVisited.clear();
            deferredReactions.clear();
            wholeAir.clear();
            lindaRemaining = -1;
            reactionsStarted = false;
            reactionSources.clear();
            return true;
        }
        return false;
    }
}

