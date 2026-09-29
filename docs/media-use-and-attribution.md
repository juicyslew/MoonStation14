# Media reuse and attribution policy

Prefer fitting Space Station 14 (SS14) sounds when audio is wanted; do not treat
uncertain file-specific provenance as a blanket ban on reuse. Reuse is a separate
decision from referencing upstream code or UI. Copy original source bytes only
when they are available; do not synthesize, reconstruct, or silently substitute a
sound for an unavailable source. Record the exact source repository, immutable
commit, upstream path, destination path, byte count, SHA-256, and whether the
local copy is unedited. Document any intentional editing or conversion separately
before distributing the result.

Preserve all known per-file metadata: exact license, copyright/author text,
source URL, upstream edits, and attribution chain. Do not infer an asset's license
from this project's code license, SS14's code license, or a nearby asset's
metadata. SS14's [README at the pinned revision](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/README.md)
says code is MIT, most assets are CC-BY-SA-3.0 unless stated otherwise, and
individual asset licenses/copyright are in metadata. Treat that as general
context, **not** a file-specific assignment of MIT or CC-BY-SA-3.0. A project's
noncommercial intent does not waive attribution, share-alike, noncommercial, or
other license conditions.

For **each newly imported audio file**, ship an explicit adjacent
`<filename>.license.txt` or `<filename>.attribution.txt` beside the audio file.
Record the upstream repository/path and pinned commit, source and destination
byte counts and SHA-256 hashes (or a verified equality statement with both
counts and hashes), whether the local bytes were edited, and all available
per-file license, author/copyright, URL, and modification information. State
the exact license where verified; otherwise state **`UNRESOLVED — do not assume MIT or CC`** and explain which file-specific evidence is missing. Include the
upstream README's general guidance only as context, never as the missing
file's assigned license. Record every imported audio file with unresolved
provenance in the [running list](audio-unknown-provenance.md) in the same change;
keep the list synchronized with the adjacent notice and the actual file state.

Unknown provenance remains visible until resolved: seek a file-specific source
notice or contact upstream maintainers/rights holders for written clarification,
record the response and update both the adjacent notice and running list. If a
sound cannot be cleared for the intended distribution, replace it with a
verified/appropriately licensed sound or remove it and its references, then
record that disposition in the list. Do not silently assign a license or erase
the record. Before distributing media under a known license, comply with its
terms and review compatibility with the intended distribution.

Existing verified credits are not overwritten by this unknown-provenance
process: see [third-party emote audio](third-party-emote-audio.md) and the
[APC image record](apc-machine-ui-sprint/assets-and-attribution.md).
