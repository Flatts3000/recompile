# Scrap Bin - spec

**Written 2026-07-24. Design, not built.** A single craftable storage block that binds to one salvage
type and holds a large amount of it, with a screen-free UX. Real-world model: the labeled bulk bin
of a sorting operation. Design decisions in this doc are the ones locked in the 2026-07-24 session;
record them in `../trashlands/docs/design_decisions.md` under a new P-code before building.

## Why

Hoarding is a designed mechanic, not just a convenience: the e-waste tier (P2.6) deliberately rewards
stockpiling boards, because late Mekanism chains recover far more from the same board than early
crude smelting does. A bulk bin per commodity is the tool that loop wants.

It complements the **Scrap Barrel** rather than replacing it. The barrel is general 27-slot storage
(it reuses `ChestMenu`); the bin is one type, enormous capacity, and no screen. The bin is also the
*most* on-brand storage the mod could have - depositing and withdrawing are world interactions, so it
opens no menu at all, which is the Sorting Tarp's stateless philosophy applied to storage.

## The mechanic

One craftable **Scrap Bin**. Empty, it reads as an obvious "put salvage here" box. The moment a
salvage type is inserted, it **binds** to that material and its appearance changes to match.

The change is a **blockstate, never a render**, and that is the whole trick - it keeps the block
inside the mod's `no BlockEntityRenderer` rule (design_decisions.md:296; a BER is a per-object draw
call, and a wall of bins would be a wall of them):

- A `content` blockstate enum (`EMPTY` + one value per known material) selects a **static baked model
  tinted that material's signature color**. Binding just sets `content=scrap_metal`; the game swaps to
  the pre-tinted model. Same move as the garbage blocks' `sorted` state. **Color is the primary
  identifier** - a solid-bodied bin reads by hue across a room, faster than any label, which is the
  real recycling-station quality. The bin's color also matches what is inside (grey body = scrap
  metal, teal = glass), so it is self-documenting.
- A `fill` blockstate (`EMPTY / LOW / MID / HIGH / FULL`, composter-style) raises a visible pile in a
  small window so a wall of bins can be scanned for what is full. Color says *which*; the window says
  *how much*.
- The **BlockEntity holds the exact count** (a BE that *holds* is allowed - the Scrap Barrel does it;
  only rendering is banned), and **Jade** reads it for the exact `Scrap Metal 3,712 / 4,096` on look.

So: one recipe, one block, changes texture on bind, glanceable fullness, exact count on look, no
screen, no BER.

## Acceptance and color - two different lists

The seam between the two systems, stated plainly so it is not conflated in the build:

- **Acceptance is tag-driven and open.** The bin accepts an item if and only if it is in
  `#recompile:binnable`. Default membership is the bulk material vocabulary (`scrap_metal`,
  `plastic_scrap`, `glass_shards`, `organic_muck`, `fiber_scrap`, `e_scrap`, `junk`, and the
  processed intermediates `rebar`, `scrap_plating`, `cullet_glass`) - not finds, food, or tools. A
  pack adds modded scrap to the tag without a mod release. Anything not in the tag is refused on
  insert.
- **The color is enum-driven and finite.** A blockstate can only show a color for a material that has
  a `content` value. The mod's own vocabulary gets its signature tint; **binnable-but-uncolored modded
  scrap binds to the neutral grey bin.** It is still held, and Jade still names it - it just does not
  get a bespoke color. This is the honest ceiling of avoiding the BER, and it is a graceful fallback,
  not a wall.

## Binding lifecycle

The binding is sticky: it survives an empty placed bin, and it travels with a full one.

| Event | Result |
|---|---|
| First salvage inserted into an unbound bin | Binds to that material; `content` set; appearance changes |
| Emptied by withdrawal **while placed** | **Stays bound** - refill without re-binding (a labeled bin remembers its job) |
| Broken / picked up **while empty** | Drops a **blank, unbound** bin - reusable for any material |
| Broken / picked up **with contents** | Dropped item **carries its scrap and its binding** - relocate a full tote of sorted metal |

Carrying contents + binding on the dropped item is the **Rain Collector water pattern**
(`CLAUDE.md`): register a `DataComponentType` on `RCDataComponents` holding `{material, count}`, write
it in `BlockEntity.collectImplicitComponents`, read it back in `applyImplicitComponents` on
placement, and copy it onto the drop with a `minecraft:copy_components` loot function
(`"source": "block_entity"`). An empty bin writes no component, so its loot table drops the plain
unbound item.

> **Open (flagged for confirmation):** "emptied while placed stays bound" is the read of the session
> decision "unbinds when picked up and empty." If a placed bin should instead forget its material the
> instant it hits zero, that is a one-line change here.

## Automation - hopper in, no automation out

The bin is the **sink** of a sorting pipeline, never a source a machine pulls from. A future sorter
can fill it; you spend it by hand.

Implemented as a `WorldlyContainer`:

- `getSlotsForFace` returns the bin's slot for all faces (the inverse of the Burn Barrel, which
  returns an empty `int[]` to cut all automation).
- `canPlaceItemThroughFace` returns true **only** for an item matching the bound material (or, for an
  unbound bin, any `#binnable` item - the first hopper insert binds it, same as a hand insert).
- `canTakeItemThroughFace` returns **false**, always. No hopper, Create funnel, or pipe extracts.

## Interaction (screen-free)

- **Right-click with matching salvage** - deposit the held stack (up to capacity).
- **Right-click with matching salvage while sneaking** - deposit every matching stack from the
  player inventory (bulk dump).
