# r40 fixture correction

r39 passed 16 focused JVM tests but failed the full native suite (915 selected of
915) at the registered carrier's upward-jump assertion. Readiness, client startup
and packaging did not run. Bound native log SHA-256:
`efc28038b07eb0cf212a44abb24cb5a440351775411a517d86297fe27da05d7a`.

`LifecycleCharacterSessionControl.playerTick` only integrates a world step when
fresh input is consumed. Teleporting a synthetic carrier onto the fixture floor
does not establish its grounded motor state. The fixture now sends neutral
production input with a bounded grounding wait before starting escape. It uses
the carrier's epoch, not the separately captured mouse epoch, and distinct control
sequences below the later jump/movement sequence range.

No `onGround(true)` override or production jump rule change was added. The upward
jump, mixed-frame displacement above 0.3, canonical frame stability, same operation
and original-mouse completion assertions remain. Compilation and source review
passed; fresh native/runtime validation is still required. Scope and known
boundaries remain in `ESCAPE-AND-PRIMARY-OUTLINE.md`.
