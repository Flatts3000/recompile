# Handoff: put AE2's Inscriber presses in sewer loot

**From:** the Trashlands pack (`../trashlands`), issue
[#41](https://github.com/Flatts3000/trashlands/issues/41).
**Analysed against:** Recompile **v0.13.0**, AE2 `26.1.10-beta` (CF project 223794), MC 26.1.2 /
NeoForge 26.1.2.94.
**Status:** specced, not implemented. Owner ruled on the approach 2026-08-20; this records what it
needs.
**Priority:** the pack owner has made this a **release blocker** for the pack's next release.

## The problem

AE2 is in the pack and **cannot be progressed at all.**

Its whole tree hangs off Sky Stone, Sky Stone comes from meteorites, and meteorites cannot generate
in this world:

- `data/ae2/worldgen/structure/meteorite.json` gates on `"biomes": "#ae2:has_meteorites"`.
- `data/ae2/tags/worldgen/biome/has_meteorites.json` resolves to a single entry,
  `#minecraft:is_overworld`, `required: false`.
- Recompile's overworld biomes are `recompile:household_sprawl` and `recompile:demolition_yard`, and
  **Recompile ships no `minecraft:is_overworld` tag entry at all.** Its only biome tags are
  `recompile:encroaches` and the `has_structure` entries.

So the tag contains nothing that exists here, no meteorite spawns, and there is no Sky Stone.

**No Sky Stone means no Inscriber presses**, and presses have only two other sources: the four
recipes in `data/ae2/recipe/inscriber/` which each require an existing press as the `top` ingredient
(circular), and a level-4 `fluix_researcher` villager trade. The pack ships no `config/ae2` that
changes any of this.

## The decision

**Put the presses in sewer loot** rather than make meteorites generate.

It is the better answer on the merits, not just the cheaper one. Sewers arrived in 0.11.0 as the
first place in this world that was *built* rather than dumped; they are gated twice over, behind the
demolition yard and then a manhole plate only a Prybar lifts; and they are already the only source of
three things. Presses as buried infrastructure recovered from a dead city fits the pack's premise
exactly, and it fits AE2's own fiction, where presses are ancient artefacts you do not craft.


## This is temporary, and it should be built to be deleted

**Owner, 2026-08-20: both cross-mod handoffs are stopgaps until KubeJS is fixed.** The pack cannot
ship data on 26.1.2 - that is the only reason this lives in the engine at all. When
[kube-mods/kubejs#1178](https://github.com/kube-mods/kubejs/issues/1178) is fixed (or any datapack
loader ports), this moves to the pack and comes back out of Recompile.

**It cuts against the engine/pack split in CLAUDE.md**, which puts curation and cross-mod tables on
the pack side. That split is not being revised; it is being suspended for one mod's delivery problem.

So build it to be removable, which is not how engine content is built:

- **One file per thing, deletable wholesale.** No folding these entries into an existing table or
  recipe that also carries engine content.
- **Nothing else may come to depend on it.** No guidebook entry, no `docs/` reference outside this
  handoff, and no GameTest asserting the entry exists - a test pinning it makes the removal a code
  change instead of a delete.
- **Not in `pack_extension.md`.** That documents what a pack may rely on, and this is the opposite:
  something a pack should take back.

### The AE2 half reversed the first bullet, on purpose (#268)

**The presses ship as a POOL INSIDE `chests/sump.json`, which also carries the echo shard** - engine
content, and the only source of that item in the game. That is the thing the first bullet forbids, so
it is recorded here rather than decided in a PR body.

The alternatives were worse. A global loot modifier is a separate file and takes conditions, but
adding the presses through one would need a Java class - code is a heavier thing to remove than data.
*(This said "NeoForge ships no add-item modifier". That is wrong: `neoforge:add_table` is a built-in
and it does fire on this mod's tables. What it cannot do is AIM - see the sourcing section below.)* A
separate table pulled in by a `minecraft:loot_table` entry still needs the reference line inside
`sump.json`, so removal still edits that file, and it adds a way to fail: delete the target and the
reference dangles.

**That last sentence was written before #276 and #276 did it anyway**, in `slag_rubble_pulls.json`
rather than in `sump.json`. It failed exactly as predicted and then some: see the sourcing section
below.

**So the removal is a POOL DELETION, not a file deletion, and the difference matters.** Deleting
`sump.json` takes the echo shard with it, which is a silent break -
`the_echo_shard_comes_from_the_sump_and_from_nowhere_else` catches it, but only if it is run. Delete
the last pool, the one whose comment says so, and leave the other two alone.

**There is no `neoforge:mod_loaded` guard, and there cannot be one.** *(An earlier version of this
list said every file needs one. That was wrong, and it survived into the same commit that disproved
it.)* `neoforge:conditions` gates a whole loot table file, not a pool or an entry inside one. The
guard is unnecessary anyway: the entry is a TAG, a `TagKey` does not resolve at parse time, and an
absent tag rolls to nothing. Naming the items directly instead is what would need a guard, and no
guard would have saved it - an unresolvable item id fails the whole table at parse.

Also remove the AE2 branch in `the_sump_is_unchanged_without_ae2` when the pool goes; it is a standing
constraint that only makes sense while the pool is there.

**The removal trigger is KubeJS working on 26.1.2, not a release number**, and nothing will announce
it. Whoever next updates mods should check that issue and reopen this.

**A tension this raises, and how it was settled.** The section above argues sewer loot is better *on
the merits* - presses as buried infrastructure fit both this world's premise and AE2's own fiction -
which would make it engine content that stays. **Owner ruled it a stopgap** (2026-08-20), so the
merits argument survives as a reason the stopgap is a *good* one to live with, not as a reason to
keep it here. The presses still belong in sewer loot afterwards; the pack is what should be putting
them there, by adding to Recompile's table from its own data rather than by Recompile shipping AE2
content.

## What to add

The four items, all verified present in AE2 `26.1.10-beta`:

```
ae2:silicon_press
ae2:calculation_processor_press
ae2:engineering_processor_press
ae2:logic_processor_press
```

They also carry the tag `ae2:inscriber_presses`, if a tag entry reads better than four item entries.

Target table: **`data/recompile/loot_table/chests/sump.json`**, not `sewer.json`. *(This section named
`sewer.json` when written; corrected in #268 after measuring.)* `chests/sewer` is the BARREL table and
a sewer rolls it twice per access chamber across several chambers, so the "one press set per sewer"
note below cannot be expressed there. `chests/sump` is the single crate at the bottom of each sewer -
already the echo shard's home, and described in `SewerPieces` as the reason to go to the bottom of one.

**Use a tag entry, not four item entries, and this is not a style preference.** An item id in a loot
table resolves against the registry when the file is parsed, so naming `ae2:silicon_press` without AE2
**kills the entire sump table** - measured, not guessed: `Unknown registry key ... ae2:silicon_press`,
and the crate at the bottom of every sewer comes up empty for every player not running AE2. A `TagKey`
does not resolve at parse time and an absent tag rolls to nothing.

```json
{ "type": "minecraft:tag", "name": "ae2:inscriber_presses", "expand": false }
```

`expand: false` yields EVERY item in the tag per roll rather than picking one - measured at 16 of 16
against a stand-in tag. That is AE2's own mechanism, lifted from its `mysterious_cube` loot table, and
it matches what AE2's guide tells players: the four presses come from ONE find. It also means a fifth
processor press would be carried the day AE2 adds it, and that `name_press` stays out because AE2's
tag leaves it out.

**No `neoforge:conditions` guard, because there is nowhere to put one and nothing to guard.**
`neoforge:conditions` gates a whole loot table file, not a pool or an entry inside one - so a
mod-gated entry is not available here. The tag entry needs no guard: it is inert without AE2 by
construction, which `the_sump_is_unchanged_without_ae2` asserts in exactly that state.

No load-order problem here either, unlike the Simple Magnets handoff: this is Recompile's own loot
table, so there is no override race and no `ordering = "AFTER"` needed.

### Design notes, not requirements

- **Consider one press per sewer rather than all four**, so a player works several. There is
  precedent in the same structure: the echo shard is one per sewer.
- **Sky Stone itself may not need a route.** Once presses exist, check whether the rest of AE2 opens
  up on its own before adding a second thing. **CHECKED 2026-08-20, and it does not** (#276). Certus
  quartz is the real gate and it is meteorite-only too, so the presses alone leave a player with an
  Inscriber and nothing to feed it.
- Whatever the rate, four presses gated behind a structure is a long chain; the pack's own
  progression notes would rather that be legible than fast.

## The second half: AE2's own tooltip has to be corrected too

**Shipped 2026-08-20, and it is easy to miss because it is not loot.** AE2's JEI info tab for the
presses reads *"Crafting Presses are obtained by breaking a Mysterious Cube. Mysterious Cubes are in
the center of meteorites... located by using a meteorite compass."* Every clause of that is false
here, and it is worse than an unhelpful tooltip: it is a confident wrong answer, pointing at a
Meteorite Compass that has nothing to point at, with no way for a player to find out.

So the stopgap is three things, not one, and all three move together:

- the press pool in `chests/sump.json`
- `assets/ae2/lang/en_us.json`, overriding `gui.ae2.inWorldCraftingPresses`
- the optional `ae2` dependency with `ordering = "AFTER"` in `neoforge.mods.toml`

**One key, not a copy of AE2's lang file**, because language files MERGE: the client applies every
lang resource for a namespace in ascending priority, so ours only has to be later, not complete. That
is the opposite of a recipe, where only the top file at a path is read - which is why the Simple
Magnets handoff needs whole-file replacements and this needs one line.

**AE2's own in-game guide is deliberately left wrong** (owner, 2026-08-20, #273 closed not-planned).
Its Getting Started and presses pages still tell players to find a meteorite and craft a Meteorite
Compass. Correcting them means overriding markdown RESOURCES, which do not merge the way lang keys do -
so it would mean owning AE2's prose and re-checking it on every update, for a surface the owner does not
read. The JEI tab carries the correction instead, and nobody can get stuck: the presses are guaranteed
in the sump. Do not re-file this.

Verified with AE2 actually loaded (its jar plus guideme dropped into `run/mods`): the sump yields all
four presses in 20 of 20 rolls, and the key resolves to our text rather than AE2's.

## The wider issue this exposed, which is not AE2's fault

**Recompile's overworld biomes being absent from `#minecraft:is_overworld` is not an AE2 problem.**
Any mod that gates worldgen, spawning or structure placement on that tag silently does nothing in
this world, and nothing anywhere reports it. AE2 is simply the first case anyone checked.

Adding the two biomes to that tag would fix AE2 and every other such mod at once, and would make this
handoff unnecessary.

**Owner ruling, 2026-08-20: no `is_overworld` entry unless we find that we need it.** So the sewer-loot
route above is the answer for AE2, not a workaround pending a better one.

The reason it is not the free win it looks like: the tag is an open door rather than a targeted fix.
It admits every mod that gates worldgen, spawning or structure placement on it, all at once and
sight-unseen, into an economy whose whole premise is that resources are closed and recovered. That is
the same argument that locks out the End - not that the content is bad, but that it leaks material
into a world built on scarcity. Meteorites are simply the first thing anyone noticed coming through.
It would also change what generates in an existing save.

**"Unless we find that we need it" is the live half of this ruling.** The trigger is a case where the
absence breaks something that cannot be fixed one mod at a time - a vanilla mechanic silently not
firing, or enough mods blocked that per-mod handoffs stop scaling. One more mod wanting overworld
worldgen is not that; it is another sewer-loot entry. Reopen this when the count of such mods makes
the pattern the problem, and note that nothing reports the gap, so the count has to be looked for.

## What the pack will do

Nothing until this ships. AE2 stays pinned and non-functional, documented as such in
`../trashlands/docs/pack_setup.md`, and the pack issue stays open pointing here. *(Path qualified 2026-09-03: that file lives in the PACK repo, and an unqualified `docs/` reads as this one.)*

## The third half: sourcing, because the presses cleared only one of two gates (#276, #277)

**Shipped 2026-08-21.** The design notes above ask whether "the rest of AE2 opens up on its own once
presses exist" and answer no. This is what closing the rest took, and it is by far the largest part of
the stopgap - four routes, one new item, and a Java class. **All of it moves to the pack with
everything else.**

330 of AE2's 364 items have a recipe and every one traces back to `certus_quartz_crystal`, whose only
non-circular source is a `quartz_cluster` off a budding block, which generates only inside a meteorite.
So the presses give a player an Inscriber and nothing to put in it.

### The four routes

| Route | Where | File |
|---|---|---|
| `ae2:silicon` from E-Scrap | Separator, anywhere | `recipe/separating_silicon.json` |
| 2x `ae2:certus_quartz_crystal` from a Granite Shard | Separator, demolition yard feed | `recipe/separating_certus_quartz.json` |
| 2x `ae2:fluix_crystal` from Phosphor Scrap | Separator, compacted depths feed | `recipe/separating_fluix.json` |
| `ae2:sky_stone_block` from 4 Sky Stone Shards | crafting grid | `recipe/sky_stone_block_from_shards.json` |

Each recipe carries a `neoforge:mod_loaded` guard, and the guard is **load-bearing rather than tidy**:
strip it and the file does not merely fail to apply, it fails to PARSE on its own result id, leaving a
single ERROR line in an otherwise green run.

### The removal list, in full

Deleting this is more than deleting files, which is the second reversal of the "one file per thing"
rule above and is recorded here rather than in a PR body:

- the four recipes in the table above
- `RCItems.SKY_STONE_SHARD`, its lang key, `models/item/`, `items/` client definition, its texture,
  its `texgen.toml` surface, and its entry in the creative tab
- its membership in `RCTags.NETHER_SHARDS` and in `tags/item/nether_shards.json`
- **the second pool in `loot_table/gameplay/slag_rubble_pulls.json`** - engine content, and the only
  source of Nether terrain in the game. Delete the pool whose comment says so; leave pool 0 alone.
- `loot_modifiers/no_sky_stone.json` and `content/loot/StripItemModifier.java`, plus its line in
  `RCLootModifiers`
- `gametest/Ae2SourcingTests.java`, and the `ae2` branch of `MODIFIER_ANCHOR`'s consumers is generic
  and stays

`StripItemModifier` is deliberately generic - it strips one named item, and the file's condition
decides when - so if a later stopgap needs the same trick, only the JSON is new.

### Why the drop is gated by a STRIP rather than by a condition, which took four measurements

This is the part worth reading before touching it, because three of the obvious mechanisms fail
silently and one of them shipped.

`neoforge:conditions` is honoured on a whole loot table file, a recipe file, an advancement, and - new
in #277 - a **`loot_modifiers` file**. It is NOT honoured on a loot pool, a loot entry, or a **tag
file**; that last one is silently ignored in 26.1, measured by watching a guarded tag keep its member
with AE2 absent.

**#276 shipped the drop as a `minecraft:loot_table` entry pointing at a guarded table, and gating the
target of a reference is not the same as gating the reference.** Without AE2 the table did not load
but the entry still did: it kept its weight, kept winning 15 rolls in 405, and handed back nothing.
That is a silent **one-in-27 empty pull in the default install** - the player spends the pull, may
crumble the block, and gets no item, with no log line and no message. Measured at 291 items from 300
rolls. It also left a permanent `Missing element recompile:gameplay/sky_stone_finds` loot-validation
WARN on every world load, pointing at an engine file.

**The obvious inverse was built next and cannot aim.** `neoforge:add_table` does fire on this mod's
pull streams - measured at 3.6% against an intended 3.7%. But restricting a modifier to one table
needs `neoforge:loot_table_id`, which compares `LootContext.getQueriedLootTableId()`, and **that is
never set on a table rolled programmatically**. All five of this mod's roll sites call
`LootTable.getRandomItems(LootParams)` directly, so with the condition the drop rate was zero and
without it the modifier fired on every table in the game.

**So the drop is unconditional and the STRIP is conditional.** The shard is named directly in
`slag_rubble_pulls` - it is our own item, so the id always resolves and cannot take the table down at
parse - in a **pool of its own**, and `no_sky_stone.json` (guarded by `neoforge:not(mod_loaded ae2)`)
removes it again when AE2 is absent. Stripping needs no aim, because the invariant really is global:
without AE2 that item is not loot anywhere.

A separate pool rather than an eighth entry, because an entry **displaces** a terrain shard and a pool
**rides along**. The seven terrain weights are back to exactly what they were before #276 touched them.

### Two viewers had to learn about it, and both were caught by existing tests

Neither is AE2-specific and both stay after removal:

- `RecipeFiles` reads bundled recipe JSON, so it saw three AE2 recipes the game had not loaded and JEI
  advertised recipes no player could make. It now evaluates `neoforge:mod_loaded` itself.
- `SortingData` is the model behind JEI's odds and the guidebook's, and it now honours
  `recompile:strip_item` modifiers. Without that it predicted sky stone at 1 in 27 while the game gave
  0 - caught by `pull_rates_match_what_the_mod_claims`, which exists for precisely that disagreement.

### GameTests pin this, which reverses the third bullet above a second time

`Ae2SourcingTests` asserts the without-AE2 state: nothing drops, no recipe loads, and - the half that
matters - **the guards are present rather than merely inferred from the silence**. Absence alone
passes in both the good state and the bad one, because an unguarded recipe fails to parse and is
equally absent.

Its with-AE2 branch runs only by hand, by dropping the AE2 and `guideme` jars into `run/mods` and
re-running the suite. That is deliberate: it makes the with-mod verification repeatable instead of
something someone once eyeballed. **Verified that way on 2026-08-21** - the shard drops in band, the
terrain pool still yields exactly one shard per roll, and all four recipes load.
