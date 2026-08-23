#!/usr/bin/env python3
"""Create the world `./gradlew runClient` boots into, with this mod's own generator.

Why this exists
---------------
`runClient` passes `--quickPlaySingleplayer <name>`. Vanilla's quick play does NOT create a world when
that name is missing: `QuickPlay.joinSingleplayerWorld` checks `levelExists` and otherwise shows a
"Failed to Quick Play / Could not find world with the provided identifier" SCREEN. A screen logs
nothing, so from a terminal the symptom is a client that sits at a menu forever and a devbridge socket
that never opens, with no error anywhere to grep for. That cost four debugging attempts and one wrong
issue (#289) before anybody looked at the game window.

And the world cannot be made by the client. `--quickPlaySingleplayer` on a missing name errors rather
than creating, and a world the client does create through the GUI is a vanilla default one that
silently ignores `recompile:garbage`. The only thing that applies the preset headlessly is a dedicated
server with `level-type` set, which is what this drives.

What it does
------------
1. Points `run/server.properties` at a level name **this script owns**, and deletes it first.
2. Runs `./gradlew runServer` until RCON is up, then stops it over RCON so the world is flushed.
3. Copies the result into `run/saves/<name>`, where the client looks.
4. Sets `confirmedExperimentalSettings` in its `level.dat`, because a custom-preset world is flagged
   experimental and the client will otherwise stop on a "Here be dragons!" modal - the third silent,
   screen-only blocker in this same path.

**It generates into its own directory and restores `server.properties` afterwards.** A server reboots
an existing world rather than regenerating it, and the generator is baked into `level.dat` at creation
- so reusing whatever `level-name` happens to point at would quietly install a copy of that world. In
this repo that is `run/showcase`, the museum world `tools/verify_showcase.sh` builds, and on a fresh
clone it is a vanilla-default `run/world`: exactly the thing this script's whole purpose is to avoid
handing you. Review of #291 caught it; the first version only ever worked because the person testing it
happened to delete the old world by hand every time.

Usage
-----
    python tools/make_dev_world.py                 # build it if missing
    python tools/make_dev_world.py --force         # regenerate from scratch
    python tools/make_dev_world.py --name other    # a differently named world

The name must match `build.gradle`'s `--quickPlaySingleplayer` argument, and **must not contain a
space**: moddev writes program arguments into `build/moddev/clientRunProgramArgs.txt` and quotes any
value containing one, so `New World` reaches the game as `"New World"` with the quotes included and
matches no directory on disk. That was the other half of #289.
"""
from __future__ import annotations

import argparse
import gzip
import os
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RUN = REPO / "run"
SAVES = RUN / "saves"
PROPERTIES = RUN / "server.properties"
JDK = Path("C:/Program Files/Java/jdk-25")

# The name build.gradle passes to --quickPlaySingleplayer. No spaces; see the module docstring.
DEFAULT_NAME = "devworld"

# Time allowed for the SERVER to come up, measured from the moment it starts booting rather than from
# the gradle invocation - a cold run has to resolve dependencies and decompile Minecraft first, and
# charging that to the server's budget kills a build that was working.
BUILD_TIMEOUT = 1800
SERVER_TIMEOUT = 300
BOOT_MARKER = "Starting minecraft server"

# RCON SPECIFICALLY, not "Done (" or "For help, type". DedicatedServer.initServer logs those BEFORE it
# creates the RCON listener, so treating them as ready races the socket and the first connect is
# refused.
READY_MARKER = "RCON running on"


def properties() -> dict[str, str]:
    if not PROPERTIES.is_file():
        sys.exit(f"{PROPERTIES} not found - run `./gradlew runServer` once to create it, then set "
                 "level-type=recompile\\:garbage in it.")
    found = {}
    for line in PROPERTIES.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            found[key.strip()] = value.strip()
    return found


def set_level_name(name: str) -> str:
    """Point the server at `name`, returning whatever it was pointing at before."""
    body = PROPERTIES.read_text(encoding="utf-8", errors="replace")
    previous = "world"
    out = []
    seen = False
    for line in body.splitlines():
        if line.startswith("level-name="):
            previous = line.split("=", 1)[1].strip()
            out.append(f"level-name={name}")
            seen = True
        else:
            out.append(line)
    if not seen:
        out.append(f"level-name={name}")
    PROPERTIES.write_text("\n".join(out) + "\n", encoding="utf-8")
    return previous


