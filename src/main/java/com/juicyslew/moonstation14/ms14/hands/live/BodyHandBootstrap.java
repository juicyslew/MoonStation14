package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Called only at an eligible CHARACTER Ready commit, never during offer or read-only authority lookup. */
public final class BodyHandBootstrap {
    private BodyHandBootstrap() { }

    public enum Result { INITIALIZED, PRESERVED, NO_HANDS, REJECTED }

    /** No rejection changes the body. In particular, legacy tokens never become live stack ownership. */
    public static Result ensure(Mob body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return Result.REJECTED;

        var prototype = HandCapability.resolveHostCharacter(body);
        if (prototype.isEmpty()) return Result.REJECTED;
        List<String> ids = prototype.orElseThrow().hands();
        LiveHands existing = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (ids.isEmpty()) return existing == null && !body.hasData(ModDataAttachments.LIVE_HANDS.get())
                ? Result.NO_HANDS : Result.REJECTED;
        if (existing != null) return existing.compatible(body) ? Result.PRESERVED : Result.REJECTED;
        if (body.hasData(ModDataAttachments.LIVE_HANDS.get())) return Result.REJECTED;
        return LiveHands.initialize(body).isPresent() ? Result.INITIALIZED : Result.REJECTED;
    }
}
