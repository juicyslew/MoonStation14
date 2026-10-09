# Examination sprint

## Goal and status

Accepted and closed by the owner on 2026-10-07. Delivered shared examination with
separate 3D targeting, authoritative eligibility, and component-contributed
information, including solution and celestial readouts. See `HANDOFF.md` for
current state and known gaps; aggregate GameTest failures are not waived.

Historical owner testing found the basic interaction broken: steel walls highlighted but
produced no popup, and airlocks produced neither highlight nor popup. This
was an acceptance blocker resolved before closure. Fixes required real server
coverage and a connected request/response/presentation check; policy-only and
source-only passes do not satisfy this gate. Server validation is no longer
restricted to a single final run.

## Ordered work

1. **Foundation.** Add shared target/request/response/entry contracts,
   examiner profile/prototype resolution, pure policy, server-authenticated body
   and observer authority, loaded-root/LOS validation, bounded contributor SPI,
   and source-supported public baseline name/description. Register lifecycle,
   codecs, networking and server handlers. Include `PARITY.md`; report gaps
   instead of asserting unsupported parity.
2. **Target providers.** Record mappings in `CONTENT.md` and provide airlock,
   APC, disposal, tool/item, and character domain data via the foundation SPI.
   Provider-specific prototypes and domain fields are not foundation-owned.
3. **3D selection and presentation.** Record selection adaptations in
   `SELECTION.md` and reuse the shared transparent-state classifier. Preserve Shift + left-click
   examination, with explicit precedence over ordinary item actions. Highlight
   the intended target, distinguish visual targeting from collision targeting,
   and allow both a transparent window and visible objects behind it to be
   deliberately selected. Bound candidate queries to loaded content. Present a
   compact, non-modal result without suppressing HUD, hands, storage, speech,
   movement, or the shared L-Alt pointer contract.
4. **Validate and deliver.** Test inheritance/default resolution, live runtime
   consumers, target selection, ordinary/detail boundaries, authority, response
   ordering, and HUD/input policy. Add tagged live fixtures. Run one coordinated
   final server validation after implementation; do not launch separate servers
   for each work unit. Keep the human checklist short.

## Established SS14 baseline

Reference: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Resources/keybinds.yml` binds `ExamineEntity` to Shift + MouseLeft. The shared
examine system normally permits examination within 16 units and details within
3; examiner condition and occlusion modify eligibility. Examination is not
unlimited-distance access. Stun is not automatically equivalent to inability to
examine; use the actual examine rules rather than hand-action admission.

`ExaminedEvent` gathers centrally ordered contributions; client/server examine
systems handle the transient popup and authoritative correction. Examiner chain,
defaults and numeric rules for the human/observer foundation are recorded in
[`PARITY.md`](PARITY.md). Machine/item contributor audits and supported local
composition are recorded in [`CONTENT.md`](CONTENT.md). Job visibility remains
unverified and is not implemented in this sprint.

## Minecraft adaptations and invariants

- Visible identification, general examination, and close details are distinct.
  Identification may use already-public rendered metadata beyond normal examine
  range; it must not invent access to authoritative details.
- Target selection follows the view; range eligibility follows the controlled
  character, not a displaced third-person camera or Minecraft carrier.
- Transparent vision does not imply physical interaction through glass. Windows
  remain selectable; opaque walls block examination. Selection through glass and
  alternate-candidate selection are explicit 3D adaptations, not claims about
  SS14's mouse-selection implementation.
- Keep stable target identity and canonical roots for two-half doors and other
  supported composite targets. Do not force chunks or scan the whole world.
- Preserve current medical, containment, native movement, power, and retained UI
  behavior. No new global modal screen or universal stun-triggered UI closure.
- Favor SS14 presentation assets when applicable and retain attribution.

## Acceptance criteria

- Shift-click examines the visibly highlighted target instead of using/dropping
  an item; ordinary unmodified interactions remain unchanged.
- A player can choose glass itself or a visible target behind it. An opaque wall
  cannot be bypassed by client-selected targets or camera displacement.
- Ordinary/detail boundaries and examiner-condition rules are authoritative;
  move out of range, change body/epoch, delete/replace the target, or reorder
  replies without displaying invalid details.
- Representative targets contribute useful names/descriptions and supported
  state through the shared system, with source-verified detail gates.
- Prototype inheritance/defaults are exercised by the live runtime, including a
  non-default test definition; no hardcoded-only prototype implementation.
- Examination presentation preserves HUD, chat, storage, movement, and shared
  pointer behavior. Input and embedded screen initialization have regressions.
- Automated results and connected acceptance limits are reported honestly. The
  previous merged full suite's 12 unresolved failures are not silently waived.

## Out of scope

Wearables, engineering-locker implementation, medical treatment, access control,
new logging services, full SS14 verb-menu reproduction, and unrelated historical
test cleanup. Existing systems may provide examination information without
expanding their gameplay features.

## Owner feedback follow-up

- Fix admission of healthy, freshly committed characters whose medical-state
  attachment is absent; use the medical system's shared default without creating
  state or weakening actor/epoch ownership checks.
- Default to transient examination, with outside-click/Escape dismissal and an
  explicit opt-in pin. Pinning never extends eligibility or target lifetime.
- Keep the actively examined target highlighted. An airlock highlight covers
  its whole controller assembly, not just one leaf or upper/lower hit piece.
- Default to the visible target behind transparent glass; retain glass as an
  alternate. Keep cycling stable while the same transparent anchor remains hit.
- Use smaller, wrapped popup text with correctly scaled mouse hitboxes.
- Ineligible targets must not receive the blue preview or an examination popup;
  server eligibility remains authoritative.
- Run server regression checks before presenting these changes. Automated GUI
  input remains declined; connected visual acceptance requires owner testing.
