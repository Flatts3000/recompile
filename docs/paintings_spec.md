# Recovered paintings - spec

**Status: SHIPPED 2026-08-02** (#99, PR #100). Six real paintings, pixelated to Minecraft resolution,
found in the trash and hung on your wall. The completion advancement is deliberately **not** part of
this: advancements ship post-alpha as a group (#32), which is why alpha ships with duplicates and why
the find rate is tuned for a duplicate-bearing set.

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

## 1b. Decisions (owner, 2026-08-01)

| Decision | Answer | Why |
|---|---|---|
| Duplicates | **None eventually. Alpha ships with them** | "I don't want them to find a coupon. I want them to find the painting." The no-duplicate mechanism needs advancements, and advancements are post-alpha (owner) |
| Fragility | **Survives, but drops** | An explosion or fire knocks it off the wall as an item rather than destroying it. The danger stays legible and a 40-hour collection can never be lost at hour 39 |
| Teardown exit | **Not required** | The found-economy invariant is retired (below) |
| Size cap | **4 blocks per side** | Matches vanilla's own vocabulary; collectible scale rather than mural scale |

**No duplicates changes the rarity maths completely.** The coupon-collector figure of 14.7 drops was for a
uniform pool. If every find is a painting the player does not yet own, **6 drops complete the set**, and
the required rate drops by 2.5x accordingly. Redo the table in section 5 against 6, not 14.7.

**And it needs per-player state, which this mod has never had - but it does not need a new save format.**
Advancements are per-player state Minecraft already persists. Grant one per painting on first find, and
have the drop offer only the works whose advancement the player lacks. Nothing new is serialized, and it
lands exactly where #32 was already heading: the completion advancements become the mechanism, not just
the reward.

**Advancements are post-alpha and ship as a group** (owner, 2026-08-01), so **alpha ships with
duplicates** and the rate below is tuned for that. When the no-duplicate mechanism lands the rate must be
divided by 2.5, and this is exactly the kind of coupled change that gets missed - the drop chance and the
duplicate rule are one decision wearing two hats.

**The found-economy invariant is retired** (owner, 2026-08-01). CLAUDE.md carried it as standing: *nothing
enters the found economy without a teardown exit, or the piles become clutter*. It had already stopped
being true - the collectibles (avocado, present, gold coin, toy car, Puzzle Cube) are found and displayed
with no teardown exit, and the mod ships twelve teardown recipes. Retiring it makes the documents
match what shipped rather than describing a rule the content had outgrown.

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

**Alpha ships with duplicates**, because the no-duplicate mechanism needs advancements and those are
post-alpha. So the number that matters now is the coupon-collector one: completing a uniform set of six
takes `6 x H(6) = 14.7` finds on average, not 6, because late finds are mostly works you already own.

**Best guess for alpha: 7% per Bulky Waste opened, roughly 1 in 14.**

Derived rather than picked:

- Mounds place at `count 5` per chunk and 5% of mound cores carry Bulky Waste, so about **0.25 Bulky
  Waste exist per chunk generated**.
- A player who is exploring while also building covers maybe **20 new chunks an hour**, so **~5 Bulky
  Waste opened per hour**.
- 40 hours x 5 = **200 opened**. `14.7 / 200 = 7.4%`.

**That reads as far rarer in play than 1 in 14 sounds**, and the two halves of the brief only reconcile
this way. "Extremely rare" and "all six in 40 hours" pull against each other, and what resolves them is
that Bulky Waste itself is uncommon: at 7% a painting turns up roughly **once every 2.7 hours of play**.
The per-roll number is not tiny; the felt rate is.

**When no-duplicates lands, this becomes 3% (1 in 33).** Same 40 hours, six finds instead of 14.7.

| Bulky Waste per hour | in 40h | alpha (14.7) | later (6) |
|---|---|---|---|
| 2 | 80 | 18.4% | 7.5% |
| **5 (assumed)** | **200** | **7.4%** | **3.0%** |
| 10 | 400 | 3.7% | 1.5% |
| 20 | 800 | 1.8% | 0.8% |
| 40 | 1600 | 0.9% | 0.4% |

**The 5-per-hour assumption is the weak link, and it is measurable rather than guessable.** The table
spans 20x, so the single most valuable thing a playtest can produce for this feature is a count of how
many Bulky Waste a player actually opens in an hour. Tracked in #36.

**Implementation: a second loot pool, not a re-weighting.** `bulky_waste.json` currently has one pool
whose weights are mattress 3, washing machine 2. Adding paintings to that pool would restate the odds of
both existing finds, and #36 warns against disturbing them. A separate pool gated on
`minecraft:random_chance` leaves them untouched and makes the painting a bonus behind the mattress
rather than an alternative to it - which is also the better fiction.

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

- **Bulky Waste per hour.** Still the only thing blocking the rate, and it needs a playtest rather than an
  opinion. With no duplicates the target is 6 drops in 40 hours instead of 14.7.
- **Which advancement shape.** One per painting plus a completion one, or a single advancement with six
  criteria. The second is tidier; the first gives six toasts, which is six moments of reward.
