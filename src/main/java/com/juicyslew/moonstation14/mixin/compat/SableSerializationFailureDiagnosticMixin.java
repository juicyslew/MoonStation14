package com.juicyslew.moonstation14.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSaveFailureDiagnostic;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.UUID;

/** Read-only context for failures in Sable's native-backed save serialization. */
@Mixin(value = SubLevelSerializer.class, remap = false)
public abstract class SableSerializationFailureDiagnosticMixin {
    private static final int DEPENDENCY_LIMIT = 16;

    @WrapMethod(method = "toData", remap = false)
    private static SubLevelData moonstation14$diagnoseSerializationFailure(ServerSubLevel frame,
            List<UUID> dependencies, Operation<SubLevelData> original) {
        return NativeSaveFailureDiagnostic.invoke(frame, dependencies, (owner, ids) -> original.call(owner, ids),
                (entrySnapshot, failure) -> {
            String after;
            try {
                boolean removed = frame.isRemoved();
                ServerLevel level = frame.getLevel();
                boolean registered = SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) == frame;
                after = snapshot(frame, dependencies, removed, registered);
            } catch (Throwable ignored) {
                after = "metadata=unavailable";
            }
            MoonStation14.LOGGER.error("[native sublevel serialization failure] entry={} failure={}",
                    entrySnapshot, after, failure);
        });
    }

    private static String snapshot(ServerSubLevel frame, List<UUID> dependencies, boolean removed,
                                   boolean registered) {
        ServerLevel level = frame.getLevel();
        BlockPos center = frame.getPlot().getCenterBlock();
        int count = Math.min(dependencies.size(), DEPENDENCY_LIMIT);
        StringBuilder nodes = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) nodes.append(',');
            UUID id = dependencies.get(i);
            SubLevel node = SubLevelContainer.getContainer(level).getSubLevel(id);
            nodes.append(id).append(':').append(node == null ? "missing"
                    : "registered,removed=" + node.isRemoved());
        }
        if (dependencies.size() > count) nodes.append(",...+").append(dependencies.size() - count);
        nodes.append(']');
        return "uuid=" + frame.getUniqueId() + ",dimension=" + level.dimension().location()
                + ",gameTime=" + level.getGameTime() + ",serverTick=" + level.getServer().getTickCount()
                + ",removed=" + removed + ",registered=" + registered + ",storageCenter="
                + center.getX() + ',' + center.getY() + ',' + center.getZ() + ",dependencies=" + nodes;
    }

}
