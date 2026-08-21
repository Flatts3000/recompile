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

## What to add

The four items, all verified present in AE2 `26.1.10-beta`:

```
ae2:silicon_press
ae2:calculation_processor_press
ae2:engineering_processor_press
ae2:logic_processor_press
```

They also carry the tag `ae2:inscriber_presses`, if a tag entry reads better than four item entries.

Target table: **`data/recompile/loot_table/chests/sewer.json`** (type `minecraft:chest`, pools rolling
3 to 6, weighted entries). `loot_table/archaeology/sewer_silt.json` is the other candidate if you
would rather they were dug rather than opened.

Each entry needs the guard so the table is unchanged without AE2:

```json
"neoforge:conditions": [
  { "type": "neoforge:mod_loaded", "modid": "ae2" }
]
```

No load-order problem here, unlike the Simple Magnets handoff: this is Recompile's own loot table, so
there is no override race and no `ordering = "AFTER"` needed.

### Design notes, not requirements

- **Consider one press per sewer rather than all four**, so a player works several. There is
  precedent in the same structure: the echo shard is one per sewer.
- **Sky Stone itself may not need a route.** Once presses exist, check whether the rest of AE2 opens
  up on its own before adding a second thing.
- Whatever the rate, four presses gated behind a structure is a long chain; the pack's own
  progression notes would rather that be legible than fast.

## The wider issue this exposed, which is not AE2's fault

**Recompile's overworld biomes being absent from `#minecraft:is_overworld` is not an AE2 problem.**
Any mod that gates worldgen, spawning or structure placement on that tag silently does nothing in
this world, and nothing anywhere reports it. AE2 is simply the first case anyone checked.

Adding the two biomes to that tag would fix AE2 and every other such mod at once, and would make this
handoff unnecessary. It would also change what generates in an existing world, so it is a bigger
decision than sewer loot and is deliberately **not** what is being asked for here. It is recorded so
the choice is visible.

## What the pack will do

Nothing until this ships. AE2 stays pinned and non-functional, documented as such in
`docs/pack_setup.md`, and the pack issue stays open pointing here.
