# Structural analogs

**What this is for.** Stories have the hero's journey. Games have spines too, and Recompile does not
have one picked. This file lists the candidates so the choice can be made against evidence rather
than re-argued from taste (owner, 2026-09-05: *"I feel like we have a lot of distinct features but
it's not a 'game' yet. Theme is the only thing holding these features together."*).

**It does not make the ruling.** It is the input to one. The decision itself belongs in
`../trashlands/docs/design_decisions.md`, because that is where feature design lives.

**The question this is downstream of has been open since 2026-07-15** and is recorded in
`../trashlands/docs/concept.md` under Open questions, tagged "the big one":

> Is sifting the price, or is sifting the game?

Nothing ever answered it. Every feature shipped since has answered it *implicitly* with "the price" -
the Sorting Tarp, the Trommel, the Garbage Vacuum, the Scrap Hauler, the market are five successive
escape hatches from the verb the mod is named after. That was never decided; it accumulated. Picking a
spine is how it gets decided on purpose.

---

## The six families

Each is: the spine in one line, exemplars, what holds features together under it, what the payoff is,
and what Recompile would have to become to fit.

### 1. The tech ladder

**Spine:** every tier exists to build the tier above it. The recipe graph *is* the game.
**Exemplars:** Factorio, Satisfactory, Techtonica, Foundry, Dyson Sphere Program.
**Cohesion:** nothing is side content, because everything is an input to something else.
**Payoff:** scale and throughput, terminating in a capstone megaproject (the rocket, the space
elevator).
**Recompile would need:** a deeper graph and a terminal goal big enough to justify climbing it.

**The honest problem.** This is the family Recompile has drifted into, and it is the worst fit of the
six. The graph is shallow, and there is no capstone: automating garbage collection currently ends in
more garbage collection. A ladder with nothing at the top reads as a chore list, which is close to the
symptom being diagnosed.

### 2. World-state transformation

**Spine:** one global meter you push; the world visibly restages as it rises.
**Exemplars:** The Planet Crafter, Terra Nil, Captain of Industry, Timberborn.
**Cohesion:** every feature feeds one number, so nothing can be orphaned by construction.
**Payoff:** the place itself, spatially and permanently.
**Recompile would need:** reclamation promoted from a per-block material source to a staged global
state.

**This is the stated vision** (`concept.md`: *"Rebuild - and eventually heal - a ruined world from its
own garbage"*, and *"every mound you kill and grass over is land visibly, permanently healed. Progress
shows on the ground"*). It is also the arc the build has served least: the reclamation ladder shipped
in Phase 2.12 through 2.17 (2026-07-24 to 07-27) and has not been touched since, while six weeks of
work went into acquiring materials faster.

**Two exemplars are worth copying from directly.** Planet Crafter runs a single Terraformation Index;
crossing thresholds advances a *stage*, and each stage unlocks new blueprints - so one number gates all
content and no system can be orphaned. Terra Nil runs three explicit phases: restore greenery, then
increase biodiversity, then **dismantle and recycle every machine you built and leave nothing behind**.
That third phase is the interesting one for this mod, because it makes the industry temporary and the
land the point, which is the opposite of how Recompile's machines currently work.

### 3. Knowledge recovery

**Spine:** you progress by understanding rather than by building. Finding a thing teaches a thing.
**Exemplars:** Subnautica, Astroneer, Grounded, Outer Wilds.
**Cohesion:** the world is a library and every object is a page.
**Payoff:** comprehension, and the access it buys.
**Recompile would need:** almost nothing. It already built this.

**Teardown-as-knowledge is Subnautica's scanner with a workbench**, and it is the most distinctive
thing in the mod. It is currently *not* the spine: it is one system among nine. Outer Wilds is the
extreme end of this family, where knowledge is the only gate that exists and the player character never
gains a single stat.

### 4. The filter

**Spine:** the game is a discrimination task. You are the eye.
**Exemplars:** Papers Please, Strange Horticulture, Dredge, Return of the Obra Dinn.
**Cohesion:** every system feeds one judgment.
**Payoff:** being right.
**Recompile would need:** things that are not worth taking, and a real cost to taking them.

**This is already written down as principle #1** in `concept.md` ("You are the filter" - ninety-nine
rejections, then *wait*), under a heading that marks all four principles **aspirational**: *"the build
does not honour them yet, and the first one it fails is the one that matters most."* The same passage
predicts the failure mode in as many words: *"if every object is worth grabbing there is no decision
left and sifting degrades into hoovering."* The rule that causes it - everything found breaks down into
materials - is still live, and a vacuum shipped afterwards.

### 5. Restoration, before and after

**Spine:** a bounded space is wrong; you make it right; the pleasure is completing *this* site.
**Exemplars:** Powerwash Simulator, Unpacking, House Flipper, A Little to the Left.
**Cohesion:** area-scoped. Each site is a level with its own end.
**Payoff:** the visible before and after.
**Recompile would need:** bounded sites.

**An endless plain structurally cannot do this**, because there is no "after". The structures are the
part of the mod that already has this shape - a sewer, an aquarium, a tire dump and a cooling tower are
each a bounded place with a state you can finish changing - which is worth noting given the owner's
read that the structures are one of the three things pulling people in.

### 6. Holding ground

**Spine:** pressure escalates, you build to hold, and loss is possible.
**Exemplars:** Factorio's biters, They Are Billions, Timberborn's droughts, Rimworld.
**Cohesion:** everything is either defence or the economy that funds defence.
**Payoff:** survival, and the tension of nearly not surviving.
**Recompile would need:** a threat that can actually take something from you.

**Encroachment is this family with the teeth pulled.** It is local, slow, and permanently defeated by
trees, and it cannot destroy anything a player would mourn. `RCEncroachment`'s one blockstate rule
(wet farmland holds, dry farmland is taken) is the only place it touches player investment at all, and
the loss is bounded to the plot and the growth, never the seed.

---

## The closest single analog: Hardspace: Shipbreaker

Worth calling out separately, because it is the one commercial game whose spine is **disassembly
itself**, and it stacks four of the six families above without feeling like four games.

- You buy a wreck and take it apart. **The disassembly is the game, not the price** - which is the
  open question above, answered the other way.
- Salvage sorts into three destinations (keep, melt, process), so **every cut is a judgment call**
  rather than a hoover. That is family 4 built into the core verb.
- **One ship is one bounded site** with a beginning and an end, which is family 5, on an endless supply
  of procedurally varied levels.
- **You learn ship layouts yourself**; the game deliberately does not teach them, so competence is real
  knowledge rather than an unlocked stat. Family 3.
- **Debt is the pressure**, escalating, and you can destroy yourself and the salvage by cutting wrong.
  Family 6, with stakes.

It is the strongest evidence that the combination Recompile keeps gesturing at is viable, and that the
thing to protect is the verb.

---

## The pattern that decides this

**Every shipped game on this page runs two of these families, not five.** Factorio is 1 + 6. Planet
Crafter is 2 + 1. Terra Nil is 2 + 5. Subnautica is 3 + 5. Hardspace is the outlier at four, and it
gets away with it because all four are expressed through a single verb.

**Recompile currently runs five at roughly twenty percent each:**

| Family | State in the build |
|---|---|
| 1 Tech ladder | Shallow graph, no capstone. Where recent work went. |
| 2 Transformation | The stated vision. Shipped in July, untouched since. |
| 3 Knowledge recovery | Built, distinctive, and not load-bearing. |
| 4 The filter | Designed as principle #1, then automated away. |
| 5 Restoration | Accidental, and only inside the structures. |
| 6 Holding ground | Built, and defanged. |

That table is the precise version of the owner's observation. It is not that theme is the only
connective tissue by accident. It is that **five candidate spines exist and none was ever promoted**,
so theme is the only thing left doing the work.

**What a ruling looks like:** name one primary and one secondary from the six, then audit every shipped
system against them. Anything that serves neither is either rescoped or accepted as explicitly
decorative. The balance pass (#36) should wait for this, because it would otherwise tune the throughput
of a game whose problem is that throughput is all it has.

---

## Sources

Researched 2026-09-05 rather than recalled, for the three that carry specific structural claims:

- [Terra Nil on Steam](https://store.steampowered.com/app/1593030/Terra_Nil/) and
  [PC Gamer's review](https://www.pcgamer.com/terra-nil-review/) - the reverse city builder framing and
  the three phases ending in dismantling your own machinery.
- [Planet Crafter Terraformation Stages](https://planet-crafter.fandom.com/wiki/Terraformation_Stages)
  and [Terraformation Index](https://planet-crafter.fandom.com/wiki/Terraformation_Index) - one index,
  staged thresholds, each stage unlocking blueprints.
- [Hardspace: Shipbreaker on TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/HardspaceShipbreaker)
  and [PC Gamer's dismantling guide](https://www.pcgamer.com/heres-how-to-dismantle-space-salvage-in-hardspace-shipbreaker/)
  - the three salvage destinations, the debt pressure, and learning ship layouts unaided.
- [Captain of Industry on Steam](https://store.steampowered.com/app/1594320/) - Factorio plus Anno plus
  terraforming, with waste and pollution as first-class systems.
