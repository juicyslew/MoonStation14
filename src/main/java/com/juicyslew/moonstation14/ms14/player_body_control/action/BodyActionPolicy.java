package com.juicyslew.moonstation14.ms14.player_body_control.action;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;

import java.util.Objects;
import java.util.UUID;

/** Pure snapshot and status decisions. None of these predicates authenticates a Minecraft connection. */
public final class BodyActionPolicy {
    private BodyActionPolicy() { }

    public enum Source { LIFECYCLE, EXPERIMENTAL_HARNESS }

    /** Only an explicitly committed CHARACTER authority can be selected. */
    public static boolean selects(boolean lifecycle, boolean experimental, boolean experimentalCharacter) {
        return lifecycle != experimental && (lifecycle || experimentalCharacter);
    }

    /** Conditions only; callers must establish these from the live server, not client assertions. */
    public static boolean eligiblePlayer(boolean serverThread, boolean exactConnected,
            boolean genuine, boolean spectator, boolean alive, boolean unmounted) {
        return serverThread && exactConnected && genuine && spectator && alive && unmounted;
    }

    /** Reference identities are intentional: an equal-looking replacement is not the same lease. */
    public record Identity(Object player, Object body, UUID bodyUuid, Source source, MindId mind,
                           MobHarnessId harness, long epoch, CharacterData prototype,
                           boolean complexInteractionEnabled) {
        public Identity {
            Objects.requireNonNull(player);
            Objects.requireNonNull(body);
            Objects.requireNonNull(bodyUuid);
            Objects.requireNonNull(source);
            Objects.requireNonNull(mind);
            Objects.requireNonNull(harness);
            Objects.requireNonNull(prototype);
            if (epoch <= 0 || !bodyUuid.equals(harness.value()))
                throw new IllegalArgumentException("Invalid body lease");
        }
    }

    public static boolean same(Identity expected, Identity current) {
        return expected != null && current != null
                && expected.player() == current.player() && expected.body() == current.body()
                && expected.bodyUuid().equals(current.bodyUuid()) && expected.source() == current.source()
                && expected.mind().equals(current.mind()) && expected.harness().equals(current.harness())
                && expected.epoch() == current.epoch() && expected.prototype() == current.prototype()
                && expected.complexInteractionEnabled() == current.complexInteractionEnabled();
    }

    /** Read-only component/status eligibility; not permission to act on any target. */
    public static boolean eligibleForComplexInteraction(Identity identity, boolean stunned) {
        return identity != null && identity.complexInteractionEnabled() && !stunned;
    }
}
