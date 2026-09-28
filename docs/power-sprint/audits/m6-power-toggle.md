# M6 server power simulation toggle

## Behavior

The common `enablePowerSimulation` option is sampled at server-about-to-start and defaults to `true`, preserving existing worlds. With the option false, cable record storage, placement/removal, visuals, creative visibility, floor/tile/pry behavior, and device/block-entity registration remain available. The server graph service does not create/index graph state, process chunk refreshes, or tick graph work; the device runtime does not index devices, solve networks, or update APC energy. Saved cable records and APC energy are not cleared or rewritten by disabling simulation.

Lamp blockstates saved as lit fail dark once as their chunks load while simulation is disabled. This uses only the block entities already present in that loaded chunk and does not build the runtime device index or run a recurring sweep. Enabling simulation again on the next server startup allows normal chunk registration and graph reconstruction.

## Validation boundary

Focused gate unit tests establish default-enabled and lifecycle reset behavior. The latest isolated server-world GameTest run completed with no power test failures, but it did not exercise `enablePowerSimulation=false`. Disabled-mode chunk-load lamp projection and restart reconstruction have not been exercised in a manual server or GameTest; the server run is not evidence for those disabled-mode behaviors.
