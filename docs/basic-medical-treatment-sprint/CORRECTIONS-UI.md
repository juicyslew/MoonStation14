# Health analyzer UI corrections

The analyzer keeps its compact fixed summary and portrait above a damage-only
scissor/scroll region. Window width is measured from localized summary labels,
values, damage rows, and title chrome at render time. Height grows to 200 GUI
pixels (or the screen height minus 16) before damage rows scroll. The six reused
medical category icons are the existing `brute`, `burn`, `airloss`, `toxin`,
`genetic`, and `metaphysical` resources. These are implementation notes, not
manual visual acceptance; use the analyzer check in `MANUAL.md`.

## Range policy

Resolved analyzer scan range is 3.5 blocks and UI retention range remains 3.0 blocks. Both are owner-requested 3D Minecraft adaptations of pinned SS14 defaults (scan 2.5, UI 2.0): `Resources/Prototypes/Entities/Objects/Specific/Medical/healthanalyzer.yml` on `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, inheriting `BaseItem`; `HealthAnalyzer` defaults to 2.5 scan and `BaseItem` UI range is 2.0. Scan distance (actor to patient) and UI lifetime (actor to scanner root) are independent. The resolved client scanner probe and server admission consume scan range; shared server UI predicate consumes UI range. Prototype tests verify the resolved 3.5/3.0 policies; lifecycle/GameTest boundary fixtures admit scan at 3.25 and reject at 3.6 while UI admits 2.75 and rejects 3.1. Clinical/treatment range is unchanged.
