package com.juicyslew.moonstation14.ms14.examine;

/** Shared target-content provider seam. Providers must mark detail rows explicitly. */
@FunctionalInterface
public interface ExamineContributor {
    void contribute(ExamineContext context, ExamineCollector collector);
}

