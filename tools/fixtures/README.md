# Dev fixtures

Datapacks that put the game into a state the shipped content cannot reach, so a screen or a mechanic
can actually be looked at. Copy one into `run/saves/devworld/datapacks/` and restart the client;
`run/` is gitignored, so nothing here leaks into a build.

These are **not tests.** They are the setup step for a manual check, and each exists because the
thing it exposes lives in a layer neither test layer can see.

## `freight_six_lines`

Overrides freight phase 1 with **six** goods instead of the shipped two, which is the only way to
make the Freight Terminal's manifest scroll.

`FreightManifest.MAX_LINES` is 6 and `FreightTerminalMenu.VISIBLE_LINES` is 4, so the scrolling half
of that screen is unreachable in an ordinary game: every shipped phase asks for two goods, `scroll`
is always 0, and both the scroll tail and every index that depends on `scroll` are dead code in
practice. `recompile:freight_phase` is public API, so a pack reaches all of it with one file.

Two defects were found this way and neither was visible without it:

- **The delivered count was read by screen row rather than by manifest line**, so a scrolled manifest
  drew each line's item and quota beside a different line's progress - and since `done` drives the
  green, a line rendered finished while it was not.
- **The "+N more" hint was drawn inside the last row**, across that row's own name and the bottom of
  its item icon. The panel is 230 rather than 220 so the hint has its own band.

To use it:

```bash
cp -r tools/fixtures/freight_six_lines run/saves/devworld/datapacks/
./gradlew runClient
```

Then place a Freight Terminal, open it, and scroll. To give one line a delivery so the counts differ
between lines - which is what makes a wrong index visible rather than a column of zeroes:

```bash
gamebridge --devbridge 8605 --player @s cmd \
  "item replace block <x> <y> <z> container.0 with minecraft:dirt 64"
```

The terminal's ticker consumes exactly the quota and leaves the remainder in the strip. Freight
progress is world state rather than terminal state, so it survives breaking and replacing the block.

Delete the directory from `run/saves/devworld/datapacks/` when finished; leaving it in place makes
the world's first rung ask for goods the shipped ladder never asks for.
