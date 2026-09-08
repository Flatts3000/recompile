# Feedforward

**Status:** current, researched and written 2026-09-07. **Mirrored from Flatts's Things**, where the
canonical copy lives at `docs/feedforward.md`; the research is repo-agnostic and the two should be
kept in step by hand. Sourced from the primary HCI literature rather than from summaries; every rule
below is attributed, and the claims that could NOT be sourced are listed at the bottom rather than
quietly dropped.

**Nothing in recompile has been audited against this yet**, and the worked examples near the end are
Flatts's Things' surfaces rather than this mod's. Do not read them as findings about recompile.

## What this is and when to read it

**Feedforward is information given BEFORE an action about what that action will do.** Feedback is
after. This document exists because these mods keep arriving at feedforward problems without the
word for them, and solving a named problem is cheaper than rediscovering it.

Read it when designing anything where the player commits to something they cannot fully see in
advance: placing a block with more than one orientation, clicking a slot that might refuse the
item, pressing a key whose effect depends on a config they cannot see, or right-clicking a block
that may or may not have an interaction.

Do NOT read it as a mandate to add hints everywhere. The literature's own strongest empirical
result is about making feedforward CHEAP and OPTIONAL, not omnipresent.

## The distinction, which is the part everyone gets wrong

Three things sit in front of an action and are routinely confused. All three are cognitive
affordances in Hartson's sense; they differ in what they reveal and when.