def kill_tree(process: subprocess.Popen) -> None:
    """Kill gradle AND the server it forked.

    <p>Killing only the gradle process leaves the server JVM alive holding the world and ports 25565
    and 25575, so the next run cannot start at all and the script's own port precheck tells the user to
    "stop it first" with no way to do so short of Task Manager. The first version did exactly that on
    three separate failure paths while carrying a comment warning against it.
    """
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        process.kill()
    try:
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        pass


def rcon(port: int, password: str, command: str) -> None:
    """Send one command. This server closes the connection after each, so never reuse the socket."""
    def pack(request_id: int, kind: int, body: str) -> bytes:
        payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(payload)) + payload

    with socket.create_connection(("127.0.0.1", port), timeout=30) as sock:
        sock.sendall(pack(1, 3, password))
        reply = sock.recv(4096)
        # A REJECTED LOGIN ANSWERS WITH REQUEST ID -1 and is otherwise a perfectly normal packet. Not
        # checking it means the `stop` below is never run, the wait times out three minutes later, and
        # the script blames the server for not stopping.
        if len(reply) >= 8 and struct.unpack("<i", reply[4:8])[0] == -1:
            raise OSError("RCON rejected the password from server.properties")
        sock.sendall(pack(2, 2, command))
        sock.recv(4096)


def stop_server(port: int, password: str, process: subprocess.Popen) -> None:
    """Ask the server to stop, retrying briefly, and fall back to killing the whole tree."""
    for attempt in range(6):
        try:
            rcon(port, password, "stop")
            break
        except OSError as failed:
            if attempt == 5:
                print(f"could not reach RCON to stop the server ({failed}); killing it instead.",
                      file=sys.stderr)
                kill_tree(process)
                return
            time.sleep(2)
    try:
        process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        print("the server did not exit after `stop`; killing it.", file=sys.stderr)
        kill_tree(process)


def generate(config: dict[str, str], name: str) -> Path:
    """Run the dedicated server on a world this script owns, stop it, and hand the world back."""
    if "recompile" not in config.get("level-type", ""):
        sys.exit(f"{PROPERTIES} does not set level-type=recompile\\:garbage.\n"
                 "Without it the server generates a DEFAULT world and the preset is ignored "
                 "silently, which is exactly the trap this script exists to avoid.")
    if config.get("enable-rcon") != "true" or not config.get("rcon.password"):
        sys.exit(f"{PROPERTIES} has no usable RCON, which is how this script stops the server.")
    port = int(config.get("rcon.port", "25575"))
    password = config["rcon.password"]

    # A PORT CLASH IS THE MOST LIKELY FAILURE AND THE WORST-REPORTED ONE. A server left running from
    # an earlier session keeps 25565 and 25575, the new one dies with "Perhaps a server is already
    # running on that port?", and gradle still says BUILD SUCCESSFUL because the JVM exits 0 after
    # writing a crash report. Better to say so up front than to let a green build read as a green run.
    for busy in (int(config.get("server-port", "25565")), port):
        with socket.socket() as probe:
            probe.settimeout(2)
            if probe.connect_ex(("127.0.0.1", busy)) == 0:
                sys.exit(f"port {busy} is already in use, so a new server cannot bind it. A dev "
                         f"server from an earlier session is probably still running - stop it first.")

    env = dict(os.environ)
    if JDK.is_dir():
        # The machine's JAVA_HOME points at a JDK that is not there; every gradle call in this repo
        # overrides it, so do the same rather than failing in a confusing way.
        env["JAVA_HOME"] = str(JDK)
    wrapper = REPO / ("gradlew.bat" if os.name == "nt" else "gradlew")

    world = RUN / name
    handle, log_path = tempfile.mkstemp(prefix="make_dev_world_", suffix=".log")
    os.close(handle)   # mkstemp leaves it OPEN, and Windows will not delete a file that still is
    log = Path(log_path)

    previous = set_level_name(name)
    try:
        if world.exists():
            shutil.rmtree(world)   # ours to delete: the script chose this name
        print(f"generating {world} with the garbage preset (log: {log}) ...")
        with log.open("w", encoding="utf-8") as sink:
            process = subprocess.Popen([str(wrapper), "runServer", "--console=plain"],
                                       cwd=REPO, env=env, stdout=sink, stderr=subprocess.STDOUT,
                                       stdin=subprocess.DEVNULL)
            started = time.monotonic()
            booted = None
            ready = False
            while True:
                if process.poll() is not None:
                    break
                body = log.read_text(encoding="utf-8", errors="replace")
                if READY_MARKER in body:
                    ready = True
                    break
                if booted is None and BOOT_MARKER in body:
                    booted = time.monotonic()
                limit = SERVER_TIMEOUT if booted else BUILD_TIMEOUT
                if time.monotonic() - (booted or started) > limit:
                    break
                time.sleep(2)

            if not ready:
                kill_tree(process)
                # THE SERVER'S OWN COMPLAINTS, not the tail. A failed boot ends in twenty lines of
                # deprecation notices and "BUILD SUCCESSFUL", because the JVM exits 0 after writing a
                # crash report - so a plain tail shows a successful build and hides the cause. That is
                # exactly how a port clash read as an unexplained 12-second exit.
                lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
                complaints = [line for line in lines
                              if "/ERROR]" in line or "/WARN]" in line or "/FATAL]" in line]
                print("\n".join(complaints[-20:] or lines[-20:]), file=sys.stderr)
                sys.exit("\nthe server never reported ready, so no world was generated. Its own "
                         "error lines are above.")

            print("server up; stopping it to flush the world ...")
            stop_server(port, password, process)
    finally:
        set_level_name(previous)
        try:
            log.unlink(missing_ok=True)
        except OSError:
            pass   # a leftover log is not worth failing a good run over

    if not (world / "level.dat").is_file():
        sys.exit(f"the server ran but {world} has no level.dat.")
    return world


