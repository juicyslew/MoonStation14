# M3a — face-mounted cable adjacency audit

## Scope and representation

`CableFaceNode` is only `(full-cube host BlockPos, Direction face, CableTier)`. The topology code is pure geometry: it does not inspect blocks, world/chunk state, registrations, devices, or rendering. A node face points outward from its host. Different faces of the same host are never joined: adjacency does not infer a route through a solid cube.

## Cable relation

An ordinary edge requires equal tiers. Coplanar cable faces connect when their hosts differ by exactly one block tangent to that face. For a wall facing EAST at `(0,0,0)`, same-face neighbors at `(0,1,0)` and `(0,0,1)` are consecutive segments; `(1,0,0)` is not, since it steps through the host face normal. Likewise an EAST-facing wall cable at `(3,10,4)` and `(3,11,4)` are explicitly adjacent vertical segments. A node at `(3,12,4)` is not implicitly reachable from the lower segment: each floor rise needs its own segment.

Perpendicular faces make a genuine edge turn only where host cubes meet along an edge. In coordinates, for first normal `n1` and second normal `n2`, the second host must be exactly `host1 + n1 - n2`. Example: an EAST node on `(0,0,0)` turns to a DOWN node on `(1,1,0)`. This is the shared external edge of the two host cubes, not a diagonal shortcut across a gap. All other offsets, parallel-opposite faces, and same-host face pairs are rejected.

The relation is symmetric, deterministic, and local. A node can have at most four coplanar neighbors plus four perpendicular edge turns (`MAX_CABLE_NEIGHBORS = 8`). `cableNeighbors` removes duplicate candidates and sorts by host coordinates, face ordinal, then tier ordinal.

## Device port contract (geometry only)

`DevicePort` has an explicit `CableTier` and outward face. A candidate cable is geometrically adjacent only when it is on the immediately neighboring host, its face opposes the port face, and its tier matches. For example, a NORTH port at `(5,7,9)` accepts an MV node on the SOUTH face of `(5,7,8)`, not an HV node or a further cable. This boolean relation creates no visual plug or device implementation.

## Proof fixtures

`PowerTopologyTest` covers all six orientations and tangent/non-tangent steps, all ordered perpendicular face turns and their reverses, off-edge diagonal rejection, same-host volume shortcuts, genuine same-face diagonal offsets (including a face-normal-plus-tangent offset), tier mismatch, explicit multi-floor wall segments, deterministic bounded neighbor sets, and typed exact device-port contacts. Vertical and in-plane tangent EAST wall segments are valid; the diagonal fixtures specifically ensure those valid continuations are not mistaken for shortcuts. There is no world access or manual/game-test dependency.
