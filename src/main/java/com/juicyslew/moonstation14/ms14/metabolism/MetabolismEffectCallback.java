package com.juicyslew.moonstation14.ms14.metabolism;

/** Receives one already-removed metabolism attempt before its products are added. */
@FunctionalInterface
public interface MetabolismEffectCallback {
    void execute(MetabolismEffectInvocation invocation);
}
