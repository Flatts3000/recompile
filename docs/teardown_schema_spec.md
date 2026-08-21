# The teardown recipe schema

**Written 2026-08-12** against `main` at v0.9.0, from `TeardownRecipe.java`. Every field, default and
range below was read out of the codec rather than recalled.

`recompile:teardown` is **public API**. Packs and addons add teardowns by dropping JSON into
`data/<namespace>/recipe/`, with no mod release and no Java. This page is the reference for that.

## Why this page exists

The schema shipped with Phase 0 and grew a whole second output form (`pools`, v0.9.0) without ever
being written down outside the class javadoc. A public API that only its own source describes is one
a pack author has to read Java to use.

## Shape

```json
{
  "type": "recompile:teardown",
  "input": "recompile:washing_machine",
  "station": "recompile:workbench",
  "tool": "recompile:prybar",
  "ticks": 120,

  "results": [ { "item": "recompile:scrap_metal", "count": 3 } ],
  "extras":  [ { "item": "minecraft:lapis_lazuli", "chance": 0.5 } ],
  "pools":   [ { "rolls": 5, "entries": [ { "item": "recompile:junk", "weight": 3 } ] } ],
  "teaches": [ { "recipe": "recompile:pump", "scraps_required": 4 } ]
}
```

| Field | Required | Default | Notes |
|---|---|---|---|
| `type` | yes | - | Always `recompile:teardown` |
| `input` | yes | - | An `Ingredient`: a bare item id, a tag as `"#c:ingots"`, or an array of item ids. **Not** the pre-1.21.2 `{"tag": ...}` object form, which does not parse in 26.1 |
| `station` | no | `recompile:workbench` | Tier gate string. One format covers the whole progression |
| `tool` | no | none | An `Ingredient` naming the tool that must be **racked at the bench**. Omit for a no-tool teardown |
| `ticks` | no | `80` (4s) | 1..72000. How long the player holds to complete one breakdown |
| `results` | no | `[]` | Deterministic core output |
| `extras` | no | `[]` | Independent chance rolls |
| `pools` | no | `[]` | Weighted draws. See below |
| `teaches` | no | `[]` | Recipes this teardown can reveal |

**A teardown must be able to produce something.** `results` and `pools` are each optional, but a
recipe with an empty `results`, and no pool that both rolls at least once and carries an entry with an
`item`, is a **load error**. This is enforced in a codec `validate` rather than left to trust: the
failure mode it prevents is a teardown that loads clean, runs, consumes the input and hands back
nothing.

## The three output forms, and when to use which

They coexist. Anything already written against `results` and `extras` keeps working - that
compatibility is deliberate, because packs extend this.

### `results` - it always gives you exactly this

```json
"results": [ { "item": "recompile:scrap_metal", "count": 3 } ]
```

| Key | Required | Default | Range |
|---|---|---|---|
| `item` | yes | - | any item id |
| `count` | no | `1` | 1..99 |

### `extras` - each entry rolls its own chance, independently

```json
"extras": [ { "item": "minecraft:lapis_lazuli", "chance": 0.5 } ]
```

| Key | Required | Default | Range |
|---|---|---|---|
| `item` | yes | - | any item id |
| `chance` | yes | - | 0.0..1.0 |

**The trap that produced `pools`.** `extras` cannot express "one of these three". Three entries at
1/3 each give you *none* of them about 30% of the time and *two or more* about 26%. If what you mean
is a pick-one, `extras` is the wrong field.

### `pools` - draw `rolls` times from a weighted list

```json
"pools": [
  { "rolls": 8, "entries": [
      { "item": "recompile:scrap_metal",   "weight": 5 },
      { "item": "recompile:plastic_scrap", "weight": 4 },
      { "item": "recompile:e_scrap",       "weight": 1 } ] }
]
```

Pool:

| Key | Required | Default | Range | Notes |
|---|---|---|---|---|
| `entries` | yes | - | non-empty | |
| `rolls` | no | `1` | 0..256 | How many independent draws. `0` disables the pool |
| `teaches` | no | `false` | | See "Knowledge that follows the draw" |
| `scraps_required` | no | `4` | 1..99 | Only meaningful with `teaches: true` |

Entry:

| Key | Required | Default | Range | Notes |
|---|---|---|---|---|
| `item` | **no** | none | any item id | **Omit it to make the entry a filler** |
| `weight` | no | `1` | 1..100000 | Relative to the other entries in the same pool |
| `count` | no | `1` | 1..64 | Stack size when this entry is drawn |

