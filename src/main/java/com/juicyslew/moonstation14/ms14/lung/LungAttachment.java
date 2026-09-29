package com.juicyslew.moonstation14.ms14.lung;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;

/** Immutable attachment wrapper; initialized empty gas remains persistable. */
public record LungAttachment(LungComponent component) implements IMS14Attachment<LungAttachment,LungComponent> {
    public static final Codec<LungAttachment> CODEC = LungComponent.CODEC.xmap(LungAttachment::new, LungAttachment::component);
    public static final StreamCodec<RegistryFriendlyByteBuf,LungAttachment> STREAM_CODEC = LungComponent.STREAM_CODEC.map(LungAttachment::new,LungAttachment::component);
    public LungAttachment { if(component==null) throw new IllegalArgumentException("component required"); }
    @Override public LungComponent toComponent(){return component;}
    @Override public Codec<LungAttachment> getCodec(){return CODEC;}
    @Override public StreamCodec<RegistryFriendlyByteBuf,LungAttachment> getStreamCodec(){return STREAM_CODEC;}
}
