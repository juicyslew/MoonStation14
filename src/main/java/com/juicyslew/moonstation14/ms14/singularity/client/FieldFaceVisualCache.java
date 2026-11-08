package com.juicyslew.moonstation14.ms14.singularity.client;

import com.juicyslew.moonstation14.ms14.singularity.containment.FieldFaceSnapshotPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Client presentation cache only; never participates in containment decisions. */
public final class FieldFaceVisualCache {
    private static ResourceLocation dimension;
    private static List<FieldFaceSnapshotPayload.Face> faces = List.of();
    private FieldFaceVisualCache() { }
    public static synchronized void accept(FieldFaceSnapshotPayload snapshot) { dimension=snapshot.dimension(); faces=snapshot.faces(); }
    public static synchronized List<FieldFaceSnapshotPayload.Face> faces() { return faces; }
    public static synchronized ResourceLocation dimension() { return dimension; }
    public static synchronized void clear() { dimension=null; faces=List.of(); }
    public static synchronized void clearUnless(ResourceLocation current) { if(!java.util.Objects.equals(dimension,current)) clear(); }
}

