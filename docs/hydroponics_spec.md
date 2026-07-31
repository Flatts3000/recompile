# Hydroponics + the power tier - spec (issue #43)

**Status: design locked 2026-07-31, not built.** Captured from a design session; every numbered item is an
owner call made there. Parent issue #43, which was originally scoped only to the four whole-plant
farmables and is now **substantially larger than its title** - it carries the mod's first energy tier.

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

## 2. The power tier

Arrives with this machine. **Two generators**, both new:

- A **burner generator** - burns Oily Rags / refuse, tying energy back into the existing fuel economy.
- The **Solar Panel becomes a real generator**, stopping being an inert prop.

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
