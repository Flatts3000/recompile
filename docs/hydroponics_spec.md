# Hydroponics + the power tier - spec (issue #43)

**Status: design locked 2026-07-31, not built.** Captured from a design session; every numbered item is an
owner call made there.

**Two issues, one spec.** The power tier is **#72** and the hydroponics machine is **#43**; #43 is blocked
on #72, since the machine consumes RF and nothing generates it yet. They were split on 2026-07-31 because
#43 had quietly grown from four plants into an energy layer plus a reversed design lock.

Design source of truth for the reversal this depends on: `../trashlands/docs/design_decisions.md` P3.5.

---

## 0. Why this exists

The Farming tier (rung 3) shipped the in-ground path only. Sugar cane, bamboo, cactus and sweet berries
were held back because they do not fit the till-and-plant model, and - the load-bearing part - **they have
no other source in the game at all.** Verified: zero loot-table and zero recipe references to any of the
four. Your call, 2026-07-26: *"Do not add a found/teardown source for them in the meantime."*

So hydroponics is not a convenience. It is the only door to a quarter of vanilla's plant life.

## 1. The machine

- **A single block, not a multiblock** - it does not use the multiblock framework the Tree Nursery and
  Grass Spreader share.
- **Consumes water and RF.**
- **Grows everything**, including the six seed-crops from the in-ground tier. It is the **automation tier
  above hand-farming**, not a parallel to it.

### The seedling swap - the core mechanic

Put an **Unknown Seedling** in. The first grow yields a **random** plant (the seedling stays a lottery
ticket, exactly as planting one in dirt is today). **The plant then swaps in as its own input** and seeds
itself from there.

This is not a new idea; it is `farming_tier_spec.md`'s existing rule applied to the plants that cannot
live in farmland: *"a volunteer is the bootstrap. Once it gives you, say, potatoes, you replant that
crop's own seed deterministically - the RNG is only the entry point, not a permanent tax."*

Two consequences worth stating because they collapse a lot of apparent complexity:

- **"Unlock the cutting" and "grow it forever" are the same mechanism**, before and after the swap. There
  is no dual-mode machine and no second output type.
- **The output is the vanilla item.** So whether the player plants it in the world or leaves it in the
  machine is entirely their choice - and the awkward terrain question (cane needs water-adjacent sand,
  cactus needs sand, and sand only exists in the demolition yard) stops being a design problem and
  becomes an optional shortcut for players who have the yard.

**Species swap freely** - change the input, change what it grows. One machine covers everything once
unlocked. Deliberately *not* the Scrap Bin's bind-on-first-use pattern.

### The crop is planted, not fed (decided 2026-08-02)

**The crop slot takes exactly one item and never consumes it.** A batch spends water and power, drops
`hydroponicsYield` into the output, and leaves the plant where it is - it keeps growing until the player
takes it back out. The slot caps at one, in the container as well as the menu, so a hopper cannot stack a
queue of crops that would sit there doing nothing.

The first build consumed one input per batch and yielded two, so the machine paid for itself only if you
shuttled the output back round. Three reasons that was wrong, in order of weight:

- **The bay is the only source of sugar cane, bamboo, cactus and sweet berries in the game.** A machine
  that consumes its crop is one bad hopper, or one broken block, away from taking a plant out of a save
  permanently. It must not be able to eat the last cactus in the world.
- **It was worse than the thing it replaces.** One sugar cane planted in vanilla is infinite cane forever.
  A late-game machine that instead charges a plant per harvest is backwards, and the yield multiplier only
  papered over it.
- **It removes the shuttling** - nothing has to move output back to input, by hand or by hopper.

`hydroponicsYield` therefore drops to **1** and changes meaning: it is throughput per batch, not a
multiplier over what went in.

**The seedling is still consumed**, because a lottery ticket is exactly what it is. It yields one plant to
the *output*, which the player then seeds the bay with, and from that point the machine never asks for
another. The swap survives intact.

### A seed-based crop is planted as its seed (decided 2026-08-02)

**Wheat grows from wheat seeds and yields wheat, not the other way round.** A wheat item is not something
you can plant in vanilla, so it is not an input here either. Same for beetroot, melon, pumpkin,
torchflower and the pitcher plant. Potato and carrot stay direct inputs because in vanilla they *are*
their own seed.

That mapping cannot come from the tag - the tag says what goes in, and this says what comes out - so it
lives in a NeoForge data map, `data/recompile/data_maps/item/hydroponic_crop.json`. **Both of its fields
are optional and the default is the elegant case:** an entry-less plantable yields itself and throws off
nothing, which is exactly right for cane, cactus, bamboo, berries, kelp and the rest. Adding a plant to
`#recompile:hydroponic` is still the whole of what makes it growable; the map exists only for plants that
need more than that.

The tag now covers every vanilla overworld plantable rather than a curated ten. **Nether wart and chorus
fruit are deliberately excluded** - both live behind `RCDimensionLockout`, and growing them here would
route around a dimension gate with a machine.

### The byproduct slot

