package com.juicyslew.moonstation14.ms14.standing;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class PostureAttachment {
    private PostureData data;
    public PostureAttachment() { this(PostureData.STANDING); }
    public PostureAttachment(PostureData data) { this.data = java.util.Objects.requireNonNull(data); }
    public PostureData data() { return data; }
    public void set(PostureData value) { data = java.util.Objects.requireNonNull(value); }
    public static final Codec<PostureAttachment> CODEC = PostureData.CODEC.xmap(PostureAttachment::new, PostureAttachment::data);
    public static final StreamCodec<RegistryFriendlyByteBuf, PostureAttachment> STREAM_CODEC = StreamCodec.of(
              (b,v) -> { var d=v.data; ByteBufCodecs.BOOL.encode(b,d.down()); ByteBufCodecs.BOOL.encode(b,d.voluntaryAutoStand()); ByteBufCodecs.VAR_LONG.encode(b,d.knockdownUntil()); ByteBufCodecs.VAR_LONG.encode(b,d.getUpKey()); ByteBufCodecs.VAR_INT.encode(b,d.totalHands()); ByteBufCodecs.VAR_INT.encode(b,d.freeHands()); ByteBufCodecs.BOOL.encode(b,d.pulling()); ByteBufCodecs.BOOL.encode(b,d.faceUp()); ByteBufCodecs.BOOL.encode(b,d.ownCrawling()); ByteBufCodecs.FLOAT.encode(b,d.movementFacingYaw()); },
               b -> new PostureAttachment(new PostureData(ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.VAR_LONG.decode(b), ByteBufCodecs.VAR_LONG.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.FLOAT.decode(b))));
}

