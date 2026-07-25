# Mod Jam roadmap - CurseForge ModJam 2026 ("Echoes of the Past")

**This is separate from `roadmap.md`.** That file is the full multi-phase build order for the mod.
This file is the **deadline-driven plan to submit Recompile to ModJam 2026 and place well** - a
different shape (fixed date, judging criteria, presentation) that ends on September 1, 2026.

## The contest (facts)

- **Entry:** Recompile is the submission (a Java Mod).
- **Theme:** "Echoes of the Past" - ancient themes, lost artifacts, prehistoric concepts.
- **Target:** Minecraft 26.1+ (we are on 26.1.2 - already compliant).
- **Timeline:** submissions open Jul 21 -> **close Sept 1** -> community vote Sept 8-14 -> winners Sept 17.
  Mid-contest author reward rounds: **Aug 4** and **Aug 18** (10 authors each) - real interim targets.
- **Category / prize:** Java Mods, $1,000 x 15 grand winners of a $50,000 pool; $2,000 Community Favorite.
- **Submit:** upload to CurseForge under the ModJam 2026 category + entry form + a GitHub link (public or
  shared private repo) for judging. Must be 18+ to claim.

## Eligibility - the one hard gate (verify, do not assume)

The ToS bar is **prior CurseForge publication**, not code age: *"All submitted projects must be new (not
published on CurseForge prior to the contest Start Date)."* Reusing our own frameworks/code is not
restricted. So Recompile qualifies **only if it is not already on CurseForge**.

- [ ] **Confirm Recompile has never been published on CurseForge.** If it has, it is disqualified - stop.
- [ ] **Do not publish it to CurseForge before the start date.** Publish it *as* the entry, during the window.
- [ ] **Re-read the 2026 ToS** (the clauses above are from the 2025 ToS) to confirm the start date, the
  exact "new project" wording, and the full judging breakdown before committing.

## Judging - what we optimize for

Confirmed: **Originality = 30%** (creativity / novel mechanics). The rest (fun, polish, theme adherence,
presentation) is per the 2026 ToS - verify the split, but plan for all four. Our competitive thesis:

1. **Originality:** lead with teardown-as-knowledge - a mechanic few mods have, and our namesake. This is
   the highest-leverage build (see the scope decision below).
2. **Theme adherence:** deliberately frame the mod as *echoes of the past* (below), or we lose points a
   garbage aesthetic would otherwise cost us.
3. **Polish + fun:** a clean, bug-free first hour that a judge can actually play in 10-15 minutes.
4. **Presentation:** the CurseForge page + a short trailer decide the community vote, and first impressions
   for judges.

## The theme bridge ("Echoes of the Past")

Recompile is already about *recovering the lost history embedded in discarded objects* - an item remembers
what it was, and teardown recovers that. That is an echo of the past. Lean into it:

- **Framing / copy:** the world is the leftover echo of a civilization that is gone; what you dig up are
  its artifacts; recompiling them recovers knowledge that was lost. (Do **not** spoil `the_twist.md` - the
  framing gestures at a lost past without stating the hidden hook.)
- **Naming pass:** align a few surfaces to the theme (creative tab name, the mod summary, a couple of item
  names / advancement titles) without renaming the mod or churning the whole catalog.
- Honest risk: judges may still read it as modern-junk, not ancient. The teardown-as-knowledge headline is
  what makes the theme land - the *mechanic* is the echo, not the textures.

## Scope decision (the fork this roadmap turns on)

**A - Headline build (recommended):** build a **jam-scoped teardown-as-knowledge** (Phase 3) as the entry's
originality centerpiece. It is the mod's defining, unbuilt mechanic, and it *is* the theme. Its open design
question (knowledge-gating vs function-recovery) gets a jam-pragmatic answer rather than the full P1.4
debate. Higher effort, highest payoff on the 30% criterion.

**B - Polish-and-present:** submit the already-substantial mod (Phases 0-2.12) with theme framing + heavy
polish + presentation, and no new headline mechanic. Lower risk, lower originality ceiling.

The milestones below assume **A**. If you pick **B**, drop the "headline feature" bucket and pull all its
time into polish + presentation.

## Milestones (work backward from Sept 1)

**M1 - by Aug 4 (reward round 1): a playable, on-theme demo build.**
- Headline mechanic playable end to end (even if rough): dig up an artifact -> study/recompile -> recover a
  lost recipe. (Scope A.)
- Theme framing pass on the visible surfaces (tab, summary, first advancements).
- Draft CurseForge page (name, one-paragraph pitch, 3-4 screenshots) + first GIFs. Public GitHub repo.
- Goal: be submittable-if-we-had-to and eligible for the first author-reward round.

**M2 - by Aug 18 (reward round 2): feature-complete jam scope + balance.**
- Headline mechanic complete and tuned; its JEI/Jade integration done.
- **Balance pass** on the first-pass placeholder drop weights + recipe costs (flagged pre-beta; the jam is
  the beta). A judge's 15 minutes must feel paced, not swingy.
- First-hour UX pass: world creation (the preset must be selected), starting-area legibility, no dead ends.
- Trailer cut (60-90s) drafted; screenshot set finalized.

**M3 - by Sept 1 (submit): polish, presentation, ship.**
- Bug/crash pass; run `build` + `runGameTestServer` clean; a full `runClient` playthrough.
- Finalize the CurseForge page (logo/icon, description with theme framing, tags, MC 26.1+, license) and the
  trailer. Publish the mod to CurseForge under the ModJam 2026 category.
- Submit the entry form (CurseForge link + GitHub link + contact). Confirm 18+.
- Buffer: leave 2-3 days before Sept 1 - do not submit at the deadline.

## Work buckets (tasks under the milestones)

- **A. Theme alignment:** framing/copy/naming toward "echoes of the past"; keep the twist unspoiled.
- **B. Headline feature (Scope A):** jam-scoped teardown-as-knowledge - the excavate -> study -> recover
  loop, its data (extend the shipped `recompile:teardown` `teaches` field), and the crafting gate/reward.
  A focused design pass first (jam-pragmatic answer to the P1.4 open question), then build + test + JEI/Jade.
- **C. Polish:** balance pass; bug/QoL/crash pass; textures finalized via texgen; JEI/Jade completeness;
  first-hour worldgen + UX; `RegistryCompletenessTests` green.
- **D. Presentation:** CurseForge page (icon, gallery, description, tags), a 60-90s trailer + GIFs, public
  GitHub repo + README + license, a clean mod summary that sells the mechanic.
- **E. Submission logistics:** publish to CurseForge (only within the window), entry form, category, repo
  link, eligibility (18+, not previously published).

## Open decisions for Jason

1. **Scope A vs B** - build the teardown-as-knowledge headline, or polish-and-present what exists?
2. **Theme depth** - light framing (copy + a few names) vs a stronger re-skin (textures/naming leaning
   ancient). More re-skin = better theme score, more art time.
3. **Trailer** - do we cut one (recommended for the community vote), and who records/edits?
4. **The P1.4 answer for the jam** - if Scope A, knowledge-gating vs function-recovery for the jam build.

## Verification / definition of done

- 2026 ToS re-read; Recompile confirmed never published on CurseForge; MC 26.1+ confirmed.
- `build` + `runGameTestServer` green; a full `runClient` first-hour playthrough with no blockers.
- CurseForge page live under ModJam 2026, GitHub repo linked, entry form submitted, before Sept 1.
