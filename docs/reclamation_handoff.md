# Reclamation - implementation handoff

**Written 2026-07-23.** The design-session record that opened the reclamation chain. Read it for the
*why* (the P2.4 -> P2.4-R economy shift); for what to build, the detail has since moved into
dedicated specs.

> **Superseded in part, same day.** After this was written, three things happened and this doc no
> longer leads:
> - **Encroachment shipped** (Phase 2.10, `RCEncroachment`) - the frontier the machines build against.
> - **The sapling lockout shipped** (P2.4-R2) - saplings are unobtainable, so the tree planter is the
>   only source of trees.
> - **Rung 1 and the multiblock framework are specced** in [`soil_spreader_spec.md`](soil_spreader_spec.md)
>   and [`multiblock_system_spec.md`](multiblock_system_spec.md), which are now the resume point for
>   implementation. Two decisions there override the "What to build" section below: **the machines are
>   multiblocks**, and **the soil spreader consumes nothing** (the running cost moved into the recipe
>   and P1.7-R supplies the ongoing pressure). Where this doc and those specs disagree, the specs win.

Design source of truth stays in the pack repo: `../trashlands/docs/design_decisions.md`
(**P2.4** original chain, **P2.4-R** economy revision, **P1.7-R** encroachment, **P2.4-R2** sapling
lockout; **P2.4-R3** for the spreader/multiblock decisions is still owed - see the specs).

---

## What changed on 2026-07-23

P2.4 item 5 said **"healing yields nothing but land."** That is superseded. It made the pack's
emotional core cost resources and return nothing, which is elegant and anti-fun.

The replacement keeps the no-bolted-on-reward spirit - healing still drops nothing - but the
**land itself becomes productive**. Grass, vegetation, trees, and animals *are* the payout.

Two consequences that matter for implementation:

1. **Garbage and healed land are two economies, not one economy minus a tax.** Garbage is the
   mineral economy (scrap, cloth, glass, metal); healed land is the biological one (wood, crops,
   food, animal products). Retiring a mound trades income rather than losing it. This closes the
   quarry-vs-heal affordability question that had been parked under P3.9.
