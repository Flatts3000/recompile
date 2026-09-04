# Mod Jam roadmap - CurseForge ModJam 2026 ("Echoes of the Past")

**This is separate from `roadmap.md`.** That file is the full multi-phase build order. This file is the
**deadline-driven plan to submit Recompile to ModJam 2026 and place well** - a different shape (fixed
date, judging criteria, presentation) that ends on September 1, 2026.

> **THE ENTRY WAS SUBMITTED** (owner, 2026-09-04). The deadline of 2026-09-01 has passed and
> Recompile went in, so everything below is the record of the plan that got it there rather
> than a live one; read the future tense accordingly. Development did not stop at the
> deadline - v0.18.0 released on 2026-09-04, after submission.
>
> **The result is not recorded here**, because it is not known to this repository. Whoever
> learns how it placed should write it in this banner.

## The jam concept: bring a dead world back to life

**The entry's primary theme is terraforming / reclamation.** You land in a grey, dead garbage world and
restore it, one tier at a time:

> **Grass -> Vegetation -> Farming -> Trees -> Animals**

The salvage loop (tearing down garbage for materials) is the **means**: you scavenge the dump to build the
machines that heal the land. Reclamation is the **story** and the star.

**Why this wins the jam:** it is the most *visual* concept we have - a grey dump turning green, flowering,
farmed, forested, then full of animals is a timelapse that basically is the trailer, and the community vote
turns on visuals. It also reuses shipped systems (Grass Spreader, encroachment, the multiblock + worldgen
frameworks), so effort goes into new tiers, not new plumbing.

**Theme fit ("Echoes of the Past"):** the world was alive once; it died into a dump; you bring back the
**echo of that lost living world**. Each tier restores more of the past. This is the framing - the past is
the green world, and reclamation recovers it. (Do not spoil `the_twist.md`; the framing gestures at a lost
living past without stating the hidden hook.)

## The contest (facts)

- **Entry:** Recompile is the submission (a Java Mod).
- **Theme:** "Echoes of the Past" - ancient themes, lost artifacts, restoring what was.
- **Target:** Minecraft 26.1+ (we are on 26.1.2 - already compliant).
- **Timeline:** submissions open Jul 21 -> **close Sept 1** -> vote Sept 8-14 -> winners Sept 17. Mid-contest
  author reward rounds: **Aug 4** and **Aug 18** (10 authors each) - real interim targets.
- **Category / prize:** Java Mods, $1,000 x 15 grand winners of a $50,000 pool; $2,000 Community Favorite.
- **Submit:** upload to CurseForge under the ModJam 2026 category + entry form + a GitHub link for judging.
  Must be 18+ to claim.

## Eligibility - the one hard gate (verify, do not assume)

The ToS bar is **prior CurseForge publication**, not code age: *"All submitted projects must be new (not
published on CurseForge prior to the contest Start Date)."* Reusing our own frameworks is not restricted.

- [x] **Not previously published.** Recompile was unpublished (local repo, never on CurseForge) up to the
  contest Start Date, so the "new project" gate is met.
- [x] **Published *as* the entry, in the window.** v0.1.0 uploaded to CurseForge project 1625740 (alpha, MC
  26.1.2 / NeoForge / Java 25) on 2026-07-27, with a matching GitHub prerelease (v0.1.0). The ModJam entry
  is submitted.
- [x] **Re-read the 2026 ToS** for the start date, "new project" wording, and the judging split (confirmed
  before submission).
- [x] **No AI-generated project avatar or gallery images.** Announced 2026-07-29 in the ModJam Discord as
  one of the rules entries were failing on, alongside the three above. **We were failing it.** The avatar
  was a pixelated AI render - `branding/compose_logo.py` said so in its own docstring - and the fix landed
  in #144: the artwork is drawn from geometry now, so the claim survives someone checking. The
  upload itself is manual, because the upload API handles project *files* and not project *images*:
  `branding/logo.png` was uploaded by hand in the project settings and **verified live on 2026-08-04**
  (the avatar on project 1625740 is the sprout-and-wordmark mark; the old one read white `REC`).
  - The **gallery was never at risk** - all eleven images are in-game screenshots, and a screenshot is not
    an AI-generated image.
  - The rule names avatars and gallery images, **not in-game assets**. This mod's textures are generated
    (texgen), which the rule as written does not reach. Comply with what is stated; do not volunteer a
    broader reading. There is no remedy available if a judge takes one, which is worth knowing rather than
    worrying about.

## Judging - what we optimize for

Confirmed from the 2025 ToS: **Originality = 30%**; the rest (fun, polish, theme, presentation) is per the
2026 ToS - verify the split, plan for all. Our thesis:

1. **Theme + presentation:** the reclamation timelapse is the pitch. A grey-to-green trailer + gallery is
   where this concept scores hardest (theme adherence + the community vote).
2. **Originality:** "reverse terraforming / heal-the-wasteland as machine-built tiers" is a fresh framing;
   the encroachment (the world fights back) is a genuinely novel twist that stops it being a chore.
3. **Fun + polish:** each tier must be a satisfying, legible beat a judge feels in a 10-15 min playthrough.

## The arc - build state per tier

