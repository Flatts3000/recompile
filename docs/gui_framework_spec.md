# GUI framework spec

**Status: built and shipped** (2026-08-08, issue #164). All eleven screens run on it. Owner call the
same day: a real GUI framework is the thing holding back several features, because a high-quality
screen was expensive enough that the honest answer was usually "don't build one".

**Where it lives.** `com.flatts.recompile.gui` is the common half - `ScreenLayout`, `GuiTheme`,
`Rect` - and `com.flatts.recompile.client.gui` is the client half - `LayoutScreen`, `GuiPainter`,
`VanillaGui`. The split is not organisational; see section 3.

**What it is not.** There is no layout algebra. No flow, no grid solver, no constraint pass. A
machine declares where its things are and the framework makes sure exactly one copy of that
declaration exists. See section 10 for why that turned out to be the whole job.

---

## 1. The problem, measured

**Eleven** custom screens ship today - Scrap Crafting Table, Tree Nursery, Burner Generator,
Hydroponics Bay, Cupola Furnace, Slag Furnace, Sintering Kiln, Sequencer, the market's Sell and
Buy Terminals, and the Hauler Depot - each a recorded exception
to "the mod keeps machine GUIs to a minimum". *(This sentence said four while the paragraph ten lines
below already said eight, so the page contradicted itself on its own subject. `find src/main/java
-name "*Screen.java"` settles it; the four in the table below are the ones the framework LAUNCHED
with, which is what that measurement is of.)*

The cost of the launch four:

| Screen | Screen LOC | Menu LOC |
| --- | --- | --- |
| Scrap Crafting Table | 290 | 632 |
| Tree Nursery | 197 | 171 |
| Burner Generator | 149 | 153 |
| Hydroponics Bay | 134 | 199 |

**2032 lines for the four screens it launched with** (eleven today), and the count is misleading in the wrong direction: most of what a
new screen costs is not in these files at all, it is in re-learning how any of it works.

Three costs repeat every time.

**Layout lives in two places that must agree by hand.** A container's slot coordinates are baked into
`Slot` objects inside the *menu*, which is common code that runs on a dedicated server. The drawing
happens in the *screen*, which is client-only. Nothing connects them. Every machine pays for that
agreement in constants copied between two files, and a mistake is a slot drawn where no slot is.

**The 26.1 render model was re-learned four times.** Drawing goes through `GuiGraphicsExtractor` in
`extractBackground(...)`, not `renderBg`; `blit` takes an explicit `RenderPipelines` pipeline and
explicit atlas dimensions; item and text draws are `graphics.item(...)` / `graphics.text(font, ...)`;
`imageWidth` / `imageHeight` are final and must be passed to a 5-arg `AbstractContainerScreen`
constructor. None of that is guessable and all of it is identical per screen.

**Chrome gets re-derived by hand, badly.** `VanillaGui`'s own header records the failure: the first
Hydroponics Bay screen filled flat rectangles in approximately vanilla colours, and the panel read as
"a grey box with holes in it rather than as something Minecraft drew".

## 2. What already exists, and must not be rebuilt

**Vanilla ships more than it appears to.** In 26.1: **533 GUI sprites**, **43 of them carrying
nine-slice `.mcmeta`**, and a complete layout engine - `GridLayout`, `LinearLayout`, `FrameLayout`,
`EqualSpacingLayout`, `HeaderAndFooterLayout`, `LayoutSettings`, `SpacerElement`.

That engine drives **option menus**, which resize, scroll and narrate. It was never wired to
`AbstractContainerScreen`, and the reason is structural rather than neglect: vanilla has roughly
fifteen containers, each hand-designed once by someone who drew the PNG and placed the slot grid
together. Paying the menu/screen agreement cost fifteen times is nothing. Paying it once per machine,
forever, is what this document is about.

**`VanillaGui` already proves the texture-free approach** (118 lines, shipped). It nine-slices the
panel out of *vanilla's own furnace background* and draws slots with the literal
`minecraft:container/slot` sprite. Two consequences worth keeping: the mod ships **no GUI texture at
all**, and a resource pack that restyles vanilla containers restyles ours too, because the chrome is
genuinely borrowed rather than imitated.

**The Hydroponics Bay already reached for the right pattern.** Its layout constants (`W`, `H`,
`GAUGE_Y`, `GAUGE_W`, `INPUT_X`, `INV_X`, `CELL`, `HOTBAR_Y`) live on the **menu**, and the screen
reads them off it. That is the framework in embryo, discovered by hand, once.

**So this is not a bet.** The framework is the generalisation of two things this codebase already
built and documented. What is missing is layout, the menu/screen contract, and a widget vocabulary.

## 3. The one architectural constraint

**The declaration is common code. Rendering is a client-side visitor over it.**

This is not a style preference. A menu is constructed on a dedicated server where no client class
exists, so the layout object must be loadable there - it may not import `GuiGraphics`,
`RenderPipelines`, `Font`, or anything under `net.minecraft.client`. Everything the screen needs to
*draw* is a separate client-side pass that reads the same declaration.

A second constraint follows from vanilla: `imageWidth` and `imageHeight` are final and must be passed
to `super(...)`. **The layout must therefore be computable before the screen object exists** - so
layout is a pure function of the declaration, not something accumulated during rendering.

## 4. Shape

```
  Layout (common)                          Renderer (client)
  ---------------                          -----------------
  declare regions + widgets                walk the same declaration
  compute absolute positions               draw each widget
  expose slot coordinates  --> Menu        expose hit regions --> tooltips
  expose size (W, H)       --> Screen ctor
```

A machine declares its screen once. The menu asks the declaration where its slots are. The screen
asks it what to draw and what the mouse is over. **One source of truth, and the two sides cannot
disagree because neither owns the numbers.**

That mirrors a pattern this repo already trusts: `Multiblock` is the single source of truth for
validation, auto-assemble, the guidebook pattern and (as of 2026-08-07) what disband returns. The
same reasoning applies here, and for the same reason - the bug you cannot have is the one where two
copies of a truth drift apart.

## 5. Widget vocabulary for v1

Derived from what the four existing screens actually need, not invented:

| Widget | Needed by | Notes |
| --- | --- | --- |
| `Panel` | all | nine-sliced vanilla chrome; already built |
| `SlotGrid(cols, rows)` | all | emits `Slot` coordinates AND draws the slot sprite |
| `PlayerInventory` | all | the 9x3 + hotbar block, identical in every container in the game - it should be one line, forever |
| `Gauge(vertical, fill, colour)` | Hydroponics Bay, Burner Generator, Tree Nursery | water, power. No vanilla equivalent, which is exactly why these screens exist |
| `Progress(sprite)` | Hydroponics Bay, Scrap Crafting Table | vanilla's arrow and flame, borrowed |
| `ItemList(scrollable)` | Scrap Crafting Table | the connected-storage panel |
| `Picker(items)` | Tree Nursery | species selection |
| `Label` | several | text, with vanilla's shadow conventions |

**Gauges are the load-bearing addition.** The recorded reason these screens exist is that energy
bars, tank gauges and pickers have no vanilla screen to borrow. A framework that ships a good gauge
removes the *reason* most of these screens were bespoke.

## 6. Texture policy

**Ship nothing.** Chrome comes from vanilla sprites and nine-slices of vanilla backgrounds, per
`VanillaGui`. Gauges are drawn as fills with borrowed bevels rather than as authored art.

This is a hard rule, not a default, and it has a second payoff beyond cost: **a screen with no
texture cannot drift from the texgen pipeline**, cannot land unapproved, and cannot be the reason a
release ships art nobody reviewed.

If a widget genuinely cannot be drawn from vanilla parts, that is a design conversation, not a
licence to add a PNG.

## 7. Acceptance criteria

**The screens that existed when it was written are the requirements document.** The framework was done when all four were
reimplemented on it and:

1. Every one is **shorter** than it is today, screen and menu combined.
2. No screen contains a `RenderPipelines`, an atlas dimension, or a hardcoded slot coordinate.
3. No layout constant appears in two files.
4. The four are deliberately heterogeneous - a crafting grid, a scrollable list, a picker, and dual
   gauges. **If a declaration can express all four with no special cases, the API is proven.** If any
   of them needs an escape hatch, the API is not ready to be extracted.
5. `runClient` shows each one rendering correctly. This layer is invisible to GameTest and to the
   JUnit layer, so a client pass is the only proof - the same rule the guidebook pages live under.

## 8. Packaging

**Build it as a package inside Recompile first. Extract to a standalone mod once criterion 4 holds.**

The reasoning is that you do not know the API until four real screens have gone through it, and a
wrong API shipped as a dependency is far more expensive than a file move. Recompile happens to have
exactly the right corpus already.

When it is extracted, the consuming mod must not gain a hard runtime dependency it cannot ship
without - the same standard Modonomicon and the JEI/Jade plugins are held to here: `runtimeOnly` and
degrade gracefully, or shade it.

## 9. Non-goals

- **Not a config-screen library.** That problem is solved elsewhere and is a different shape.
- **Not a replacement for reusing vanilla screens.** The rule stands: a container that fits
  `ChestMenu` or `FurnaceMenu` uses it. The framework is for the producers with gauges that have no
  vanilla equivalent.
- **Not skinnable/themeable in v1.** It looks like vanilla because it *is* vanilla's chrome.
- **Not a scripting or data-driven layer.** Declarations are Java. A JSON layout format is a
  plausible v2 and an unnecessary v1.

## 10. The questions this had open, and what they turned out to be

### Prior art exists for NeoForge 26.1, and the recollection was wrong

LibGui is not Fabric-only - **LibGuiFoxified** is a NeoForge port (MIT), and **LibGui Reforged**
ports it to Forge. Neither matters, though, because both stop well short of 26.1 and Foxified was
last pushed 2025-03-08.

**owo-lib is the one that matters.** It has a `26.1-Neo` branch whose `gradle.properties` reads
`minecraft_version=26.1.2` - our exact version - it ships an `owo-lib-neoforge` artifact, it is MIT,
and `0.13.1+26.1` was published 2026-08-06. It is live, maintained and good.

We did not adopt it, for three reasons in increasing order of how hard they are to argue with:

1. **It needs mixins.** `BaseOwoContainerScreen` imports `owo.mixin.ui.SlotAccessor` and
   `OwoSlotExtension`; it mutates `Slot.x`/`Slot.y` and injects a disabled-override field. This mod
   deliberately has no mixins, and taking on a mixin toolchain to lay out a handful of screens is a large
   change to pay for a cosmetic subsystem.
2. **Its layout is client-side by design, so it cannot fix the defect we have.** owo's
   `slotAsComponent(int)` "will always move the linked slot to where the component gets placed by the
   layout". That is coherent - slot coordinates are purely visual, since a click sends a slot *index*
   - but it puts the truth on the client, where `MenuLayoutTests` cannot reach it.
3. **It is a hard runtime dependency** (a separate mod, plus jankson and kdl4j), which section 8
   forbids, and shading it with its mixins is not realistic.

**What the research settled is more valuable than a yes or no.** There are exactly two viable
architectures for this problem:

- **A (LibGui, and this framework):** the declaration is common code; the menu reads slot coordinates
  from it.
- **B (owo):** the menu places slots anywhere; the client layout moves them afterwards.

B is less code and imposes no discipline about what the declaration may import. It costs server-side
testability of geometry. **We chose A specifically because we already own the test layer that B would
blind** - and that is now a recorded reason rather than something to rediscover.

The second thing owo settled: its component tree is dozens of classes with a full flexbox, KDL/XML UI
models and error toasts. We need none of it. Vanilla containers are hand-placed by nature and there
are four of them. **The value is one declaration feeding both sides, not an auto-layout engine.**

### Hit regions reach the screen by name

As guessed: the declaration exposes named rectangles and the client pass maps names to behaviour.
`GuiPainter` takes a group name for every verb, deliberately - the alternative, handing back a
graphics object and a pair of offsets, is the escape hatch that would let a screen go back to typing
numbers. `GuiFrameworkDisciplineTest` holds that line by refusing to let a screen so much as mention
`leftPos`.

### The scrollable list did belong in v1, and it paid for itself

It was the one that could have been left bespoke. Including it is what produced the two verbs the
simpler screens do not need, and both turned out to be general rather than special cases:

- **`noChrome()`**, because the Scrap Crafting Table is built on vanilla's whole `crafting_table.png`,
  which already draws its own slot wells. The slots are still declared and the menu still places them
  from the declaration; only the painting belongs to the image.
- **`backdrop`**, because a surface other elements sit on top of is not an overlap bug, and the
  sweep needs to know the difference to stay strict about everything else.

It also forced the one genuinely interesting property in the API: **a vertical run of rows answers for
the row after its last**, so the list can put its tail line ("+6 more") directly under however many
rows it actually drew. Nothing else extrapolates, and asking a fuel row for a sixth slot throws.

### `VanillaGui` became the chrome layer, as predicted

Moved to `client.gui`, its constants lifted into `GuiTheme` so a common-code layout can name a colour.
It is now the only class in the mod permitted to know a render pipeline or an atlas dimension.

## 11. What it found on the way in

Converting the original four screens was also an audit, and it turned up four live defects that no test could
have seen before:

- **The Tree Nursery's screen declared `FERT_X = 44` while its menu independently passed `44` to a
  `Slot`.** The exact two-copies-of-one-truth this document was written about, sitting in the tree.
- **Three screens carried a private `panel()` / `slot()` / `recess()`** - flat fills approximating
  vanilla rather than borrowing it - so the Burner Generator and the Tree Nursery drew a visibly
  different panel from the Hydroponics Bay's nine-sliced one.
- **Water had two colours.** The nursery's tank and the bay's tank, in a mod where both machines share
  one water economy. Each was a single-file literal, so the colour-consistency guard had been silent
  about both.
- **`GuiColourConsistencyTest` scanned `client/` with `Files.list`, not `Files.walk`.** Adding a
  subpackage would have silently dropped every file that moved into it out of coverage.

And one trap worth keeping: **a static `LAYOUT` that transitively touches a registry-backed class
cannot be named from another class's static initialiser during mod construction.** `MenuLayoutTests`
referencing `TreeNurseryMenu.LAYOUT` eagerly pulled in `TreeNurseryBlockEntity`, whose static
`FluidResource.of(Fluids.WATER)` throws *"Components not bound yet"* - and the whole mod fails to
load. The list holds suppliers now, which is what the previous version was accidentally doing by
holding only factories.

## Changelog

- **2026-08-29** - Count corrected from four to eight during a SCRUB. The spec said "all four screens
  run on it" in six places while the Cupola, Slag Furnace, Sintering Kiln and Sequencer had all shipped
  on it since. Where the number was load-bearing about the ORIGINAL scope (the 2032-line figure, the
  requirements-document argument) it is now said in the past tense rather than restated as a present
  fact, because those sentences were true when written and only the tense was wrong.
