# The market: selling products, and buying back knowledge

**Status: BUILT 2026-09-04, on the branch for #311.** Rulings 2026-08-30 and 2026-09-04 are marked
with their date; everything else is derivation and is arguable. Section 13 records what the build
decided where this document had left a choice open, and each of those is the assistant's call rather
than the owner's.

**This document is lore-free on purpose, and that is a hard constraint rather than an oversight.** The
market block is Recompile, which is a system; what it MEANS is Trashlands, which is curation. The
engine buys scrap, pays scrip, and the destination is "off-site" and nothing more. Anyone designing
against this reads `../trashlands/docs/the_twist.md` first, and then does not restate it here, in the
issue, or anywhere else in this repository. #311 and this repo are public and are the most visible
surface the project has.

---

## 0. The concept in one paragraph

A conglomerate buys the products of your labour and pays in **company scrip**, which is not an item and
which you can spend only with them. What they sell back is **knowledge**: Blueprints, at a steep price,
bought with the balance alone. You are not buying materials and you are not buying a way past a gate;
you are buying your way past the fragment grind, and you still need every material and the bench to
build the thing. Two blocks, one for each verb, each a per-player terminal rather than a container.

---

## 1. What it is for

The economy axis is the thinnest one on the board. **Junk currently has a use but never a price.**
Every material in this mod is valued in what it lets you build next, and nothing is valued against
anything else, so there is no such thing as surplus and therefore no reason to scale production past
what your next recipe needs.

A price fixes that specifically. It turns surplus into a goal, which is the thing that makes a factory
worth building rather than a tech tree worth walking.

---

## 2. What it must not sell, and this is the constraint that shapes everything else

**A shop that sells materials deletes the mod.** Worse, the obvious stock is load-bearing:

| Tempting stock | What selling it would delete |
|---|---|
| Saplings | The Tree Nursery's monopoly, and with it the whole rung-4 gate |
| Water | The Rain Collector's monopoly, which gates the green tier |
| Iron | The blasting gate, which is the second design of the iron gate and the first one that holds |
| Obsidian | The slag chain, and the Nether behind it |

None of those is a shortcut. Each is the removal of a gate that something else was built to be.

**So the stock has to be what the dump structurally cannot give**, and there is exactly one thing in
that category that is not a cosmetic: knowledge.

---

## 3. What it sells

### 3.1 Blueprints, and this is the whole feature

The mod's progression currency is already Idea Fragments and Blueprints. A conglomerate that sells you
a Blueprint sells a **shortcut past the grind, not past the gate**: you still need every material, the
station and the bench. A player who does not enjoy fragment-hunting gets a real way out, and the price
is what stops that being the default rather than the escape hatch.

It also says something true about the setting without a word of prose. The recipes went in the bin and
somebody owns them now.

**Priced in the balance alone** (owner, 2026-08-30), not balance plus fragments. Money genuinely
replaces the grind rather than discounting it. Half-and-half would mean doing both jobs.

### 3.2 Bulk orders: LAST, and possibly never

Buying quantities of things you can already make is buying time, never capability. It is safe, and it
is dull enough that it should not be in v1. Recorded so it is not rediscovered as a new idea.

### 3.3 Disposal: CUT (owner, 2026-09-04)

#311 argued for it at length and it is thematically exact - paying to have refuse taken away, in a mod
about clearing a landfill. It is cut anyway, because the same issue's later ruling makes the market buy
**products, not refuse**, and a disposal verb reintroduces exactly the junk sink section 8 refuses.

Recorded rather than dropped, because the argument for it was good and someone will make it again.

### 3.4 Not cosmetics-only

That is the safe answer and it makes the whole feature ignorable.

---

## 4. The currency: company scrip, and it is not an item

**Not emeralds.** Emeralds are deliberately scarce here: the only ones in the world come from curing a
zombie villager. A market trading in them either devalues that or is priced out of reach.

**Company scrip** is tokens issued by the company, redeemable only at the company store, worthless
everywhere else. It is a real and grim piece of industrial history, it fits the tone exactly, and
mechanically it stops the currency becoming a parallel economy: scrip buys nothing except what the
conglomerate sells.