| Tier | What it is | Status | Jam work |
|---|---|---|---|
| **1. Grass** | Grass Spreader (reclamation rung 1) heals dead ground to grass; encroachment fights back | **SHIPPED** | polish + make it the on-ramp |
| **2. Vegetation** | Fertilizer scatters weeds/wildflowers on reclaimed grass + mushrooms on mycelium (rung 2; plants are `frontier_cover`) | **SHIPPED** | done - fancy-bonemeal scatter, 3s ripple, weeds-only flowers |
| **3. Farming** | farmland from `Fertilizer + dirt` (no hoe in this world); the six seed-crops, seeds via compost volunteers; Grass Spreader (and rain) irrigate & defend a plot | **SHIPPED** (in-ground path) | done - compost recipe + hoe-till lockout; Unknown Seedling volunteers; spreader irrigation. Whole-plant farmables (cane/bamboo/cactus/berries) held for a later **hydroponics** option |
| **4. Trees** | the Tree Nursery (2x2x1 multiblock) raises a chosen vanilla sapling from water + Fertilizer + an Unknown Seedling; saplings are stripped from every loot roll, so the nursery is the forest source (a wandering trader sells them for emeralds since #227, far later than this tier) | **SHIPPED** (v0.2.0, #40) | done - bespoke screen, slow cook, glows while working, 9 species (pale oak added #230) |
| **5. Animals** | diet baits (herbivore/carnivore/omnivore, each with a Rich grade) placed on grass settle an allowlisted mob when no player is near; apple-gated so it waits on trees | **SHIPPED** (v0.2.0, #42) | done - per-diet look, spawn weights, cluster + player gates, JEI/Jade held-reasons |

Each new tier reuses the shipped multiblock framework; the salvage/teardown loop (shipped) feeds their
recipes. **Jam depth, not full-phase depth** - every *tier* is one satisfying beat, tuned for a short demo, not
the exhaustive version `roadmap.md` will eventually build. That is a scoping rule for the reclamation
ladder, not a cap on the entry.

**Build status (2026-07-27): all five reclamation tiers are SHIPPED (v0.2.0).** The ladder plays end to
end, grey to living.

**That is the ladder finished, not the entry finished.** An earlier version of this paragraph said "the
build is done, the pitch is what is left", and that was wrong: **jam content is not done until the jam is
over** (owner, 2026-08-01). There is no scope ceiling. Presentation (trailer, gallery, CurseForge page)
and the balance pass (#36) are the *floor* - the things that must exist by Sept 1 - and everything above
that floor is worth building for as long as the clock allows.

The tier table below still reads as though the arc were the whole entry. It is not; it is the part that
was planned in July. Content added after that date is judged on the same criteria and counts the same.

## Milestones (work backward from Sept 1)

**M1 - by Aug 4 (reward round 1): the first two tiers + a demo.**
- **Grass** polished as the on-ramp; **Vegetation** built (flowers/ferns spreading on healed ground).
- The grey -> green -> flowering beat is playable and reads on camera.
- Draft CurseForge page (name, pitch, 3-4 screenshots) + first GIFs; public GitHub repo. Submittable-if-forced.

**M2 - by Aug 18 (reward round 2): the middle tiers + balance.**
- **Farming** and **Trees** built; the arc plays Grass -> Vegetation -> Farming -> Trees.
- **Balance pass** on placeholder drop weights + recipe costs (the jam is the beta); pace the 15-min demo.
- First-hour UX: world-preset selection, starting-area legibility, no dead ends. Trailer cut (60-90s) drafted.

**M3 - by Sept 1 (submit): the climax + polish + ship.**
- **Animals** - the emotional payoff: life returns to the healed world.
- Bug/crash pass; `build` + `runGameTestServer` clean; a full `runClient` grey-to-living playthrough.
- Finalize the CurseForge page (icon, gallery, description with theme framing, tags, MC 26.1+, license) and
  the trailer. Publish under ModJam 2026. Submit the form. Confirm 18+. **Buffer 2-3 days - do not ship at the deadline.**

## Work buckets (tasks under the milestones)

- **A. The five tiers** (above) - each: a focused design pass (jam-scoped), build on the multiblock
  framework, GameTests, JEI/Jade, and a texgen art pass. Tiers 2-5 are the bulk of the build.
- **B. Theme + framing** - copy/lore/naming toward "echoes of the past = the lost green world"; keep the
  twist unspoiled; a creative-tab / summary / advancement pass.
- **C. Polish** - balance pass; bug/QoL/crash pass; textures finalized via texgen; JEI/Jade completeness;
  first-hour worldgen + UX; `RegistryCompletenessTests` green.
- **D. Presentation** - the grey-to-green **trailer** (60-90s) + GIF set, CurseForge page (icon, gallery,
  description, tags), public GitHub repo + README + license, a summary that sells the timelapse.
- **E. Submission logistics** - publish to CurseForge (only within the window), entry form, category, repo
  link, eligibility (18+, not previously published).

## Open decisions for Jason

1. **Scope ambition** - **LOCKED: all five tiers** at jam depth (Animals is the climax + the trailer's
   closing shot). Decided 2026-07-25.
2. **Theme depth** - light framing (copy + a few names) vs a stronger re-skin leaning "lost green past"
   (more art, better theme score).
3. **Trailer** - who records + edits the grey-to-green timelapse (the single highest-leverage asset for the
   vote)?
4. **Encroachment in the demo** - feature the "world fights back" tension (adds novelty + stakes) or keep
   the demo purely constructive so a judge is not fighting decay in 15 minutes?

## Verification / definition of done

- 2026 ToS re-read for the judging split; MC 26.1+ confirmed (Recompile is unpublished, so eligibility is met).
- `build` + `runGameTestServer` green; a full `runClient` grey-to-living first-hour playthrough, no blockers.
- CurseForge page live under ModJam 2026, GitHub repo linked, entry form submitted, before Sept 1.
