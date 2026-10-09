package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Local identity snapshot: controller object lifetime for BE targets, state signature otherwise. */
public final class ExamineControllerIdentity {
    public record Binding(ExamineTarget target, Object controllerReference, ResourceLocation blockId,
                          String stateSignature) { }
    private ExamineControllerIdentity() { }
    public static boolean same(Binding a, Binding b) {
        if (a == null || b == null || !a.target().equals(b.target()) || !Objects.equals(a.blockId(), b.blockId()))
            return false;
        if (a.controllerReference() != b.controllerReference()) return false;
        return a.controllerReference() != null || Objects.equals(a.stateSignature(), b.stateSignature());
    }
}
