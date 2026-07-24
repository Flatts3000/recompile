# Where a Pump comes from

**Written 2026-07-23.** One page on how the Grass Spreader's Pump is sourced, what the numbers
actually are, and the decisions still open. The Pump matters more than its size suggests: it is the
only part of reclamation rung 1 with no recipe, and currently the only salvaged machine part in the
mod, so whatever gates it gates the whole chain.

## The chain today

```
mound worldgen -> Bulky Waste block (5% per core cell)
               -> pry it open -> Washing Machine (40% of finds; mattress is the other 60%)
               -> tear down at the Recompile Workbench with a prybar, 120 ticks
               -> 1 Pump + 3 scrap metal + 2 plastic scrap
```

Nothing crafts a Pump. That is deliberate: it puts rung 1 behind the teardown spine, so you have to
have built a Workbench and a prybar before you can water anything.

## Why it is a washing machine and not a "Broken Appliance"

It was the generic until 2026-07-23. The rename is not flavour, it is the core mechanic doing its
job: **teardown-as-knowledge sells one idea, that what comes out is what was inside.** "Broken
Appliance -> 1 Pump" asks the player to take our word for it. "Washing Machine -> 1 Pump" needs no
explanation, because everyone knows a washing machine pumps water out. The generic name was the one
thing in that recipe doing no work.

A washing machine over the other candidates because it is the classic curb item, because it is
*bulky* in a way a dishwasher is not, and because it has the most nameable guts (pump, drum motor,
steel drum, hoses) - a deep well for future outputs from a single find. A fridge is the worst first
pick despite the iconic silhouette: its signature part is a compressor, which reads as a different
component than a pump, and it drags refrigerant along with it.

The rename also cost nothing, because the icon was a placeholder recolour of scrap metal and had no
texgen surface at all. That was the vague name showing through: **"a broken appliance" is not a
prompt you can generate against.** It now has a real surface, drawn as a front-loader - at 16px a
top-loader is an off-white box and nothing else, while the porthole door reads as one specific
object.

## The numbers, and the surprise in them

Bulky Waste generates at ~**12 per chunk** (measured 2026-07-15, recorded in `MoundFeature`), and
40% of finds are washing machines. So:

- **~4.8 washing machines per chunk -> ~4.8 Pumps per chunk**
- A Grass Spreader needs **one**

**So a single chunk of mounds supplies roughly five spreaders.** The Pump is not scarce. It had been
called "the scarcest gate" through the whole build and the arithmetic does not support that.

What it actually gates is **timing, not quantity**: you cannot get a Pump until you have a Workbench
and a prybar, and you have to find your first machine. After that, pumps pile up faster than you will
build machines.

That may be exactly right. Reclamation is a mid-game tier, and by the time a player reaches it they
have cleared mounds. A gate that is real once and generous afterwards is a reasonable shape, but it
should be a choice rather than an accident of numbers nobody added up.

## The find table is two entries, and that is the real problem

| Find | Weight | Share | Exit |
|---|---|---|---|
| Mattress | 3 | 60% | The bed (`MattressBlock`), or cut with a scrap knife |
| Washing Machine | 2 | 40% | Teardown with the prybar -> the Pump |

That is the whole "found economy": a coin flip. And it decides the tuning answer, because **a player
needs one mattress.** After that, 60% of every Bulky Waste they pry open is clutter. So the washing
machine's competition is not another useful find, it is a dead entry.

Which rules out the obvious dial. **Do not make Pumps scarcer by lowering the washing machine's
weight** - that tilts the table further toward the thing nobody needs, and buys scarcity by making
prying *less* interesting.

The fix for both problems is the same: **more finds.** Each line added dilutes the washing machine's
share naturally, and turns prying open Bulky Waste into a real "what did I get" beat. The invariant
holds - a new find is a loot line, a teardown recipe, and one texture - but it only pays off if
things actually get added, and each one needs a teardown exit before it can enter.

## Splitting the appliance: not yet, and the trigger is clear

Dishwasher and refrigerator are the obvious siblings, and they are worth adding, but not today.
**Three appliances only pay off when they tear down into different things.** Right now the mod has
one machine part and one machine, so three finds that all yield a Pump is three textures, three lang
keys, and three loot lines for the same coin flip - a table that looks richer while being identical.

**The trigger to split is a second machine needing a second part.** Note the Motor no longer exists
(it became the Pump), so when a machine wants one back, the washing machine's drum motor is already
sitting there as the obvious source. Naming the find concretely now is what makes that split
additive instead of a rename plus three additions.

One justification to rule out in advance: **copper is not scarce.** `copper_from_scrap.json` smelts
scrap metal straight into nuggets, so the copper pipes in the drip ring are backed by the most
common material in the world. A find introduced as "the copper source" would be solving a problem
that does not exist.

## It is renewable, which is the part worth keeping

Bulky Waste is finite per mound, but **mounds regrow** (Phase 5, P1.6), and regrowth refills the
original bounds with the same block mix. So washing machines regenerate, and the Pump supply is
renewable without a recipe.

That closes what would otherwise be a real hole: a machine you can permanently lose access to. It
also ties reclamation to the quarry loop - retiring mounds shrinks the very supply the machines need,
which is exactly the quarry-vs-heal tension the pack is built on, arriving here without being
designed for.

## Open questions

1. **Should a Pump ever be craftable?** Today: no. A late recipe (copper + scrap, once the metal tier
   exists) would let a player who has healed their mounds keep building. Against it: "salvaged, never
   made" is a strong identity line, and the renewability above means the hole never opens.
2. **Should the teardown yield more than one?** One in, one out is legible. A yield of 2 would halve
   the effective gate; a chance-based extra would blur it. Leave at 1 unless the gate proves too slow.
3. **What are the next finds, and what are their exits?** This is the open design question the rest
   of the page keeps arriving at. A find needs a reason to exist - a part nothing else provides -
   and nothing currently needs a part we cannot already get. So the finds arrive *with* the machines
   that want them, not ahead of them.
4. **Each added find re-cuts this.** Every new line takes share from both existing entries, so the
   Pump rate falls whenever the found economy grows. Worth re-checking the washing machine's share
   each time rather than discovering it drifted.
5. **Does the knowledge system (P1.4) touch this?** If teardown ever gates on *studying* a recipe,
   the Pump inherits that gate. P1.4 is under review; nothing here should assume it lands.

## Recommendation

**Leave the chain and the weights alone.** The no-recipe rule, the single-Pump yield, and the
prybar-and-Workbench requirement are all doing their job, the supply is renewable through mound
regrowth so there is no dead end to design around, and worldgen stays untouched - its 5% is already
playtested and should not be retuned for one find.

The work that pays is **widening the find table**, and that lands with the machines that need the
parts.

All of these numbers are first-pass and join the pre-beta balance pass with every other weight in the
repo.
