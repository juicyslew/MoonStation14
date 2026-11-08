package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent;

import java.util.Optional;

/** Client-only projection consumer; it never resolves server actor authority. */
public final class PortableLivingClientTargetPolicy {
    private PortableLivingClientTargetPolicy() { }

    public static Optional<PortableBodyComponent> portableCapability(CharacterData projectedCharacter) {
        return projectedCharacter == null ? Optional.empty()
                : projectedCharacter.component(PortableBodyComponent.class);
    }
}