# TAG_Byte, big-endian name length, then the name. Matching the tag header rather than the bare string
# means this cannot hit the same characters appearing inside some other value.
CONFIRM_NAME = b"confirmedExperimentalSettings"
CONFIRM_FLAG = b"\x01" + len(CONFIRM_NAME).to_bytes(2, "big") + CONFIRM_NAME


def confirm_experimental_settings(world: Path) -> bool:
    """Pre-answer the "Here be dragons!" modal that blocks loading a custom-preset world.

    <p>A world generated from a world_preset is flagged experimental, and the client refuses to load
    one until a human clicks through a confirmation. That modal is a SCREEN, so it logs nothing and an
    unattended client simply sits on it forever - the third failure of exactly this shape found while
    fixing #289, after the missing world and the quoted name.

    <p>Patched at the byte level rather than through an NBT library, because this repo has no NBT
    dependency for tooling and the tag is a single byte in a known header. Returns False rather than
    raising if the tag is missing or truncated, and the caller treats that as a failure.
    """
    level = world / "level.dat"
    raw = gzip.decompress(level.read_bytes())
    at = raw.find(CONFIRM_FLAG)
    if at < 0:
        return False
    value = at + len(CONFIRM_FLAG)
    if value >= len(raw):
        return False   # truncated level.dat: a stack trace here would hide a clear failure
    if raw[value] == 1:
        return True
    level.write_bytes(gzip.compress(raw[:value] + b"\x01" + raw[value + 1:]))
    return True


def install(world: Path, name: str) -> Path:
    target = SAVES / name
    SAVES.mkdir(parents=True, exist_ok=True)
    if target.exists():
        shutil.rmtree(target)
    # session.lock is SKIPPED rather than copied and deleted after. The client treats a locked world
    # as in use and refuses it, which looks identical to the world not existing - but the bigger
    # problem is the copy itself, since Windows raises WinError 33 on a handle the server may still
    # be closing and that takes the whole install down with it.
    shutil.copytree(world, target, ignore=shutil.ignore_patterns("session.lock"))
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--name", default=DEFAULT_NAME,
                        help=f"world name for the client (default {DEFAULT_NAME})")
    parser.add_argument("--force", action="store_true",
                        help="regenerate even if the world is already there")
    args = parser.parse_args()
    sys.stdout.reconfigure(line_buffering=True)

    if " " in args.name:
        sys.exit("the world name must not contain a space - moddev quotes such a value into the "
                 "program-arguments file, and the game then looks for a directory whose name "
                 "includes the quote characters. See #289.")

    target = SAVES / args.name
    if target.is_dir() and not args.force:
        print(f"{target} already exists; nothing to do (pass --force to rebuild).")
        return 0

    world = generate(properties(), args.name)
    installed = install(world, args.name)
    if not confirm_experimental_settings(installed):
        # NON-ZERO, because the client would sit on the "Here be dragons!" modal forever - the exact
        # unattended hang this script exists to prevent. Printing a warning and returning 0 lets any
        # wrapper read a pass while the thing is broken.
        print(f"ERROR: no confirmedExperimentalSettings tag in {installed}/level.dat, so the client "
              "will stop on the experimental-settings modal and never load the world.",
              file=sys.stderr)
        return 1
    regions = len(list(installed.rglob("*.mca")))
    print(f"installed {installed} ({regions} region files)")
    print("`./gradlew runClient` will now boot into it, and devbridge will open on 8605.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
