# Medical item footprint and stack parity

Verified against Space Station 14 checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Prototype ancestry is in `Resources/Prototypes/Entities/Objects/Specific/Medical/healing.yml`
and `healthanalyzer.yml`; item size defaults are in `Resources/Prototypes/item_size.yml`.

The medical prototype ancestry used here resolves `BaseItem` -> `BaseHealingItem` ->
ointment, gauze, and brutepack. The local typed `medical_item` composition retains
the inherited `Small` item size and stack maximum independently of each child's
initial quantity. Healing supply singles start at one while retaining a maximum
of ten; the regular supply prototypes start at ten. The handheld analyzer has no
upstream healing stack type and remains a native singleton (maximum one).
The syringe resolves `Tiny`; its single-item native registration remains
nonstackable (the prototype quantity is one).

Upstream supplies use distinct `Ointment`, `Gauze`, and `Brutepack` stack types,
each with a ten-item maximum; the `*1` prototypes change initial quantity only.
The local typed model represents the shared maximum through inherited
`stack.max_count`, independently from `stack.count`.

Footprints are derived from the resolved medical `item.size` only when no
explicit footprint profile exists. This uses the existing profile catalog as
the override/fallback source rather than duplicating medical item geometry in
separate profile JSON: Small maps to the existing small 1x2 cell geometry and
Tiny to 1x1. This agrees with the existing bottle precedent. The medical
footprint mapping is a Minecraft grid adaptation, not SS14 grid cells.

Upstream sizing facts verified from the pinned `Resources/Prototypes/item_size.yml`:
Tiny has weight
1 and shape `(0,0,0,0)`; Small has weight 2 and shape `(0,0,0,1)`. The local
storage adaptation intentionally uses the existing 1x2 small grid profile;
these grid cells are not a claim that SS14 shape coordinates are grid cells.