### The balance is per-player state, and that is the strong part

**There is no scrip in your inventory.** The company keeps your balance and hands it back as credit
against their stock, which is what company scrip usually was: ledger credit at the company store far
more often than tokens in a hand.

Three things fall out of that rather than being imposed on it:

- **It cannot be dropped, stolen, stored in a chest, or lost on death.** An item can be all four.
- **Selling cannot be automated, because an account belongs to a player and a hopper does not have
  one.** The market stays hand-paced like the rest of the pick-through loop, and no automation rule
  had to be written to make it so.
- **You never actually hold the money**, which is the point.

The cost is that a balance is invisible except where a screen shows it. That is itself an argument for
the two screens, so the design is at least internally consistent.

### Scrip supply is unbounded, and that is accepted (owner, 2026-09-04)

Mounds regrow (Phase 5), so scrap is renewable, so scrip is renewable. **A Blueprint's price is
therefore a time cost and not a scarcity cost**, and the spec says so plainly rather than pretending
otherwise. That is consistent with mounds being renewable quarries rather than a finite stock, and a
player who would rather grind scrip than fragments has made a real choice between two grinds.

Two alternatives were considered and rejected: a once-only purchase per Blueprint, and a price that
decays as you sell more of one product. Both add per-player state beyond the balance, and the second
adds a second number every screen would then have to show.

---

## 5. Two blocks, two screens, and this reverses a standing rule

**Two blocks.** Different verbs, different screens, two recipes.

**Name them from the PLAYER's verb, not the company's**, and settle that before either block is
registered. "The block that sells" is genuinely ambiguous - it reads equally as the one that sells TO
you and the one you sell AT - and this spec said "one sells, one buys" for a day without noticing. Two
blocks whose names can be read backwards is a support question forever. So:

| Block | The player | The balance |
|---|---|---|
| where you hand over products | sells | goes up |
| where you spend on Blueprints | buys | goes down |

**Both are instanced per player like an ender chest.** Two players at the same block see their own
account. The block is a terminal, not a container.

**Neither takes power** (owner, 2026-09-04). No FE, no energy capability, no bar on either screen.
They are terminals against someone else's ledger rather than machines that do work, and a shopfront
that needs a generator before it will talk to you is a machine pretending to be a counter. It also
keeps the two screens honest: the only number either one shows is a price or a balance, which is the
whole justification in section 5 for minting them at all.

That is a real decision rather than an omission, because every other block in this mod with a screen
and a job burns something. `MachineParityTests` derives its sweep from multiblock cores answering
`Capabilities.Energy.BLOCK`, so these two fall outside it by construction, the same way the Slag
Furnace and the Sintering Kiln do.

### The reversal, recorded

An earlier proposal in #311 had GUI-less selling plus recipe-based buying, specifically to avoid
minting screens. That is off the table (owner, 2026-08-30), and it is written down because **this mod
has eight custom screens and the standing rule is that each one is a deliberate exception recorded in
CLAUDE.md.** This feature makes ten in one go, which is the largest single addition to that count.

The justification is the same shape as every existing exception: **no vanilla screen shows a price.**
The Burner Generator needed an energy bar, the Tree Nursery a species picker, the Cupola a second
output slot. A shop needs browsable stock with costs attached, and vanilla has no container for that.
Reusing a chest screen would show the items and hide the only number that matters.

### The art: seven surfaces, one of them shared (owner, 2026-09-04)

Each block owns a **front**, a **side** and a **top**. The two **share one bottom**.

| Surface | Count | Why |
|---|---|---|
| front | 2, one each | The face you read the block by. It is what tells the two apart across a room. **A screen** (owner, 2026-09-04, second ruling): a terminal is a thing you read, so the front is a display in a bezel, lit green where you sell and amber where you buy. |
| side | **1 drawn, 1 retint** | The Buy Terminal's side is a recolour of the Sell Terminal's, so the two casings match exactly rather than resembling each other (owner, 2026-09-04). |
| top | **1 drawn, 1 retint** | Same. |
| bottom | **1, shared** | Nobody sees it, and two terminals from one company should agree somewhere. |

