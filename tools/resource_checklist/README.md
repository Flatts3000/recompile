# Resource checklist pipeline

Generates `docs/vanilla_resource_checklist.md`: every resource vanilla Minecraft gives a player
without a crafting grid, grouped by where you go to get it, with each one checked off according to
whether a player of **Recompile standalone** can actually reach it.

```bash
python tools/resource_checklist/run.py                 # everything
python tools/resource_checklist/run.py --from reach    # only the mod-dependent half
```

The vanilla stages are slow and only change when the Minecraft version does. `--from reach` skips
them, which is what you want after touching the mod's loot, recipes, worldgen or structures.

Intermediates land in `build/resource_checklist/` (gitignored). The only committed output is the
markdown. **The pipeline is deterministic**: running it on an unchanged tree reproduces the
committed file byte for byte, so a diff in that doc always means the mod changed.

## Why this exists rather than a hand-written list

A hand-written list of "what can you get in this mod" is wrong the day after it is written, and
wrong in a way nobody notices, because the failure is an item silently having no source. Three of
the findings behind the current numbers were invisible to reading:

- **Loot entries of type `minecraft:tag` are not item entries.** The creeper's music-disc drop is a
  tag entry, so ten discs were missing from the first index until tags were expanded.
- **The honey chain is circular unless you know one vanilla rule.** No bee nest generates in this
  world and a beehive costs honeycomb. The only way in is a birch, oak or cherry sapling grown
  within two blocks of a flower, which has a 5% chance of carrying a nest. Honeycomb, candles,
  honey blocks and every waxed copper block sit behind that.
- **The mod's wood supply is one chain**: weedgrass to Compost Heap to a volunteer seedling to the
  Tree Nursery. Saplings are stripped from every loot roll in the game, so every plank, stick,
  apple and bee nest is downstream of it.

## Stages

Each stage writes a json into the work directory and reads only the previous stages' output, so any
one of them can be re-run alone while debugging.

| Stage | Script | Writes | What it does |
|---|---|---|---|
| extract | `extract.py` | `mcdata/` | Unpacks loot tables, recipes, trades, worldgen, structures, tags and lang out of the client jar in the NeoForm cache. |
| index | `index_vanilla.py` | `index.json` | Every item each vanilla loot table and trade can yield, plus every craftable result. Expands tag entries. |
| nbt | `nbt.py` | `structblocks.json` | Reads all 1,202 vanilla structure templates and collects each one's block palette. |
| domains | `domains.py` | `worldgen.json` | Resolves biomes to a domain via biome tags, and each biome's placed to configured features to the blocks they place. |
| catalogue | `catalogue.py` | `rows.json`, `dropped.json` | Decides what counts as a resource and files each one under a primary domain. |
| reach | `reachability.py` | `reach.json` | Closes over what Recompile can reach: world, mobs, loot, then every recipe that still loads. |
| render | `render.py` | the markdown | Writes the document. |

`renewability.py` is data, not a stage: the wiki's renewable and non-renewable lists, which
contradict each other on 52 items, reduced to the subset nothing disputes.

## Determinism

The document carries no timestamp, and every glob that feeds a first-wins record is sorted. Both
matter: `REACH[item]` keeps the route that reached it FIRST, so unsorted `glob.glob` order (which
is `os.scandir` order, and differs between NTFS and ext4) silently rewrites ~149 route labels
without changing the reachable set. A CI box would then produce a different file from a dev box
and the diff would mean nothing.

## Things that bit, and will bite again

- **`unzip 'data/minecraft/loot_table/*'` extracts nothing** and reports success. These trees need
  `**`. `extract.py` uses `zipfile` and prefix matching instead, so it cannot recur there.
- **The client jar ships optional datapacks.** `data/minecraft/datapacks/trade_rebalance/` contains
  a full second set of chest loot tables that are off unless a world enables them. A careless glob
  mixes an experimental trade rebalance into the answer; `extract.py` excludes that prefix.
- **Tree features reference PLACED features, not configured ones.** `trees_savanna` points at
  `acacia_checked`, which lives in `placed_feature/`. A resolver that only looks in
  `configured_feature/` silently drops the leaves of every tree behind a `_checked` reference.
- **A crafted block dropping itself is not acquisition.** 383 items are excluded on that basis,
  which is what keeps concrete and the copper oxidation states out of a resource list.
- **Some routes are neither loot nor recipe** and have to be declared: bucket fills, axe-stripping,
  copper oxidation, concrete in water, the Compost Heap volunteer, the Sequencer's amber byproduct,
  and the Dry Clay Body's cauldron step. The resin and clay chains are dead without the last two.
  They live in `INTERACT` in `reachability.py`; adding a mechanic of that shape means adding a line
  there, and nothing else will tell you it is missing.
- **A PROCEDURAL structure is invisible here, and its blocks read as unreachable.** The index reads
  structure NBT templates (`nbt.py` walks their palettes); this mod's structures are Java that writes
  blocks directly, with no template to read. So the Municipal Aquarium's fifteen dead corals, its
  prismarine cladding and its heart of the sea are all mineable in-world and all show unchecked here,
  and the 48 rows that structure did move came from its two LOOT TABLES and one recipe, which the
  index does read. Treat the reachable count as a floor rather than a measurement wherever a
  procedural structure is involved.
- **A mod that PLACES a vanilla block is invisible here, and the doc reads as if the block is not in
  the world at all.** `FertilizerScatter` scatters fern, large fern, tall grass and four small
  flowers on every fertilizer use, and the closure models none of it - it only knows loot tables,
  recipes and `INTERACT`. Today no verdict is wrong, because everything on that list is reachable by
  another route anyway, so this is latent rather than live. It still cost something: #344's stated
  reason for a new find was "a large fern needs a fern to bone-meal that nothing here provides",
  which is false and came from reading this doc. The verdict there was right for a reason the doc
  does not print - a placed large fern shears into `minecraft:fern`, so the `large_fern` ITEM really
  is bouquet-only. **A row says whether an item is reachable, never why it is not**, and inferring
  the second from the first is how a wrong premise gets written down.

## When the mod changes

`reachability.py` reads the mod's data directly, so new loot tables, recipes and tags are picked up
with no edit. Three things are hand-maintained in it and will drift silently:

- `MOBS` - which mobs can exist, and why. **Every entry is hand-declared, including the biome
  spawners**: `reachability.py` never opens a biome file. So adding a mob to a biome's spawner list
  changes nothing here until `MOBS` is edited too, and nothing will tell you. `recompile:pigeon` is
  the live example - it is in `household_sprawl.json` and not in `MOBS` (harmless only because it
  has no loot table).
- `VANILLA_IN_WORLD` - the vanilla blocks the mod's code-generated structures place. Sewers, cooling
  towers and smokestacks build from Java, so their palettes cannot be read from data.
- `INTERACT` - the non-recipe routes described above.
