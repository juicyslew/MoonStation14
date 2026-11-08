# Core mouse and ghost roles

Status: core implementation built and ready for connected-player testing; sprint not accepted. Owner selected **core mouse + roles first**. See `VALIDATION.md` for final automated results and remaining acceptance checks.

## Ordered work

1. Add a typed, resolved mouse composition and registered mob. Consume existing physiology, movement and identity systems; import pinned SS14 mouse sprites and squeak with attribution. Implement supported mouse speech. Test inherited defaults and live consumers.
2. Extend existing lifecycle ownership/control to prototype-eligible ordinary mobs. Add server-owned available-role discovery and ghost teleport/claim requests for villagers, pigs and mice. Preserve exclusive ownership and Begin/Ready/Commit authority; old body claims are forfeited only on successful takeover. Add ordinary-player testing suicide through the controlled body's medical death path.
3. Add a ghost-only role menu with separate **Teleport** and **Request role** actions. Reuse existing UI/network services. Teleport does not claim a body; request is only an intent and requires authoritative server success.
4. Independently review composition, reuse, authority, death/repeated takeover and contention. Run unit checks and focused native fixtures, then the full native suite for the broad ownership changes. Provide a concise connected-player checklist; do not claim connected acceptance without evidence.

Work 1 and the ownership portion of work 2 may proceed independently. Work 3 follows an agreed backend contract. No commits, pushes, automatic enabling of default-off lifecycle gates, lobby redesign or migration framework.

## Reuse and boundaries

- Character catalog, host mapping and identity: `ms14/character/ModCharacters`, `CharacterIdentitySystem`, existing Pig/Human compositions and typed physiology consumers.
- Movement: resolved movement components, `GroundedHarnessWorldStep`, `CharacterMovementPolicy`; preserve approved 1.25× grounded conversion. Raw mouse walk/sprint is 2.5/5, effective 3.125/6.25. Ghost flight remains unchanged.
- Ownership: `BodyControlRegistry`, `PlayerLifecycleRegistry`, lifecycle character/ghost sessions, profile CAS and existing hand-independent `BodyActionAuthority`. Do not create a parallel owner registry or call experimental possession for production roles.
- Target composition grants role eligibility; being any living Minecraft Mob is insufficient. Villager/Pig eligibility is an explicit owner-requested adaptation. Mouse has no human hands or humanoid profile.
- Available roles are loaded, Alive, unclaimed eligible bodies. Claims are exclusive, server validated and revalidated at commit. Preparation failure preserves the current ghost and role availability; success invalidates old control epochs and the old body's active/revival claim.
- User requests ghost **teleport**, rather than SS14's follow action. Teleport preserves ghost identity/control and must not transfer ownership. Older own-corpse-only planning does not restrict this newly authorized feature.
- Suicide targets the exact controlled body, never the spectator carrier. Testing command may deliberately simplify SS14's item/environment suicide dispatch; document that departure and use real typed damage/death transitions.
- Successful takeover persists `ACTIVE` with the target UUID, authoritative dimension/location and target-derived appearance/identity, keeping account/profile/Mind identity and advancing generation/revision. Clear the offline timestamp. Non-human metadata records the resolved `prototype` rather than stale human appearance/body shape. A trusted runtime supplies target metadata; the core validates it against the reserved body. Failed preparation/CAS preserves the current ghost and old durable claim. No historical-role storage is required.

## Verified upstream baseline

Reference checkout: `C:/Users/William/Documents/SS14-dev/space-station-14`, verified revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

`MobMouse` in `Resources/Prototypes/Entities/Mobs/NPCs/animals.yml:1720–1904` composes `SimpleMobBase` and `BaseMobAnimal`. Audit inherited parents in `NPCs/simplemob.yml`, `Mobs/base.yml` and `Body/Animals/animal.yml`; consuming systems, not YAML names alone, establish behavior. Thresholds are Alive 0 / Critical 10 / Dead 20; radius 0.2; reference bloodstream contains 50 Blood. Hunger thresholds 35/25/15/10/0, decay 0.5; thirst starts 25, thresholds 35/25/15/10/0, decay 0.04. Respirator Asphyxiation damage/recovery 0.25/-0.25; barotrauma Blunt 0.1. Preserve inherited atmosphere/temperature and supported animal organs/defaults.

Mouse RSI is `Mobs/Animals/mouse.rsi`: idle `mouse-0`, moving `mouse-moving-0` (three frames/direction at 0.2 seconds), critical `dead-0`, dead `splat-0`, 32×32/four directions. License CC-BY-SA-3.0, upstream tgstation source and Vermidia modification credited in meta.json. Sound: `Audio/Animals/mouse_squeak.ogg`; verify provenance or record in `docs/audio-unknown-provenance.md`. Mouse accent replaces speech with seven upstream squeaks; inspect accent and speech consumers before implementation.

Ghost roles are first-come-first-served for ordinary mouse; upstream unregisters critical/dead or minded bodies. Source `Content.Server/Ghost/Roles/GhostRoleSystem.cs`; role flags allow movement/speech and sentience. Upstream takeover prevents return to original body. `/suicide` source `Content.Server/Chat/SuicideSystem.cs` ghosts first and normally applies lethal Bloodloss, with item/environment overrides.

## Explicit follow-up gaps

Owner deferred mouse AI, living-mouse pickup/headwear, inventory escape and critical-state actions. Faxing, butchering, edible-mouse/food crafting integrations and unsupported systems are not implemented merely by adding inert components. No inferred vent crawling, cable damage or slip immunity. Record any further concrete supported-system parity gaps rather than claiming all mouse functionality.

## Acceptance evidence

- Resolution/default tests plus live mouse consumers, renderer registration and retained sprite/audio attribution.
- Ghost-only menu/list/teleport/claim authority; teleport retains ghost, claim controls requested live body; two competing requests cannot both succeed.
- Failure/cancel/disconnect cleanup and old-epoch rejection using shared lifecycle ownership. Claimed pig/mouse work without hands. Role death and suicide return through the existing ghost lifecycle; repeat role selection works.
- Unit and native results with selected/total/pass/failure counts, no owner configuration overwritten. Keep useful failure artifacts; remove disposable passing fixtures.
- Connected-client observations remain separately pending unless actually exercised; existing FakePlayer rejection must not be bypassed to manufacture positive authority evidence.
