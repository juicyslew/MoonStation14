package com.juicyslew.moonstation14.ms14.ingestion;

import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.MouthComponent;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.world.entity.LivingEntity;

/** Resolved oral capability and optional live hand context for a body ingestion action. */
public final class OralAccess {
    private OralAccess() { }

    public record HandContext(String id, long revision) {
        public HandContext {
            if (id == null || id.isBlank() || revision < 0) throw new IllegalArgumentException("invalid hand context");
        }
    }

    public static boolean handless(LivingEntity body) {
        var actor = ActiveCharacterPolicy.resolveActor(body).orElse(null);
        return supportsHandlessOralAccess(actor);
    }

    /** Pure resolved-composition predicate shared by runtime admission and prototype tests. */
    public static boolean supportsHandlessOralAccess(CharacterData actor) {
        if (actor == null || actor.component(MouthComponent.class).isEmpty()) return false;
        return actor.component(HandsPrototypeComponent.class).map(h -> h.hands().isEmpty()).orElse(true);
    }
}
