# Recovered paintings - spec

**Status: design locked 2026-08-01, not built.** Six real paintings, pixelated to Minecraft resolution,
found in the trash and hung on your wall.

## 0. The idea

The Mona Lisa in a landfill. These are the things humanity kept for five hundred years, and they ended
up in the same heap as the mattresses.

It is the most direct statement of the jam theme the mod can make. The reclamation ladder is the past
as ecology and blueprints are the past as knowledge; this is the past as **the things people valued**,
and it says so without a word of prose - which matters, because the standing rule is to minimise
authored writing since players distrust it.

**It also finishes a room.** The Display Pedestal already holds objects. Walls holding images turns a
base from storage into a museum of the world that was, and that is a place a player stands in rather
than a mechanic they read about.

## 1. Acceptance criteria (owner, 2026-08-01)

The whole feature is these four, and the fourth is the only one vanilla does not already do:

1. **The item in your hand says Mona Lisa.**
2. **Jade says Mona Lisa** when it is on the wall.
3. **Placing it places the Mona Lisa image**, never a random painting.
4. **Breaking it keeps all three true** of the item you get back.

## 2. What vanilla already gives us

Checked against 26.1.2 rather than assumed:

- **Painting variants are pure data.** `data/<ns>/painting_variant/<id>.json` carrying `asset_id`,
  `width`, `height`, `title`, `author`, plus a PNG at `textures/painting/<id>.png`. No code.
- **Size limit is 1 to 16 per side** (`ExtraCodecs.intRange(1, 16)` on both fields). Vanilla ships only
  nine of the possible sizes and has no 3x2 or 2x3, but those are legal - unused is not unsupported.
- **`minecraft:painting/variant` is a persistent item component**, so a loot table can drop a painting
  item that IS a specific artwork.
- **`HangingEntityItem.appendHoverText` already renders title, author and dimensions** from that
  component. Criterion 1 is free, and adding `minecraft:item_name` makes the item's name read
  "Mona Lisa" as well.
- **Placing reads the component**, so criterion 3 is free too.
- **The `placeable` painting-variant tag** decides what a variant-less painting can become. Replacing it
  means vanilla's Kebab and Aztec can never appear and **every painting in this world is a recovered
  one.**

## 3. The one thing that does not work

`Painting.dropItem` spawns a bare `Items.PAINTING` and throws the variant away. Break a Mona Lisa today
and you get a blank canvas.

NeoForge does not patch it - its only `HangingEntity` patch is support-box logic - so there is no native
hook, and this mod has no mixins.

**The seam is `EntityJoinLevelEvent`.** Whatever destroys a painting (punch, arrow, creeper, or removing
the block behind it) `dropItem` runs and an ItemEntity appears. Catch a bare `minecraft:painting` item
joining the level, find the Painting entity being removed beside it, and stamp the variant and name back
onto the stack. One event class, every destruction cause, no mixin.

This is the same problem `CLAUDE.md` already documents for the Rain Collector - state lost because
breaking destroys the thing holding it - with an entity in place of a BlockEntity.

## 4. The six, and their sizes

Public domain in **both** the US and the EU, which is the bar for a globally distributed mod: US and EU
terms differ, and works that have lapsed in one can still be protected in the other. Every artist below
died before 1945.

Sizes preserve the real canvas orientation (owner: portrait stays portrait, landscape stays landscape),
capped at 4 blocks per side to match vanilla's own vocabulary.

| work | artist (d.) | real w:h | blocks | ratio | error | texture |
|---|---|---|---|---|---|---|
| The Great Wave off Kanagawa | Hokusai (1849) | 1.471 | 3x2 | 1.500 | 2.0% | 48x32 |
| The Starry Night | van Gogh (1890) | 1.250 | 4x3 | 1.333 | 6.7% | 64x48 |
| The Scream | Munch (1944) | 0.808 | 3x4 | 0.750 | 7.1% | 48x64 |
| Mona Lisa | Leonardo (1519) | 0.688 | 2x3 | 0.667 | 3.1% | 32x48 |
| Girl with a Pearl Earring | Vermeer (1675) | 0.876 | 3x4 | 0.750 | 14.4% | 48x64 |
| The Kiss | Klimt (1918) | 1.000 | 4x4 | 1.000 | exact | 64x64 |

**Vermeer is the compromise and it is deliberate.** At 0.876 the closest ratio in the entire grid is
1x1, which would flip a portrait into a square and shrink it to a single tile. Orientation was held and
the 14.4% error accepted: nobody measures a painting on a wall, everybody notices a portrait that is
not one. Raising the cap to 6 would cut the worst error to 4.9% and make Starry Night exact, at the cost
of a 6-block-wide Great Wave - a mural rather than a collectible.

