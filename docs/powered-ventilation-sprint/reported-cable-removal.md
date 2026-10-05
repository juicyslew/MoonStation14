# Reported cable-removal issue

Owner report: placing blocks on top of wires sometimes removes **other wires that are under blocks**. Behavior appears inconsistent.

Recorded only, as requested. Not reproduced, diagnosed, or fixed in the current ventilation feedback pass. Do not assume it is ordinary removal of the cable on the block being replaced. Start future investigation with cable host/surface-position validation, block replacement hooks, and chunk-level record pruning; preserve unrelated cable records.
