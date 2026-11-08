package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** One attachment write between two distinct body-owned grid hosts. */
public final class BodyBodyGridTransfer {
    private BodyBodyGridTransfer() { }
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result move(ServerPlayer actor, LiveHands.GridHost from, LiveHands.GridHost to,
            String child, long revision, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return moveOwned(authority.orElseThrow().body(), from, to, child, revision, oldAnchor, oldRotation,
                newAnchor, newRotation, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    static Result moveOwned(LivingEntity body, LiveHands.GridHost from, LiveHands.GridHost to,
            String child, long revision, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation, BooleanSupplier authorityValid) {
        return moveOwned(body, from, to, child, revision, oldAnchor, oldRotation, newAnchor, newRotation,
                authorityValid, (carrier, next) -> LiveHandsCommitGateway.commitOwned(carrier, next));
    }

    static Result moveOwned(LivingEntity body, LiveHands.GridHost from, LiveHands.GridHost to,
            String child, long revision, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation, BooleanSupplier authorityValid,
            BiConsumer<LivingEntity, LiveHands> publish) {
        if (body == null || from == null || to == null || authorityValid == null || publish == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level) || !CharacterControlSystem.canAct(body))
            return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        var equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty() || !equipment.orElseThrow().persisted()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        ItemStack source = hostStack(before, equipment.orElseThrow(), from);
        ItemStack destination = hostStack(before, equipment.orElseThrow(), to);
        if (source == null || destination == null) return Result.DENIED;
        var next = before.moveBodyGrid(revision, declared, from, to, child, oldAnchor, oldRotation, newAnchor, newRotation);
        if (next.isEmpty() || !BodyHandStateService.representable(before, body)) return Result.DENIED;
        boolean authorized;
        try { authorized = authorityValid.getAsBoolean(); }
        catch (RuntimeException failure) { return Result.DENIED; }
        if (!authorized || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(current -> current.persisted()
                    && current.slots().stream().map(LiveHands.EquipmentSlot::id).toList().equals(declared)
                    && hostStack(before, current, from) != null
                    && hostStack(before, current, to) != null
                    && ItemStack.matches(source, hostStack(before, current, from))
                    && ItemStack.matches(destination, hostStack(before, current, to))).orElse(false))
            return Result.DENIED;
        try { publish.accept(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static ItemStack hostStack(LiveHands state, LiveHands.EquipmentSnapshot equipment, LiveHands.GridHost host) {
        if (host.id() == null || host.token() == null) return null;
        if (host.worn()) return equipment.slots().stream().filter(s -> s.id().equals(host.id())
                && s.token().filter(host.token()::equals).isPresent()).findFirst()
                .flatMap(LiveHands.EquipmentSlot::stack).orElse(null);
        if (!state.handIds().contains(host.id())) return null;
        return state.token(host.id()).filter(host.token()::equals).flatMap(ignored -> state.stackCopy(host.id())).orElse(null);
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}

