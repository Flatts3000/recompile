# Recompile - Claude Code context

**What this is:** a standalone **NeoForge** mod (target MC 26.1). Core mechanic: **teardown-as-knowledge** - disassemble items to recover their recipes (not just materials). Also ships the garbage-world systems (worldgen, Blocks of Garbage, mound regrowth) that the **Trashlands** modpack is built on.

**Status:** design phase, **no code yet** (2026-07-14). This repo is currently a skeleton; the Gradle NeoForge MDK is added when the feasibility slice starts (confirm the exact NeoForge version for MC 26.1 first - mirror the `../productive-frogs` toolchain, which is the proven sibling).

## The design lives in the Trashlands repo (source of truth)
The full feature-by-feature design, decisions log, and material economy are in the sibling pack repo, not here:
- `../trashlands/docs/design_decisions.md` - per-feature locked decisions + session bookmark. **Start here.**
- `../trashlands/docs/concept.md` - the vision.
- `../trashlands/docs/material_economy.md` - metals/gems/mod-spine sourcing.
- `../trashlands/docs/the_twist.md` - **FULL SPOILERS** (the hidden narrative hook). Never restate its contents in player-facing copy.

## Naming history
The mod was working-named "Salvage" during design; renamed to **Recompile** (2026-07-14) because "Salvage" is taken on CurseForge/Modrinth by several materials-recovery mods (Salvage!, Salvaged, Salvage Furnace, Create: Salvage) - exactly the mods this would be confused with. "Recompile" names the distinct axis (recover recipes by taking things apart) and has a clean namespace. Mod id / package: `recompile`.

## Conventions (this machine / Jason's mod work)
- Target **NeoForge / MC 26.1** to match the Productive Frogs 2.x line. Mirror `../productive-frogs` for project structure and toolchain.
- Data-driven first (JSON teardown tables, garbage-block loot). Public teardown schema (`recompile:teardown`).
- **No em-dashes or en-dashes, no emoji** in any authored text (Jason's hard rule). ASCII punctuation only.
- **Minimize authored prose** - only quests and technical guidance get writing; players distrust AI writing. Carry meaning through mechanics.
