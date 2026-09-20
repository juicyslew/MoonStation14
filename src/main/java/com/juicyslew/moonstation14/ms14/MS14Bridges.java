package com.juicyslew.moonstation14.ms14;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectComponent;
import com.juicyslew.moonstation14.util.SystemLink;

public class MS14Bridges {
    // We use Suppliers or Lazy initialization to ensure we don't hit
    // NullPointerExceptions if the Registries aren't fully baked yet.

    public static final SystemLink<ReagentAttachment, ReagentComponent> REAGENT = new SystemLink<>(
            ModDataAttachments.REAGENT,
            ModDataComponents.REAGENT,
            ReagentAttachment::new
    );

    public static final SystemLink<StatusEffectAttachment, StatusEffectComponent> STATUS_EFFECT = new SystemLink<>(
            ModDataAttachments.STATUS_EFFECT,
            ModDataComponents.STATUS_EFFECT,
            StatusEffectAttachment::new
    );
}