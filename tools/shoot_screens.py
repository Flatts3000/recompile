"""Open every custom screen in the dev client and screenshot it.

**This is the only layer that can prove a screen draws.** Slot and element geometry is asserted
server-side by `MenuLayoutTests` because it lives on a `ScreenLayout`, and the layout algebra is
covered by `ScreenLayoutTest` - but neither can see a pixel. A gauge filled from the wrong end passes
both. Issue #164 makes a runClient pass an acceptance criterion for that reason; this makes it a
command rather than twenty minutes of clicking.

Usage, with `./gradlew runClient` already up:

    python tools/shoot_screens.py

Shots land in `run/screenshots/screen_<name>.png`. Exits non-zero if any screen failed to open.

Three things about the harness, all of which cost time to find:

  * **devbridge has no world-interaction verb.** Its `click` drives GUI widgets only, so opening a
    machine's screen has to be a synthesized right-click from outside the protocol.
  * **The game window must be foreground or that click goes nowhere, silently.** Activating it also
    nudges the pointer, which rotates the camera - so activation happens once, up front, and each
    machine re-aims with a `tp` afterwards rather than being framed before it.
  * **`input off` is devbridge's behaviour, which LOCKS input.** A locked client ignores the click.
    It stays on here, which is the opposite of what a screenshot run normally wants.

Everything is placed at absolute coordinates. Player-relative staging drops the player a block the
moment a `fill` clears the floor under their feet, and two machines later the camera is elsewhere.
"""

import os
import subprocess
import sys
import tempfile
import time

PORT = "8605"          # claimed for this repo; see CLAUDE.md on why there is no default
INSTANCE = "run"       # moddev's game directory, NOT the repo root

# A flat stage clear of anything, and the exact spot the player stands on it.
X, Y, Z = 300, 70, 300

SCREENS = [
    ("burner_generator", "recompile:burner_generator"),
    ("hydroponics_bay", "recompile:hydroponics_bay"),
    # The nursery is a multiblock core and refuses to open unformed, so its state is set directly
    # rather than assembling a 2x2 wall the screen has no opinion about.
    ("tree_nursery", "recompile:tree_nursery[formed=true,facing=north]"),
    ("scrap_crafting_table", "recompile:scrap_crafting_table"),
]

FOCUS_PS = """
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class Fg {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
}
'@
$p = Get-Process | Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Select-Object -First 1
if (-not $p) { Write-Output "no minecraft window"; exit 1 }
[Fg]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
[Fg]::BringWindowToTop($p.MainWindowHandle) | Out-Null
[Fg]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
"""

CLICK_PS = """
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class Clk {
  [DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,UIntPtr e);
}
'@
[Clk]::mouse_event(0x0008, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 80
[Clk]::mouse_event(0x0010, 0, 0, 0, [UIntPtr]::Zero)
"""


def powershell(script):
    """Run a PowerShell snippet from a file.

    Via a file rather than `-Command`, and that is not a style choice: both snippets below use an
    `Add-Type` here-string, whose closing `'@` has to sit at column 0 of its own line. Passed through
    `-Command` it does not, the type never compiles, and the failure is silent - the click simply
    does not happen and every screen reports "opened nothing".
    """
    with tempfile.NamedTemporaryFile("w", suffix=".ps1", delete=False, encoding="utf-8") as handle:
        handle.write(script)
        path = handle.name
    try:
        subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", path],
                       capture_output=True, text=True)
    finally:
        os.unlink(path)


def bridge(*args, player=False):
    argv = ["gamebridge", "--devbridge", PORT]
    if player:
        argv += ["--player", "@s"]
    argv += list(args)
    done = subprocess.run(
        [sys.executable, "-c",
         "from gamebridge.cli import main; import sys; sys.argv=%r; main()" % argv],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (done.stdout or "") + (done.stderr or "")


def cmd(command):
    return bridge("cmd", command, player=True)


def main():
    # Assert what answered before anything else, so every step below inherits a checked assumption.
    # Every project that keeps devbridge's default port lands on one socket, and a verifier that
    # connects to the wrong game reports a clean pass about somebody else's world.
    ping = bridge("ping", "--expect-instance", INSTANCE)
    if "worldName" not in ping:
        print("wrong game, or no game on port %s:\n%s" % (PORT, ping))
        return 1

    powershell(FOCUS_PS)
    bridge("input", "on")
    bridge("hud", "off")
    cmd("gamemode creative")
    cmd("clear @s")             # an item in hand makes a right-click place a block instead of using one
    cmd("time set noon")
    cmd("gamerule advance_time false")

    cmd("fill %d %d %d %d %d %d minecraft:stone" % (X - 4, Y - 1, Z - 4, X + 4, Y - 1, Z + 4))
    cmd("fill %d %d %d %d %d %d air" % (X - 4, Y, Z - 4, X + 4, Y + 3, Z + 4))

    failures = []
    for name, block in SCREENS:
        bridge("screen", "close")
        cmd("setblock %d %d %d air" % (X, Y + 1, Z + 2))
        cmd("setblock %d %d %d %s" % (X, Y + 1, Z + 2, block))
        # Yaw 0 faces +Z. The eye sits at feet + 1.62, which is inside the block at Y+1 and above the
        # one at Y - place it a block lower and the crosshair sails over the top of it.
        cmd("tp @s %.1f %d %.1f 0 0" % (X + 0.5, Y, Z + 0.5))
        time.sleep(0.5)

        looking = bridge("look")
        if block.split(":")[1].split("[")[0] not in looking:
            failures.append("%s: crosshair is not on the block\n%s" % (name, looking))
            continue

        powershell(CLICK_PS)
        time.sleep(1.2)
        opened = bridge("screen")
        if "Screen" not in opened:
            failures.append("%s: right-click opened nothing\n%s" % (name, opened))
            continue

        bridge("shot", "screen_" + name)
        klass = next((l for l in opened.splitlines() if l.startswith("screen:")), "?")
        print("captured %-22s %s" % (name, klass))

    bridge("screen", "close")
    bridge("hud", "on")

    if failures:
        print("\nFAILURES:")
        for failure in failures:
            print(" - " + failure)
        return 1
    print("\nall %d screens opened and were captured" % len(SCREENS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
