package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Recipe choice is scoped to one exact body-control epoch and mind, never just an actor UUID. */
final class DisposalRecipeSelection {
    private record Choice(long epoch, MindId mind, String prototype) { }
    private final Map<UUID, Choice> choices = new HashMap<>();

    void select(UUID body, long epoch, MindId mind, String prototype) {
        if (body == null || epoch <= 0 || mind == null || prototype == null || prototype.isBlank())
            throw new IllegalArgumentException("invalid disposal recipe selection");
        choices.put(body, new Choice(epoch, mind, prototype));
    }

    Optional<String> selected(UUID body, long epoch, MindId mind) {
        Choice choice = body == null ? null : choices.get(body);
        return choice != null && choice.epoch == epoch && choice.mind.equals(mind)
                ? Optional.of(choice.prototype) : Optional.empty();
    }
}

