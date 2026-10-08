# Health analyzer reading limitations

The reader reports species from resolved `HumanoidProfile` composition. Its
scanner-relevant field was added after auditing the pinned Human/Pig parent
chains; Human provides `Human`. Pig lacks this upstream component and retains
the analyzer's unknown-species fallback. Entity classes and inventory species IDs
are not substitutes for this field. See `NEWPATIENT-COMPOSITION.md`.

Analyzer physiology is read-only: absent typed damage is not synthesized from
vanilla health, and absent temperature/blood attachments remain unknown. Blood
availability uses the minimum configured reference-reagent ratio, capped by the
resolved bloodstream policy's maximum volume modifier.
