package com.juicyslew.moonstation14.ms14;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectComponent;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.alert.AlertAttachment;
import com.juicyslew.moonstation14.ms14.alert.AlertComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerAttachment;
import com.juicyslew.moonstation14.ms14.hunger.HungerComponent;
import com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment;
import com.juicyslew.moonstation14.ms14.thirst.ThirstComponent;
import com.juicyslew.moonstation14.util.SystemLink;

public class MS14Bridges {
    // We use Suppliers or Lazy initialization to ensure we don't hit
    // NullPointerExceptions if the Registries aren't fully baked yet.

    public static final SystemLink<ReagentAttachment, ReagentComponent> REAGENT = new SystemLink<>(
            ModDataAttachments.REAGENT,
            ModDataComponents.REAGENT,
            ReagentAttachment::new,
            new SystemLink.ActivityBinding<>(EntityActivity.REAGENT_METABOLISM, data -> !data.isEmpty())
    );

    public static final SystemLink<StatusEffectAttachment, StatusEffectComponent> STATUS_EFFECT = new SystemLink<>(
            ModDataAttachments.STATUS_EFFECT,
            ModDataComponents.STATUS_EFFECT,
            StatusEffectAttachment::new,
            new SystemLink.ActivityBinding<>(EntityActivity.STATUS_EFFECT, data -> !data.isEmpty())
    );

    public static final SystemLink<ReagentAttachment, ReagentComponent> STOMACH = new SystemLink<>(
            ModDataAttachments.STOMACH, ModDataComponents.STOMACH,
            ReagentAttachment::new);
    public static final SystemLink<AlertAttachment, AlertComponent> ALERT = new SystemLink<>(
            ModDataAttachments.ALERT, ModDataComponents.ALERT, AlertAttachment::new,
            new SystemLink.ActivityBinding<>(EntityActivity.ALERT, data -> data.snapshot().values().stream()
                    .anyMatch(alert -> alert.deadline().isPresent())));
    public static final SystemLink<HungerAttachment, HungerComponent> HUNGER = new SystemLink<>(
            ModDataAttachments.HUNGER, ModDataComponents.HUNGER, HungerAttachment::new,
            new SystemLink.ActivityBinding<>(EntityActivity.HUNGER,
                    data -> com.juicyslew.moonstation14.ms14.hunger.HungerSystem.needsTicking(data)));
    public static final SystemLink<ThirstAttachment, ThirstComponent> THIRST = new SystemLink<>(
            ModDataAttachments.THIRST, ModDataComponents.THIRST, ThirstAttachment::new,
            new SystemLink.ActivityBinding<>(EntityActivity.THIRST,
                    data -> com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.needsTicking(data)));
}
