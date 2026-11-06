package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContact;
import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContactAccess;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactJournal;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Attaches short-lived contact observations to this exact Sable result, never changing physics. */
@Mixin(value = SubLevelEntityCollision.CollisionInfo.class, remap = false)
public abstract class SableCollisionInfoAirlockContactsMixin implements NativeAirlockContactAccess, NativeSelectedContactAccess {
    @Unique private final NativeSelectedContactJournal moonstation14$selectedJournal = new NativeSelectedContactJournal();
    @Unique private List<NativeAirlockContact> moonstation14$airlockContacts;
    @Unique private Set<NativeAirlockContactAccess.Selection> moonstation14$airlockSelections;

    @Override
    public List<NativeSelectedContactAccess.Entry> moonstation14$selectedContacts() { return moonstation14$selectedJournal.snapshot(); }
    @Override
    public boolean moonstation14$selectedContactsOverflowed() { return moonstation14$selectedJournal.overflowed(); }
    @Override
    public void moonstation14$recordSelectedContact(java.util.UUID frameId, BlockPos pos, net.minecraft.world.phys.Vec3 normal, boolean verticalCollision,
            net.minecraft.world.phys.Vec3 entityPosition, net.minecraft.world.phys.Vec3 entityVelocity, long serverTick, long levelGameTime, boolean moveInFlight) {
        moonstation14$selectedJournal.record(frameId, pos, normal, verticalCollision, entityPosition, entityVelocity, serverTick, levelGameTime, moveInFlight);
    }

    @Override
    public List<NativeAirlockContact> moonstation14$airlockContacts() {
        return moonstation14$airlockContacts == null ? List.of() : List.copyOf(moonstation14$airlockContacts);
    }

    @Override
    public void moonstation14$recordAirlockContact(NativeAirlockContact contact) {
        if (moonstation14$airlockContacts == null) moonstation14$airlockContacts = new ArrayList<>(4);
        if (!moonstation14$airlockContacts.contains(contact))
            moonstation14$airlockContacts.add(contact);
    }

    @Override
    public boolean moonstation14$claimAirlockSelection(SubLevel frame, BlockPos selectedPos, BlockState selectedState,
                                                        double normalX, double normalY, double normalZ,
                                                        int substep, int iteration) {
        if (moonstation14$airlockSelections == null) moonstation14$airlockSelections = new HashSet<>(4);
        return moonstation14$airlockSelections.add(new NativeAirlockContactAccess.Selection(frame, selectedPos.immutable(), selectedState,
                (int) Math.round(normalX * 100), (int) Math.round(normalY * 100),
                (int) Math.round(normalZ * 100), substep, iteration));
    }
}
