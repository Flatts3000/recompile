# Recompile

A standalone **NeoForge** mod (target MC 26.1). Its core is **teardown-as-knowledge**: tear an item down and you recover not just its materials but its **recipe** - you reverse-engineer how the old world was made by picking through its pieces. Unlearned technology can't be crafted until you have studied it.

This is the distinct axis. Plenty of mods recycle equipment back into materials; Recompile recovers the *know-how*.

## What's in the mod

- **Teardown-as-knowledge** - a workbench where you disassemble found items for components plus a chance to study their recipe. Deterministic study points (repeat teardowns complete the study); learned recipes unlock permanently. World-agnostic - works in any pack.
- **The garbage-world systems** that power the [Trashlands](https://github.com/Flatts3000/trashlands) modpack: the coarse-dirt world preset, Blocks of Garbage, garbage regions, and mound regrowth (renewable quarries that rain back down from the sky).
- **Data-driven teardown tables** (JSON) - a public schema so packs and addons extend the teardown tree without a mod release. Cross-mod teardown is the content.

## Relationship to Trashlands

Recompile is the engine; **Trashlands** is its showcase modpack (the Productive Frogs -> Sky Frogs pattern: a standalone mod, plus a pack built to show it off). The full design lives in the Trashlands repo:

- Design docs: https://github.com/Flatts3000/trashlands (`docs/`)

## Status

**Design phase - no code yet** (2026-07-14). The design is locked through the mid-game tiers; the next milestone is a one-week feasibility slice (custom garbage worldgen + one garbage block that tears down into sorted materials). The Gradle NeoForge project is added when the slice starts, against the confirmed NeoForge / MC 26.1 toolchain.

## License

MIT (see [LICENSE](LICENSE)).