**That is exactly `minecraft:block/orientable_with_bottom`**, whose four texture slots are front, side,
top and bottom, and which is already on `RegistryCompletenessTests.VANILLA_PARENTS` so it needs no
allowlist change. The shared bottom is one texgen surface that both models point at rather than two
surfaces held in sync, and the retints read the promoted sell faces, so neither can drift: re-select
the sell side and the buy side re-promotes from it.

Six faces for the owner to `select` (two fronts, one side, one top, the bottom, and the find's dead
front); the two retints derive. An assistant `select` while generating is not approval.

**A front face means the block is directional**, so it carries `BlockStateProperties.HORIZONTAL_FACING`
set from placement. In 26.1 that is an `EnumProperty<Direction>`; `DirectionProperty` does not exist and
every 1.21-era snippet that declares one will not compile.

### The sell terminal shows what it will pay before you commit

Assistant's call, unopposed in #311. A shop that tells you the price after you have handed the goods
over is a con, and this one does not need its interface to be one as well.

---

## 6. What sells: a tag, and a data map beside it

**Membership is `#recompile:sellable`** (owner, 2026-08-30), shipped with the mod's own goods in it and
extendable by a pack without a mod release. The curated-versus-everything argument becomes data rather
than code, which is how every extension point here works.

**At ship it holds components and finished goods only** (owner, 2026-09-04): the Pump, Motor, Bulb,
Battery, Clean Mattress and their kin. Things with a real assembly step behind them.

**It explicitly excludes anything one press away from raw junk**, and that exclusion is the whole
ruling rather than a detail. Pressed Junk is a building family made directly from the commonest thing
in the household stream; putting it on the sell list would hand junk a price, which is precisely the
sink section 8 refuses. The junk ruling and the sell list are the same decision seen twice.

Two surfaces, deliberately:

- the **tag** says what may be sold, because that is the ruling and because a tag is what a pack
  already knows how to extend;
- a **data map** (`data_maps/item/scrip_value.json`) says what each is worth, because a tag cannot
  carry a number and this mod already moves per-item numbers that way (`furnace_fuels`, the hydrating
  crop map).

A tag member with no price is a bug, so the sweep in section 10 **fails the build on one** rather than
letting it sell for nothing. See the open question in section 12 about collapsing the two.

---

## 7. Prices are flat per product

