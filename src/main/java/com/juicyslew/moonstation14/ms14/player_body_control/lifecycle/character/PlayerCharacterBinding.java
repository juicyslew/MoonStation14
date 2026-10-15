package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import net.minecraft.nbt.CompoundTag;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;

/** Immutable per-body account/profile/Mind identity, deliberately separate from its character prototype. */
public record PlayerCharacterBinding(UUID accountId, String profileKey, UUID mindId) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(UUID.fromString(value)); }
        catch (IllegalArgumentException invalid) { return com.mojang.serialization.DataResult.error(() -> "invalid UUID"); }
    }, UUID::toString);
    public static final Codec<PlayerCharacterBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("account").forGetter(PlayerCharacterBinding::accountId),
            Codec.STRING.fieldOf("profile").forGetter(PlayerCharacterBinding::profileKey),
            UUID_CODEC.fieldOf("mind").forGetter(PlayerCharacterBinding::mindId)
    ).apply(instance, PlayerCharacterBinding::new));
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

