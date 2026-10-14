package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded, live equalization of one already-loaded pipe component. */
final class LoadedPipeEqualization {
    private LoadedPipeEqualization() { }

    /** Returns false without mutation whenever the complete live component cannot be verified. */
    static boolean equalize(ServerLevel level, VentilationDeviceBlockEntity device) {
        if (level == null || device == null || level.isClientSide || device.getLevel() != level
                || !level.getServer().isSameThread() || !level.hasChunkAt(device.getBlockPos())
                || level.getBlockEntity(device.getBlockPos()) != device
                || !(level.getBlockState(device.getBlockPos()).getBlock() instanceof VentilationDeviceBlock)
                || !device.settingsValid()) return false;
        VentilationDeviceConfig.Settings settings = device.config().settings();
        if (!settings.configuredEnabled()) return false;
        int lane = switch (settings.selectedLayer()) {
            case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
            case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
            case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
        };
        var discovered = LoadedPipeComponent.discover(level, device.pipePortPos(), lane);
        if (discovered.isEmpty()) return false;
        var snapshot = discovered.get();
        if (snapshot.nodes().size() <= 1) return true;
        var saved = PipeGasInventorySavedData.existing(level).orElse(null);
        if (saved == null) return false;
        PipeGasInventory before = saved.snapshot();
        List<PipeComponentEqualization.Node> nodes = new ArrayList<>(snapshot.nodes().size());
        for (var node : snapshot.nodes()) {
            var entry = before.get(node.key()).orElse(null);
            if (entry == null || entry != node.entry() || !entry.active()
                    || !entry.incarnation().equals(node.incarnation())) return false;
            nodes.add(new PipeComponentEqualization.Node(node.key(), node.incarnation(), entry.version(),
                    node.volume(), node.mixture()));
        }
        Map<PipeTopology.NodeKey, PipeGasInventory.ExpectedIdentityVersionAndGasAfter> plan;
        try {
            plan = PipeComponentEqualization.plan(nodes);
            boolean changed = false;
            for (var node : nodes) {
                if (!same(node.mixture(), plan.get(node.key()).gasAfter())) { changed = true; break; }
            }
            if (!changed) return true;
            PipeGasInventory after = before.withBatchMixtures(plan);
            if (saved.snapshot() != before) return false;
            return saved.replace(before.revision(), before, after);
        } catch (RuntimeException invalidOrStale) {
            return false;
        }
    }

    private static boolean same(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture a,
                                com.juicyslew.moonstation14.ms14.atmos.core.GasMixture b) {
        return a.gasMoles().equals(b.gasMoles())
                && Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) == 0;
    }
}

