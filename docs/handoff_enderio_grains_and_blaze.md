# Handoff: Ender IO content living in the Recompile engine

**Analysed against:** Recompile at v0.14.0+, Ender IO `9.0.5-alpha` (the jar the pack pins), MC 26.1.2 /
NeoForge 26.1.2.76.
**Status:** shipped 2026-08-21 across #279 and #281.
**Move-back tracker:** `Flatts3000/trashlands#52`, alongside `#46` (AE2) and `#47` (Simple Magnets).

## Why this is in the engine at all

Identical to the other two stopgaps: **the pack cannot ship data on 26.1.2.** No datapack loader has a
NeoForge build, KubeJS crashes the client
([kube-mods/kubejs#1178](https://github.com/kube-mods/kubejs/issues/1178)), CraftTweaker has not
ported. So content that belongs to Trashlands ships here instead.

**It cuts against the engine/pack split in `CLAUDE.md`**, which puts curation and cross-mod tables on
the pack side. That split is not being revised; it is suspended for one delivery problem. **The trigger
for moving it back is KubeJS working, not a release number, and nothing will announce it.**

## Unlike the other two, Ender IO needed no sourcing work

Worth stating up front so nobody repeats the audit. **Ender IO is fully playable in this world with no
help**, which is the opposite of AE2. A reachability closure over its 1187 parseable recipes put
**897 of 924** items in reach from a vanilla-only seed. Of the 27 that were not:

- ~7 painted blocks and ~9 Vat fluid items were artefacts of the analysis, not real gaps.
- 5 enderman-head items are reachable: **endermen spawn here**, weight 10 in the demolition yard and 1
  in the compacted depths.
- The soul vial chain is fine; the vial crafts from fused quartz and soularium.

Its entire alloy spine - conductive, energetic, dark steel, end steel, vibrant, soularium, pulsating,
redstone alloy, grains of infinity, capacitors, conduits, Alloy Smelter, SAG Mill - was reachable
before any of this landed. **Ender IO also makes its own silicon** by SAG-milling sand, so it does not
depend on AE2 for `#c:silicon`.

**Two genuine gaps remain and are not addressed here**: `prismarine_shard` and `wind_charge` have no
route in this world, which blocks Cloud Seed and its concentrate. Tracked in #278.

## What ships, and what removing it means

### 1. Grains of Infinity as a find (#279)

One entry in `loot_table/gameplay/mechanical_pulls.json`, weight 20 of 247 - about one pull in twelve.

**Not a gap being closed.** Ender IO already makes grains here through its own `fire_crafting`: a fire
on deepslate (40%) or bedrock (80%, one to three), both restricted to `minecraft:overworld`, and this
world has sixty blocks of deepslate under the dirt slab with bedrock beneath. **That route stays and is
the bulk supply.** This entry exists so the material also arrives out of the waste.

**In the yard rather than the depths** (owner, 2026-08-21). Grains are consumed in bulk - two per basic
capacitor, one per iron gear, both in nearly every machine - so their source sets the pace of the whole
mod. The yard is reachable long before the Nether.

**`expand: true` is load-bearing.** Naming `enderio:grains_of_infinity` directly would kill the whole
table at parse without Ender IO, taking the Motor and Magnet Scrap with it. A `TagKey` does not resolve
at parse time. And `expand: true` makes a tag contribute one entry PER MEMBER, so an absent tag
contributes none; `expand: false` would leave one entry holding the full weight that wins one roll in
twelve and hands back nothing.

**Removal:** delete the entry. Nothing else.

### 2. The blaze rod grinding recipe, disabled (#281)

`data/enderio/recipe/sag_milling/blaze_powder.json`, a replacement that never loads.

Ender IO grinds one blaze rod into **four** blaze powder. This mod's chain runs the other way - four
powder press into a Blaze Briquette and the Sintering Kiln fires it into one rod - so that recipe alone
makes the round trip break even, which is exactly what the Briquette exists to prevent. Worse in
practice: `data_maps/item/grinding_ball.json` runs up to an **OutputMultiplier of 1.75** on the vibrant
alloy ball, so a rod returns up to **seven** powder against the four it cost. Blaze rods gate brewing
here.

**Disabled rather than rebalanced to two** (owner). Cutting the yield restores the loss but leaves a
number to re-check whenever Ender IO retunes its balls.

**There is no remove-recipe primitive.** A file at another mod's recipe id replaces it wholesale (only
the top file at a path is read), and a `neoforge:never` condition means the replacement itself never
loads - net, the id is gone. The body is well-formed but never decoded, which is why it is inert
without Ender IO despite declaring an `enderio:sag_milling` type.

**Removal:** delete the file **and** its `[[dependencies]]` block in `neoforge.mods.toml`.

### 3. The glass bottle exemption (#281)

`enderio:tank_empty/glass_bottle` in `EXEMPT_RECIPES` in `FoundNotCraftedTests`.

Draining an experience bottle in an Ender IO tank hands back a `minecraft:glass_bottle`, which is in
`#recompile:found_only`. **Owner ruled it acceptable** (2026-08-21): it is a container conversion, not
manufacture. Exempted **by recipe id rather than by serializer**, because `enderio:tank` also covers 19
concrete-powder recipes and the nutritious stick, which are genuine manufacture.

The caveat is recorded rather than glossed: an experience bottle can be **bought**, so a player with
emeralds has a narrow route that does not involve finding one.

**Removal:** delete the entry from `EXEMPT_RECIPES`.

## Rules this reverses, recorded rather than decided in a PR body

The other two handoffs say to build a stopgap as one file, deletable wholesale, with nothing depending
on it and no GameTest pinning it. This one breaks three of those, each deliberately:

- **Not one file.** The grains entry lives inside an engine loot table that is the only source of the
  Motor and Magnet Scrap; the exemption lives inside an engine test. Both removals are **edits, not
  file deletions.**
- **Things depend on it.** The blaze disable needs the `[[dependencies]]` block, and
  `every_cross_mod_override_is_ordered_after_its_mod` now derives its list from the namespaces this mod
  ships files under - so removing the enderio data directory is what removes it from that guard.
- **GameTests pin it**: `grains_of_infinity_is_inert_without_enderio` and
  `the_blaze_grinding_override_can_never_load`.

## Verifying with the mod actually installed

Drop `enderio-9.0.5-alpha.jar` into `run/mods` and run `runGameTestServer`. **CI cannot do this**, so
this mod's own invariant guards are only ever exercised against Ender IO by someone doing it
deliberately - which is how both defects in #280 were found in the first place. Worth repeating for the
pack's other majors.

**One caveat: about 58 unrelated tests fail with Ender IO present**, for an environmental reason rather
than a real one. Ender IO registers a payload the headless harness refuses (`Payload
enderio:powered_spawner_soul may not be sent to the client`), which breaks every test that uses a mock
player. Filter those out before reading a red run as a regression; after #281 the non-payload failure
count is **zero**.
