# Handoff: AE2 and Ender IO teardown routes

**From:** issue #275.
**Analysed against:** AE2 `26.1.10-beta` and Ender IO `9.0.5-alpha`, the jars the pack pins.
**Status:** shipped 2026-08-21.
**Move-back tracker:** `Flatts3000/trashlands#52` (Ender IO) and `#46` (AE2) - this ships with them.

## Why it is in the engine

Same as every other cross-mod stopgap: the pack cannot ship data on 26.1.2. `CLAUDE.md` puts "most
cross-mod teardown tables" on the pack side and `docs/pack_extension.md` calls the teardown schema
public API a pack extends without a mod release, so this is **definitionally pack work** living here
until KubeJS is fixed.

## What ships

Eight files in `data/recompile/recipe/`, each guarded with `neoforge:mod_loaded`:

| file | input | tool |
|---|---|---|
| `teardown_ae2_glass_cable` | `#ae2:glass_cable` | scrap knife |
| `teardown_ae2_covered_cable` | `#ae2:covered_cable` | scrap knife |
| `teardown_ae2_smart_cable` | `#ae2:smart_cable` | scrap knife |
| `teardown_ae2_covered_dense_cable` | `#ae2:covered_dense_cable` | scrap knife |
| `teardown_ae2_smart_dense_cable` | `#ae2:smart_dense_cable` | scrap knife |
| `teardown_enderio_conduit` | `enderio:conduit` | scrap knife |
| `teardown_enderio_basic_capacitor` | `enderio:basic_capacitor` | prybar |
| `teardown_enderio_iron_gear` | `enderio:iron_gear` | prybar |

**Removal is deleting the eight files.** Nothing else changed for them - no tag edits, no engine loot
table, no `[[dependencies]]` block, because these are new ids rather than overrides.

## Three decisions worth keeping

**Tags, not items.** AE2 ships 90 cable items - five types in seventeen colours - and enumerating them
is the sixteen-beds mistake. Its own tags group them, so five entries cover all ninety and a new colour
is carried for free. A tag input also cannot take the file down when the mod is absent, because a
`TagKey` does not resolve at parse time.

**Assemblies only, not ingots.** An alloy ingot is a MIXTURE - you remelt it, you do not prise it apart
with a knife - so conductive, energetic, dark steel and the rest are deliberately absent. What is here
is what a player manufactures, supersedes, and ends up with a chest of. If ingots are ever wanted, the
Separator is the machine whose verb fits, not the Workbench.

**Every entry loses, and the arithmetic is in the file.** Read from each mod's own recipes rather than
estimated: a glass cable costs 0.50 fluix and returns 0.25; a dense cable costs 2 fluix and returns
0.6; a conduit costs 0.375 of a conductive alloy ingot and returns one at 10%; a capacitor costs two
Grains of Infinity and returns 0.5. Recovering is worth doing with a chest of superseded parts and is
never worth crafting parts to feed.

## Two invariants this tripped, both found only with the mods installed

Neither is visible to CI, and both were caught by guards this mod already owned.

**Gated materials.** The first draft returned `minecraft:redstone` from the smart cables and a
`minecraft:gold_nugget` from the capacitor. `GemTierTests.no_teardown_recipe_yields_a_gated_material`
rejected it: everything past the iron gate - diamond, emerald, redstone, gold, amethyst - has the
Separator as its sanctioned route and teardown is not it. Those returns are this mod's own scrap now,
which only widens the margin.

**Tag inputs did not reach any viewer.** `TeardownData` dropped them with a comment saying they would
be surfaced "later if a real recipe needs it" - this is that recipe. Left unhandled, all five cable
teardowns parsed and ran in-world while JEI denied they existed, which is exactly the Broken
Hydroponics Bay failure that class's own comment records. `TeardownData.parseAll` now expands a tag
into one row per member.

That fix also corrected the test guarding it: `every_bundled_teardown_reaches_the_viewers` asserted
rows EQUALS files, which assumed one row per file and would have failed on correct data. It now asks
per file whether anything surfaced, which is stronger and names the offender.

## Verifying with the mods installed

Drop `appliedenergistics2`, `guideme` and `enderio` into `run/mods` and run `runGameTestServer`.
**CI has none of them**, so these guards only ever meet the pack's mods when someone does this
deliberately - which is how both invariants above were caught.

**About 58 unrelated tests fail with Ender IO present**, for an environmental reason: it registers a
payload the headless harness refuses (`Payload enderio:powered_spawner_soul may not be sent to the
client`), breaking every test that uses a mock player. Filter those before reading a red run as a
regression. With this change the non-payload failure count is **zero**.

## Deliberately not done

- **No pull-stream entries.** The dump is a recognisable modern household dump; a fluix processor fails
  the *would a person throw this away* test in the other direction. These are things the player
  manufactures and supersedes, so the route is the Workbench.
- **No `teaches` entries.** Teardown can grant Idea Fragments, and none of these do: the blueprint
  system covers this mod's own gated recipes, and teaching an AE2 recipe would mean this mod having an
  opinion about AE2's progression.
- **Not in `pack_extension.md`**, which documents what a pack may rely on. This is the opposite:
  something a pack should take back.