- **Right-click empty-handed** - withdraw one stack.
- **Right-click empty-handed while sneaking** - withdraw one item.
- Right-click with a non-matching `#binnable` item into a bound bin does nothing (wrong bin); into an
  unbound bin, it binds. A non-`#binnable` item is always refused.

Exact controls are provisional and get felt out in `runClient`; the deposit/withdraw split above is
the Storage-Drawers convention, which players know.

## Capacity

A config value (`RCConfig`), default **4,096** (64 stacks). A balance-pass number, not final -
joins the pre-beta pass with every other tunable.

## Recipe

`scrap_plating` box with a `cullet_glass` window - the glass justifies the fill window you read
through, and both are already in the economy. Exact shape is a first-pass:

```
P P P      P = scrap_plating
P G P      G = cullet_glass
P P P
```

## Blocks, items, registry

| Registry name | Type | Notes |
|---|---|---|
| `scrap_bin` | `ScrapBinBlock` + `BlockItem` | The one craftable. Holds `content` + `fill` blockstates; BE for the count. |

- **BlockEntity** `ScrapBinBlockEntity` - holds `{material id, count}`, serialized with
  `ValueOutput`/`ValueInput` (26.1), plus the implicit-component read/write for break+replace.
- **`content` enum** - a custom `EnumProperty` over the labeled materials + `EMPTY`. Finite; adding a
  material later is a mod update (new value + texture), the same constraint as any blockstate.
- **`fill` enum** - `EMPTY/LOW/MID/HIGH/FULL`, derived from `count / capacity` and refreshed on every
  deposit/withdraw.

## Data and assets

- `tags/item/binnable.json` - the acceptance allowlist (defaults above).
- `RCDataComponents` - a `{material, count}` component type; a `copy_components` loot function in
  `loot_table/blocks/scrap_bin.json`.
- **Blockstate**: `content` x `fill` variants. The unbound state (`content=EMPTY`) is the neutral
  grey bin; each colored `content` value points at the shared bin model with that material's tint at
  each `fill` height.
- **Models**: one bin body shape reused for every material - only the texture tint differs - plus a
  fill pile visible in the window. The pile height is the only thing `fill` changes, so the five
  levels are a parameterized template shared across all colors.
- **Color via retint, no atlas gotcha:** one base bin body PNG (tint-ready, near-greyscale) plus one
  **retinted** copy per material - the same texgen `retint` backend the cullet glass pane already
  uses. ~10 deterministic recolors, no fresh generation, and the tint lives on the block texture
  itself, so the item-atlas-vs-block-atlas trap never applies. The neutral base PNG is the empty,
  unbound, and modded-fallback appearance. **Colors are material-matched** (each bin takes its own
  material's signature hue), so no palette is invented and the bin agrees with its contents.

## Tests (GameTest)

Driven through a static entry point per the `sortOnce` / `spreadOnce` convention where a helper fits;
the rest set blockstate/BE directly.

1. inserting a `#binnable` item into an unbound bin binds it (`content` set, count = inserted)
2. a non-`#binnable` item is refused (unbound bin stays `EMPTY`, item not consumed)
3. deposit adds to the count; withdraw removes; counts are exact
4. `fill` blockstate tracks `count / capacity` across the five levels
5. capacity is a hard cap - a deposit past it leaves the remainder in hand
6. **emptied while placed stays bound** (withdraw the last item; `content` unchanged)
7. **broken while empty drops a blank unbound bin** (no data component on the drop)
8. **broken with contents carries scrap + binding** (drop's component = `{material, count}`; replace
   restores both) - the load-bearing break+replace test, mirroring the Rain Collector's
9. a hopper under/beside a bound bin inserts a **matching** item
10. a hopper is refused a **non-matching** item
11. a hopper **cannot extract** from a full bin (`canTakeItemThroughFace` false)
12. binnable-but-uncolored (modded) item binds to the neutral grey bin (`content=GENERIC` or
    equivalent), still counts, still Jade-named

Negative-control the two that can pass silently: the hopper-cannot-extract test (a stray true would
make it pass as a normal container) and the carry-contents-on-break test (asserting the wrong
component, or none, is the Rain-Collector-water failure mode).

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to a file, check `$?`.
2. `runGameTestServer` - full suite; reported total is ours **plus one**.
3. `runClient` - place several bins, bind them to different materials, watch the labels and fill
   levels swap; feed one with a hopper; confirm no hopper pulls out; break a full one and replace it
   to confirm contents survive; check the Jade readout. Iterate on the look.
4. Code review before the PR, not after.

## Decided in the 2026-07-24 session

- **Colors are material-matched**, one per material, via retint - not the bold recycling-convention
  palette. The bin agrees with its contents and no palette is invented.
- **Placed-and-emptied bins stay bound** (they only unbind when broken while empty). Confirmed.
- **The modded/uncolored fallback is the neutral grey bin** - the same as the empty appearance, with
  the exact item named by Jade.

## Open questions

1. **Exact `#binnable` default membership** - the processed intermediates (`rebar`, `scrap_plating`,
   `cullet_glass`) are in by the draft above, but `scrap_plating` reads more as a building block than
   bulk scrap you stockpile. A curation call, best made with the material economy in view.
2. **Fill granularity** - five levels vs more; five matches the composter and is probably enough.
3. **Sound** - a deposit/withdraw clink; deferred with all polish.
4. **All numbers** - capacity, recipe cost. Deferred to the balance pass.
