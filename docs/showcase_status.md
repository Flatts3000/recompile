# Showcase scenes - where this got to

**Marker, 2026-08-04.** Phase 1 and 2 of `docs/showcase_spec.md` are built and merged. This file is the
resume point, not a design doc.

## Built and shot

| Scene | State |
| --- | --- |
| `museum` | **Done and approved.** Six recovered masterworks over four loaded pedestals, landfill on the horizon. Ready for the CurseForge gallery |
| `machine_wall` | **Done.** Sixteen machines in one plane plus the Separator, assembled by the game, standing in the dump |
| `reclaim_before` / `reclaim_after` | **Done, and shot automatically.** Ground-anchored, standing in real terrain with the dump around them, HUD off, identical camera. `tools/shoot_reclaim.py` takes both without a keystroke |

## Where the images go

`run/screenshots/` is gitignored, so a shot is not in the project until it is processed:

```bash
python tools/prepare_gallery.py     # crop to subject, fit under 2 MB, write the numbered files
```

Output lands in `docs/cf image gallery/`, numbered into the running order rather than appended.
**The numbers are the gallery's order and the order is an argument:** theme first, because Theme Fit
is the pillar this entry is weakest on and a judge skims the strip before reading a word. The museum
leads, the reclamation pair follows as one beat, and the materials tier sits at the back. See
`../mod-jam-2026/round_1_rewards_analysis.md`. **CurseForge rejects a gallery
image over 2 MB**, and the raw 1920x1080 captures are 1.5 to 2.6 MB, so this step is not optional.
`_originals/` there is untracked and holds raw captures; only the numbered files are committed.

**The reclamation pair shares one crop constant**, and the tool refuses to run if somebody splits it
into two. Cropping those frames separately would reintroduce exactly the difference the identical
camera exists to remove.

## Shooting them now

Both scenes are driven by tooling rather than by hand. With a dev client up (`./gradlew runClient`,
which opens devbridge on **8605**, the port claimed for this repo):

```bash
python tools/shoot_scenes.py                       # the reclamation pair
python tools/shoot_scenes.py --at 400 400 machine_wall
bash tools/verify_showcase.sh            # the museum, placed and asserted (needs runServer)
```

**The window size is forced to 1920x1080** in the client run. A screenshot IS the framebuffer, so it
comes out at whatever the window happens to be - the default gave 854x480, unusable next to a gallery
of 1300px images.

**The pair anchors to the player**, so the script returns them to the same block before each scene.
The function ends by teleporting to the camera, so without that the second scene would place itself
relative to the first shot's viewpoint. The anchor's ground height is found by probing rather than
hardcoded, because the garbage world is mounds and a y that is open air in one place is inside a hill
twenty blocks away.

**The machine showcase is a wall, not a floor** (owner, 2026-08-04). Laid out as a workshop the
machines occlude each other and shrink with distance, and no camera catches the Separator without
losing the bench behind it. One plane gives every machine the same size and the same distance, which
is why the museum works.

**Multiblocks are assembled by the game, not written into the structure.** A scene lists a machine's
loose components and the generator emits the setblocks; `tryForm` supplies the formed cells. That
matters because a formed cell carries a `CELL` index driving the whole-machine skin, and transcribing
those by hand would both scramble the skin and put a second copy of the blueprint in the tool.

## Known open points

- **The before scene's staged mounds are barely visible** now that the shot sits in real terrain: the
  surrounding dump supplies all the garbage the frame needs. They could probably go entirely.
- **The after scene's tree sits centre-frame** and hides both the kept mound and part of the wheat.
  Moving it off-centre would open the plot up.
- **The museum's right-hand column is sparse** - two 2-tall paintings stacked, against 3 and 4 tall
  neighbours. Swapping Mona Lisa to the right would even the mass.
- **Two empty courses across the top of the museum wall.** Trimming to 9 would tighten it.

