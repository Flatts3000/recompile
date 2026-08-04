# Showcase scenes - where this got to

**Marker, 2026-08-04.** Phase 1 and 2 of `docs/showcase_spec.md` are built and merged. This file is the
resume point, not a design doc.

## Built and shot

| Scene | State |
| --- | --- |
| `museum` | **Done and approved.** Six recovered masterworks over four loaded pedestals, landfill on the horizon. Ready for the CurseForge gallery |
| `reclaim_before` / `reclaim_after` | **Built, shot once, reframed since.** The A/B works and the cameras are provably identical; the first pair was floating and read as an island, so both now anchor to the player and stand on real ground. **Not re-shot yet** |

## The immediate next step

Re-shoot the pair with the ground-anchored framing:

```
/reload
# stand somewhere flat with about 25 blocks of clear ground in front of you, facing north
/function recompile:showcase/reclaim_before   -> F2
/function recompile:showcase/reclaim_after    -> F2
```

Do not move between the two. The second `place` overwrites the first in the same footprint.

## Known open points

- **The before scene's mounds read as scattered rubble**, not a dump. They were sized for a floating
  slab; against real terrain they may want to be bigger and denser, or removed entirely so the
  surrounding world supplies the garbage.
- **The after scene's kept mound is hidden** behind a tree from this camera.
- **The museum's right-hand column is sparse** - two 2-tall paintings stacked, against 3 and 4 tall
  neighbours. Swapping Mona Lisa to the right would even the mass.
- **Two empty courses across the top of the museum wall.** Trimming to 9 would tighten it.
- Phase 3, the machine hall, is specced and not started.
