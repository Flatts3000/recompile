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

- A `content` blockstate enum (`EMPTY` + one value per known material) records what the bin is bound
  to. The **color is applied at render** by a block color handler keyed on that state - the same
  `tintindex` mechanism vanilla uses for grass and water, which is a **static-model feature, not a
  BER**. So every material shares **one model and one texture set**; only the tint differs. Binding
  sets `content=scrap_metal` and the body renders grey. **Color is the primary identifier** - a
  solid-bodied bin reads by hue across a room, faster than any label, and it matches what is inside
  (grey = scrap metal, teal = glass), so it is self-documenting.
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
  `#recompile:binnable`. Default membership is exactly the **raw materials pulled from garbage**: the
  P0.4 vocabulary `scrap_metal`, `plastic_scrap`, `glass_shards`, `organic_muck`, `fiber_scrap`,
  `e_scrap`, `junk`. **Not** the pull stream's non-raw outputs (`rebar`, `tin_can`, `glass_bottle`),
  not crafted intermediates (`scrap_plating`, `cullet_glass`), not finds, food, or tools - only raw
  scrap. A pack adds modded scrap to the tag without a mod release; anything not in the tag is refused
  on insert.
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

## Interaction (screen-free) - Functional Storage's scheme

Adopted wholesale from Functional Storage, the controls players already know. Right-click inserts,
left-click extracts, and the granularities mirror each other:

| Input | Result |
|---|---|
| **Right-click**, matching salvage | Deposit the held stack; binds an empty bin |
| **Double right-click**, matching salvage | Dump *every* matching stack from the inventory |
| **Left-click** (tap) | Extract one item |
| **Sneak + left-click** | Extract a full stack |

- The double-click is a real second click within ~8 ticks; the bin remembers the last click
  (transient, per-player) so the second one dumps all - even after the first emptied your hand.
- Left-click rides the `LeftClickBlock` event (there is no `Block.attack` hook in 26.1), on the
  initial press only, so one tap is one extract. It does **not** cancel the break: a tap does not
  chip a strength-1.4 block, but holding left-click still breaks the bin, which is how you pick up a
  full one (contents ride the drop). Cancelling would trap the contents until you emptied it by hand.
- A non-matching `#binnable` item into a bound bin does nothing; into an unbound bin it binds. A
  non-`#binnable` item is always refused.

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
- **Blockstate**: `content` x `fill`. The model depends only on `fill` - every `content` value uses
  the same model per fill height, because the color is applied at render, not by swapping textures.
  `content` exists so the color handler and Jade can read what the bin holds.
- **One model, one texture set**: a single bin model per `fill` height (five), with the body faces
  flagged `tintindex: 0`; the steel frame, rivets and glass window are left un-indexed so they keep
  their own look and only the panels take the material color. The fill pile is part of the same
  model, its height the only thing `fill` changes.
- **Color at render - no per-material art, no atlas gotcha:** one authored texture set (near-greyscale
  on the tinted faces so a multiply reads true), colored in-engine by a client `BlockColor` registered
  on `RegisterColorHandlersEvent`, keyed on the `content` blockstate - the same mechanism vanilla
  grass and water use, and explicitly not a BER. Register the matching item color so the held bin is
  tinted too. This is where a material's color lives: a `material -> int` entry plus its enum value,
  **no PNG per material**. `EMPTY` and any unknown (modded) `content` return white, so the neutral
  texture shows through - the empty and modded-fallback bin for free. Colors are **material-matched**.
- **The color is not GameTest-able** - color handlers are client-only. The tests assert the `content`
  blockstate (server-side); the actual hues are a `runClient` check.

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

**Tests.** GameTests cover the mechanic and the interaction (binding, refusal, deposit/
withdraw, capacity, sticky binding, break-survives, hopper-in/no-out, the double-click
deposit-all, and left-click extract). The mod's first JUnit unit tests cover the pure logic
(the content-to-color mapping and the contents-component codec round trip), run by
`./gradlew test`.

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to a file, check `$?`.
2. `runGameTestServer` - full suite; reported total is ours **plus one**.
3. `runClient` - place several bins, bind them to different materials, watch the labels and fill
   levels swap; feed one with a hopper; confirm no hopper pulls out; break a full one and replace it
   to confirm contents survive; check the Jade readout. Iterate on the look.
4. Code review before the PR, not after.

## Decided in the 2026-07-24 session

- **Colors are material-matched** and applied as a **render-time tint** over one texture set (a
  `BlockColor` keyed on the `content` state), not per-material art and not the bold recycling
  palette. The bin agrees with its contents and no palette is invented.
- **`#binnable` is the raw material vocabulary only** - the seven materials pulled from garbage
  (`scrap_metal`, `plastic_scrap`, `glass_shards`, `organic_muck`, `fiber_scrap`, `e_scrap`, `junk`).
  Excludes the pull stream's non-raw outputs (`rebar`, `tin_can`, `glass_bottle`), crafted
  intermediates (`scrap_plating`, `cullet_glass`), finds, food, and tools.
- **Placed-and-emptied bins stay bound** (they only unbind when broken while empty). Confirmed.
- **The modded/uncolored fallback is the neutral grey bin** - the same as the empty appearance, with
  the exact item named by Jade.

## Open questions

1. **Fill granularity** - five levels vs more; five matches the composter and is probably enough.
2. **Sound** - a deposit/withdraw clink; deferred with all polish.
3. **All numbers** - capacity, recipe cost. Deferred to the balance pass.
