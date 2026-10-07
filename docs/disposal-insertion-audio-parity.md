# Disposal insertion audio

Pinned SS14 (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`) resolves
`DisposalUnit -> DisposalUnitBase -> BaseMachinePowered -> BaseMachine ->
BaseStructure`. `DisposalUnitBase` supplies `DisposalUnit`'s
`InsertSound` default `/Audio/Effects/trashbag1.ogg`; the shared unit system
plays it only after an accepted insertion. Local `disposal_unit_base` composes
the equivalent `unit.insertionSound`, inherited by `disposal_unit`; the live
unit reads this resolved field after committed item/body admissions. Minecraft
uses the authoritative server sound broadcast instead of SS14 prediction.
Entry remains independent of power. World-item insertion is only sounded at
the successful ownership commit, never on polling/rejection/rollback.

Asset file-specific author/source/license are unresolved; see the adjacent
asset notice and [unknown provenance list](audio-unknown-provenance.md).
