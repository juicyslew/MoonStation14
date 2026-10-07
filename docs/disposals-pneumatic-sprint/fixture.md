# Disposal fixture

Run as an operator (permission level 2), with server `enablePowerSimulation` and
`enableAtmospherics` enabled. Install the same jar on client and server, then
build in a fresh clear, loaded site with `/ms14 disposals_fixture build ~ ~ ~`
(console must supply coordinates). Optional `/ms14 disposals_fixture status`
reports readiness. The command's coordinates are the lowest floor reference
(`minY`); standing entry is one block above. Room bounds are
`[x-6,y,z-8]..[x+5,y+6,z+3]`. The sparse fixture reference bounds are
`[x-6,y,z-8]..[x+24,y+6,z+20]`; that outer cuboid is not wholly built or
required clear/loaded. Builder does not overwrite blocks or force chunks.

Build automatically seeds the newly built room with finite breathable air
before reporting success; manual `initialize` remains available to reset that
finite mixture for an operator. If seeding cannot complete, build reports a
partial fixture rather than success. Status should show a valid route, powered
unit, finite room and READY phase.

Remembered sites reset when the server stops. After restarting, supply the
floor-reference coordinates explicitly if you want to reuse an existing fixture.

Manual ride: enter the powered unit and engage with E controls; allow about two
seconds for flush and about 26 seconds for travel. From the rear third-person
view, press F5 and orbit the mouse to inspect the camera through terrain. Transit
camera is locked; return inside the room should restore the prior perspective.
This connected-client visual behavior still requires manual verification; the
native focused GameTests do not establish mouse/camera presentation.

Automated focused acceptance and known full-suite failures are recorded in
`validation.md`.
The room bounds are steel-filled except for a 6×4×4 (96-cell) play area.
Initialization samples every atmosphere-passable cell in the bounded interior,
including partial-collision unit, pipe and device volumes connected to the play
area, so the finite endpoint can validate the complete gas component within its
1,024-probe trusted-setup limit. Floor, roof and adjacent walls remain steel. A real APC-powered lamp
illuminates the room for inspecting fixture models.
The fixture supplies real power, a finite 500,000 J APC charge and
finite room air—not a powered-state override. Unit flush captures room gas at
the real flush boundary; it does not background-charge the unit reservoir.

A failed build may leave a partial fixture. Inspect the reported bounds before
repairing/rebuilding; do not break the enclosure to enter—use `enter`.
