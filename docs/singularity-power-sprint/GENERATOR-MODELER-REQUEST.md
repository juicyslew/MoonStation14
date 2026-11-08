# Containment-generator state art request

Asset: R6 geometry / R8 texture-only vertical-state revision of
`generation_singularity_containment`.

The current `a1`, `a2` and `a3` atlas images are pixel-identical to `default`.
We need **visible state art**, not another HUD message. New geometry is not
required if the existing lobe/cap surfaces can carry readable indicators.

- `default`: disabled; field indicators dark.
- `a1`: enabled, including zero charge and no connections; clearly illuminated
  field indicators.
- `a2`: enabled with one field connection; distinguishable from `a1`.
- `a3`: enabled with multiple field connections; distinguishable from `a1/a2`.
- Preserve independent `p1/p3/p6` charge indication and `on` connected indication.
  Do not use the connected lamp as the enabled lamp.

Use the existing `field_lobe_0`, `field_lobe_cap_0`, `field_lobe_1` and
`field_lobe_cap_1` UV regions. Preserve the nine-part geometry, UV layout, atlas
dimensions, texture references and state identities. Keep metal surfaces and
other indicators unchanged so the runtime can compose charge, field and
connection states independently. Make the difference readable from ordinary
operating distance and from both sides, without making the whole machine glow.

Return the updated state exports, preview images showing disabled/zero-charge
enabled/connected, hashes and attribution. Updated art needs owner acceptance
before import; original accepted R6 resources remain untouched.

Owner explicitly approved R8 on 2026-10-12 and eight textures are imported and
wired to native adapters. The project records approval separately; external
candidate saved acceptance remains false and no external acceptance is claimed.
Focused source/runtime checks and GPU visual acceptance remain pending.

SS14 correspondence: enabling immediately selects `a1`; `on` means connected,
not merely enabled. No upstream toggle sound was established, so no new sound
is required to satisfy this request.

Use pinned SS14
`Resources/Textures/Structures/Power/Generation/Singularity/containment.rsi`
(`a1/a2/a3`, `p1/p3/p6`, `on`) as the indicator/palette reference. Its prototype
uses unshaded indicator layers. Retain the RSI attribution/license if source
pixels are reused; do not invent a different state meaning.
