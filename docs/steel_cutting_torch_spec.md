# Steel I-Beam + Cutting Torch - spec (issue #48)

**Status: SHIPPED 2026-07-30** (#48, PRs #52 and #54). The **bulk-iron** half of the demolition yard's
iron path. Rebar (the trickle) shipped in #47; this added the second, higher-volume grade and its
dedicated tool, charged with Oily Rags rather than durability-only. Parent spec:
[`demolition_yard_spec.md`](demolition_yard_spec.md) S4.3 / S6-torch. Everything ships config-gated; defaults
are the design; tuning joins the pre-beta pass (#36).

**Where the build moved past this plan:** the beam drops **Steel Offcut**, not raw iron (section 1),
and both iron routes are `minecraft:blasting` in the Cupola (#91). The block is **`SteelBeamBlock`**,
not a `RotatedPillarBlock`: `AXIS` plus `X` / `Z` / `TOP` / `BOTTOM` run-and-gusset properties, a slim
`noOcclusion()` I-profile model, and a connection state machine ported from Create's `GirderBlock`
(MIT code; none of its assets). **A pickaxe returns the beam itself** (#129, PR #132, and the beam is in
`#minecraft:mineable/pickaxe`) so players can build with steel, while the torch still cuts it into
offcuts. Real textures shipped (`steel_beam_flange`, `steel_beam_web`, `steel_beam_gusset`,
`cutting_torch`).

## 0. The two iron grades (why this exists)

```
rebar        (from Reinforced Concrete, common ~35%, sledgehammer)  -> blast (Cupola) -> 1 iron nugget  [trickle, shipped]
Steel I-Beam (from husks / steel piles, cut with the Cutting Torch) -> Steel Offcut (bulk) -> blast (Cupola) -> iron ingot  [bulk, THIS]
```

You crush concrete but you **cut** steel - two verbs, two tools. The I-beam is the frame of the building
husks (#49), so it is also the bulk of the steel piles; this issue builds the block + tool + processing,
survival placement lands with #49.

## 1. Steel I-Beam (block)

- **A `RotatedPillarBlock`** (the log/pillar pattern): an `AXIS` property (x / y / z) so a beam reads as a
  vertical **column** or a horizontal **girder**, placed by the face clicked and by the husk generator.
  End-cap texture on the two axis ends, web/flange texture on the four sides (like a log's end vs bark).
- **Solid, `requiresCorrectToolForDrops`**, carries the new `#recompile:mineable/cutting_torch` tag - so
  bare hands and the sledgehammer yield **nothing**. Steel is cut, not crushed.
- ~~**Not in `#minecraft:mineable/pickaxe`** (there are no pickaxes here anyway) - the cutting torch is the
  only correct tool.~~ **Reversed 2026-08-04 (#129):** the beam is in `#minecraft:mineable/pickaxe`, and
  a pickaxe hands the beam itself back (the loot table's fallback branch) so a girder can be moved; only
  the torch yields offcuts.
- `strength` a bit tougher than reinforced concrete (steel); `sound(SoundType.METAL)`, `mapColor` grey.
- The final model is a non-cube I-beam profile, so the block sets `noOcclusion()` (the occlusion trap in
  CLAUDE.md and `docs/systems_notes.md`).

### Loot (`loot_table/blocks/steel_i_beam.json`)
- Drops **Steel Offcut** - `recompile:steel_offcut`, uniform **2-4** (tune #36). Settled 2026-07-30,
  replacing `raw_iron`, which has left the mod entirely - it only ever existed because the beam vended it.
- **Why not raw iron.** It was metallurgically backwards: recycled structural steel is already-reduced
  metal, so it becomes graded scrap (ISRI calls this grade Plate & Structural) and is *remelted*, never
  returned to ore. It also handed the gated metal straight to a basic furnace, since `raw_iron` smelts the
  vanilla way. The offcut is what a torch actually leaves behind.
- **It remelts to iron only in the Cupola Furnace (#50)** (`recipe/iron_from_steel_offcut.json`). The
  recipe is **`minecraft:blasting`**, and the Cupola is a `RecipeType.BLASTING` machine: a vanilla furnace
  cannot run a blasting recipe at all, and a vanilla blast furnace costs 5 iron ingots, so the route is
  circular and unreachable before iron. **The gate is a property of the machine, and no fact about this
  world's materials has to hold for it to work.**

  **This bullet described the first design, which failed** (#91), and it stayed here after the fix. It
  said the recipe was ordinary `minecraft:smelting`, gated because the Burn Barrel refuses it and no
  other furnace is craftable - and it called that "load-bearing and fragile", correctly. It broke exactly
  as predicted: wood makes a wooden pickaxe, a wooden pickaxe drops cobbled deepslate (plain `deepslate`
  is in `mineable/pickaxe` and in no `needs_*` tag), and `recipe/deepslate_from_shards.json` is a third
  route that needs no pickaxe at all. A gate built from the absence of a material dies the moment
  anything adds the material. `no_smelting_recipe_turns_a_mod_item_into_iron` asserts the current one.

  **Consequence for terrain work (2026-08-17):** the world's rock went from 7-11 blocks per column to
  59-63 for the sewers (#90), roughly ten times the world deepslate. That touches nothing here - the gate
  has not depended on deepslate scarcity since #91, and `deepslate_from_shards` was already an unbounded
  crafted route - but it is written down so the next person to read this bullet is not measuring against
  the retired design.
- The texture derives from the beam's own steel via texgen `match_hue`, so the offcut reads as cut from the
  block it drops out of (measured hue gap: 5.7).
- **No dedicated steel output - the yield is iron** (owner call, 2026-07-28). A "steel ingot" tier is a
  possible future consideration **if/when Mekanism is integrated** (it ships a steel tier); revisit then,
  not now. Until then, "Steel I-Beam" is the block's flavour and the material it yields is iron.
- No secondary pool for v1 (keep it clean); a rare bonus can join in the balance pass.

## 2. Cutting Torch (tool)

- **`tool()`-over-a-tag**, the knife/prybar/sledgehammer pattern: `props.tool(TORCH_TIER,
  RCTags.MINEABLE_WITH_CUTTING_TORCH, attack, speed, 0F)`. Mines **only** the cutting-torch tag; it is a
  cutter, not a general tool, and **not** a tier ladder (single tool).
- **Custom single `ToolMaterial` `TORCH_TIER`** (like the sledgehammer's `COPPER_TIER`): iron-ish mining
  level so it cuts steel. Its durability is **moot** - the torch carries `UNBREAKABLE` (see below) - but the
  material still supplies the mining tier, speed and attack stats.
- **Fuel model - a charged torch** (owner call, 2026-07-30, superseding both earlier models). The torch is
  `UNBREAKABLE` and carries its own charge in a `torch_fuel` data component. You **feed it rags ahead of
  time** (right-click with the torch in hand, `CuttingTorchItem`), and cutting a block in
  `#recompile:mineable/cutting_torch` spends one charge (`RCTorchFuel`, on NeoForge's `BreakBlockEvent`).
  - **Numbers (first-pass, balance is #36):** 1 Oily Rag = **8 cuts**; capacity **64 cuts** (8 rags).
  - **A new torch is not empty.** With no stored value the torch reads as one rag's worth, because its
    recipe already spends an Oily Rag. A spent torch stores an explicit 0, so the default only ever applies
    to a torch that has never been used.
  - **Charging refuses to overfill rather than clamping.** A rag that would be partly wasted is simply not
    taken, and the torch says it is full. Clamping would silently burn most of a rag with nothing on screen
    to show it.
  - **The charge is visible** as an orange bar on the item, using the durability-bar slot that
    `UNBREAKABLE` leaves free. A charge you cannot see is one players run out of mid-cut without warning.
  - **Empty means the cut is refused outright** - the block is left standing with an action-bar nudge,
    rather than broken with no drops, because silently eating the steel gives no way to learn the rule.
  - Creative is exempt, and blocks outside the tag are free, so breaking dirt with a torch in hand costs
    nothing.
  - **Why a charge rather than draining the pack:** fuelling becomes a deliberate act with a visible gauge,
    and running dry is discovered when you charge rather than mid-swing. Reaching into the player's
    inventory on every break is invisible in the moment and reads as rags going missing.
  - **Superseded (v1, 2026-07-28):** durability *was* the fuel tank - the torch cut a bounded number of
    beams, then was spent and re-crafted. **Superseded (v2, 2026-07-30):** a rag consumed from the
    inventory per cut. Kept as the record of what changed and why, not as live options.
- Low attack (it is a torch, not a weapon) and a slow-ish mine speed on steel.

### Recipe (`recipe/cutting_torch.json`) - gated past first-COPPER (owner, 2026-07-30)
```
. C      C = copper pipe    - the torch tube and nozzle
P R      P = plastic scrap  - the hose, feeding R = rebar, the body/handle
. O      O = oily rag       - the fuel, and the torch's first charge
```
Each component is a part of the real object, and **all four are obtainable before any iron exists**: the
pipe is 2 copper nuggets (6 nuggets -> 3 pipes) and nuggets come straight from scrap metal in the Burn
Barrel, rebar and plastic scrap come from the pull streams, and the rag from fiber + muck. The pipe is
deliberately cheaper than a copper ingot would be - 2 nuggets against 9 - so the torch sits just past
first-copper rather than a full ingot's worth beyond it.

**Why not iron.** The torch used to cost an iron ingot, bootstrapped by smelting rebar in the Burn Barrel.
That stopped working when iron moved behind the Cupola Furnace (#50) - the torch would have needed iron to
cut the steel that is the only source of iron. Substituting copper breaks the circle: copper is the everyman
metal (`material_economy.md`), so the torch sits one step past first-copper and the demolition yard is
reachable without the Cupola. What still waits on the Cupola is *refining* what you cut - beams drop Steel
Offcut, and nothing turns it into iron until that machine exists. That is a deliberate "you found it, now build the
smelter" beat rather than a lockout.

The Oily Rag ties the P1.4-A fuel line into the tool, and is why a freshly crafted torch arrives with one
rag's charge already in it.

## 3. New tag

- **`#recompile:mineable/cutting_torch`** (`RCTags.MINEABLE_WITH_CUTTING_TORCH`) - a block tag, members:
  `steel_i_beam` (JSON in `tags/block/mineable/cutting_torch.json`). Same shape as `mineable/sledgehammer`.

## 4. Registry + data

- **`RCBlocks`**: `STEEL_I_BEAM` (`SteelBeamBlock` as built, not the `RotatedPillarBlock` first planned;
  requiresCorrectToolForDrops, noOcclusion, METAL sound).
- **`RCItems`**: `TORCH_TIER` ToolMaterial + `CUTTING_TORCH` item; `STEEL_I_BEAM` block-item (the block
  takes its axis from the clicked face in its own placement, so a plain block-item is fine).
- **`RCTags`**: `MINEABLE_WITH_CUTTING_TORCH`.
- **`RCCreativeTabs`**: steel I-beam in the raw-source group (near rubble / reinforced concrete), the
  cutting torch in the tools group (after the sledgehammer ladder).
- **Data**: `tags/block/mineable/cutting_torch.json`; `loot_table/blocks/steel_i_beam.json`;
  `recipe/cutting_torch.json`; blockstate (axis=x/y/z -> the pillar model rotations); `models/block`
  (I-beam) + `models/item`; `items/` client defs; `lang`.

## 5. Textures (placeholder at first, real via #51; the real ones have shipped, see the note at the top)

- `steel_i_beam` -> `minecraft:block/iron_block` (end) + a metal side, or just `iron_block` all faces as a
  placeholder. `cutting_torch` -> a vanilla tool/torch item texture (e.g. `minecraft:item/flint_and_steel`,
  which reads as a torch/igniter). Repoint to `recompile:` textures when #51 lands.

## 6. GameTests (`DemolitionYardTests`)

- **Tool gate:** a Steel I-Beam is `requiresCorrectToolForDrops`; the Cutting Torch `isCorrectToolForDrops`,
  a bare hand and a Copper Sledgehammer are **not** (mirror `reinforced_concrete_needs_sledgehammer`).
- **Yield:** `Block.getDrops(...)` with the torch returns Steel Offcut (count in range); as built,
  `steel_beam_drops_offcuts_not_ore` and `a_pickaxe_returns_the_beam_and_a_torch_cuts_it`.
- Placement is covered by the husk feature's tests (#49).

## 7. 26.1 API notes

- `RotatedPillarBlock` for the axis; blockstate maps `axis=y/x/z` to the base model + `x`/`y` rotations
  (the vanilla log blockstate is the template).
- `ToolMaterial` is the record `(TagKey<Block> incorrectBlocks, int durability, float speed, float
  attackBonus, int enchantValue, TagKey<Item> repairItems)` - confirmed by the sledgehammer's `COPPER_TIER`.
- `props.tool(material, mineableTag, attackDamage, attackSpeed, disableBlockingSeconds)` - the knife pattern.

## 8. Build order

1. `MINEABLE_WITH_CUTTING_TORCH` tag + `tags/block/.../cutting_torch.json`.
2. `STEEL_I_BEAM` block (RotatedPillarBlock) + loot + block-item + blockstate/model (placeholder).
3. `TORCH_TIER` + `CUTTING_TORCH` item + recipe.
4. Creative tab + lang.
5. GameTest (tool gate) - green via `runGameTestServer`.
6. Placeholder textures wired; real art -> #51; survival placement -> #49.

## 9. Balance (#36)

I-beam offcut count (2-4), the torch's charge (8 cuts per rag, 64 capacity), and the recipe costs. All
first-pass; fold into the single pre-beta pass. *(This listed torch durability and fuel-per-cut, both
settled by the charged-torch model in section 2.)*
