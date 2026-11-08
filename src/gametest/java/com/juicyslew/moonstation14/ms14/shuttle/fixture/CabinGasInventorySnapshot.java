package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.gametest.framework.GameTestAssertException;
import java.util.*;

/** Finite gas ownership boundary for the authored cabin. */
public record CabinGasInventorySnapshot(AtmospherePrecisionBudget.Inventory inventory,
                                        AtmospherePrecisionBudget.Inventory roomInventory,
                                        AtmospherePrecisionBudget.Inventory supplyInventory,
                                        AtmospherePrecisionBudget.Inventory returnInventory,
                                        Map<PipeTopology.NodeKey, UUID> incarnations) {
    public CabinGasInventorySnapshot { incarnations = Map.copyOf(incarnations); }

    public static CabinGasInventorySnapshot capture(ServerLevel level, ShuttleCabinFixture.Built cabin) {
        return capture(level, cabin, Map.of());
    }

    public static CabinGasInventorySnapshot capture(ServerLevel level, ShuttleCabinFixture.Built cabin,
                                                     Map<PipeTopology.NodeKey, UUID> expected) {
        var room = new ArrayList<GasMixture>();
        for (var cell : cabin.roomCells()) {
            var atmosphere = AtmosphereService.INSTANCE.readAtmosphere(level, cell).orElse(null);
            if (atmosphere == null || atmosphere.mixture() == null)
                throw new GameTestAssertException("missing/invalid cabin room gas owner at cell " + cell);
            room.add(atmosphere.mixture());
        }
        var supply = new ArrayList<GasMixture>(); var returns = new ArrayList<GasMixture>();
        var identities = new LinkedHashMap<PipeTopology.NodeKey, UUID>();
        var keys = new LinkedHashSet<PipeTopology.NodeKey>();
        keys.addAll(cabin.supplyPipeNodes()); keys.addAll(cabin.returnPipeNodes());
        var ports = keys.stream().map(LoadedPipeGasWork.Port::new).toList();
        var work = LoadedPipeGasWork.capture(level, ports).orElseThrow(
                () -> new GameTestAssertException("could not capture consistent cabin pipe network image"));
        var countedNetworks = new HashSet<UUID>();
        for (var key : keys) {
            BlockEntity be = level.getBlockEntity(key.position());
            int lane = switch (key.layer()) {
                        case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
                        case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
                        case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
                    };
            var entry = be instanceof VisualPipeBlockEntity
                    ? LoadedPipeGasOwner.activeSnapshot(level, key.position(), lane).orElse(null)
                    : LoadedSealedWallGasOwner.activeSnapshot(level, key.position(), key.layer()).orElse(null);
            if (entry == null)
                throw new GameTestAssertException(missingOwnerDiagnostic(level, key, lane, expected.get(key)));
            if (identities.put(key, entry.incarnation()) != null)
                throw new GameTestAssertException("invalid/duplicate cabin pipe gas owner at node " + key + " lane " + key.layer());
            var resolved = work.inventory().get(key).filter(PipeGasInventory.Entry::active).orElse(null);
            if (resolved == null || !resolved.incarnation().equals(entry.incarnation()))
                throw new GameTestAssertException("cabin pipe owner changed during network capture at " + key);
            var network = work.inventory().network(key).orElseThrow();
            if (countedNetworks.add(network.id()))
                (cabin.supplyPipeNodes().contains(key) ? supply : returns).add(network.mixture());
        }
        var all = new ArrayList<GasMixture>(room); all.addAll(supply); all.addAll(returns);
        return new CabinGasInventorySnapshot(AtmospherePrecisionBudget.Inventory.capture(all),
                AtmospherePrecisionBudget.Inventory.capture(room), AtmospherePrecisionBudget.Inventory.capture(supply),
                AtmospherePrecisionBudget.Inventory.capture(returns), identities);
    }

    private static String missingOwnerDiagnostic(ServerLevel level, PipeTopology.NodeKey key, int lane, UUID expected) {
        BlockPos pos = key.position();
        var loaded = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        String loadedState = "unloaded";
        String block = "n/a", be = "null", removed = "n/a", hasLayer = "n/a", functionalId = "n/a";
        if (loaded instanceof LevelChunk chunk) {
            var state = chunk.getBlockState(pos); BlockEntity entity = chunk.getBlockEntity(pos);
            block = state.toString(); be = entity == null ? "null" : entity.getClass().getSimpleName();
            removed = entity == null ? "n/a" : Boolean.toString(entity.isRemoved());
            if (entity instanceof VisualPipeBlockEntity visual) {
                hasLayer = Boolean.toString(visual.hasLayer(lane));
                functionalId = visual.functionalIdentity(lane).map(UUID::toString).orElse("empty");
            }
            loadedState = "FULL";
        }
        var inventory = PipeGasInventorySavedData.existing(level).map(data -> data.snapshot()).orElse(null);
        var keyEntry = inventory == null ? null : inventory.get(key).orElse(null);
        String saved = keyEntry == null ? "absent" : "id=" + keyEntry.incarnation() + ",active=" + keyEntry.active()
                + ",version=" + keyEntry.version() + ",volume=" + keyEntry.volume() + ",species="
                + inventory.mixture(key).gasMoles().keySet() + ",heat=" + inventory.mixture(key).thermalEnergy();
        String diagnostic = "missing active cabin pipe gas owner node=" + key + " loaded=" + loadedState + " block=" + block
                + " be=" + be + " removed=" + removed + " hasLayer=" + hasLayer + " functionalId=" + functionalId
                + " saved=" + saved + " expected=" + expected + " retired="
                + (inventory != null && expected != null && inventory.retiredSnapshot().containsKey(expected));
        return diagnostic.substring(0, Math.min(900, diagnostic.length()));
    }

    public static double precisionOperations(ShuttleCabinFixture.Built cabin, long elapsedTicks) {
        var cells = new HashSet<>(cabin.roomCells());
        int pairs = 0;
        for (var cell : cells) for (var direction : List.of(net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.UP, net.minecraft.core.Direction.SOUTH))
            if (cells.contains(cell.relative(direction))) pairs++;
        long cycles = Math.max(0, elapsedTicks) / AtmosphereService.TICK_CADENCE;
        int paidIntervals = 2 * ((int) Math.max(0, elapsedTicks) / com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.TICKS_PER_SOLVE + 1);
        return AtmospherePrecisionBudget.operations(cycles, pairs, paidIntervals);
    }

    public void assertConserved(CabinGasInventorySnapshot after, double operations) {
        if (!incarnations.equals(after.incarnations)) throw new GameTestAssertException("cabin pipe owner incarnation changed");
        for (GasType gas : GasType.values()) {
            double delta = Math.abs(inventory.moles(gas) - after.inventory.moles(gas));
            double budget = inventory.moleBudget(gas, after.inventory, operations);
            if (!Double.isFinite(delta) || !Double.isFinite(budget) || delta > budget)
                throw new GameTestAssertException("cabin " + gas + " inventory changed by " + delta + " > " + budget);
        }
    }
}
