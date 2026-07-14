# Recompile - implementation roadmap

**Status:** Phase 0 and Phase 1 shipped to `main` (2026-07-14). Phases are ordered by
**gameplay discovery** - the sequence a player actually lives, so each phase delivers a
coherent playable increment. The locked feature design is the source of truth in the
Trashlands repo (`../trashlands/docs/design_decisions.md` + `feature_matrix.md`); this
file is the engineering build order and maps to those P-codes. Everything ships
config-gated, but "defaults are the design."

**Confirmed toolchain (mirrors `../productive-frogs`):** MC `26.1.2`, NeoForge `26.1.2.76`,
ModDevGradle `2.0.141`, Java 25, Gradle wrapper `9.5.1`, pack format `84`. Mod id
`recompile`, package `com.flatts.recompile`.

**Engine / pack split:** Recompile is the *engine* (systems + the public `recompile:teardown`
schema + tag-driven defaults). Trashlands is the *pack* (curation, quests, tuning, most
cross-mod teardown tables). The mod never *requires* Create or Mekanism; it ships standalone
config fallbacks.

**Organizing principle - discovery order:** build in the order the player encounters things.
The garbage world comes first (you spawn in it), then the early hand loop, then the tools and
sorting that loop demands, and only then teardown-as-knowledge - the mod's distinct axis, which
is the *payoff* of the early loop, not its entry (its on-ramp is the prybar + appliances). A
few systems (dimension lockout, the knowledge system's risky internals) are pulled earlier than
their discovery slot for a concrete reason, called out where they occur.

---

## Phase 0 - Project scaffold + data spine  *(DONE)*

Gradle NeoForge MDK; main `@Mod` class + config; the public `recompile:teardown` recipe type
with its full P0.5 schema (`input`/`station`/`results`/`extras`/`teaches`), shipped up front so
the knowledge axis is never retrofitted; CI (`build` + `gameTest`).

## Phase 1 - The garbage world  *(DONE, design P0)*

The go/no-go slice - spawn, dig, sort, get materials, in a world that reads as a dump.
- P0.1 world preset (rolling thin crust: coarse-dirt cap, variable deepslate, bedrock, void below).
- P0.2 garbage mounds (varied height 3-15, width 4-15).
- P0.3 Block of Garbage (drops itself, shovel-mineable, randomized variants).
- P0.4 hand-sorting (empty-hand pull; crumbles after 4-6 pulls) + the 7 base materials.
- P0.5 a real teardown table proving the schema.
- Plus: the texgen texture pipeline, JEI/Jade dev tooling, GameTest harness.

**Playtest-confirmed.** Gravity on the garbage block (P0.3) is the one deferred slice item.

---

## Phase 2 - The early loop  *(design P1.1 / P1.2 / P1.3 / P1.8)*

What the player reaches for the moment hand-sorting palls: better tools and a faster sort.
- **Trash tools (P1.2):** scrap knife, prybar, junk shovel, rebar (universal handle). No
  pickaxe, on purpose - nothing to mine, and its absence tells the player that.
- **Garbage variants (P1.1):** bags (hand/instant), bales (scrap knife), appliances (prybar).
  One interlocking tool+block matrix. Appliances are introduced here as the future teardown
  input - the on-ramp to Phase 3.
- **Sorting Tarp (P1.3):** the batch upgrade to the sort verb - the hold-to-sort UI later
  machines inherit; screen slot skews *what*, not *how much*.
- **Dimension lockout (P1.8):** cheap config disabling Nether/End portals. Pulled in here
  (ahead of its "try a portal" discovery slot) to plug the vanilla-resource leak early.
- Fold in garbage-block gravity (deferred from Phase 1).

**Exit:** the full pre-knowledge loop - scavenge with real tools, meet every garbage type, sort
at two speeds.

## Phase 3 - Teardown-as-knowledge  *(design P1.4) - the distinct axis*

The payoff and the mod's reason to exist: tear a found item down at the **Recompile Workbench**
to recover materials AND its recipe. Deterministic study points -> schematic item -> learn it
permanently; unlearned tech can't be crafted until studied. Rides vanilla `doLimitedCrafting` +
recipe-book grants; hand-authored JSON unlock tables only.

**Open with a de-risk spike.** The riskiest internals - the JEI/EMI locked-recipe overlay and
FTB Teams team-shared sync - get proven in isolation *first*, before wiring the full loop, so
discovery-order sequencing doesn't defer the technical risk. Automatic recipe introspection
stays maybe-never.

## Phase 4 - Garbage regions  *(design P1.5)*

"I've stripped this area" - venture out. Real biomes distance-banded from spawn; launch trio
household / scrapyard / e-waste; per-region garbage blocks (the drop table travels with the
block). One region = one datapack bundle.

## Phase 5 - Mound regrowth + healed-land immunity  *(design P1.6 / P1.7)*

"Wait, the mounds grew back." Original-bounds memory per mound; deorbit falling-block delivery
(reuses P0.3 gravity); grass/built blocks retire a footprint forever. The quarry-vs-heal tension
is the pack's engine.

---

## Phase 6 - The full loop  *(design P2)*

Discovered as you climb tiers; leans on curation + sibling mods.
- Tier-2 processing: Burn Barrel (custom) + excavated-and-repaired furnace; purity-as-yield,
  junk fuel, no energy (P2.2).
- Reclamation chain: compost + clean water + seed -> grass -> crops -> trees; yields only land (P2.4).
- E-waste recovery chains, two-stage purity-as-yield + battery mini-tree (P2.6).
- Tier-3 logistics seam: "Recompile converts, Create moves"; never *require* Create (P2.3).
- Hazmat gating via Mekanism radiation + suit; Recompile ships biome/blocks/caches only (P2.5).
- Cross-mod teardown tables at scale: tag-driven defaults + landmark hand-authoring + a
  completeness-check build step (P2.8) - **pack content**.
- Quest line in FTB Quests, exile fiction, spoiler-safe hidden post-twist chapters (P2.7) -
  **pack content**; the opening spectacle + scripted deorbit (P2.1).

## Phase 7 - Themed dimensions + polish  *(design P3)*

- Themed Nether (Hard) - solid techno-organic waste; first RF power + osmium originate here.
- Themed End (Medium-Hard) - the found-economy capstone.
- Field Manual (whichever guide-book mod is on 26.x; not a lore vehicle).

**Explicitly parked, out of scope until reopened:** the endgame redesign (P3.9) and the final
chapter/postgame. Do not build against these yet.

---

## Notes carried into implementation

- Discovery order governs sequencing; where technical risk argues for building something earlier
  than its discovery slot (the Phase 3 knowledge spike, the Phase 2 dimension lockout), it's
  called out at that phase rather than reordering the player's journey.
- Minimize authored prose (Jason's hard rule): only two sanctioned writing surfaces - quests
  (via the quest-voice skill) and terse technical guidance. No ambient lore. ASCII punctuation
  only, no em/en-dashes, no emoji.
