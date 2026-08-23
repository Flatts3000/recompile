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
1. Runs `./gradlew runServer`, which uses `run/server.properties` - already pinned to
   `level-type=recompile\\:garbage` - until the server reports ready.
2. Stops it over RCON, so the world is saved and `session.lock` released.
3. Copies the result into `run/saves/<name>`, where the client looks.
4. Sets `confirmedExperimentalSettings` in its `level.dat`, because a custom-preset world is flagged
   experimental and the client will otherwise stop on a "Here be dragons!" modal - the third
   silent, screen-only blocker in this same path.

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

# Generating spawn on this flat preset takes seconds. The cap is a backstop for a first run that also
# has to resolve dependencies and decompile Minecraft.
TIMEOUT_SECONDS = 900

# Any one of these means the server is up. Matching only one turns a working run into an unexplained
# timeout, which is how the first version of this script failed.
READY_MARKERS = ("For help, type", "RCON running on", 'Done (')


def properties() -> dict[str, str]:
    if not PROPERTIES.is_file():
        sys.exit(f"{PROPERTIES} not found - run `./gradlew runServer` once to create it.")
    found = {}
    for line in PROPERTIES.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            found[key.strip()] = value.strip()
    return found


def rcon(port: int, password: str, command: str) -> None:
    """Send one command. This server closes the connection after each, so never reuse the socket."""
    def pack(request_id: int, kind: int, body: str) -> bytes:
        payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(payload)) + payload

    with socket.create_connection(("127.0.0.1", port), timeout=30) as sock:
        sock.sendall(pack(1, 3, password))
        sock.recv(4096)
        sock.sendall(pack(2, 2, command))
        sock.recv(4096)


def generate(config: dict[str, str]) -> Path:
    """Run the dedicated server until it is ready, stop it cleanly, and hand back the world."""
    level = config.get("level-name", "world")
    world = RUN / level
    if "recompile" not in config.get("level-type", ""):
        sys.exit(f"{PROPERTIES} does not set level-type=recompile\\:garbage.\n"
                 "Without it the server generates a DEFAULT world and the preset is ignored "
                 "silently, which is exactly the trap this script exists to avoid.")
    if config.get("enable-rcon") != "true" or not config.get("rcon.password"):
        sys.exit(f"{PROPERTIES} has no usable RCON, which is how this script stops the server.")
    port = int(config.get("rcon.port", "25575"))

    # A PORT CLASH IS THE MOST LIKELY FAILURE AND THE WORST-REPORTED ONE. A server left running from
    # an earlier session keeps 25565 and 25575, the new one dies with "Perhaps a server is already
    # running on that port?", and gradle still says BUILD SUCCESSFUL. Better to say so up front than
    # to let the caller read a green build as a green run.
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

    # OUTPUT GOES TO A FILE AND THE SERVER IS STOPPED OVER RCON, rather than reading its stdout and
    # terminating the process. Two reasons, both paid for while writing this: terminate() kills the
    # GRADLE process and leaves the forked server JVM running, which then holds the world so the next
    # run cannot start at all; and driving it through a stdin pipe made the server exit in 11 seconds
    # having logged nothing this could match.
    # mkstemp hands back an OPEN descriptor as well as a path; leaving it open means the file
    # cannot be deleted on Windows at the end of a perfectly successful run.
    handle, log_path = tempfile.mkstemp(prefix="make_dev_world_", suffix=".log")
    os.close(handle)
    log = Path(log_path)
    print(f"generating {world} with the garbage preset (log: {log}) ...")
    with log.open("w", encoding="utf-8") as sink:
        process = subprocess.Popen([str(wrapper), "runServer", "--console=plain"],
                                   cwd=REPO, env=env, stdout=sink, stderr=subprocess.STDOUT,
                                   stdin=subprocess.DEVNULL)
        started = time.monotonic()
        ready = False
        while time.monotonic() - started < TIMEOUT_SECONDS:
            if process.poll() is not None:
                break
            body = log.read_text(encoding="utf-8", errors="replace")
            if any(marker in body for marker in READY_MARKERS):
                ready = True
                break
            time.sleep(2)

        if not ready:
            process.kill()
            # THE SERVER'S OWN COMPLAINTS, not gradle's epilogue. A failed boot ends with twenty lines
            # of deprecation notices and "BUILD SUCCESSFUL", because the JVM exits 0 after writing a
            # crash report - so a plain tail shows a successful build and hides the cause completely.
            # That is exactly how a port clash read as an unexplained 12-second exit.
            lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
            complaints = [line for line in lines
                          if "/ERROR]" in line or "/WARN]" in line or "/FATAL]" in line]
            print("\n".join(complaints[-20:] or lines[-20:]), file=sys.stderr)
            sys.exit("\nthe server never reported ready, so no world was generated. Its own error "
                     "lines are above.")

        print("server up; stopping it to flush the world ...")
        try:
            rcon(port, config["rcon.password"], "stop")
        except OSError as failed:
            process.kill()
            sys.exit(f"could not reach RCON on {port} to stop the server: {failed}")
        try:
            process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.kill()
            sys.exit("the server did not exit after `stop`.")

    try:
        log.unlink(missing_ok=True)
    except OSError:
        pass   # a leftover log is not worth failing a good run over

    if not (world / "level.dat").is_file():
        sys.exit(f"the server ran but {world} has no level.dat.")
    return world


# TAG_Byte, big-endian name length, then the name. Matching the tag header rather than the bare
# string means this cannot hit the same characters appearing inside some other value.
CONFIRM_FLAG = b"\x01" + len(b"confirmedExperimentalSettings").to_bytes(2, "big") \
    + b"confirmedExperimentalSettings"


def confirm_experimental_settings(world: Path) -> bool:
    """Pre-answer the "Here be dragons!" modal that blocks loading a custom-preset world.

    <p>A world generated from a world_preset is flagged experimental, and the client refuses to load
    one until a human clicks through a confirmation. That modal is a SCREEN, so it logs nothing and an
    unattended client simply sits on it forever - the third failure of exactly this shape found while
    fixing #289, after the missing world and the quoted name.

    <p>Patched at the byte level rather than through an NBT library, because this repo has no NBT
    dependency for tooling and the tag is a single byte in a known header. Returns False if the tag is
    not found, so a silent no-op cannot masquerade as success.
    """
    raw = gzip.decompress(world.joinpath("level.dat").read_bytes())
    at = raw.find(CONFIRM_FLAG)
    if at < 0:
        return False
    value = at + len(CONFIRM_FLAG)
    if raw[value] == 1:
        return True
    patched = raw[:value] + b"\x01" + raw[value + 1:]
    world.joinpath("level.dat").write_bytes(gzip.compress(patched))
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
    if not confirm_experimental_settings(target):
        print("WARNING: could not find confirmedExperimentalSettings in level.dat, so the client "
              "will stop on the 'Here be dragons!' modal and never load the world.", file=sys.stderr)
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

    world = generate(properties())
    installed = install(world, args.name)
    regions = len(list(installed.rglob("*.mca")))
    print(f"installed {installed} ({regions} region files)")
    print("`./gradlew runClient` will now boot into it, and devbridge will open on 8605.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
