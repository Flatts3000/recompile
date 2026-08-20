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

## The numbers, 2026-08-19

All three rows share one denominator - 9712 lines, `src/main/java` minus `gametest` - so they can be
compared. That is enforced by a single `COVERAGE_EXCLUDES` list both report tasks use. They did not
share it at first: one report also dropped `client/**`, which put 389 zero-covered lines in one
denominator and not the other and made this very table a comparison of different things.

| | line | branch |
|---|---|---|
| Merged, both layers | **69.9%** | 54.2% |
| JUnit layer alone | 23.2% | 3.4% |
| **Actionable** (minus what neither layer can reach) | **75.8%** | - |

The floor in `~/.claude/rules/common/testing.md` is **80%**, so the actionable figure is under it.

## What neither layer can reach, and why that is not a gap to fill

750 lines, all at 0%, and they are excluded from the actionable figure rather than counted as debt:

- **`client/**` (389 lines)** - screens, the GUI framework's rendering visitor, the one
  BlockEntityRenderer. A GameTest server has no client and JUnit loads none. `CLAUDE.md` already says
  screens are the layer both test layers are blind to; `python tools/shoot_screens.py` against a
  running `runClient` is the acceptance evidence for them, not a coverage number.
- **`compat/jei` (361 lines)** - categories and renderers. JEI's own registration only happens
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
