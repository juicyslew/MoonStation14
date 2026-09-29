# APC overload UI status

The APC menu now exposes the BE-owned `tripLatched` state in an additional standard
`ContainerData` slot. That slot is authoritative and is synchronized independently
to every viewer's open menu. The initiating viewer also receives the latch in the
validated toggle response snapshot.

When latched, the existing menu layout displays **OVERLOAD — TRIPPED** in red below
the breaker control. A manually opened breaker has no overload warning, so OFF and
TRIPPED remain distinct. The breaker control can still reclose a tripped APC; the
server clears the latch and updates all viewers. Optimistic breaker presentation
does not set the overload indication.

Coverage: focused menu-data/response tests and an APC menu GameTest exercise shared
viewer state, overload latching, and manual reclose. Validation results are recorded
with the implementation handoff.

Validation: `./gradlew.bat compileJava compileGameTestJava test --no-daemon` passed;
`git diff --check` passed. No manual game launch was performed.
