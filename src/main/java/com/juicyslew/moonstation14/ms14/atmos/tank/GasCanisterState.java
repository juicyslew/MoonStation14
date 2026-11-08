package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasPressureRelease;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Resolved domain state for one server-owned gas canister and its finite inserted-tank slot. */
public final class GasCanisterState {
    public record Saved(ResourceLocation profile, UUID owner, Map<String, Double> moles, double temperature,
                        double volume, double releasePressure, boolean valveClosed, long revision,
                        MachineItemSlot.EncodedData slot) {
        public Saved {
            Objects.requireNonNull(profile);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(slot);
            moles = Map.copyOf(moles);
            if (!Double.isFinite(temperature) || !Double.isFinite(volume) || volume <= 0
                    || !Double.isFinite(releasePressure) || revision < 0 || revision == Long.MAX_VALUE)
                throw new IllegalArgumentException("Invalid saved canister state");
        }
    }

    private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("profile").forGetter(Saved::profile),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("owner").forGetter(Saved::owner),
            GasCompositionCodec.CODEC.fieldOf("moles").forGetter(Saved::moles),
            Codec.DOUBLE.fieldOf("temperature").forGetter(Saved::temperature),
            Codec.DOUBLE.fieldOf("volume").forGetter(Saved::volume),
            Codec.DOUBLE.fieldOf("releasePressure").forGetter(Saved::releasePressure),
            Codec.BOOL.fieldOf("valveClosed").forGetter(Saved::valveClosed),
            Codec.LONG.fieldOf("revision").forGetter(Saved::revision),
            MachineItemSlot.codec().fieldOf("slot").forGetter(Saved::slot)
    ).apply(i, Saved::new));

    private final ResourceLocation profile;
    private final UUID owner;
    private GasMixture gas;
    private double releasePressure;
    private boolean valveClosed = true;
    private long revision;
    private final MachineItemSlot slot;
    private final GasCanisterProfile resolved;
    private final ItemLocationLedger ledger;

    public GasCanisterState(ResourceLocation profileId, GasCanisterProfile resolved, UUID owner,
                            ItemLocationLedger ledger) {
        profile = Objects.requireNonNull(profileId);
        this.resolved = Objects.requireNonNull(resolved);
        this.ledger = Objects.requireNonNull(ledger);
        this.owner = Objects.requireNonNull(owner);
        gas = Objects.requireNonNull(resolved).initialMixture();
        releasePressure = resolved.releasePressureKpa();
        slot = createSlot(owner, ledger);
    }

    private GasCanisterState(Saved saved, GasCanisterProfile resolved, ItemLocationLedger ledger) {
        this(saved.profile(), resolved, saved.owner(), ledger);
        if (Math.abs(saved.volume() - resolved.volumeCubicMeters()) > 1e-9
                || saved.releasePressure() < resolved.minReleasePressureKpa()
                || saved.releasePressure() > resolved.maxReleasePressureKpa())
            throw new IllegalArgumentException("Saved canister does not match resolved profile");
        gas = new GasMixture(GasCompositionCodec.decode(saved.moles()), saved.temperature(), saved.volume());
        releasePressure = saved.releasePressure();
        valveClosed = saved.valveClosed();
        revision = saved.revision();
        if (!slot.restore(saved.slot()))
            throw new IllegalArgumentException("Invalid/conflicting canister snapshot");
    }

    private static MachineItemSlot createSlot(UUID owner, ItemLocationLedger ledger) {
        return new MachineItemSlot(owner, "tank_slot",
                stack -> com.juicyslew.moonstation14.item.custom.GasTankItem.contents(stack).isPresent(), ledger);
    }

    public static Codec<Saved> codec() { return SAVED; }
    public GasMixture gas() { return gas; }
    public UUID owner() { return owner; }
    public ResourceLocation profile() { return profile; }
    public MachineItemSlot slot() { return slot; }
    public double releasePressure() { return releasePressure; }
    public boolean valveClosed() { return valveClosed; }
    public long revision() { return revision; }

    public synchronized boolean setReleasePressure(long expectedRevision, double pressure) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || !Double.isFinite(pressure)
                || pressure < resolved.minReleasePressureKpa() || pressure > resolved.maxReleasePressureKpa()) return false;
        if (Double.compare(releasePressure, pressure) == 0) return true;
        releasePressure = pressure; revision++; return true;
    }

    public synchronized boolean setValveClosed(long expectedRevision, boolean closed) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE) return false;
        if (valveClosed == closed) return true;
        valveClosed = closed; revision++; return true;
    }

    /** Finite tank-to-canister transfer; callers provide observed revisions for an atomic stale-write gate. */
    public synchronized boolean refillFromInsertedTank(long expectedStateRevision, long expectedSlotRevision,
            long expectedLedgerRevision) {
        if (revision != expectedStateRevision || revision == Long.MAX_VALUE || valveClosed) return false;
        var occupied = slot.snapshot();
        if (occupied.revision() != expectedSlotRevision || occupied.token() == null || occupied.stack().isEmpty()) return false;
        var tank = GasTankItem.contents(occupied.stack()).orElse(null);
        if (tank == null) return false;
        var result = GasPressureRelease.release(tank.mixture(), gas, releasePressure);
        if (!(result.transferredMoles() > 0)) return false;
        var ledgerSnapshot = ledger.snapshot();
        if (ledgerSnapshot.revision() != expectedLedgerRevision
                || !new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.MachineSlot(owner, "tank_slot")
                .equals(ledgerSnapshot.locations().get(occupied.token()))) return false;
        if (!slot.mutate(expectedSlotRevision, occupied.token(), stack -> {
            ItemStack updated = GasTankItem.withMixture(stack, result.sourceAfter());
            stack.applyComponents(updated.getComponents());
            return true;
        })) return false;
        gas = result.receiverAfter(); revision++; return true;
    }

    public Saved snapshot() {
        return new Saved(profile, owner, GasCompositionCodec.encode(gas.gasMoles()), gas.temperatureKelvin(),
                gas.volumeCubicMeters(), releasePressure, valveClosed, revision, slot.encode());
    }

    public static GasCanisterState restore(Saved saved, GasCanisterProfile resolved, ItemLocationLedger ledger) {
        return new GasCanisterState(Objects.requireNonNull(saved), Objects.requireNonNull(resolved), ledger);
    }

    public boolean suspend() { return slot.suspend(); }
    public boolean resume() { return slot.resume(); }
}

