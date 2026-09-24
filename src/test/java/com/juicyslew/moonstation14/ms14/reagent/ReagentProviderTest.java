package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.util.SystemLink;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentProviderTest {
    @Test
    void detachedAbsentReadAndNoOpDoNotCreateAttachment() {
        TestHolder holder = new TestHolder();
        SystemLink<ReagentAttachment, ReagentComponent> link = testLink();

        ReagentAttachment read = MS14Provider.get(holder, link);
        assertTrue(read.isEmpty());
        assertEquals(0, holder.getCalls);
        assertEquals(0, holder.setCalls);

        ReagentAttachment detached = MS14Provider.getDetached(holder, link);
        assertTrue(detached.isEmpty());
        assertEquals(0, holder.getCalls);

        var before = MS14Provider.snapshot(detached);
        assertFalse(MS14Provider.updateIfChanged(holder, link, before, detached));
        assertEquals(0, holder.setCalls);
    }

    @Test
    void changedDetachedAttachmentIsAttachedExactlyOnce() {
        TestHolder holder = new TestHolder();
        SystemLink<ReagentAttachment, ReagentComponent> link = testLink();
        ReagentAttachment detached = MS14Provider.getDetached(holder, link);
        var before = MS14Provider.snapshot(detached);
        detached.specificAdd(ResourceKey.create(
                ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("test", "reagent")), 1f, 10f);

        assertTrue(MS14Provider.updateIfChanged(holder, link, before, detached));
        assertEquals(1, holder.setCalls);
    }

    @Test
    void existingAttachmentIsCopiedBeforeDetachedMutation() {
        TestHolder holder = new TestHolder();
        SystemLink<ReagentAttachment, ReagentComponent> link = testLink();
        ResourceKey<ReagentData> key = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("test", "stored"));
        holder.setData(link.attachment().get(), new ReagentAttachment(Map.of(key, 1f)));
        holder.getCalls = 0;
        holder.setCalls = 0;

        ReagentAttachment detached = MS14Provider.getDetached(holder, link);
        detached.specificAdd(key, 1f, 10f);

        assertEquals(2f, detached.getMap().get(key));
        assertEquals(1f, holder.getData(link.attachment().get()).getMap().get(key));
        assertEquals(2, holder.getCalls);
        assertEquals(0, holder.setCalls);
    }

    @Test
    void normalGetRetainsExistingAuthoritativeAttachment() {
        TestHolder holder = new TestHolder();
        SystemLink<ReagentAttachment, ReagentComponent> link = testLink();
        ReagentAttachment stored = new ReagentAttachment();
        holder.setData(link.attachment().get(), stored);

        assertSame(stored, MS14Provider.get(holder, link));
    }

    private static SystemLink<ReagentAttachment, ReagentComponent> testLink() {
        AttachmentType<ReagentAttachment> type = AttachmentType.builder(
                (java.util.function.Supplier<ReagentAttachment>) ReagentAttachment::new).build();
        return new SystemLink<>(() -> type, () -> null, ReagentAttachment::new);
    }

    private static final class TestHolder implements IAttachmentHolder {
        private final Map<AttachmentType<?>, Object> data = new HashMap<>();
        private int getCalls;
        private int setCalls;

        @Override
        public boolean hasAttachments() {
            return !data.isEmpty();
        }

        @Override
        public boolean hasData(AttachmentType<?> type) {
            return data.containsKey(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getData(AttachmentType<T> type) {
            getCalls++;
            return (T) data.get(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T setData(AttachmentType<T> type, T value) {
            setCalls++;
            return (T) data.put(type, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T removeData(AttachmentType<T> type) {
            return (T) data.remove(type);
        }
    }
}