**`rolls` is also how quantity works.** Eight rolls over metal, plastic and e-scrap gives eight items
split by weight, differently every time, rather than a fixed pile. That is the difference from
`results`: same average, different every teardown.

**An entry with no `item` is the filler**, and it is how a pool gives nothing some of the time. A pool
with `{"weight": 1}` alongside three real entries misses one draw in four. This is the same
weighted-plus-filler idiom the mod's pull streams use, on purpose, so it is a shape you have already
read.

**A pool with no filler always produces.** That is not a special case in the code, it falls out of the
weights - but it is the mechanism a "you always get exactly one of these" pool relies on, so do not
add a filler to such a pool without meaning to.

## Knowledge that follows the draw

Two independent ways to teach.

**`teaches` (top level)** is a static list. Every entry is rolled on every teardown.

| Key | Required | Default | Range | Notes |
|---|---|---|---|---|
| `recipe` | yes | - | a recipe id | What gets revealed |
| `scraps_required` | no | `1` | 1..99 | Deterministic study threshold: how many teardowns complete it |
| `chance` | no | `0.0` | 0.0..1.0 | **Acceleration only** - a lucky insight grants extra progress. There is no bad-streak failure |

**`"teaches": true` on a pool** couples knowledge to what was produced. The pool grants an Idea
Fragment for the recipe whose id **matches the item it just drew**. Tear down a fridge and pull a
motor, you learn the motor; pull a bulb, you learn the bulb.

That identity - component item id equals its blueprint recipe id - holds for every component blueprint
in this mod. If you add a component whose blueprint is registered at a different id, a teaching pool
will silently teach nothing for it.

A teaching pool draws once per roll, so `rolls: 2` on a teaching pool grants two fragments.

## What a viewer shows

JEI and Jade read the bundled recipe files. Pools are surfaced with a per-item chance of
`weight / total * rolls`, clamped to 100%, and a stack count of the **expected** number rather than
the entry's `count` - because chance alone cannot say "eight of these", and an eight-roll pool
saturates at 100% long before the eighth roll.

Datapack-added teardowns are **not** reflected in JEI. Viewers read the mod's own bundled files, not
the live recipe manager, because the manager is reliably empty at the moment JEI asks. Your recipe
will work in-world and not appear in the viewer. This is a known, accepted limitation.

## Worked example

The Dead Fridge, the one recipe that uses all three kinds of pool at once
(`data/recompile/recipe/fridge.json`). For the minimal teaching case instead, read
`washing_machine.json`: one single-entry teaching pool for the Pump, one materials pool, nothing else.

```json
"pools": [
  { "rolls": 8, "entries": [
      { "item": "recompile:scrap_metal",   "weight": 5 },
      { "item": "recompile:plastic_scrap", "weight": 4 },
      { "item": "recompile:e_scrap",       "weight": 1 } ] },

  { "rolls": 1, "entries": [
      { "item": "minecraft:snowball", "weight": 3 },
      { "item": "minecraft:ice",      "weight": 1 } ] },

  { "rolls": 1, "teaches": true, "scraps_required": 4, "entries": [
      { "item": "recompile:motor" },
      { "item": "recompile:pump"  },
      { "item": "recompile:bulb"  } ] }
]
```

Eight scrap split by weight, exactly one thing out of the freezer, exactly one component - and
whichever component came out is the one you learn about.

## Rules a pack should know

- **A component pool must not carry a filler.** `a_component_teardown_always_yields_a_component`
  fails the build if a pool offering a gated component can draw a blank. Taking an object apart for
  its component must never come away with none, because the blueprint route costs four of the same
  teardown and cannot rescue a bad streak.
- **A teardown may not hand out a water source** unless it is on an explicit allowlist
  (`no_teardown_hands_out_an_unsanctioned_water_source`). `minecraft:ice` broken without silk touch
  leaves a water source block, and two are infinite water. The Dead Fridge is the one sanctioned
  exception, by owner ruling.
- **`_comment` keys are ignored** by the codecs, so JSON files here carry their reasoning inline. Use
  a string or an array of strings.

## Where the code is

| Thing | File |
|---|---|
| Schema, codecs, `Pool`, `PoolEntry` | `content/recipe/TeardownRecipe.java` |
| Drawing pools, granting fragments | `content/block/entity/RecompileWorkbenchBlockEntity.java` |
| What the viewers read | `compat/TeardownData.java` |
| Pure-logic tests | `src/test/java/com/flatts/recompile/TeardownPoolTest.java` |
| In-world tests | `gametest/RecompileWorkbenchTests.java`, `gametest/ComponentBlueprintTests.java` |
