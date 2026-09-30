package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Collections;

/** Ready-commit-only isolation marker; never moves or creates an item. */
public final class BodyCarrierIsolationBootstrap {
    private BodyCarrierIsolationBootstrap() { }

    public enum Result { INITIALIZED, PRESERVED, REJECTED }

    /** Call only after a CHARACTER Ready verified the exact owner, registry epoch and body. */
    public static Result ensure(ServerPlayer player, LivingEntity body) {
        if (player == null || body == null || !(player.level() instanceof ServerLevel level)
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || body.isPassenger() || player.isPassenger()
                || level.getEntity(body.getUUID()) != body || player.getCamera() != body
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) return Result.REJECTED;
        var fixture = CarrierHandInventoryGate.inspect(player).orElse(null);
        if (fixture == null) return Result.REJECTED;
        return decide(fixture, park -> player.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), park));
    }

    /** Value-level policy seam; a fixture is never evidence of a connected player or body. */
    public static Result decide(CarrierHandInventoryGate.Fixture fixture,
                                java.util.function.Consumer<CreativeParkedInventory> install) {
        if (install == null || !CarrierHandInventoryGate.allows(fixture)) return Result.REJECTED;
        if (fixture.markerPresent()) return Result.PRESERVED;
        try {
            CreativeInventorySnapshot empty = CreativeInventorySnapshot.capture(
                    Collections.nCopies(CreativeInventorySnapshot.MAIN, ItemStack.EMPTY),
                    Collections.nCopies(CreativeInventorySnapshot.ARMOR, ItemStack.EMPTY),
                    Collections.nCopies(CreativeInventorySnapshot.OFFHAND, ItemStack.EMPTY),
                    ItemStack.EMPTY, fixture.selected(), Collections.emptyList());
            install.accept(new CreativeParkedInventory(fixture.account(), empty));
            return Result.INITIALIZED;
        } catch (RuntimeException failure) {
            return Result.REJECTED;
        }
    }
}
