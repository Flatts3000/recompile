# Cross-mod stopgaps

What this mod ships on behalf of other mods (AE2, Ender IO, formerly Simple Magnets) because the Trashlands pack could not, what each piece is waiting on to leave, and the silent traps in shipping data at another mod's ids.
Relocated from CLAUDE.md on 2026-09-10; CLAUDE.md keeps a one-line index entry pointing here.

- [Why these exist, and the exit condition](#why-these-exist-and-the-exit-condition)
- [Simple Magnets went home first, and it is the template](#simple-magnets-went-home-first-and-it-is-the-template)
- [What is left, and what each waits on](#what-is-left-and-what-each-waits-on)
- [Ender IO](#ender-io)
- [AE2](#ae2)
- [Three traps](#three-traps)
- [What CI can and cannot see](#what-ci-can-and-cannot-see)

Specs and removal instructions for each stopgap live in the handoff docs, which stay as they are:
[`handoff_ae2_presses_sewer_loot.md`](handoff_ae2_presses_sewer_loot.md),
[`handoff_simple_magnets_recipes.md`](handoff_simple_magnets_recipes.md),
[`handoff_enderio_grains_and_blaze.md`](handoff_enderio_grains_and_blaze.md).

## Why these exist, and the exit condition

**The pack could not ship data on 26.1.2** - no datapack loader had a NeoForge build, KubeJS crashed
the client, CraftTweaker had not ported - so things that belong to Trashlands shipped here instead.
**That cut against the engine/pack split rather than revising it**, and the exit condition was always
KubeJS being fixed. **It was, on 2026-09-07**, so these are now being dismantled in order rather than
waiting on anything (`Flatts3000/trashlands#46`, `#47` and `#52`; the engine half is #420).

## Simple Magnets went home first, and it is the template

**Simple Magnets went first, on 2026-09-08, and it is the template for the other two.** Its four
recipe overrides (Simple Magnets' recipes re-themed onto Magnet Scrap, at that mod's own recipe ids)
now ship from `pack/kubejs/data/simplemagnets/recipe/` in the pack, and nothing under
`data/simplemagnets/` remains here (#420); the `[[dependencies]]` block and the
`a_guarded_override_is_inert_without_its_mod` GameTest left with them.

**The ORDER is the part to copy**: the pack copy has to be live before the engine copy is deleted,
never the other way round. Both shipping at one recipe id for a while is safe only because they are
identical and the pack's wins on load order; deleting the engine's first would leave a window with
neither. `trashlands#47` was closed as done while this repo was still shipping its copies, which is how
that window nearly opened. For Ender IO the same mistake is worse than a window - see
[the blaze-grinding disable](#the-blaze-grinding-disable-subtracts-rather-than-adds), where the
override is the only thing keeping a rod-to-powder loop shut.

**The consequence for a standalone install is deliberate.** A Recompile install with Simple Magnets and
without the pack now gets Simple Magnets' own recipes, which want an ender pearl, lapis, an ender eye
and a diamond. That is a downgrade for that configuration and it is the split working as ruled -
re-theming another mod's recipes is curation, and curation is the pack's.

## What is left, and what each waits on

AE2's Inscriber-press pool and lang key wait on `trashlands#46`; its **four sourcing recipes are a
separate question** and are argued under [AE2](#ae2) rather than assumed to be moving. Ender IO's
Grains of Infinity find and blaze-grinding disable wait on `trashlands#52`. Neither has shipped in the
pack (as of 2026-09-10 both issues are open and the pack's `pack/kubejs/data/` holds only
`simplemagnets` and `ftbultimine`), so neither may be deleted here yet.

## Ender IO

### No sourcing work needed

**Ender IO needed no sourcing work, unlike AE2**, and that is worth knowing before anyone re-audits it:
a reachability closure over its 1187 recipes puts 897 of 924 items in reach from a vanilla-only seed,
its whole alloy spine included, and it makes its own silicon by SAG-milling sand. What ships for it is
a Grains of Infinity find in Mechanical Waste (#279, owner call - the material was already obtainable
via Ender IO's own fire crafting on deepslate), plus the two invariant fixes below.

### The blaze-grinding disable subtracts rather than adds

**This stopgap SUBTRACTS rather than adds, and that is a shape worth knowing** (#280, owner
2026-08-21). Ender IO's SAG Mill grinds a blaze rod back into **four** blaze powder. This mod's chain
runs the other way - four powder press into a Blaze Briquette and the Sintering Kiln fires it into one
rod - so that recipe alone makes the round trip break even, which is exactly what the Briquette exists
to prevent. It is worse than break-even in practice: Ender IO's `data_maps/item/grinding_ball.json`
runs from 1.0 up to an **OutputMultiplier of 1.75** on the vibrant alloy ball, so a rod returns up to
**seven** powder against the four it cost - a 75 percent gain per automated cycle. Blaze rods gate
brewing here.

### How a disable works: an override that never loads

**There is no remove-recipe primitive, so a disable is an override that never loads.** A file at
another mod's recipe id replaces it wholesale (only the top file at a path is read), and a
`neoforge:never` condition means the replacement itself is skipped - net, the id is gone. The body
still has to be well-formed JSON but is never decoded, which is the same mechanism that lets a guarded
recipe safely name an absent mod's items. The file here is
`data/enderio/recipe/sag_milling/blaze_powder.json`.

Two things make it work and both are silent if missed: `ordering = "AFTER"` on an optional `enderio`
dependency (without it Ender IO's file stays on top and nothing is logged), and the condition itself.
`every_cross_mod_override_is_ordered_after_its_mod` pins the ordering for every mod this one ships
files for (ae2 and enderio today), and `the_blaze_grinding_override_can_never_load` pins the
condition.

### The glass bottle exemption, by recipe id

**A found-only item gained a route and it was accepted rather than fixed** (owner, 2026-08-21).
Emptying an experience bottle in an Ender IO tank hands back a `minecraft:glass_bottle`, which is in
`#recompile:found_only`. It is a container conversion rather than manufacture - you already had the
bottle - so the single recipe id `enderio:tank_empty/glass_bottle` is in `EXEMPT_RECIPES` in
`FoundNotCraftedTests`.

**By id, and that is the point of it, so do not "tidy" it into the serializer list**
(`RETURNS_ITS_OWN_INPUT`, which holds serializers: `minecraft:crafting_dye` and
`minecraft:smithing_trim`). Exempting the `enderio:tank` serializer wholesale is the exact shape review
of #281 rejected: it would also cover 19 `tank_fill/*_concrete` recipes and
`tank_fill/nutritious_stick`, which are genuine manufacture with a different output item. None of those
produces a found-only item today, but the exemption would be wider than the ruling behind it and would
silently cover the next one that did.

The caveat is recorded there rather than glossed: an experience bottle can be **bought**, so a player
with emeralds has a narrow route that does not involve finding one, judged the same shape as the
wandering trader's saplings.

### CI is blind to it

**None of this is visible to CI, which has no Ender IO**, so both defects were found by dropping the
jar into `run/mods` and running the suite - and the guards that caught them are this mod's own. Worth
repeating for the pack's other majors. One caveat when doing so: **about 58 unrelated tests fail with
Ender IO present** because it registers a payload the headless harness refuses (`Payload
enderio:powered_spawner_soul may not be sent to the client`), which breaks every test using a mock
player. Filter those before reading a red run as a regression.

## AE2

**AE2 is playable here, and it takes both halves to be so.**

**The presses.** The **four Inscriber presses** are a pool on `loot_table/chests/sump.json` plus a lang
override (`assets/ae2/lang/en_us.json`) correcting AE2's own tooltip. That was #270, and it cleared ONE
of two gates while being reported as clearing both (#276).

**The materials.** The second gate: 330 of AE2's 364 items have a recipe and everything traces back to
`certus_quartz_crystal`, whose only non-circular source is a `quartz_cluster`, which drops only from
budding blocks, which generate only inside a meteorite. AE2's entire worldgen is
`structure/meteorite.json`, no AE2 chest table carries certus, and so the presses alone gave a player
an Inscriber and nothing to put in it.

**#277 closed it with four routes, none of them a find**: silicon separates out of E-Scrap, certus out
of the demolition yard's granite, fluix likewise (`separating_silicon`, `separating_certus_quartz`,
`separating_fluix`), and Sky Stone Shards ride the slag rubble stream. Manufacture rather than a drop
because the owner puts a playthrough at 4 to 8 stacks each of certus and fluix, and only a machine
produces at that scale.

**No meteorites, and that ruling stands.** Meteorites gate on `#minecraft:is_overworld` and this mod
ships **no entry for it, by owner ruling 2026-08-20**. Adding the tag would fix AE2 and every other mod
keyed on it at once, and that breadth is the objection: it admits anything gating worldgen on it,
sight-unseen, into a closed economy. Reopen only if a vanilla mechanic turns out to be silently not
firing, or if enough mods are blocked that per-mod handoffs stop scaling. The sourcing routes are what
made that ruling affordable.

## Three traps

All three were measured rather than reasoned about.

- **An unresolvable ITEM id kills a whole loot table at parse; a TagKey does not resolve at parse
  time.** Naming `ae2:silicon_press` in `sump.json` without AE2 gives `Unknown registry key` and takes
  the entire file down - the crate at the bottom of every sewer comes up empty, which reads in-game as
  bad luck. A `minecraft:tag` entry is inert instead, and `expand: false` yields EVERY member per roll
  rather than picking one, which is how AE2's own `mysterious_cube` hands the set over.
- **`neoforge:conditions` gates a whole loot table FILE, not a pool or an entry inside one.** So a
  mod-gated loot entry is not available; the tag entry is what makes the guard unnecessary. On a
  RECIPE the condition does work, and it is load-bearing: strip it and the file fails to PARSE on its
  own result id.
- **Language files MERGE; recipes and resources do not.** The client applies every lang resource for a
  namespace in ascending priority, so a one-key `assets/<their-ns>/lang/en_us.json` override only has
  to be LATER, not complete. A recipe at another mod's id is a whole-file replacement and only the top
  file at a path is read at all - which also means a typo there does not degrade to their recipe, it
  deletes the id. Both need `ordering = "AFTER"` on an optional dependency. `neoforge.mods.toml`
  carries three such entries (neoforge, ae2, enderio; measured 2026-09-10), but read the number off the
  file rather than off this sentence - which is what `every_cross_mod_override_is_ordered_after_its_mod`
  does: it derives the namespaces needing a block from what this mod actually ships under `data/` and
  `assets/`, so a stopgap arriving or leaving moves the requirement by itself.

## What CI can and cannot see

**CI cannot see either override working**, because neither mod is present at test time. What the tests
assert is inertness WITHOUT the mod, plus the reason for it; the with-mod half was verified by dropping
the jars into `run/mods` and running the gametest server against them.