**Sourcing.** The artwork being public domain is not the whole question; a *photograph* of it can carry
its own copyright in some jurisdictions. Take them from museum open-access programmes or Wikimedia PD-Art
and **record the source file beside each variant**, so provenance is auditable rather than assumed. This
is the same standard the collectibles already meet by using CC0 models, and the same reason the Create
port authored its own art.

## 5. Rarity: the 40-hour target

Owner's target: a player collects all six in roughly 40 hours.

**The naive reading is 2.5x wrong.** Completing a set of six is a coupon-collector problem, not six
draws - late drops are mostly duplicates you already own:

    expected drops to complete = 6 x H(6) = 6 x 2.450 = 14.7

So the target is **14.7 painting drops in 40 hours**, not 6.

What that implies per Bulky Waste opened:

| Bulky Waste per hour | in 40h | chance each | 1 in |
|---|---|---|---|
| 2 | 80 | 18.4% | 5 |
| 5 | 200 | 7.4% | 14 |
| 10 | 400 | 3.7% | 27 |
| 20 | 800 | 1.8% | 54 |
| 40 | 1600 | 0.9% | 109 |

**The unknown is Bulky Waste per hour, and it is measurable rather than guessable.** Mounds place at
`count 5` per chunk and 5% of mound cores carry Bulky Waste, so roughly 0.25 Bulky Waste exist per
chunk generated - but the driver is how much *new ground* a player covers per hour, which only a
playtest gives. **Do not pick a number from this table without that measurement**; the range spans 20x.

Whatever is chosen is config-gated and folds into #36.

## Phase 1 - the six variants

**Ships:** six paintings that exist and can be given in creative.

- Six `painting_variant` JSONs, six PNGs, six lang entries for title and author.
- Art: source images downscaled to the exact texture size. Pixelation is the aesthetic - these should
  read as low-resolution reproductions, not as smudges.
- Reviewed through `gen/recompile_textures_review.html` like every other texture in this mod.

**Acceptance:**
- Each variant loads and renders at its declared size.
- `RegistryCompletenessTests`-style sweep: every variant has a texture at the declared dimensions,
  and a title and author that are not raw lang keys.
- Provenance recorded per variant.

## Phase 2 - exclusivity

**Ships:** vanilla paintings can no longer be hung.

- Replace `data/minecraft/tags/painting_variant/placeable.json` with our six.

**Acceptance:**
- A painting item with no variant places one of ours, never a vanilla one. Asserted by placing many and
  checking every result, in the manner of `wool_can_no_longer_make_a_bed` - a tag replacement that
  silently fails looks identical to one that worked until you look at a wall.

## Phase 3 - finding one

**Ships:** paintings drop from Bulky Waste, each as itself.

- A pool in `loot_table/blocks/bulky_waste.json` using `minecraft:set_components` to stamp
  `minecraft:painting/variant` and `minecraft:item_name`.
- Rate config-gated, per the standing rule.

**Acceptance:**
- A dropped painting carries both components and its tooltip reads the work's name.
- The existing mattress and washing-machine weights are not disturbed - adding a pool must not quietly
  restate the odds of what is already there.

## Phase 4 - surviving the break

**Ships:** criterion 4. The only phase that can fail.

- An `EntityJoinLevelEvent` handler that restores the variant and name onto the dropped item.

**Acceptance:**
- Place, break, and the recovered item is still the same painting. Asserted for **every** destruction
  cause the event has to cover: player break, projectile, explosion, and removing the supporting block.
- Breaking a vanilla-variant painting is untouched.
- Nothing is stamped when no painting was destroyed nearby, so the handler cannot rewrite an unrelated
  painting item lying on the floor.

## Phase 5 - Jade and the collection

- Jade entity provider naming the work on the wall (criterion 2).
- **Paintings are the obvious second category for #32's completion tracking**, and both features get
  better for it: objects on plinths, images on walls, one collection.

## Open

- **Bulky Waste per hour.** Blocks the rate, and needs a playtest rather than an opinion.
- **Do paintings tear down?** The standing invariant is that nothing enters the found economy without a
  teardown exit or the piles become clutter. Collectibles are the existing exception because they are
  displayed rather than processed; paintings are the same shape of thing, but that should be stated
  rather than assumed.
- **Cap 4 or 6.** Fidelity against wall space.