| | Reveals | When | Answers |
| --- | --- | --- | --- |
| **Signifier** (Norman's later term for perceived affordance) | that an action is possible, and how to perform it | before | "What can I do here?" |
| **Feedforward** | the system function that action invokes | before | "What will happen if I do it?" |
| **Feedback** | the result | during / after | "What just happened?" |

Vermeulen et al. (CHI 2013), verbatim: *"While perceived affordances reveal the physical affordance,
which tells users that there is a physical action available and how to perform it, feedforward
reveals the functional affordance, which tells users what will happen when they perform that
action."*

Djajadiningrat et al. (DIS 2002), who introduced the term to HCI: *"Inviting the appropriate action
is a prerequisite for feedforward but it is not sufficient. The product also needs to communicate
what the user can expect."* Their thesis is that usability lies in communicating **the purpose of an
action**, not the action.

**Norman's gulfs.** Feedback bridges the Gulf of Evaluation. Signifiers and feedforward both bridge
the Gulf of Execution, at different stages: the signifier serves specifying an action, feedforward
serves forming the intention.

**Why this matters more in a mod than in a doorknob.** Vermeulen quotes Hartson: in Norman's world
of physical objects "a purpose for a physical affordance is always implied." Once you leave door
hardware, the same physical affordance (a button, a slot, a right-click) gets reused for arbitrary
functions, so purpose has to be stated. Their conclusion: **"The more complex a system or
interaction context gets, the larger the need will be for elaborate feedforward."** A mod is
definitionally an increase in that complexity, layered on a game whose conventions the player has
already learned.

**One boundary genuinely blurs.** Feedback can become feedforward for the next action. Wensveen
calls this "inherent traces of action": a flicked switch's new position reports what you did AND
promises that flicking again reverses it. Marking menus exploit it deliberately, flipping a label to
its inverse function after invocation. **A reversible state indicator is doing both jobs; design it
for both readings on purpose.**

## The rules

Each is attributed. Where a rule is inference rather than citation, it says so.

### On what to say

1. **State the purpose, not just the possibility.** Djajadiningrat et al. 2002. An outline showing
   that a slot exists is a signifier; showing what it accepts is feedforward.
2. **Pair the signifier with the feedforward.** Vermeulen's worked example is the iPhone lock
   screen: the slider and arrow are the signifier, "slide to unlock" is the feedforward.
3. **Labels and icons are not a design failure.** Vermeulen explicitly rehabilitates them. Norman
   only said *"When simple things need pictures, labels, or instructions, the design has failed"* -
   the load-bearing qualifier is "simple things."
4. **Calibrate detail to stakes.** Vermeulen: feedforward is usually low to average detail, but
   "there might be situations in which feedforward can be provided with lots of details, for example
   to reassure the user when they have to trust the system."

### On when to show it

5. **Trigger on hesitation, around 250 ms.** OctoPocus appears "after a 'press and wait gesture' of
   approximately 250ms", a threshold inherited from marking menus. The rationale is the most
   transferable thing in this literature: *"Because OctoPocus appears only if the user hesitates,
   experts can execute commands very efficiently, but can slow down at any time to see which
   gestures and commands remain."*
6. **Make it cheap to summon or it will not be used.** OctoPocus participants called it "less risky"
   than a help menu because hesitating cost nothing, "without having to decide to move the mouse to
   go to the Help menu." They chose it 40 percent of the time against 32 percent for the menu.
   **Feedforward that requires a decision and a navigation is feedforward nobody reaches for.**
7. **Do not tax the expert.** Implicit across marking menus and OctoPocus, and the reason rule 5
   exists at all: unconditional feedforward charges the people who did not need it.

### On where and how

8. **Anchor it at the locus of the action.** OctoPocus centres its guide on the cursor. Frogger's
   "location" coupling demands the same. In practice: at the block, not in a HUD corner.
9. **Solid means committed, translucent means not yet real.** OctoPocus renders the already-drawn
   prefix solid and the remainder translucent. This is the cleanest available convention for
   separating a preview from actual state, and here it is free: the game's own solid rendering
   already means "this exists."
10. **Update as the action proceeds.** OctoPocus: "both feedforward and feedback are continuously
    updated as the gesture progresses." Vermeulen notes this is easiest to achieve in software.
11. **Or update at checkpoints if continuous would be noise.** Yu et al. (CHI 2024) separate
    discrete (advances at critical moments, better emphasis) from continuous (most responsive, but
    "trainees need to visually focus on the constantly updating feedforward, which may lead to a
    higher dependency on this guidance").
12. **Encode confidence in a continuous variable, not a binary.** OctoPocus maps each option's
    remaining error budget onto path thickness, so options visibly thin out before vanishing. A
    binary "best match" highlight throws that information away.
13. **Give a free way back out.** OctoPocus resets if the user returns to the start without
    releasing. Muresan et al. (TOCHI 2023) make it structural, organising VR guidance around three
    stages: starting the feedforward, previewing actions and outcomes, and **returning the world to
    its state before the feedforward**. If a preview mutates anything, restoring it is part of the
    feature rather than cleanup.
14. **Consider non-visual channels.** Vermeulen flags near-total reliance on the visual as an
    unexplored gap. Caveat from the same page: tactile suits exploratory actions, because it cannot
    be perceived passively. In Minecraft terms, sound is the underused channel.

## The two taxonomies, which are not the same list

Keeping these apart matters; they get conflated constantly, including in the first draft of this
document.

### Wensveen's three types (Interaction Frogger, DIS 2004)

Applies symmetrically to feedforward and feedback.

- **Inherent** - what action is possible and how to carry it out, read off the thing's own form.
  Appeals to perceptual-motor skill. Maps to Hartson's physical affordance.
- **Augmented** - information from an ADDITIONAL source about the action possibilities or their
  purpose. Labels, icons, messages, sounds. Appeals to cognitive skill.
- **Functional** - information about the general purpose of the whole product. Wensveen's strategy
  is Norman's visibility: make the working parts visible, as a vending machine shows the sweets.

Wensveen's own caveat, relayed by Vermeulen: the split "was mainly made for analysis purposes."

### Vermeulen et al.'s four classes (CHI 2013)

- **Hidden** - the action-to-function coupling exists but is not conveyed. A button with no label.
- **False** - conveys incorrect information about what the action does. See the failure section.
- **Sequential** - made available at points in time, or updated during the action. OctoPocus,
  marking menus. Contrasted with static feedforward, which is a fixed label.
- **Nested** - a hierarchy, with functional feedforward at the root and finer layers beneath.

## Frogger's six couplings, as an audit

For any action and the information the system returns, ask whether each is coupled:

| Coupling | Question |
| --- | --- |
| **Time** | do the reaction and the action coincide in time? |
| **Location** | do they occur in the same place? |
| **Direction** | is the reaction's direction the same as the action's? |
| **Dynamics** | are position, speed and acceleration of the reaction coupled to the action's? |
| **Modality** | are the sensory modalities in harmony, and rich across senses? |
| **Expression** | does the reaction's expression represent the expression in the action? |

**The point is not to maximise all six.** A TV remote deliberately breaks location, which is what
makes it remote, and breaks time, because the set warms up. The audit's value is knowing which
couplings you are breaking and being able to say why.

## Failure modes

### False feedforward is the dangerous one

Vermeulen et al.'s definition: *"Feedforward can be false when it conveys incorrect information about
what system function the action performs."* Their example places it in serious company:

> "A simple example of a false feedforward in a graphical user interface is a button with an
> incorrect label (an effective technique which is often employed by malicious software to trick the
> user in invoking certain destructive actions)."

**The asymmetry against hidden feedforward is the practical rule.** Hidden feedforward leaves the
player uncertain and cautious. False feedforward makes them confidently wrong and gets them to act.
The literature files an inaccurate preview alongside deceptive design regardless of whether the
inaccuracy was intended.

**So: when the true outcome cannot be computed reliably, show NOTHING.** Silence is the safe
failure; a guess is not. This is the single most important line in this document for anything
resembling a preview.

### The rest

- **Visual clutter.** Bau and Mackay name this as OctoPocus's own principal weakness: "the level of
  visual complexity that users face if they enter novice mode without having drawn any portion of a
  gesture." Their mitigations (translucent tails, fast elimination) are treated as partial, not
  solved.
- **Dependency through over-persistence.** Yu et al.: continuous guidance can produce "a higher
  dependency on this guidance." Their counter-design is deliberate removal once the skill should
  have transferred.
- **Design the option set, not just the display.** Bau and Mackay: "we can create gesture sets that
  let OctoPocus take advantage of this by designing gestures that share a common prefix." With 16
  commands, roughly 60 percent are pruned within the first 10 percent of the gesture. **Legibility
  is partly a property of the choice set, not only of the rendering.**

## Worked examples, from Flatts's Things

**These are the sibling mod's surfaces, not recompile's**, and they are kept in this copy because
they are the part that turns the rules above into judgment. Observations rather than a work plan.

**The tool slot outlines are already a correct feedforward decision, made without the vocabulary.**
Four slots carry a tool outline; the fifth has none. CLAUDE.md's stated reason is that a sword
outline "would be an invitation the slot then refuses." That is **exactly false feedforward**, and
refusing to draw it is exactly what the rule above prescribes. The absent fifth outline is the
harder call: it is hidden feedforward, deliberately preferred over false, which is the right trade.

**The Z key reports what the toggle became, and says so when the pack has it off** rather than
pretending to flip. That is refusing to emit false FEEDBACK, the same instinct one step later in the
loop.

**The woodcutter's option buttons are the mod's best example.** Each button is the actual result
item, at the locus of the action, updated when the input changes. It satisfies rules 1, 8 and 10
without having tried to.

**The cauldron transforms have none.** Nothing distinguishes a cauldron that will transform your
concrete powder from one that will not, and nothing marks which items are in
`#flattsthings:cauldron_transformable`. A player learns the set by trying items. That is hidden
feedforward, and it is the mod's largest gap of this kind.

**The player pressure plates are a genuine tension rather than a defect.** They are deliberately
indistinguishable from vanilla plates, because parity is the feature. That is a decision to ship
zero feedforward in exchange for silhouette parity, and it is recorded here as such so it is not
later discovered and misread as an oversight.

**Placement preview (Flatts's Things issue 60) is the case study**, and the reason this document
exists. The third-party mods already in that space compute placement state without handling
`DataComponents.BLOCK_STATE`, which means they ship false feedforward for those stacks.

## Applying it to recompile

**Not done.** No surface in this mod has been read against the rules above, and nothing should be
written here that has not been. The obvious first pass is the screens, since recompile has far more
of them than its siblings and a GUI polish pass is in flight; the terminals, the Cupola Furnace and
the Scrap Crafting Station each ask the player to commit an item to a machine whose output they
cannot see in advance, which is the exact shape the rules above are about. That is a hypothesis
about where to look, not a finding.

## What is NOT established

The house rule in both repos is that a confident wrong document is worse than a stale
one. These were
looked for and not found.

- **No citable rule against showing feedforward where there is no meaningful choice to preview.**
  The nearest support is indirect: OctoPocus is designed entirely around disambiguating competing
  options, and Hartson notes that where purpose is already implied "there was no need for explicit
  feedforward." Both are consistent with the idea; neither states it. **Treat it as a defensible
  inference, never cite it as a principle.**
- **No rendering latency budget.** The 250 ms figure is a TRIGGER threshold for hesitation,
  inherited from marking menus. It is not a "must render within X ms" number, and no such number
  appears in this literature.
- **Muresan et al.'s 15 VR design guidelines were not read.** The paper is paywalled. Its existence,
  venue (TOCHI 30(6), Article 82), three-stage structure and the fact that it concludes with 15
  guidelines are verified; the guideline texts are not. Worth buying if this mod ever does anything
  3D-interactive beyond block placement.

## Sources

Primary, all verified against full text or Crossref metadata:

- Djajadiningrat, Overbeeke and Wensveen (2002), "But how, Donald, tell us how? On the creation of
  meaning in interaction design through feedforward and inherent feedback", DIS 2002, 285-291.
  DOI `10.1145/778712.778752`. Introduces the term to HCI.
- Wensveen, Djajadiningrat and Overbeeke (2004), "Interaction frogger: a design framework to couple
  action and function through feedback and feedforward", DIS 2004, 177-184.
  DOI `10.1145/1013115.1013140`. The three types and the six couplings.
- Vermeulen, Luyten, van den Hoven and Coninx (2013), "Crossing the Bridge over Norman's Gulf of
  Execution: Revealing Feedforward's True Identity", CHI 2013, 1931-1940.
  DOI `10.1145/2470654.2466255`. Best Paper Honorable Mention. The reframing, and the hidden /
  false / sequential / nested classes.
- Bau and Mackay (2008), "OctoPocus: A Dynamic Guide for Learning Gesture-Based Command Sets",
  UIST 2008, 37-46. DOI `10.1145/1449715.1449724`. The exemplar, and the empirical warrant.

Supporting:

- Hartson (2003), "Cognitive, physical, sensory, and functional affordances in interaction design",
  Behaviour and Information Technology 22(5), 315-338. DOI `10.1080/01449290310001592587`.
- Gaver (1991), "Technology affordances", CHI 1991, 79-84. DOI `10.1145/108844.108856`. Source of
  the false / hidden / sequential / nested vocabulary Vermeulen ports onto feedforward.
- Norman (2008), "Signifiers, not affordances", interactions 15(6), 18-19.
  DOI `10.1145/1409040.1409044`.
- Muresan, McIntosh and Hornbaek (2023), "Using Feedforward to Reveal Interaction Possibilities in
  Virtual Reality", TOCHI 30(6), Article 82. DOI `10.1145/3603623`. Not read; see above.
- Yu, Lee and Sedlmair (2024), "Design Space of Visual Feedforward And Corrective Feedback in
  XR-Based Motion Guidance Systems", CHI 2024. DOI `10.1145/3613904.3642143`. Open preprint at
  arXiv 2402.09182.
