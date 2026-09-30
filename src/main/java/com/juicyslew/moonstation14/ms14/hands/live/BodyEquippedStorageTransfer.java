package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Moves a single cell between a worn body-owned bag/belt and a body-owned hand. */
public final class BodyEquippedStorageTransfer {
    private BodyEquippedStorageTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result store(ServerPlayer actor, String slot, String expectedContainerToken,
                               String hand, String sourceToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), slot, expectedContainerToken, hand,
                sourceToken, expectedRevision, true,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result take(ServerPlayer actor, String slot, String expectedContainerToken,
                              String hand, String childToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), slot, expectedContainerToken, hand,
                childToken, expectedRevision, false,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Fixture seam: only a real spawned body; tests substitute the controller validity, not ownership.
    static Result transferOwned(LivingEntity body, String slot, String containerToken, String hand,
                                String token, long revision, boolean storing, BooleanSupplier authorityValid) {
        if (body == null || slot == null || containerToken == null || hand == null || token == null
                || authorityValid == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !live(body, level)
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        Optional<LiveHands.EquipmentSnapshot> equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        if (equipment.orElseThrow().slots().stream().noneMatch(candidate -> candidate.id().equals(slot)
                && candidate.token().filter(containerToken::equals).isPresent())) return Result.DENIED;
        Optional<LiveHands> next = storing
                ? before.insertEquippedStorage(revision, declared, slot, containerToken, hand, token)
                : before.extractEquippedStorage(revision, declared, slot, containerToken, hand, token);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level)
                || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(snapshot -> snapshot.slots().stream()
                        .map(LiveHands.EquipmentSlot::id).toList().equals(declared)).orElse(false))
            return Result.DENIED;
        try {
            body.setData(ModDataAttachments.LIVE_HANDS.get(), next.orElseThrow());
        } catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