**A third slot, take-only, under the harvest.** Vanilla potatoes carry a 2% chance of a poisonous one, and
seeds come off wheat, beetroot, melon and pumpkin. The yield stack is type-locked, so with a single output
a byproduct would have to be either binned silently or merged into the harvest - and one poisonous potato
in fifty would stall a potato farm outright. Both harvest slots pull from the bottom face, or a hopper
under a potato farm drains the potatoes and lets the poisonous ones fill up and jam it one block lower.

**Room for the byproduct is checked before the batch starts, whether or not that batch will roll one.**
Gating on the roll would make an identical machine sometimes run and sometimes stall for reasons a player
cannot see; the alternative loses items silently.

### No growth medium

**The bay does not require dirt, farmland or sand**, as a medium slot or as a placement rule. Hydroponics
means soil-free; that is the word. Sand exists only in the demolition yard and dirt is coarse until the
reclamation ladder, so a medium requirement would gate a gate, and it would add a third answer to "why is
it not running" - the exact confusion the two-gauge GUI exists to reduce. If a consumable sink is ever
wanted, the right item is **Fertilizer**, which already exists for the Tree Nursery, as an optional
speed-up rather than a requirement.

## 2. The power tier - SHIPPED

**Built 2026-07-31 (#72).** The machine below still has to be written; the energy layer it consumes now exists. **Two generators**, both new:

- The **Burner Generator** - burns anything in the vanilla fuel data map, so it tracks the Burn Barrel's
  fuel list rather than keeping its own. Fed by right-click, no screen and no inventory. 20 FE/tick.
- The **Solar Panel is a real generator** - 2 FE/tick scaled by actual daylight, using vanilla's own
  daylight-detector maths so night, dusk and weather all fall out of one expression.

Both **push to adjacent consumers** each tick rather than waiting to be pulled, so the mod works with no
pipe mod installed: put a generator against a machine and it runs. A pipe mod is then an upgrade for
reaching further, not a requirement for having power.

### The Solar Panel's existing instances

`recompile:solar_panel` is already placed inside two shipped machines - one in the Grass Spreader, two in
the Tree Nursery. **They keep generating; those two machines simply do not consume.** Nothing breaks in
existing saves, there is no "why doesn't this panel work" split-brain, and a panel embedded in a spreader
quietly feeding an adjacent hydroponics reads as a reward for compact layout.

The alternative - making shipped machines start requiring power - was considered and rejected as the only
version that breaks saves.

### RF is a NeoForge standard, not a mod dependency

`Capabilities.Energy.BLOCK` returns a `BlockCapability<EnergyHandler, Direction>` - the same shape as the
fluid capability the Rain Collector already uses. So:

- The mod consumes FE with **zero mod dependencies**.
- Every energy mod interoperates automatically - Powah and AE2 today, Mekanism whenever it ports.
- **Mekanism and Create are not options on 26.1.2**: neither has a NeoForge build past 1.21.1. This was
  checked, not assumed.

**Consequence for progression:** because the engine only speaks FE, *when* the player gets power is a
**pack** decision - Trashlands controls it by choosing when to introduce a power mod. Recompile ships one
modest standalone generator so it is playable alone.

## 3. What this reversal costs

Recorded in full at `design_decisions.md` P3.5. The headline: **the Nether loses its stated reason to
exist.** "First RF power originates here" was the draw. Osmium and the netherite-analog remain, but
whether they carry a dimension alone is **open, and must be answered before P3.5 is built.**

Three specs that restated "no RF" have been annotated: `grass_spreader_spec.md`,
`multiblock_system_spec.md`, `tree_nursery_spec.md`. The **Pump stays inert** - that is P2.3, untouched.

## 4. Open questions

- **Does it need a screen?** The swap probably wants a visible slot showing what the machine is seeded
  with. Note that CLAUDE.md's "there is exactly one custom screen" is **already false** - the Tree Nursery
  ships `TreeNurseryScreen` alongside the Scrap Crafting Table's, registered together in `RCMenuScreens`.
  That doc needs correcting either way; whether machine screens are now simply accepted for producers is
  a decision that has been made in practice but never written down.
- **Growth rate, RF cost, water cost** - all first-pass, folding into #36.
- **Does it undercut rung 3?** In-ground farming's point is that irrigation *defends* a plot against
  encroachment. A box that grows everything indoors makes that loop optional. Late arrival is the
  intended mitigation: by the time you have power, the frontier fight has already done its work.
- **Fertilizer interaction** - #71 proposes Fertilizer as a growth accelerant. If both ship, decide
  whether it also speeds hydroponics or only in-ground growth.

## 5. Build order (when it is picked up)

1. Energy layer: `EnergyHandler` on a scratch block, proven against a real consumer in `runClient`. The
   API moved to the transfer package like fluids did, so 1.21-era tutorials will be wrong.
2. The two generators.
3. The hydroponics block: water + RF + the seedling swap.
4. Screen, if the swap needs one.
5. Re-scope #43, or split the power tier into its own issue - it is no longer one PR.
