# Reactive puddle contact superseded / disabled (2026-09-23)

## Current behavior

The previously implemented direct `entityInside` contact adaptation is **superseded and disabled**. Walking into, standing in, or directly simulating `entityInside` on a puddle does not consume reagents, apply reactive effects, or create character exposure state. There is no local surface-exposure store or contact-dose transfer path. Serializable reactive-effect prototype definitions and `EffectCause.CONTACT` remain data-only and unreachable from puddle gameplay until real slippery / `SlipEvent` mechanics exist.

Future contact behavior is deferred until slip eligibility/threshold, sliding exclusion, chance, and reactive target capability can be implemented as a coherent slip mechanic. The upstream 15% split does not establish a local skin-storage destination; this project intentionally has no surface store to receive a dose.

The temporary `surface_exposure` attachment and item component registrations were removed. Existing development saves containing that temporary attachment may lose it; no compatibility or migration layer is provided.

No arbitrary contact cooldown or episode tracker exists.

## Puddle low-volume lifecycle and rendering boundary

Puddle blockstate level zero uses the faint `splata` texture, but is no longer a persistent empty-puddle state. The authoritative cent total (`ReagentAttachment.totalUnits()`) removes the puddle block and block entity when a committed update drains it to exactly zero; a stale preexisting empty puddle is removed on its server tick. Empty block placement can survive only until that tick so an operation can place and fill it synchronously. Do not use `/setblock ... moonstation14:puddle` as ordinary setup: it creates a transient empty puddle that despawns. Create filled puddles with a filled BottleItem by sneak-right-clicking the top face of a solid block. Every finite strictly positive reagent volume, including one cent, selects at least fill level one (the existing visible `splatb` model); no positive residue is discarded or given a minimum-volume despawn threshold. Pure polytrinic acid therefore remains until actually drained or cleaned. The established higher fill thresholds remain unchanged. The tint calculation uses double-precision weighted sums and accepts only positive finite weights so a subnormal single-reagent amount retains that reagent's catalog RGB instead of collapsing to black. The texture/model appearance still needs a manual client visual check; this server-side logic has no client rendering test.

## Historical / retained behavior notes

The low-volume appearance behavior below is independent of contact and remains active. Reactive prototype decoding remains available for future integration; the prior direct-contact GameTests and runtime classes have been removed in favor of a negative no-contact GameTest.
