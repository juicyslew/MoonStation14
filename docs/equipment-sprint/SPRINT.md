# Equipment foundation

Closed with owner approval on 2026-10-08. See [HANDOFF.md](HANDOFF.md) for the
final state, architecture, validation boundaries and deferred work. Changes remain
uncommitted; sprint closure does not authorize a commit or push.

## Scope

Owner-approved on 2026-10-08:

- Provide every standard SS14 equipment slot for the supported humanoid character.
  Slot availability remains prototype-driven rather than granting human anatomy
  to every mob. The nine clothing slots occupy the bottom-left HUD with a bag
  toggle; ID/PDA, back, belt, pockets and suit storage occupy the bottom hotbar.
- Supply a small representative item set covering every slot. Use SS14 inventory
  artwork with attribution and replaceable placeholder 3D models.
- Render authoritative held items and equipped clothing on the character in F5
  third person and for other viewers. First-person arms and held-item viewmodels
  are explicitly out of scope; do not draw a replacement body pass there.
- Keep stripping in a separate sprint; self-equipping does not require it.

## Ordered work

### Owner correction acceptance

- Restore the original bottom-hotbar bag/belt positions, with the other hotbar
  inventory slots arranged alongside hands as in the supplied SS14 screenshot.
- Halve the clothing panel size. Align the bag toggle immediately left of shoes
  and below jumpsuit; do not leave a blank row below the clothing.
- Use opposite show/hide toggle arrows. Upstream uses one static texture; the
  requested alternate state is an explicit port presentation adaptation.
- Support Z quick-equip and clothing drag/drop between accepting hand, equipment
  and storage surfaces, through shared authoritative transfers.
- Preserve wide/slim fit of third-person clothing and held layers for the
  controlled body and observers.

### Implementation sequence

1. Audit pinned SS14 inventory templates, slot/dependency rules, and complete
   candidate prototype ancestry/defaults/consumers. Map supported composition and
   record deliberate placeholder/functionality boundaries.
2. Extend existing resolved prototypes, `LiveHands`, equipment transfers, and
   owner snapshots. Preserve item-owned storage and the shared commit gateway;
   validate complete candidate hands/equipment/grids consistently.
3. Extend the current pointer/HUD input path with the collapsible equipment panel.
   Preserve existing hands, storage, analyzer, construction, and wire interactions.
4. Add presentation-only held/worn replication and layers to the existing
   character renderer for third person and observers. Never broadcast private
   storage/action credentials.
5. Review and validate the integrated result. Run resource-intensive checks
   sequentially, with focused fixtures and no repeated automatic client launches.

## Acceptance

- Every human inventory slot has a working runtime consumer and HUD representation,
  including conditional pockets/ID/suit storage, not merely JSON or disabled icons.
- Resolved item/slot composition governs fit and dependencies. Removing a supporting
  garment conserves dependent contents using verified SS14 behavior.
- Existing bags/belts retain their contents, storage windows, and interaction rules.
- Representative items cover all slots; candidates and model replacement contracts
  are documented for the modeller. Unsupported unrelated functions are explicit.
- Hands and worn clothing follow the actual body's authoritative state across
  equip, unequip, hand changes, death, and tracking/authority changes.
- F5 third-person and observer render paths consume the same visible projection;
  first person has no arms or held-item models. Observer presentation grants no
  action authority and contains no private storage contents.
- Prototype inheritance/defaults, real transfer/consumer paths, layout/hit testing,
  visibility projection, and relevant lifecycle cases have focused evidence.
  Connected graphical acceptance remains separate from CPU/source checks.

## Starting points

`../hands-inventory-program/HANDOFF.md`, `../wearables-sprint/HANDOFF.md`,
`../basic-medical-treatment-sprint/HANDOFF.md`, and its `MERGE-MAIN.md`.
Baseline is owner-merged `86145a8`; 2,813 unit tests passed, full native suite
682/696 passed with 14 unclassified failures. Do not treat them as proven inherited
or pull unrelated physics/power/ventilation repairs into this sprint.
