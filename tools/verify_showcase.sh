#!/usr/bin/env bash
# Place the museum on a running dev server and assert it landed. See docs/showcase_spec.md.
#
# Needs `./gradlew runServer` up with RCON on. Chunks unload with nobody standing in them, so the
# forceload is not optional: without it every check below fails with "not loaded" rather than saying
# anything about the scene.
set -euo pipefail
BR="python -m gamebridge.cli"

$BR wait --for 240
$BR cmd "forceload add -16 -16 48 48" >/dev/null
$BR cmd "function recompile:showcase/museum" >/dev/null

$BR check "block 6 125 0 recompile:corrugated_metal"
$BR check "block 6 120 8 recompile:pressed_junk_block"
$BR check "block 2 121 2 recompile:display_pedestal"
$BR check "entity @e[type=minecraft:painting]" --count 6
echo "museum verified"
