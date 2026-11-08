# Short owned-movement trace

Diagnostic `run_e4ed6101f4a242fe887194e6` passed selected checks for the
pose-scheduling correction and pit fixture; acknowledged, cleanup CONFIRMED.
Filtered native selection: 123/1,074, all 123 passed in 18.61 seconds. Selected
JUnit and readiness checks passed. This short retest is now available; actual
owner feedback subsequently confirmed good movement with 100 crowded Kobolds.
Air-control changes are being investigated separately; wait for their new check
result before testing a newer air-control candidate. Feature readiness and owner
acceptance remain outstanding.
This is a diagnostic, not the sustained population or observer acceptance test.

The owner uses a new world on every launch. The repeatable setup below replaces
manual pit construction and spawning; do not ask them to reuse an existing world.
The latest reported slowdown is global after spawning, not location-dependent.

1. Restart with the validated fixture candidate and join the new world normally.
   As an operator, run `/ms14 kobold_fixture setup` near clear ground. This builds
   a bounded enclosure ahead of the owned character and spawns 100 normal Kobolds
   in an 8×8 footprint. If safe placement is refused, move to clearer ground;
   the command does not clear obstructions or teleport you.
2. Run `/ms14movementtrace start 30`. Reproduce the choppy movement anywhere in
   the world after setup. Move/jump normally; no near/far, look-direction, or
   mandatory idle phase is required.
3. The trace stops automatically. `/ms14movementtrace stop` ends it early. Note
   whether the symptom occurred and roughly when. Chat reports the saved file path.

Optional fixture commands: `setup 100 8` makes count/width explicit (count 1–200,
width 6–16); `/ms14 kobold_fixture status` reports actual population state;
`/ms14 kobold_fixture clear` removes only fixture-owned entities and unchanged
fixture blocks. Damage, cramming, AI and gamerules are not disabled. Missing or
unloaded entities are not automatically labeled dead; incomplete cleanup is
reported and retained for retry.

The trace does not move the character, spawn animals, change game rules, or record
video. It records bounded timing/position samples and exports JSON asynchronously.
Server-step samples are available for the integrated-server lifecycle character
path; a remote server's timing cannot be inferred from client samples alone.
Camera samples represent the first successful owned-camera setup per observed
render-frame marker, not GPU completion or every camera pass. Frame intervals
are marker-to-marker; camera-setup offsets are recorded separately.
Do not treat handler-execution timestamps as packet wire-arrival times, or append
cost as total diagnostic overhead. Raw artifacts stay under the game's ignored
build/report directory, not in docs.
