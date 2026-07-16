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

**Alpha, playable** (2026-07-15). Built against MC `26.1.2` / NeoForge `26.1.2.76`.

Shipped: the garbage world (custom preset, coarse-dirt plain, mounds), the Blocks of Garbage and their variants (bags, bales, Bulky Waste), the trash-tier tools, the Sorting Tarp, dimension lockout, storage (the Scrap Barrel), the food tier (scavenged tin cans, foraged dump mushrooms), and shelter (a mattress dug out of the trash).

Next: **teardown-as-knowledge** (the Recompile Workbench), the mod's distinct axis. Its data spine - the public `recompile:teardown` recipe type - is already in place. See [`docs/roadmap.md`](docs/roadmap.md) for the build order.

## License

MIT (see [LICENSE](LICENSE)).
