# Repository-wide SS14 prototype and composition discipline

For every future feature design or implementation, not just ventilation:

- Before designing fields or writing feature code, inspect the pinned Space Station 14 entity prototype's full parent chain, inherited components and their defaults, and the systems that actually consume them. Reference checkout: `SS14-dev/space-station-14` under the developer's Documents (or an equivalent checkout), pinned to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Verify the checkout revision before relying on it.
- Map that resolved upstream composition to local typed prototypes: represent relevant inheritance and composed components, distinguish upstream behavior from Minecraft adaptations, and never introduce components the local runtime does not support just to mimic upstream YAML.
- Wire live gameplay to consume **resolved** local prototypes, including inherited defaults; a JSON definition alone or hardcoded-only behavior is not a prototype/composition implementation. Record verified parity and deliberate departures with source references and rationale. If upstream is unavailable, label the mapping **unverified** and do not claim parity.
- Test prototype resolution, inheritance/defaults, and the live runtime consumer path for the feature. Preserve existing project priorities, asset/provenance constraints, and prediction/authority boundaries while doing so.

Pure bugfixes that do not design or extend a feature need not repeat the upstream prototype audit; do not use that exception to bypass the audit for new behavior.
