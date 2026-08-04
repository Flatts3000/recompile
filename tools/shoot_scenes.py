"""Shoot showcase scenes automatically. Needs a dev client with devbridge on port 25580.

    python tools/shoot_scenes.py                          # the reclamation pair
    python tools/shoot_scenes.py machine_hall             # one scene
    python tools/shoot_scenes.py --at 300 300 machine_hall

The pair is only worth anything if the two frames share a camera to the pixel, and three things have
to be right for that:

  * The plot is PLAYER-ANCHORED, so the player must stand on the same block for both. The function
    ends by teleporting them to the camera, so this returns them to the anchor before the second one
    - otherwise the second scene places itself relative to the first shot's viewpoint.
  * Commands run with `player=` so `@s` and `~` mean something. As the console they do not, and the
    scene places correctly while silently not moving the camera.
  * The HUD is hidden around both shots rather than per shot, because Screenshot.grab captures the
    frame already drawn.
"""

import sys
import time

sys.path.insert(0, "F:/minecraft-repos/mc-pack-toolkit/gamebridge")
from gamebridge.devbridge import DevBridge   # noqa: E402

PLAYER = "Dev"

args = sys.argv[1:]
if args[:1] == ["--at"]:
    ANCHOR_XZ = (int(args[1]), int(args[2]))
    args = args[3:]
else:
    ANCHOR_XZ = (100, 100)
# The pair by default, because they are the ones that must share a camera and so must be shot
# together. Any other scene can be named on its own.
SCENES = tuple(args) if args else ("reclaim_before", "reclaim_after")


def find_surface(bridge, x: int, z: int, high: int = 110, low: int = 55) -> int:
    """The y a player would stand on at these coordinates.

    Worth doing rather than hardcoding: the garbage world is mounds, so a y that is open air at one
    spot is inside a hill twenty blocks away. Getting it wrong puts the camera inside a block, which
    is a screenshot of the inside of a block.
    """
    for y in range(high, low, -1):
        solid = bridge.command(f"execute unless block {x} {y - 1} {z} minecraft:air")
        clear = bridge.command(f"execute if block {x} {y} {z} minecraft:air")
        if "passed" in solid and "passed" in clear:
            return y
    raise SystemExit(f"no surface found at {x},{z} between y {low} and {high}")


def main() -> None:
    x, z = ANCHOR_XZ
    with DevBridge(port=25580) as bridge:
        # Chunks unload with nobody in them, and /place into an unloaded chunk quietly does nothing.
        bridge.command(f"forceload add {x - 20} {z - 20} {x + 40} {z + 40}")
        y = find_surface(bridge, x, z)
        print(f"anchor: {x} {y} {z}")
        bridge.command(f"gamemode spectator {PLAYER}")
        bridge.hud(False)
        try:
            for scene in SCENES:
                bridge.command(f"tp @s {x} {y} {z}", player=PLAYER)
                bridge.command(f"function recompile:showcase/{scene}", player=PLAYER)
                time.sleep(2.0)   # let the chunk meshes rebuild before grabbing the frame
                print(f"{scene}: {bridge.screenshot(scene).get('path')}")
        finally:
            bridge.hud(True)
            bridge.command(f"gamemode creative {PLAYER}")


if __name__ == "__main__":
    main()