2. **Nothing renews on its own.** Explicitly rejected: spontaneous spread or free regrowth of
   vegetation, trees, or mobs. Every green block is paid for by a machine the player built and
   feeds. A global progress metric (Planet Crafter's Terraformation Index) was also explicitly
   rejected - that is a *goal*, and goals belong in the pack, not the mod.

---

## What to build

Four machines, one per rung, in this order.

> **Running-cost model superseded (see the banner above).** This section originally had each machine
> *consume compost + clean water*. Rung 1 as specced consumes **nothing** - the cost is the one-time
> multiblock build, and P1.7-R's erosion is the ongoing pressure. Rungs 2-4 are not yet specced;
> whether they consume anything is open, but the spreader's precedent is "no running cost."

| Rung | Machine | Converts | Requires in range |
|---|---|---|---|
| 1 | Soil spreader | coarse dirt **straight to grass** (never via plain dirt), over a radius | nothing |
| 2 | Vegetation seeder | tall grass + flowers onto healed grass | rung 1 terrain |
| 3 | Nursery / tree planter | saplings and trees onto vegetated land | rung 2 terrain |
| 4 | Animal rung (**mechanism undecided**) | brings livestock to qualifying land | rung 3 terrain |

**Rungs are also a defence, not only a yield ladder (added 2026-07-23 by P1.7-R).** The junkyard
fights back: coarse earth takes back grass that borders unhealed ground. Bare rung-1 grass reverts,
rung-2 cover absorbs the hit and is stripped instead, and rung-3 trees hold the ground permanently.
So climbing the ladder is what *keeps* the land, not just what makes it pay - and a rung's job is
half yield, half armour. Shipped as Phase 2.10 (`RCEncroachment`) ahead of the machines, so rungs
1-3 are built against a frontier that already moves.

**Preconditions are terrain state, not counters or quest flags.** The nursery does nothing over
coarse dirt. This keeps the ladder physical and legible on the ground, and keeps the mod free of
pack-level progression state.

**Higher rungs additionally require a minimum contiguous qualifying area.** This is the spatial
decision: concentrate healing to reach a rung, or spread thin and stay low. Thresholds are JSON
so packs retune; defaults are the design.

The soil spreader already has a home in the design as P2.4 item 3's tier-4/5 spreader. Rungs 2-4
are new.

### Interactions with shipped systems

- **Mound retirement (Phase 5, design P1.6/P1.7) is the load-bearing tie-in.** Grass stops mound
  regrowth, so rung 1 is what retires a footprint forever. Build reclamation against the regrowth
  memory, not beside it. Note the two layers pull apart under P1.7-R: **mound** retirement is
  permanent, the **green surface** is contested. Encroachment reverts grass to plain coarse dirt,
  never back to mound bed, so a retired mound stays retired even if its surface later erodes.
- **P1.9 locked "no ambient creature spawns in the initial biome, on purpose."** That decision is
  what makes rung 4 land. Do not weaken it to make animals easier.
- **Wood-as-treasure (P2.4 item 4) survives**, reshaped: keep the nursery expensive, but gate it
  on healed area rather than on elapsed time. Wood becomes scarcity the player can attack rather
  than scarcity that is simply withheld. The first tree is still a monument - and under P1.7-R it
  is load-bearing rather than ornamental, because a ring of trees is the only thing that locks a
  border for good.

---

## Technical calls already made

**Do not implement runtime biome mutation.** Converting coarse dirt to grass is block-level and
cheap. Rewriting biome IDs in generated chunks needs client sync, is historically nasty, and on
26.1 there are no ported reference mods to crib from. It is the single most likely thing here to
eat a schedule.

**Consequence to verify early:** vanilla grass tint is biome-derived, so restored grass will take
the dead-world preset's grass color. Confirm the P0.1 preset's biome tints green acceptably
*before* building rung 1, or the whole payoff reads wrong. If it does not, fixing the preset's
biome color is far cheaper than runtime biome swapping.

**Everything data-driven.** Rung thresholds, conversion rates, machine costs, and area minimums
are JSON. A new rung should not require Java.

---

## Open questions

1. **The animal rung has no mechanism.** Rungs 1-3 have obvious shapes; "a machine that brings
   livestock" does not. It also has the best payoff moment in the chain, so it deserves real
   design rather than a placeholder. Undecided.
2. **Threshold values.** All of them. Falls under the existing pre-beta gate: every loot weight
   and recipe cost currently in the repo is a first-pass placeholder, and the roadmap already
   requires one playtest-driven balance pass across all tables together before launch. Reclamation
   numbers join that pass rather than getting tuned piecemeal.
3. **What "healed land produces" concretely means per rung.** The principle is settled (the land
   becomes productive); the specific yields are not.

---

## ModJam 2026 context

A contest decision was in flight when this was written and **was not resolved**. Recording it so
the deadline pressure in surrounding notes is legible.

CurseForge ModJam 2026, theme "Echoes of the Past". Submissions close **September 1, 2026**;
mid-contest reward selections **August 4 and 18**; winners **September 17**. Requires MC 26.1+
(this repo already targets 26.1.2), a public repo for code review, and a project not previously
published on CurseForge (this one has not been). Judging is Originality 30 / Fun 30 /
Aesthetics 30 / Community Engagement 10.

Reclamation was identified as the strongest possible entry: best demo, clearest theme fit, and
it reuses the shipped garbage world.

**The decision rule, unresolved:** enter if and only if the reclamation chain was going to be
built in this window anyway. If yes, the marginal cost is small (art pass, project page, the
balance pass the roadmap already requires) and distribution is worth more than the prize. If the
contest is what is *causing* the build, do not enter - expected value is roughly $450 against six
weeks. Capacity is the real constraint, not design.

**If entering, three divergences from locked pack design were proposed. All are jam-only and must
be flagged as such, never silently absorbed:** heal payouts pulled early enough for a ten-minute
session, the quarry-vs-heal cost made legible on the first spread rather than at hour four, and
the first animal reachable inside ten minutes of play. Contorting the mod to win a contest at the
cost of the pack is a negative return.

---

## Doc drift to clean up

These still assert the superseded version and will mislead:

- `../trashlands/docs/concept.md` lines 90-91 - frames reclamation as pure income sacrifice with
  the affordability question open. It is closed.
- `../trashlands/CLAUDE.md` - "reclamation costs you income."
- `README.md` (this repo) - no reclamation framing yet; the mod is still described teardown-first.

Already updated: `../trashlands/docs/design_decisions.md` (P2.4-R plus bookmark),
`../trashlands/docs/feature_matrix.md`, and `docs/roadmap.md` Phase 6.