Predictable, no new state, no rotating want-list, nothing to sync beyond the balance. Tuning the
numbers is a build-time job and belongs with the balance pass (#36), not with this design.

---

## 8. What this does NOT solve, stated plainly

**Junk gets no new outlet, and that is deliberate** (owner, 2026-08-30). It is landfill. Most of it
should stay a nuisance you burn or build with, and a sink for everything would undercut the premise
that the dump is mostly rubbish.

**Junk is weight 200 of 658 in the main pool of `household_pulls`, so a shade over 30% of what a pull
hands you, and its only sink remains the Burn Barrel.** That is a real cost of this design rather than
an oversight, it is not addressed anywhere else yet, and this paragraph exists so nobody closes it by
accident while working on this feature.

*(#311 says 31%, measured here at 30.4% on 2026-09-04. Worth knowing HOW to measure it, because the
obvious way is wrong: `household_pulls` has five pools and the four beyond the first carry filler
weights in the hundreds of thousands, so summing item weights across all of them puts junk near 19%
and means nothing. Junk's share is its share of the pool it is in.)*

---

## 9. How you get the terminals: recovered, not invented

**You find one of their machines in the rubbish, tear it down at the Teardown Workbench, and build your
own from what you learned** (owner, 2026-08-30, from a one-word answer: "teardown").

That is the mod's own knowledge loop rather than a plain recipe, and it answers the obvious objection
to crafting a shopfront: you are not manufacturing a conglomerate terminal out of nothing, you are
**reverse-engineering one you dug up**, which is what this mod is about.

Mechanically it is the shape already shipped eleven times: a find, a `recompile:teardown` recipe whose
`teaches` grants the fragment, and a blueprint-gated bench recipe at the end.

---

## 10. Build notes

**Data attachments, and this is a first.** The balance is a NeoForge `AttachmentType` on the player,
not a scoreboard and not `SavedData` keyed by UUID. **This mod uses none today** (verified by grep
2026-09-04), so the first one carries the cost of learning the API.

**`copyOnDeath` must be set explicitly.** An account that empties on death is a bug nobody would report
as one - it reads as the shop being broken.

**The balance has to reach the client** for either screen to draw it. A menu data slot is how every
other screen here moves a number, and it keeps the value out of the attachment-sync path entirely.

**It takes TWO data slots, and that is not an implementation detail.** A data slot is 16 bits on the
wire whatever its Java type says: `ClientboundContainerSetDataPacket` keeps an `int` in its field and
then writes it with `writeShort`. A balance over 32,767 arrives wrapped negative and one over 65,535
arrives truncated, with nothing logged and the server still right, so the only symptom is a screen
quoting a number the player knows is wrong. Nothing in this mod had met that ceiling before, because
every other synced value here is a tank or a buffer capped at 20,000 or less. The balance travels as
a low half and a high half, recombined on the client (`BalanceSync`, arithmetic on `Market` so a unit
test can drive it without a menu).

**Both screens go through the GUI framework**: a `ScreenLayout` declared once in common code, rendering
as a client-side visitor over it. `GuiFrameworkDisciplineTest` fails the build if either screen
mentions a pipeline, a blit or even `leftPos`.

**Hold suppliers, not layouts.** A static `LAYOUT` that transitively touches a registry-backed class
cannot be named from another class's static initialiser during mod construction; that is the
"Components not bound yet" trap, and it takes the whole mod down with a bare
`ExceptionInInitializerError`.

**A Blueprint is an item carrying a data component**, a set of recipe ids under
`RCDataComponents.BLUEPRINT`. The buy terminal hands over a Blueprint stack with that component set,
which is the same shape `fragment_assembly` already produces. It does not need a new knowledge system.

### Tests this needs, because rulings that are only prose go stale here

- **Every item in `#recompile:sellable` has a price**, derived from the registry and failing the build
  on a member with none. Same shape as `every_sortable_block_is_in_a_vacuum_band`, and for the same
  reason: it fails CLOSED, so an unpriced item cannot silently sell for zero.
- **Nothing in `#recompile:sellable` is raw scrap or one step from junk.** This is section 6's ruling
  made mechanical. A comment would not survive the first person adding a building block to the tag.
- **A balance survives death**, which is `copyOnDeath` asserted rather than assumed.
- **Two players have two balances**, which is the per-player claim asserted rather than assumed.
- **A hopper against either terminal moves nothing.** Automation is refused by the design rather than
  by a rule, and a test is what keeps that true when someone later adds a capability out of habit.
- **Buying a Blueprint spends the balance and yields a sheet with the right component**, and buying
  with an insufficient balance spends nothing and yields nothing. Paired, because "it produced no
  sheet" passes just as well on a terminal that never works.
- **A balance survives the trip to the screen**, at 32,768 and either side of it. A unit test rather
  than a GameTest, because the failure is arithmetic and the fix is arithmetic; it drives the same
  `(short)` narrowing the packet performs, so it tests the real failure and not the encoding in
  isolation.

---

## 11. Deltas from #311

| #311 said | This spec says | Why |
|---|---|---|
| Disposal is the second thing it should sell | Cut | Owner, 2026-09-04. The issue's own later ruling makes the market buy products rather than refuse, and disposal reintroduces the junk sink section 8 refuses. |
| Open question: is disposal free or paid? | Moot | It is cut, so the question has no subject. |
| Open question: Blueprints for balance alone, or balance plus fragments? | Balance alone | Already answered in the same issue's Decided section. Recorded here because the Open section still asked it, and a spec that leaves both standing is worse than either. |
| GUI-less selling, recipes for buying | Two screens | Owner, 2026-08-30. Reversed deliberately and recorded, because the no-new-screen rule requires it. |

---

## 12. Open questions

1. **Should the tag and the data map be one surface?** A data map alone could carry both membership and
   price, which would remove the fail-closed sweep by construction rather than by test. The tag is what
   the owner ruled and is the idiom a pack already knows, so the spec keeps it; this is a note that the
   redundancy is deliberate and not an oversight.
2. **Which find is the recovered terminal, and from which stream?** Bulky Waste is where finds live, and
   Mechanical Waste is where a machine would plausibly be. It needs a name and one loot line.
3. **Do the two terminals share one found machine or need two?** One find teaching both is cheaper and
   reads fine: you recovered their terminal, and you built both ends of it.
4. **Is there a floor on what a Blueprint costs relative to its fragment count?** Pricing is tuning, but
   the RATIO between the two routes is design: if scrip is much faster than fragments for every
   blueprint, the fragment loop is dead content rather than an alternative.

---

## 13. As built (2026-09-04)

Where the spec left a choice open, the build made one. Every item here is the assistant's call, made
to ship, and is the first thing to revisit if it reads wrong.

| Open point | What was built | Why |
|---|---|---|
| Names (section 5) | `sell_terminal` / **Sell Terminal** and `buy_terminal` / **Buy Terminal** | The player's verb, literally. "Sell Terminal" is already how section 5 refers to it. |
| Q2: which find, which stream | **Broken Terminal**, weight 1 in `gameplay/bulky_spine` beside the Broken Hydroponics Bay | Bulky Waste is where finds live and the guidebook sweep already reads that table; a machine in Mechanical Waste would be a second convention for one thing. |
| Q3: one find or two | **One find, two Blueprint sets** (`recompile:sell_terminal`, `recompile:buy_terminal`), both taught by the one teardown at four fragments each | A set has exactly one `blueprint_crafting` recipe (`the_clean_mattress_blueprint_recipe_loads` counts on it), and the two blocks are two recipes. One teardown carrying two `teaches` lines gives the cheaper reading the question wanted without bending that. |
| Q1: tag and data map | Kept as two surfaces, exactly as section 6 says | The redundancy is deliberate, and `every_sellable_item_has_a_price` is what makes it safe. |
| Where the Buy Terminal's stock lives | A **`recompile:market_offer` recipe type**: `{"blueprint": ..., "price": N}`, one file per line of stock | A Blueprint set is an id on a component, not a registry entry, so no data map can key on it. A recipe is the other thing a pack adds by dropping in a file, it reloads with the world, and the terminal reads the loaded set when it opens. It is never matched against anything and is `isSpecial`, so no recipe book or viewer lists it as a craft. |
| How the stock reaches the client | Written into the menu's **open buffer**, the way the Scrap Crafting Table sends its position | The screen draws exactly the list the server sells from, and no second sync path exists to drift. The balance is a menu data slot, per section 10. |
| Q4: the ratio | First-pass offers from 120 (Bulb) to 1,500 (the spawner cage and the netherite pattern), against sell prices of 5 to 45 per item | Each sheet is priced at roughly what selling its fragment count's worth of teardown yield would take, so scrip is an alternative to the grind without being faster for every sheet; region-gating sheets sit past a casual balance. The RATIO is design and the numbers are #36's. |
| The sell list at ship | The eight machine parts plus every Clean Mattress, via `#recompile:clean_mattresses` | "Components and finished goods with a real assembly step." `nothing_sellable_is_raw_scrap_or_one_step_from_junk` is the ruling made mechanical: nothing binnable, and nothing craftable from binnable inputs alone. |
| The art | Declared in `texgen.toml` as section 5 lays out after the second ruling: screen fronts, the buy side and top as retints of the sell ones, one shared bottom, and a dead-screen front for the Broken Terminal, which reuses the Sell Terminal's flanks | AI candidates are generated and one per face is promoted so the blocks render as terminals; none is in `gen/approved.json` until the owner runs `select`. |

**What the build did not do, on purpose.** No JEI category for the offers (the info panels on the
three blocks say where the stock is), no Jade provider (there is no state on the block to show), and
no automation of any kind. Bulk orders and disposal remain exactly where sections 3.2 and 3.3 left
them.
