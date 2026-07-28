# Steel I-Beam + Cutting Torch - spec (issue #48)

**Status: design locked, not built.** The **bulk-iron** half of the demolition yard's iron path. Rebar (the
trickle) shipped in #47; this adds the second, higher-volume grade and its dedicated tool. Parent spec:
[`demolition_yard_spec.md`](demolition_yard_spec.md) S4.3 / S6-torch. Everything ships config-gated; defaults
are the design; tuning joins the pre-beta pass (#36).

## 0. The two iron grades (why this exists)

```
rebar        (from Reinforced Concrete, common ~35%, sledgehammer)  -> smelt -> 1 iron    [trickle, shipped]
Steel I-Beam (from husks / steel piles, cut with the Cutting Torch) -> raw iron (bulk)  -> smelt -> iron   [bulk, THIS]
```

You crush concrete but you **cut** steel - two verbs, two tools. The I-beam is the frame of the building
husks (#49), so it is also the bulk of the steel piles; this issue builds the block + tool + processing,
survival placement lands with #49.

## 1. Steel I-Beam (block)

- **A `RotatedPillarBlock`** (the log/pillar pattern): an `AXIS` property (x / y / z) so a beam reads as a
  vertical **column** or a horizontal **girder**, placed by the face clicked and by the husk generator.
  End-cap texture on the two axis ends, web/flange texture on the four sides (like a log's end vs bark).
- **Solid, `requiresCorrectToolForDrops`**, carries the new `#recompile:mineable/cutting_torch` tag - so
  bare hands, the sledgehammer, and any other tool yield **nothing**. Steel is cut, not crushed or mined.
- **Not in `#minecraft:mineable/pickaxe`** (there are no pickaxes here anyway) - the cutting torch is the
  only correct tool.
- `strength` a bit tougher than reinforced concrete (steel); `sound(SoundType.METAL)`, `mapColor` grey.
- Full cube, so no `noOcclusion()` needed (if the final model is a non-cube I-beam profile, add it - see the
  CLAUDE.md occlusion trap).

### Loot (`loot_table/blocks/steel_i_beam.json`)
- Drops **raw iron in bulk** - `minecraft:raw_iron`, uniform **2-4** (tune #36). This is the world's only
  `raw_iron` source (no mining), and it smelts to iron the vanilla way, so the Burn Barrel handles it now
  and the Makeshift Forge (#50) automates it in bulk. Reusing `raw_iron` avoids a redundant "steel" item.
- No secondary pool for v1 (keep it clean); a rare bonus can join in the balance pass.

## 2. Cutting Torch (tool)

- **`tool()`-over-a-tag**, the knife/prybar/sledgehammer pattern: `props.tool(TORCH_TIER,
  RCTags.MINEABLE_WITH_CUTTING_TORCH, attack, speed, 0F)`. Mines **only** the cutting-torch tag; it is a
  cutter, not a general tool, and **not** a tier ladder (single tool).
- **Custom single `ToolMaterial` `TORCH_TIER`** (like the sledgehammer's `COPPER_TIER`): iron-ish mining
  level so it cuts steel, modest durability (the fuel tank, see below), repair via copper or iron ingots.
- **Fuel model - durability for v1.** The torch's durability *is* its fuel tank: it cuts a bounded number
  of beams, then is spent and re-crafted (which re-spends an Oily Rag). This keeps it a standard `Item`
  with no event hooks or fuel-component plumbing. **Deferred alternative (#36 / follow-up):** consume an
  Oily Rag from the inventory per cut (a `BlockEvent.BreakEvent`/`destroyBlock` hook), so the torch is
  reusable and the sink is the rag, not the tool. Ship durability; note the hook path.
- Low attack (it is a torch, not a weapon) and a slow-ish mine speed on steel.

### Recipe (`recipe/cutting_torch.json`) - gated one step past first-iron
```
I     iron ingot   (the cutting nozzle - needs the rebar-iron bootstrap first)
C     copper ingot (the body)
R     oily_rag     (the fuel / wick)
```
A 1x3 shaped recipe (iron / copper / oily_rag, top to bottom). So: sledgehammer + rebar -> your first iron
-> craft the torch -> cut I-beams -> bulk iron. The Oily Rag ties the P1.4-A fuel line into the tool.

## 3. New tag

- **`#recompile:mineable/cutting_torch`** (`RCTags.MINEABLE_WITH_CUTTING_TORCH`) - a block tag, members:
  `steel_i_beam` (JSON in `tags/block/mineable/cutting_torch.json`). Same shape as `mineable/sledgehammer`.

## 4. Registry + data

- **`RCBlocks`**: `STEEL_I_BEAM` (RotatedPillarBlock, requiresCorrectToolForDrops, METAL sound).
- **`RCItems`**: `TORCH_TIER` ToolMaterial + `CUTTING_TORCH` item; `STEEL_I_BEAM` block-item (a
  `RotatedPillarBlock` places by clicked face via its own placement, so a plain block-item is fine).
- **`RCTags`**: `MINEABLE_WITH_CUTTING_TORCH`.
- **`RCCreativeTabs`**: steel I-beam in the raw-source group (near rubble / reinforced concrete), the
  cutting torch in the tools group (after the sledgehammer ladder).
- **Data**: `tags/block/mineable/cutting_torch.json`; `loot_table/blocks/steel_i_beam.json`;
  `recipe/cutting_torch.json`; blockstate (axis=x/y/z -> the pillar model rotations); `models/block`
  (I-beam) + `models/item`; `items/` client defs; `lang`.

## 5. Textures (placeholder now, real via #51)

- `steel_i_beam` -> `minecraft:block/iron_block` (end) + a metal side, or just `iron_block` all faces as a
  placeholder. `cutting_torch` -> a vanilla tool/torch item texture (e.g. `minecraft:item/flint_and_steel`,
  which reads as a torch/igniter). Repoint to `recompile:` textures when #51 lands.

## 6. GameTests (`DemolitionYardTests`)

- **Tool gate:** a Steel I-Beam is `requiresCorrectToolForDrops`; the Cutting Torch `isCorrectToolForDrops`,
  a bare hand and a Copper Sledgehammer are **not** (mirror `reinforced_concrete_needs_sledgehammer`).
- **Yield:** `Block.getDrops(...)` with the torch returns `raw_iron` (count in range). Optional.
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

I-beam raw-iron count (2-4), torch durability (the fuel-tank size), the recipe costs, and whether the torch
moves to fuel-per-cut. All first-pass; fold into the single pre-beta pass.
