# Test coverage

How to measure this mod's coverage, and how to read the number without being misled by it.

## The one command

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25" \
  ./gradlew test runGameTestServer -PgameTestCoverage coverageReport
```

Report lands at `build/reports/jacoco/coverageReport/html/index.html`, XML beside it.

## Why it takes three tasks

**Neither test layer alone tells the truth, and reading either one on its own gives a wrong answer in
the opposite direction.**

- `./gradlew test` is the JUnit layer: pure logic against a loaded mod context, no world. Its report
  (`jacocoTestReport`) puts `content/block/entity` at **under 1%** - and that package holds almost
  every machine in the mod, each with thorough in-world tests. Read alone it says the mod is untested.
- `runGameTestServer` is where this mod actually proves its behaviour, and it runs in a **separate
  server JVM** launched by moddev, so the `test` task's agent never sees it. `-PgameTestCoverage`
  attaches a JaCoCo agent to that JVM; without the flag nothing is instrumented and the run is
  unchanged, which is the point - a normal run should not pay for this.
- `coverageReport` merges both `.exec` files into one report.

The `gametest` package is the only thing excluded from the denominator. It is test code that happens to
live under `src/main/java`, because GameTest registration requires it - counting it would inflate the
number with tests testing themselves.

**`client/**` is deliberately NOT excluded from the reports**, even though neither layer can reach it.
Hiding it would make the headline look better while deleting the evidence that 389 lines have no
automated coverage at all. The "actionable" row subtracts it in the open, where the subtraction can be
argued with.

**Two ways this measurement can lie to you, both now guarded:**

- Running `coverageReport` without `-PgameTestCoverage` used to merge whatever `gameTest.exec` was left
  on disk. `append=false` truncates that file only when the agent actually attaches, so a stale one
  survives and gets reported as current - a wrong number that looks exactly like a right one. The task
  now fails rather than merging an exec file this invocation did not produce.
- Gradle does not honour command-line order for tasks with no declared relationship, so `coverageReport`
  could run before either test task had written anything. It now `mustRunAfter` both.

## The numbers, 2026-08-31 (post-v0.16.0)

| | line | branch |
|---|---|---|
| Merged, both layers | **71.6%** | 57.4% |
| JUnit layer alone | 23.3% | 3.4% |
| **Actionable** (minus what neither layer can reach) | **77.4%** | - |

Up from 70.6% / 55.2% at v0.14.0, across two releases that added the landmarks, cardboard, the
Sequencer and the resin chain - so the suite kept pace with the code rather than being diluted by it.

**What the 2026-08-31 COVER pass changed.** `compat/jade` went from 28.4% to 34.8%. It was the largest
gap either layer could actually reach, and the reason it existed is worth keeping: `JadeDataTests`
derives its subjects from powered multiblock cores, so it covered exactly three providers - Separator,
Trommel, Pulverizer, the only three at 100% - and five more `IServerDataProvider` classes had never
been run at all. A derived list is the right shape and it still only derives the set somebody thought
of, which is the same failure the Pulverizer's missing providers were.

**What is still uncovered, on purpose:** `compat/jei` (443 lines), `client` (258) and `client/gui`
(153) are client-render code neither layer can reach - that is what the actionable row subtracts, and
`tools/shoot_screens.py` plus `tools/shoot_guidebook.py` are the evidence for them instead. Jade's
`IBlockComponentProvider` half is in the same position: it draws, so it is only checkable by looking.

## The numbers, 2026-08-20 (post-v0.14.0)

All three rows share one denominator - 9847 lines, `src/main/java` minus `gametest` - so they can be
compared. That is enforced by a single `COVERAGE_EXCLUDES` list both report tasks use. They did not
share it at first: one report also dropped `client/**`, which put 389 zero-covered lines in one
denominator and not the other and made this very table a comparison of different things.

| | line | branch |
|---|---|---|
| Merged, both layers | **70.6%** | 55.2% |
| JUnit layer alone | 23.3% | 3.4% |
| **Actionable** (minus what neither layer can reach) | **76.6%** | - |

*(The JUnit-alone row was briefly dropped from this table and put back in review of #274: the preamble
above argues that reading either layer on its own gives a wrong answer in the opposite direction, and
this row is the evidence for that argument. 23.3% is what this mod looks like to someone who runs only
`./gradlew test`.)*

The floor in `~/.claude/rules/common/testing.md` is **80%**, so the actionable figure is still under it.

Two releases landed between the 2026-08-19 reading and this one and the number did not move on its own -
69.9% to 69.9% - which is the useful part: new code arrived carrying roughly the coverage the mod
already had. The movement here is from the COVER pass that followed, and it is deliberately small and
aimed rather than broad: `content/menu` branch went 30.2% to 36.7% and `compat` branch 51.1% to 57.2%,
because that is where the two verified findings were.

**Where the remaining actionable debt sits**, largest first, so the next pass does not re-derive it:
`content/block` (437 missed lines), `content/block/entity` (382), `content/menu` (230 - **four** other
menus override `quickMoveStack` and none is covered: `BurnerGeneratorMenu`, `HydroponicsBayMenu`,
`TreeNurseryMenu` at 0 branches each, and `ScrapCraftingStationMenu` at 43.5% of 184), `compat` (178),
`content/worldgen` (118 -
`MoundFeature` is at 4.5% and `MyceliumPatchFeature` at 7.4%, both features a GameTest can call
directly), and `event` (118).

## What neither layer can reach, and why that is not a gap to fill

771 lines, all at 0%, and they are excluded from the actionable figure rather than counted as debt:

- **`client/**` (395 lines: `client` 242 plus `client/gui` 153)** - screens, the GUI framework's rendering visitor, the one
  BlockEntityRenderer. A GameTest server has no client and JUnit loads none. `CLAUDE.md` already says
  screens are the layer both test layers are blind to; `python tools/shoot_screens.py` against a
  running `runClient` is the acceptance evidence for them, not a coverage number.
- **`compat/jei` (376 lines)** - categories and renderers. JEI's own registration only happens
  client-side. `SortingData` is the server-safe half and is covered by `SortingDataTests`, which is
  exactly why that split exists.

Chasing these would mean either a headless-client harness or tests that assert nothing. Neither is
worth it; both are worth saying out loud so the next person does not re-derive it.

## The half of Jade that IS reachable

`compat/jade` splits into two kinds of class and they are not equally testable:

- **`*DataProvider`** implements `IServerDataProvider`. It runs on the server, reads a BlockEntity, and
  writes numbers into a `CompoundTag`. Fully testable, and it was at **0%**.
- Everything else implements `IBlockComponentProvider` and draws an `ITooltip`. Client-side.

`JadeDataTests` covers the first kind. It reaches Jade **by reflection rather than by import**,
deliberately: Jade is an optional dependency, a GameTest class registers unconditionally at mod init,
and an `import snownee.jade.api.BlockAccessor` in one would throw `NoClassDefFoundError` on a Jade-less
install and take the mod down with it. The accessor is a dynamic `Proxy` - the providers ask it for
exactly three things (BlockEntity, Level, position), so no Jade internals are touched and nothing
breaks when Jade's `impl` package moves.

## Reading a low number correctly

A package sitting low is a **question**, not a verdict. Ask which layer should have covered it:

- Low and client-side -> expected, see above.
- Low and pure logic -> a real unit-test gap.
- Low and in-world behaviour -> a real GameTest gap.

And the reverse trap is worse: **a covered line is not a tested one.** Every provider in `compat/jade`
reported a few covered lines before any of this, because `MachineParityTests` asserted a class of the
right NAME existed - a check an empty method body passes. Branch coverage was the honest signal there:
0 of 260.
