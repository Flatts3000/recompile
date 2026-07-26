# Farming tier spec - farmland from compost, not from a hoe

Engineering notes for reclamation rung 3 (Farming). Design agreed with Jason 2026-07-26.

## What it is

The dead garbage world has no hoe and no sticks, so a survival player can't till farmland. Farming is
gated behind the compost economy instead: **`Fertilizer + dirt` crafts `minecraft:farmland`**, and
hoe-tilling is disabled so that recipe is the only path. You can't till the dead ground fertile - you
enrich it with compost.

## The key fact (corrected 2026-07-26)

**26.1 gives `minecraft:farmland` a real item** (`Items.FARMLAND` exists; so does `Items.DIRT_PATH`).
Older versions had no farmland item, which is what made this look like it needed a custom block or a
placer item. It does not: a plain crafting recipe can **output vanilla farmland directly**, and the
placed block is real vanilla farmland - crops, moisture, drying, trampling, and the existing wet/dry
encroachment defense all just work, nothing to replicate.

So both earlier routes (a bespoke `Composted Farmland` block; a custom placer item) are dropped. The
whole feature is a recipe plus a one-line hoe lockout.

## Implementation

- **Two recipes** (both shapeless -> `minecraft:farmland`, shown in JEI), a cost gradient by ground
  quality: `farmland_from_dirt` = **dirt + 1 fertilizer** (cheap - real dirt, e.g. from breaking
  reclaimed grass); `farmland_from_coarse_dirt` = **coarse dirt + 4 fertilizer** (expensive - coarse dirt
  is the raw dead dump the player has in bulk, so making it fertile costs more compost).
- **Hoe lockout** (`RCFarming`): an `@EventBusSubscriber` handler cancels NeoForge's
  `BlockEvent.BlockToolModificationEvent` when the ability is `ItemAbilities.HOE_TILL` and
  `disableHoeTilling` is set. No hoe exists in the base mod, so this is defensive - it keeps the compost
  recipe canonical even in a pack that adds a hoe. No mixin.
- **Config**: `disableHoeTilling` (default true).
- **Water tie kept**: it *is* vanilla farmland, so it dries without water and the sweep takes it when dry
  (P1.7-R item 0a). Irrigate (Rain Collector) to hold a plot.

## Seeds (open thread, not blocking)

Wheat seeds already close: Fertilizer's vegetation scatter drops vanilla `short_grass` / `tall_grass`,
which drop wheat seeds when hand-broken. Other crop seeds (carrot / potato / beetroot) obtainability is a
separate thread (loot, teardown), out of scope here.

## Not this

- No bespoke farmland block, no placer item, no custom texture (farmland's own item covers it).
- The P1.9 scrap planter (potted muck-compost food-grower) is untouched and still deferred; this
  in-ground path coexists with it.

## Tests

`fertilizer_and_dirt_craft_farmland` (the recipe resolves and yields `minecraft:farmland`). The hoe
lockout is untested for want of a hoe in the mod; the handler is trivial.
