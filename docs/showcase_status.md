# Showcase scenes - where this got to

**Marker, 2026-08-04.** Phase 1 and 2 of `docs/showcase_spec.md` are built and merged. This file is the
resume point, not a design doc.

## Built and shot

| Scene | State |
| --- | --- |
| `museum` | **Done and approved.** Six recovered masterworks over four loaded pedestals, landfill on the horizon. Ready for the CurseForge gallery |
| `reclaim_before` / `reclaim_after` | **Done, and shot automatically.** Ground-anchored, standing in real terrain with the dump around them, HUD off, identical camera. `tools/shoot_reclaim.py` takes both without a keystroke |

## Shooting them now

Both scenes are driven by tooling rather than by hand. With a dev client up (`./gradlew runClient`,
which opens devbridge on 25580):

```bash
python tools/shoot_reclaim.py            # both frames, HUD off, identical camera
bash tools/verify_showcase.sh            # the museum, placed and asserted (needs runServer)
```

**The pair anchors to the player**, so the script returns them to the same block before each scene.
The function ends by teleporting to the camera, so without that the second scene would place itself
relative to the first shot's viewpoint.

## Known open points

- **The before scene's staged mounds are barely visible** now that the shot sits in real terrain: the
  surrounding dump supplies all the garbage the frame needs. They could probably go entirely.
- **The after scene's tree sits centre-frame** and hides both the kept mound and part of the wheat.
  Moving it off-centre would open the plot up.
- **The museum's right-hand column is sparse** - two 2-tall paintings stacked, against 3 and 4 tall
  neighbours. Swapping Mona Lisa to the right would even the mass.
- **Two empty courses across the top of the museum wall.** Trimming to 9 would tighten it.
- Phase 3, the machine hall, is specced and not started.
