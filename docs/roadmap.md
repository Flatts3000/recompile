# Recompile - implementation roadmap

**Status:** Phase 0 in progress (started 2026-07-14). This is the engineering sequencing view; the locked feature design is the source of truth in the Trashlands repo (`../trashlands/docs/design_decisions.md` + `feature_matrix.md`). Every feature ships config-gated, but "defaults are the design."

**Confirmed toolchain (mirrors `../productive-frogs`):** MC `26.1.2`, NeoForge `26.1.2.76`, ModDevGradle `net.neoforged.moddev 2.0.141`, Java 25, Gradle wrapper `9.5.1`, pack format `84`. Mod id `recompile`, package `com.flatts.recompile`.

**Engine / pack split:** Recompile is the *engine* (systems + the public `recompile:teardown` schema + tag-driven defaults). Trashlands is the *pack* (curation, quests, tuning, most cross-mod teardown tables). The mod never *requires* Create or Mekanism; it ships standalone config fallbacks.

---

## Phase 0 - Project scaffold + data spine

**Goal:** an empty mod that builds green, loads in a dev client, and already parses the data format the whole design rides on.

- Gradle MDK cloned from productive-frogs (moddev 2.0.141, Java 25, the 4 run types + `gameTestServer`, `processResources` token expansion), `neoforge.mods.toml` + `pack.mcmeta`.
- Main `@Mod` class + `MOD_ID`, `RCConfig` skeleton.
- **The data spine, up front on purpose:** the custom `recompile:teardown` recipe type with its full codec (P0.5 schema: `input` / `station` / `results` / `extras` / `teaches` incl. `scraps_required`). `teaches` exists from day one so the knowledge axis (P1.4) is never retrofitted into a live schema.
- One example teardown table (vanilla items) that loads end to end.
- CI (`ci.yml` with the required `build` job + `gameTest` job).

**Exit:** `./gradlew build` green; empty mod loads; the example `recompile:teardown` JSON parses.

Item / block / creative-tab registry holders are deferred to Phase 1, where they get real entries (no empty holder classes now).

---

## Phase 1 - The feasibility slice (design P0) - throwaway-able

**Goal:** prove the world *feels* like an endless dump and the data pipeline works end to end. Go/no-go gate for the whole project.

- **P0.1** Custom world preset - coarse dirt over deepslate over one bedrock layer, solid all the way down, gently rolling terrain, scattered leachate pools. (Datapack.)
- **P0.2** Garbage surface layer - variable mounds on the coarse-dirt plain, no continuous burial. **The brand and the one Medium-risk item.**
- **P0.3** One Block of Garbage (household), drops itself, gravity on (config-gated). Randomized texture variants.
- **P0.4** Pick-through hand-sorting - right-click a placed block, one loot-table pull per click, crumbles after 4-6 pulls. Introduces the base 7-material vocabulary (scrap metal, plastic scrap, glass shards, organic muck, fiber scrap, e-scrap, junk).
- **P0.5** One hand-authored teardown table exercised end to end (the Phase-0 schema against real content).

**Exit test:** spawn, dig garbage, sort it, get sorted materials. *If the world doesn't feel right, stop and rethink before any Phase 2 work.*

---

## Phase 2 - Core identity (design P1)

Sequenced by dependency. Mostly Recompile-the-mod code; region/pull-table JSON is content.

1. **Block + tool matrix (P1.1 / P1.2)** - bags / bales / appliances; scrap knife / prybar / junk shovel / rebar handle. **No pickaxe, on purpose.** Appliances + prybar are the on-ramp to knowledge, so this lands before the workbench.
2. **Sorting Tarp (P1.3)** - first station, the hold-to-sort UI later machines inherit; screen slot skews *what*, not *how much*.
3. **Recompile Workbench + teardown-as-knowledge (P1.4)** - **the distinct axis and the riskiest build.** Bench block/BE/menu, hold-to-disassemble, tool rack, deterministic study points -> schematic item -> permanent learn. Rides vanilla `doLimitedCrafting` + recipe-book grants. Named risks: JEI/EMI locked-recipe overlay, FTB Teams sync. Hand-authored JSON unlock tables only; automatic recipe introspection stays maybe-never. **De-risk with a standalone spike before wiring.**
4. **Garbage regions (P1.5)** - real biomes distance-banded from spawn; launch trio household / scrapyard / e-waste; per-region garbage blocks (drop table travels with the block). One region = one datapack bundle.
5. **Mound regrowth + healed-land immunity (P1.6 / P1.7)** - original-bounds memory per mound, deorbit falling-block delivery (reuses P0.3 gravity), grass/built blocks retire a footprint forever. The quarry-vs-heal tension is the pack's engine.
6. **Dimension lockout (P1.8)** - config flags disabling Nether/End portals. Can land anytime.

**Exit:** full early-to-mid loop is playable - scavenge, sort at three speeds, tear down to recover recipes, watch quarries regrow, heal land to retire them.

---

## Phase 3 - The full loop (design P2)

Heavier reliance on curation + sibling mods (Create for logistics, Mekanism for chemistry/radiation/energy).

- Opening spectacle + scripted deorbit (P2.1); trash wind optional flavor.
- Tier-2 processing: Burn Barrel (custom) + excavated-and-repaired furnace; purity-as-yield, junk fuel, no energy (P2.2).
- Tier-3 logistics seam: "Recompile converts, Create moves" - powered sorter + powered disassembler; never *require* Create (P2.3).
- Reclamation chain: compost + clean water + seed -> grass -> crops -> trees; yields only land (P2.4).
- Hazmat gating via Mekanism radiation + suit; Recompile ships biome/blocks/caches only (P2.5).
- E-waste recovery chains, two-stage purity-as-yield + battery mini-tree (P2.6).
- Quest line in FTB Quests, exile fiction, spoiler-safe hidden post-twist chapters (P2.7) - **pack content**.
- Cross-mod teardown tables at scale: tag-driven defaults + landmark hand-authoring + a **completeness-check build step** flagging any craftable with no table and no tag rule (P2.8).

---

## Phase 4 - Polish + themed dimensions (design P3)

- Field Manual (use whichever guide-book mod is on 26.x; not a lore vehicle).
- Themed Nether (Hard) - solid techno-organic waste; first RF power + osmium originate here.
- Themed End (Medium-Hard) - the found-economy capstone.

**Explicitly parked, out of scope until reopened:** the endgame redesign (P3.9) and the final chapter/postgame. Do not build against these yet.

---

## Notes carried into implementation

- Phase 1 is the real risk gate, and it's mostly datapack / Medium worldgen. Everything Hard (knowledge overlay, themed dimensions) lives in Phases 2 and 4.
- The workbench / knowledge system (2.3) is the single riskiest engineering item; spike it in isolation first.
- Minimize authored prose (Jason's hard rule): only two sanctioned writing surfaces - quests (via the quest-voice skill) and terse technical guidance. No ambient lore. ASCII punctuation only, no em/en-dashes, no emoji.
