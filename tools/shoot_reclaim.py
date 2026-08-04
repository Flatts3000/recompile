"""Shoot the reclamation pair automatically. Needs a dev client with devbridge on port 25580.

    python tools/shoot_reclaim.py [x y z]

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
ANCHOR = tuple(int(a) for a in sys.argv[1:4]) if len(sys.argv) >= 4 else (100, 69, 100)
SCENES = ("reclaim_before", "reclaim_after")


def main() -> None:
    x, y, z = ANCHOR
    with DevBridge(port=25580) as bridge:
        # Chunks unload with nobody in them, and /place into an unloaded chunk quietly does nothing.
        bridge.command(f"forceload add {x - 20} {z - 20} {x + 40} {z + 40}")
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
