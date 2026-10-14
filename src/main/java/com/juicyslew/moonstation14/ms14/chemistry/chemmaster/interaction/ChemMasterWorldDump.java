package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.DumpableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/** Whole-mixture world-container transaction. It changes reagent ownership only; the jug remains. */
public final class ChemMasterWorldDump {
    private ChemMasterWorldDump() { }
    /** Shared ordinary body-item pickup reach; deliberate Minecraft adaptation from SS14 drag/drop range. */
    public static final double DUMP_INTERACTION_RANGE = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH;
    public enum Result { SUCCESS, DENIED }

    public record ExpectedSource(UUID entityId, ItemStack exactStack) {
        public ExpectedSource { if (entityId == null || exactStack == null || exactStack.isEmpty()) throw new IllegalArgumentException(); exactStack = exactStack.copy(); }
        @Override public ItemStack exactStack() { return exactStack.copy(); }
    }
    /** Capture eligible currently-loaded jug snapshot on gesture begin; release need not re-aim at the jug. */
    public static ExpectedSource captureEligibleSource(net.minecraft.server.level.ServerLevel level, UUID sourceId) {
        if (level == null || sourceId == null || !level.getServer().isSameThread()) return null;
        var entity = level.getEntity(sourceId);
        if (!(entity instanceof LooseItemEntity item) || !level.hasChunkAt(item.blockPosition())
                || item.isRemoved() || !item.isAddedToLevel() || item.getTarget() != null || item.hasPickUpDelay()
                || item.getItem().getCount() != 1 || !(item.getItem().getItem() instanceof IReagentTrait trait)) return null;
        var policy = trait.drainableSolution(item.getItem(), level);
        var component = item.getItem().get(ModDataComponents.REAGENT.get());
        if (policy == null || !policy.enabled() || !policiesMatch(policy, trait.solutionName(item.getItem(), level), null)
                || component == null || component.centContents().isEmpty()) return null;
        try {
            ReagentUnits.total(component.centContents().values());
            if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(component.contents(), level, "ChemMaster world dump source")) return null;
            return new ExpectedSource(sourceId,item.getItem());
        }
        catch (RuntimeException invalid) { return null; }
    }

    /** Authenticated actor route intended for the later gesture backend. */
    public static Result transfer(ServerPlayer actor, ExpectedSource expected, BlockPos machinePos,
            UUID machineIdentity, long stateRevision, long bufferRevision) {
        var authority = BodyActionAuthority.resolve(actor).orElse(null);
        if (authority == null || !BodyActionAuthority.eligibleForComplexInteraction(actor, authority)
                || !BodyActionAuthority.revalidate(actor, authority)) return Result.DENIED;
        var body = authority.body();
        return transferOwned(body, expected, machinePos, machineIdentity, stateRevision, bufferRevision,
                () -> BodyActionAuthority.revalidate(actor, authority));
    }

    /** Trusted fixture seam; never represents authenticated packet transport. */
    static Result transferOwned(net.minecraft.world.entity.LivingEntity body, ExpectedSource expected, BlockPos machinePos, UUID machineIdentity,
            long stateRevision, long bufferRevision, java.util.function.BooleanSupplier authorityValid) {
        if (body == null || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        Level level = body.level();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !server.getServer().isSameThread()
                || expected == null || machinePos == null || authorityValid == null || !authorityValid.getAsBoolean()) return Result.DENIED;
        var entity = server.getEntity(expected.entityId());
        if (!(entity instanceof LooseItemEntity item) || item.level() != server || !server.hasChunkAt(item.blockPosition()) || item.isRemoved() || !item.isAddedToLevel()
                || item.getTarget() != null || item.hasPickUpDelay() || item.getItem().getCount() != 1
                || !same(expected.exactStack(), item.getItem())) return Result.DENIED;
        var machineChunk = server.getChunkSource().getChunkNow(machinePos.getX() >> 4, machinePos.getZ() >> 4);
        if (machineChunk == null) return Result.DENIED;
        var be = machineChunk.getBlockEntities().get(machinePos);
        if (!(be instanceof ChemMasterBlockEntity machine) || !server.hasChunkAt(machinePos) || !machine.isLoadedValid() || !machineIdentity.equals(machine.placementIdentity())
                || machine.prototype() == null || !machine.prototype().dumpableSolution().enabled()
                || !machine.prototype().dumpableSolution().unlimited()
                || !(item.getItem().getItem() instanceof IReagentTrait trait)) return Result.DENIED;
        if (!com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinInteractionReach(body, item.getBoundingBox())
                || !com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinInteractionReach(body,
                new net.minecraft.world.phys.AABB(machinePos))
                || !unobstructed(body, item.getBoundingBox().getCenter(), null)
                || !unobstructed(body, machinePos.getCenter(), machinePos)) return Result.DENIED;
        var policy = trait.drainableSolution(item.getItem(), server);
        ReagentComponent component = item.getItem().get(ModDataComponents.REAGENT.get());
        if (!policiesMatch(policy, trait.solutionName(item.getItem(), server), machine.prototype().dumpableSolution())
                || component == null || component.centContents().isEmpty()) return Result.DENIED;
        try { ReagentUnits.total(component.centContents().values()); } catch (RuntimeException invalid) { return Result.DENIED; }
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(component.contents(), server, "ChemMaster world dump source")) return Result.DENIED;
        ChemMasterBuffer staged = new ChemMasterBuffer(machine.buffer().snapshot());
        if (!staged.addWholeMixture(component.toAttachment())) return Result.DENIED;
        ItemStack after = item.getItem().copy(); after.remove(ModDataComponents.REAGENT.get());
        return machine.commitExternalBuffer(machineIdentity, stateRevision, bufferRevision, staged.snapshot(), () -> {
            if (!authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body) || !body.isAlive() || body.isRemoved()
                    || body.level() != server || server.getEntity(body.getUUID()) != body
                    || server.getEntity(item.getUUID()) != item || item.isRemoved()
                    || !item.isAddedToLevel() || item.getTarget() != null || item.hasPickUpDelay()
                    || !same(expected.exactStack(), item.getItem())
                    || !com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinInteractionReach(body, item.getBoundingBox())
                    || !com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinInteractionReach(body,
                    new net.minecraft.world.phys.AABB(machinePos))
                    || !unobstructed(body,item.getBoundingBox().getCenter(),null)
                    || !unobstructed(body,machinePos.getCenter(),machinePos) || server.getBlockEntity(machinePos) != machine
                    || !machine.isLoadedValid() || !machineIdentity.equals(machine.placementIdentity())) return false;
            item.setItem(after);
            return same(after, item.getItem());
        }) ? Result.SUCCESS : Result.DENIED;
    }
    static boolean policiesMatch(DrainableSolutionPolicy sourcePolicy, String actualSourceName, DumpableSolutionPolicy targetPolicy) {
        return sourcePolicy != null && sourcePolicy.enabled() && actualSourceName != null
                && actualSourceName.equals(sourcePolicy.solutionName())
                && (targetPolicy == null || targetPolicy.enabled() && targetPolicy.unlimited()
                && ChemMasterBuffer.SOLUTION_NAME.equals(targetPolicy.solutionName()));
    }
    private static boolean unobstructed(net.minecraft.world.entity.LivingEntity body, Vec3 target, BlockPos intendedBlock) {
        var level = body.level();
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(target))) return false;
        Vec3 from = body.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || intendedBlock != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && intendedBlock.equals(hit.getBlockPos());
    }
    private static boolean same(ItemStack a, ItemStack b) { return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a,b); }
}

