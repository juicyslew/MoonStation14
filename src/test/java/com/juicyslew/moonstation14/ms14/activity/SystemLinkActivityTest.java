package com.juicyslew.moonstation14.ms14.activity;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.util.SystemLink;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemLinkActivityTest {
    @Test
    void legacyLinksHaveNoActivityAndBindingsAreTyped() {
        AttachmentType<ReagentAttachment> type = AttachmentType.builder(
                (java.util.function.Supplier<ReagentAttachment>) ReagentAttachment::new).build();
        SystemLink<ReagentAttachment, ReagentComponent> legacy = new SystemLink<>(
                () -> type, () -> null, ReagentAttachment::new);
        assertTrue(legacy.activityBinding().isEmpty());

        SystemLink<ReagentAttachment, ReagentComponent> linked = new SystemLink<>(
                () -> type, () -> null, ReagentAttachment::new,
                new SystemLink.ActivityBinding<>(EntityActivity.REAGENT_METABOLISM,
                        data -> !data.isEmpty()));
        assertTrue(linked.activityBinding().orElseThrow().activity()
                == EntityActivity.REAGENT_METABOLISM);
        assertFalse(linked.activityBinding().orElseThrow().needsTicking().test(new ReagentAttachment()));
    }
}
