# Dev tooling

How this repo's assets are generated (texgen for textures, sfxgen for audio), the vanilla resource checklist pipeline, and how to drive a running game from outside with devbridge / gamebridge.
Relocated from CLAUDE.md on 2026-09-10; CLAUDE.md keeps a one-line index entry pointing here.

- [Textures (texgen)](#textures-texgen)
- [Audio (sfxgen)](#audio-sfxgen)
- [Resource checklist](#resource-checklist)
- [Driving a running game (devbridge)](#driving-a-running-game-devbridge)

## Textures (texgen)

Textures are **generated, never hand-drawn**, by the shared engine at `../mc-pack-toolkit/texgen/`
(install: `pip install -e F:/minecraft-repos/mc-pack-toolkit/texgen`). This repo carries `texgen.toml`,
which declares every surface (prompt, backend, variants/faces). Workflow:
`texgen generate --surface X` -> `texgen sheet` -> `select X <idx>...` -> `promote` -> `validate`.

`select` takes **one ref per slot, in slot order** - so a 4-face surface takes four refs. Pass **bare
indices** (`select washing_machine 3 3 3 2`), which is also what the review page prints. The
`<batch>/<idx>` form only resolves for a single-slot surface: a multiface surface pools each face in
its own dated directory (`washing_machine_front-<date>/`), so a shared batch prefix resolves to a path
that does not exist and `promote` dies with `candidate not found`.

**Jason running `select` IS approval** - there is no `approve` verb, and `promote` does not grant it.
The review page prints a `select` line per candidate; when he issues one, add that surface id to
`gen/approved.json` so the page stops listing it as pending. A `select` run by the assistant while
generating is *not* approval, so the id stays out until he picks. (`mound_ground` shipped in v0.7.0
while still pending, which is how a texture reaches a release with no approval record.)

`texgen sheet` builds `gen/recompile_textures_review.html`, the page Jason reviews art in: pending
surfaces on top with their `select` commands, approved ones at the bottom. **Re-run it after anything
that changes a texture** - it is a build artifact, not a live view. Approval is explicit in
`gen/approved.json`; drop a surface's id back out when its art changes so it returns to the pending
queue.

**Hard rule: no raw AI output lands in the repo.** `gen/` and `art_src/` are gitignored; only the
finalized 16px PNGs under `src/main/resources/assets/recompile/textures/` are committed. A texture
change should show up in the diff as *only* the small PNG plus the manifest.

Two surfaces that must read as the same object in different states use `reference = "<other-surface>"`
(e.g. `tin_can_open` -> `tin_can`), which derives one from the other's source art instead of generating
it blind. Generating each state from its own prompt makes them drift into different objects.

`kind` picks both the output directory and whether the art keeps an alpha background. When those need
to differ - a cross-model plant is a transparent sprite that must live in `textures/block/` - set
`transparent = true` explicitly (see `dump_mushroom`).

Changing a surface's `kind` and re-running `promote` re-finalizes from `art_src/` with **no API call**,
so moving art between atlases costs nothing and cannot drift.

Two promote-time keys exist for tone, and both beat re-prompting for it: `match_hue = "<surface>"`
re-casts a surface onto a shipped sibling's colour while keeping its own luminance (parts of one object
generated separately otherwise come back as different materials), and `brightness = <float>` trims tone
deterministically. **Calibrate tone against neighbours, not in isolation** - a `brightness` tuned
against one block is wrong the moment that neighbour is regenerated.

**Trust the shipped PNG over the manifest's `backend`.** A manifest can declare `procedural` while the
shipped PNG is AI art (`garbage_block` and `stone_rubble` did, for months; both now declare `ai`). A
procedural draw can only produce its palette's colours; anything at the 16-colour quantize cap came from
AI. **Procedural is still the right call in places and is not a fallback there:** every Puzzle Cube
face, the cube pieces and the Display Pedestal are deliberately procedural, because AI and downsampling
both failed a twisty cube at 16px.

Blocks with `variants = N` get randomized variants from the **blockstate JSON, not code**.

## Audio (sfxgen)

**The audio is SYNTHESISED, and that reversed a ruling made hours earlier the same day** (owner,
2026-09-05). The first call was "sounds we can source"; pricing it reversed it.

**Licensing decided it**: this repo is MIT and a mod ships loose `.ogg` files inside a jar anyone can
unzip, so the licence hands every downstream user redistribution rights that the obvious AI tool
forbids - ElevenLabs' prohibited use policy 9(c) bars distributing sound-effect output "on a standalone
basis ... including as isolated files, audio samples ... or other collections of sounds". A permissive
licence cannot promise what its assets do not back, which is the Create split (MIT code, reserved
assets) in a new place.

Two more reasons made it the better answer anyway. **A loop is seamless by construction** - partials
locked to a whole number of cycles, the noise bed an inverse FFT of exactly one period - where a
sourced clip has to be crossfaded by hand and still drifts, and a machine loop plays for as long as a
button is held so a seam is a click once a cycle forever. And **every sound this mod needs is a
machine**, so a declared voice gives a family that cannot drift apart, and a tier ladder becomes a
scalar rather than four more sourcing jobs.

**Two voices, one per machine** (owner, 2026-09-05, the same day): `heavy` for the Garbage Vacuum,
`cheerful` for the Scrap Hauler. The Hauler first shipped on the vacuum's voice and the little robot
sounded like the machine that digs, because it was. What a declared voice buys is that ONE machine's
own sounds stay consistent, not that two machines share a character: the vacuum's twelve sounds are a
scalar apart, and the Hauler is a different character and gets to be one. **What separates them is
INFLECTION rather than timbre.** Machinery is steady; a small creature bends its pitch, and the bend is
the message - rising is a question, falling is a sigh. That is what `sfxgen`'s `chirp` primitive
carries, and why three of the Hauler's four sounds are a pitch contour rather than a rotor.

The generator is **`sfxgen`** (`../mc-pack-toolkit/sfxgen`, texgen's audio sibling, `pip install -e`),
driven by `sfxgen.toml` here; `python -m sfxgen all` renders auditions and a review page into `gen/`,
then `approve` and `promote`. Same discipline as textures: **only the finished mono 44.1 kHz Ogg is
committed**, auditions stay in gitignored `gen/`, and **approval is explicit and never inferred from a
file existing**.

Three things it checks that are all SILENT in game: a stereo file (still plays, just not positioned or
attenuated), a `sounds.json` naming a missing file (one startup line, then a mute event), and a loop
seam.

**Vorbis quality is load-bearing on a loop specifically** - the codec's reconstruction error at the
file edges lands exactly on the loop point, the one place an error becomes a click rather than a blur,
measured on the Hauler's idle at 4.59% of peak at q4 and 1.59% at q8 - so `sfxgen` defaults a loop to
q8 and a one-shot to q6.

## Resource checklist

**`tools/resource_checklist/` generates `docs/vanilla_resource_checklist.md`** (#323): every resource
vanilla gives you, checked against what this mod can actually reach. It is a pipeline rather than a
one-shot script, and its own README (`tools/resource_checklist/README.md`) is the reference. The batch
of `question` issues about unreachable vanilla items came out of it.

## Driving a running game (devbridge)

**devbridge is its own repo** (`F:\devbridge`, MIT), and its onboarding doc (`docs/onboarding.md`
there) is the reference - this section is only what is specific to this repo. It is two halves of one
wire protocol: a dev-only NeoForge jar and the `gamebridge` Python client, kept in one repo so they
cannot drift. The history of this repo's port claim is in [`handoff_devbridge.md`](handoff_devbridge.md).

### Making the dev world

**Already wired here, but the world has to exist first.** Run `python tools/make_dev_world.py` once:
it drives `runServer` to generate a world with **this mod's preset**, stops it over RCON, installs it
at `run/saves/devworld` and sets `confirmedExperimentalSettings` in its `level.dat`. After that
`./gradlew runClient` opens the devbridge socket on **8605** and boots straight into it.

**Three separate things block that boot and NONE of them logs anything** (#289). Quick play does not
create a missing world - it shows a *"Could not find world with the provided identifier"* screen. A
custom-preset world is flagged **experimental**, so the client stops on a *"Here be dragons!"*
confirmation. And moddev **quotes any program argument containing a space** into
`clientRunProgramArgs.txt`, so the old `'New World'` reached the game as `'"New World"'` and matched no
directory. All three are screens or silent mismatches, so the only symptom is a client sitting at a
menu with a socket that never opens; four debugging attempts and one wrong issue went by before anyone
looked at the game window. Do not let the client create the world itself either - a world made through
the GUI is a vanilla default one that ignores `recompile:garbage` silently.

The mod half is a jar in `run/mods` (`devbridge-26.1.2-0.6.0.jar` as of 2026-09-10; the `use` verb
needs 0.6.0 or later). `run/` is gitignored, so a fresh clone needs the jar from that repo's Releases.

```bash
pip install "gamebridge @ git+https://github.com/Flatts3000/devbridge.git#subdirectory=gamebridge"

gamebridge --devbridge 8605 cmd "function recompile:showcase/museum" --player @s
gamebridge --devbridge 8605 shot museum

# Or RCON against ./gradlew runServer, which needs no mod at all.
bash tools/verify_showcase.sh     # places the museum and asserts it landed
```

### Port 8605, and dialing localhost

**8605 is claimed for this repo** (pinned as `systemProperty 'devbridge.port', '8605'` in
`build.gradle`), deliberately not devbridge's own 25580 default. Every project that keeps the default
shares one socket, and that has already produced a wrong answer rather than an error: Trashlands' quest
verifier connected to *this* client and reported six item ids resolving, a clean pass about the wrong
game. Claiming prevents a clash but cannot detect one - `ports check` enumerates IPv4 only while
devbridge binds `getLoopbackAddress()` (`::1` here), so the registry calls the port free while a game
holds it. **For the same reason the client must dial `localhost`, never the `127.0.0.1` literal**,
which otherwise gets connection refused from a socket the log says is listening.

**So a tool that must be sure asks what answered - and since devbridge 0.2.0, `ping` can tell it.** It
reports the game's own `gameDir` and world name, and the client checks it: `ping(expect_instance=...)`
raises rather than let you drive the wrong game. `tools/shoot_scenes.py` passes `run/`, which is the
moddev default game directory and NOT the repo root - pointing it at the repo fails the check against
the game that is behaving correctly. It replaced a hand-rolled sentinel (a Recompile-only item id
handed to the command parser, which rejects an unknown item as a *parse* error); same guard, now the
tool's job.

### Verification, and the limits of RCON

The point is **verification**: `gamebridge check "entity @e[type=minecraft:painting]" --count 6` exits
non-zero, which is a check a screenshot cannot make - a painting that fails to hang deletes itself
silently and just leaves the picture looking emptier.

**Two limits that are properties of Minecraft, not of the tool.** RCON cannot reach a singleplayer
world (its integrated server listens on nothing) or take a screenshot (a dedicated server has no
framebuffer); that is what devbridge is for. And chunks unload with nobody standing in them, so a
playerless server answers `data get block` with "That position is not loaded" and otherwise looks like
it worked - `forceload add` first.

### Commands run as the console unless you pass a player

**Commands run as the console unless you pass a player, so `@s` matches nothing and `~` is
spawn-relative.** Every showcase function ends in `tp @s`, so driving one over the bridge places the
set correctly and silently does not move the camera. **The fix is `--player @s`** (`player=` from
Python), which devbridge resolves to the only player online; an unmatched name fails loudly rather than
falling back to the console. `tools/shoot_scenes.py` passes it on every command.

### Verbs, the mouse default, and capture size

Beyond `cmd` and `shot` the verbs are `ping`, `hud`, `input`, `look`, `pause`, `stop`, since 0.2.0
`screen`, `cursor`, `click` and `log`, and since 0.6.0 `use` - so a tool can drive an open GUI and read
what the game logged. A toggle with no argument restores vanilla behaviour (`hud on`, `input on`,
`pause on`).

**0.2.0 reversed the mouse default and it matters here.** Loading a world still turns off
pause-on-lost-focus - an unfocused singleplayer client stops ticking and would answer nothing - but it
no longer takes the mouse unless asked, because a person who opened the game to fly around has no idea
a mod took it and the symptom looks like a broken game. An unattended run has to ask:
`shoot_scenes.py` calls `input(False)` explicitly, or a stray alt-tab between framing a shot and
grabbing it silently changes the picture, and gives it back in a `finally`.

**`shot` can capture at an exact size** (0.2.0), resizing the window for the moment of the grab. The
`--width 1920 --height 1080` in the client run block predates that and is now belt-and-braces rather
than the only way to get a usable frame.

### devbridge must never ship

**devbridge must never ship.** It binds loopback only and is inert without `-Ddevbridge.port`, but it
executes arbitrary commands and is deliberately a separate jar rather than a dependency.
