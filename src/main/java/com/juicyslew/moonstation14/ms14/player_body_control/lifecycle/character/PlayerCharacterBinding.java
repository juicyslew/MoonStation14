package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import net.minecraft.nbt.CompoundTag;
import java.util.Objects;
import java.util.UUID;

/** Immutable per-body account/profile/Mind identity, deliberately separate from its character prototype. */
public record PlayerCharacterBinding(UUID accountId, String profileKey, UUID mindId) {
    private static final int MAX_PROFILE_KEY_LENGTH = 64;
    private static final String PROFILE_KEY_PATTERN = "[A-Za-z0-9_-]{1,64}";
    private static final String ACCOUNT = "Account";
    private static final String PROFILE = "Profile";
    private static final String MIND = "Mind";

    public PlayerCharacterBinding {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(profileKey, "profileKey");
        Objects.requireNonNull(mindId, "mindId");
        if (profileKey.length() > MAX_PROFILE_KEY_LENGTH || !profileKey.matches(PROFILE_KEY_PATTERN))
            throw new IllegalArgumentException("profileKey has invalid syntax");
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ACCOUNT, accountId);
        tag.putString(PROFILE, profileKey.toString());
        tag.putUUID(MIND, mindId);
        return tag;
    }

    static PlayerCharacterBinding load(CompoundTag tag) {
        if (!tag.hasUUID(ACCOUNT) || !tag.hasUUID(MIND) || !tag.contains(PROFILE, CompoundTag.TAG_STRING)) {
            throw new IllegalArgumentException("Saved player-character binding is incomplete");
        }
        return new PlayerCharacterBinding(tag.getUUID(ACCOUNT), tag.getString(PROFILE), tag.getUUID(MIND));
    }
}
