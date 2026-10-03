# Grenade audio

The grenade audio registration uses SS14's beep1 and explosion1 as fixed
representative sounds and also imports the original `explosionfar.ogg`. This does
not claim the complete SS14 randomized explosion collection. Original OGG files and adjacent per-file notices are in
`src/main/resources/assets/moonstation14/sounds/grenade/`; those notices record
the pinned source revision, hashes, license, attribution, source URL, and edits.

`beep1.ogg` is CC0-1.0. `explosion1.ogg` and `explosionfar.ogg` are CC-BY-SA-3.0;
retain their respective credit and license notices on redistribution, and distribute
adaptations under CC-BY-SA-3.0 or a compatible license permitted by its terms. The
`explosionfar.ogg` is unchanged: 12,252 bytes, SHA-256
`0DFDDACEDE1CFD5B2C3FCD0F347268480107DDE2C14CBE62C45623C455196D7F`, original
STEREO intended for non-positional client-local playback. Original `explosion1.ogg`
is mono and positional. After APPLIED/PARTIAL blast, the server selects exactly one
sound per eligible same-dimension real player using server `getCamera`: near <=32
blocks gets `explosion1.ogg` at gain 0.5; distant (32,96) gets a custom bounded
server-authored S2C cue at the client camera, gain <=0.25 fading toward 0 at 96.
There is no unbounded global delivery. SS14 has a similar separate far-explosion
collection, but this distance/gain policy is not parity. Beep1 tick 0/+40 on the
60-tick fuse and existing audio are untouched. Packet receipt and client perception
still need owner-connected-client retest, particularly far carrier and moving camera.
No audio is synthesized or converted in this import.
