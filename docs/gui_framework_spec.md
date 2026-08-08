# GUI framework spec

**Status:** proposed, not built. Owner call 2026-08-08: a real GUI framework is the thing holding
back several features, because a high-quality screen is currently expensive enough that the honest
answer is usually "don't build one".

**Working name:** to be picked. Referred to below as *the framework*.

---

## 1. The problem, measured

Four custom screens ship today, each a recorded exception to "the mod keeps machine GUIs to a
minimum". Their cost:

| Screen | Screen LOC | Menu LOC |
| --- | --- | --- |
| Scrap Crafting Table | 290 | 632 |
| Tree Nursery | 197 | 171 |
| Burner Generator | 149 | 153 |
| Hydroponics Bay | 134 | 199 |

**2032 lines for four screens**, and the count is misleading in the wrong direction: most of what a
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

**Gauges are the load-bearing addition.** The recorded reason all four screens exist is that energy
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

**The four existing screens are the requirements document.** The framework is done when all four are
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

## 10. Open questions

- **Does prior art exist for NeoForge 26.1?** LibGui is Fabric-only, but that is recollection and a
  search has not confirmed it. This must be answered before any code is written - the repo's own
  workflow makes research-and-reuse mandatory, and a GUI library is exactly the kind of thing that
  should be adopted rather than written.
- **How do hit regions and tooltips reach the screen** without the declaration importing client code?
  Probably: the declaration exposes named rectangles, the client pass maps names to tooltip suppliers.
- **Does the Scrap Crafting Table's scrollable list belong in v1?** It is the most complex widget and
  the only consumer. There is a real argument for leaving it bespoke and proving the framework on the
  other three.
- **What happens to `VanillaGui`?** Most likely it becomes the framework's chrome layer more or less
  intact, since it is already the right thing.
