"""Shoot showcase scenes automatically. Needs a dev client with devbridge on port 8605.

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

It also proves what answered the port before it drives anything - see `assert_it_is_this_repo`.
"""

import sys
import time

try:
    from gamebridge.devbridge import DevBridge
except ImportError:
    sys.exit("gamebridge is not installed. It now ships with the devbridge mod it talks to:\n"
             '  pip install "gamebridge @ git+https://github.com/Flatts3000/devbridge.git'
             '#subdirectory=gamebridge"')

# The port this repo has CLAIMED. Not devbridge's 25580 default, which every project that copies the
# example lands on together - see build.gradle's run block.
PORT = 8605

# "@s" rather than a hardcoded account name: devbridge resolves it to the only player online, so this
# works whoever is signed in. A name that does not match fails loudly ("no player named ..."), it does
# not quietly fall back to the console - but it still fails, and there is nothing to gain by naming.
PLAYER = "@s"

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


def assert_it_is_this_repo(bridge) -> None:
    """Prove the game that answered is Recompile's dev client and not somebody else's.

    A fixed port is a guess about who is listening, and claiming one prevents a clash without
    detecting it: `ports check` enumerates IPv4 only while devbridge binds `getLoopbackAddress()`,
    which is `::1` here, so the registry calls a port free while a game holds it. `ping` cannot settle
    it either - it reports dedicated vs integrated, and two projects' dev clients are both integrated.

    This is not hypothetical. Trashlands' quest verifier connected to THIS repo's client and reported
    six item ids resolving: not a lie so much as an answer about the wrong game, and far worse than an
    error because it looked like a pass.

    The probe hands a Recompile-only id to the command parser, which rejects an unknown item as a
    PARSE error before the command looks for anything to act on. The selector deliberately matches
    nobody, which is the load-bearing part - against a singleplayer world with a real player connected,
    a bare @a would hand them the item instead of merely asking about it.
    """
    out = bridge.command("give @a[tag=recompile_sentinel_matches_nobody] recompile:garbage_block")
    # Item known -> the selector is what fails ("No player was found"). Item unknown -> the parse
    # fails first, and whatever answered is not running this mod.
    if "Unknown item" in out:
        raise SystemExit(
            f"port {PORT} answered, but it is not Recompile's dev client - recompile:garbage_block "
            f"does not exist there. Something else is on this port; shooting it would produce "
            f"screenshots of the wrong game. Reply was: {out.strip()!r}")


def main() -> None:
    x, z = ANCHOR_XZ
    with DevBridge(port=PORT) as bridge:
        assert_it_is_this_repo(bridge)
        # Chunks unload with nobody in them, and /place into an unloaded chunk quietly does nothing.
        bridge.command(f"forceload add {x - 20} {z - 20} {x + 40} {z + 40}")
        y = find_surface(bridge, x, z)
        print(f"anchor: {x} {y} {z}")
        # player= on these two as well: they target @s now, and as the console @s matches nothing.
        bridge.command(f"gamemode spectator {PLAYER}", player=PLAYER)
        bridge.hud(False)
        try:
            for scene in SCENES:
                bridge.command(f"tp @s {x} {y} {z}", player=PLAYER)
                bridge.command(f"function recompile:showcase/{scene}", player=PLAYER)
                time.sleep(2.0)   # let the chunk meshes rebuild before grabbing the frame
                # Re-pose IMMEDIATELY before the grab. The shot depends on where the player is
                # looking, and anything that touches the window during that pause - an alt-tab, a
                # nudged mouse - silently changes the picture.
                #
                # BACK TO THE ANCHOR FIRST. The camera function is written in coordinates relative to
                # the anchor, so running it while already standing at the camera moves the player a
                # second time by the same offset. Found by testing the recovery rather than assuming
                # it: the frame came back level and pointing the right way, from the wrong place.
                bridge.command(f"tp @s {x} {y} {z}", player=PLAYER)
                bridge.command(f"function recompile:showcase/{scene}_camera", player=PLAYER)
                print(f"{scene}: {bridge.screenshot(scene).get('path')}")
        finally:
            bridge.hud(True)
            bridge.command(f"gamemode creative {PLAYER}", player=PLAYER)


if __name__ == "__main__":
    main()
