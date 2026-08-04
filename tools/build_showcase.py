"""Build showcase scenes: a structure for the arrangement, a function for the shot.

Spec: docs/showcase_spec.md. Run:

    python tools/build_showcase.py

Writes `dev/showcase/data/recompile/{structure,function}/showcase/<scene>.{nbt,mcfunction}`.
Copy or symlink `dev/showcase/` into `run/saves/<world>/datapacks/`, `/reload`, then

    /function recompile:showcase/museum

and press F2. The point of all this is that the second screenshot is identical to the first, which is
what makes a re-shoot after an art change cost one command instead of an afternoon.

**The NBT writer is hand-rolled on purpose.** The format is small and fully known (read out of the
shipped `structure/empty_5x5x5.nbt`), and a library version bump silently changing how it writes a
list would be a bad way to find out that a scene no longer loads.
"""

from __future__ import annotations

import gzip
import struct
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).parent
REPO = HERE.parent
OUT = REPO / "dev" / "showcase" / "data" / "recompile"

# 26.1.2, read out of the shipped empty_5x5x5.nbt rather than looked up.
DATA_VERSION = 4671

# ---------------------------------------------------------------- NBT writing

TAG_END, TAG_BYTE, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 8, 9, 10


def _string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(value) -> tuple[int, bytes]:
    """(tag id, payload bytes) for the small subset of NBT a structure file uses."""
    if isinstance(value, Byte):
        return TAG_BYTE, struct.pack(">b", value.value)
    if isinstance(value, int):
        return TAG_INT, struct.pack(">i", value)
    if isinstance(value, str):
        return TAG_STRING, _string(value)
    if isinstance(value, dict):
        return TAG_COMPOUND, _compound_body(value)
    if isinstance(value, list):
        # An empty list is written as TAG_END-typed, which is what vanilla does and what the shipped
        # empty structure contains.
        if not value:
            return TAG_LIST, struct.pack(">bi", TAG_END, 0)
        element_ids = {_payload(v)[0] for v in value}
        if len(element_ids) != 1:
            raise TypeError(f"a list must be homogeneous, got tag ids {element_ids}")
        element_id = element_ids.pop()
        body = struct.pack(">bi", element_id, len(value))
        for item in value:
            body += _payload(item)[1]
        return TAG_LIST, body
    raise TypeError(f"cannot write {type(value)!r} to NBT")


def _compound_body(mapping: dict) -> bytes:
    body = b""
    for key, value in mapping.items():
        tag_id, payload = _payload(value)
        body += struct.pack(">b", tag_id) + _string(key) + payload
    return body + struct.pack(">b", TAG_END)


class Byte(int):
    """An int that should be written as TAG_Byte. Painting facing needs one."""

    @property
    def value(self) -> int:
        return int(self)


def write_nbt(path: Path, root: dict) -> None:
    body = struct.pack(">b", TAG_COMPOUND) + _string("") + _compound_body(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(body, mtime=0))


# ---------------------------------------------------------------- scene model

# Which way a painting looks, and the byte the entity stores. Direction.LEGACY_ID_CODEC_2D, read from
# Painting.addAdditionalSaveData.
FACING_ID = {"south": 0, "west": 1, "north": 2, "east": 3}


@dataclass(frozen=True)
class Painting:
    """A painting, positioned by the BOTTOM LEFT block of the area it covers as a viewer sees it.

    Expressed that way because the anchor block the game actually wants is not the corner and is not
    even the centre for every size: `Painting.calculateBoundingBox` shifts the centre by half a block
    for EVEN dimensions only, so a 4-wide and a 3-wide painting anchored on the same block do not line
    up. Working in corners and converting once, here, keeps that arithmetic out of the scene data.
    """

    variant: str
    width: int
    height: int
    left: int      # scene X of the leftmost column, viewer's left
    bottom: int    # scene Y of the bottom row
    z: int         # scene Z of the air block the painting hangs in
    facing: str = "south"

    def anchor(self) -> tuple[int, int, int]:
        # Derived from centre = block_centre + 0.5-if-even, span = centre +/- size/2.
        ax = self.left + ((self.width - 1) // 2 if self.width % 2 else self.width // 2 - 1)
        ay = self.bottom + ((self.height - 1) // 2 if self.height % 2 else self.height // 2 - 1)
        return ax, ay, self.z


@dataclass(frozen=True)
class Machine:
    """A multiblock, placed as its loose COMPONENTS and assembled by the game.

    **The formed blocks are deliberately not written into the structure.** A formed cell carries a
    `CELL` index that drives the whole-machine skin, and hand-transcribing those would put a scrambled
    machine in the shot and a second copy of the blueprint in this file - the exact drift the
    guidebook's multiblock pages already have a test to prevent. Placing components and letting
    `tryForm` run means the game supplies the formed states, so the scene cannot disagree with the
    machine.

    Three things this has to get right, all learned the hard way:

      * `facing=north` is the identity rotation. The others rotate the blueprint (south is 180), so
        offsets written here would land on the opposite side.
      * The core only sees a neighbour update from a cell ADJACENT to it, so placing components in
        blueprint order runs `tryForm` too early and never again.
      * `setblock` to a state a block already has is a no-op and fires nothing. The nudge has to be a
        real change: clear a neighbouring cell and put it back.
    """

    core: str                       # block id, without state
    at: tuple[int, int, int]        # scene coordinates of the core
    cells: dict                     # offset -> component block id
    facing: str = "north"


@dataclass(frozen=True)
class Camera:
    """Camera pose, scene-relative like everything else.

    EVERY coordinate in a scene is relative to `origin` and the generator adds it once, on the way
    out. Mixing the two conventions is how the first run put the paintings at y=131: a bottom edge
    written as an absolute 66 had the origin's 64 added to it again.
    """

    pos: tuple[float, float, float]
    yaw: float     # 0 south, 90 west, 180 north, -90 east
    pitch: float   # positive looks down


@dataclass
class Scene:
    name: str
    origin: tuple[int, int, int]
    legend: dict[str, str | None]
    layers: list[list[str]]          # index 0 is the BOTTOM layer
    camera: Camera
    paintings: list[Painting] = field(default_factory=list)
    cells: dict[tuple[int, int, int], str] = field(default_factory=dict)
    assemble: list = field(default_factory=list)   # multiblocks the GAME builds, see Machine
    block_entities: dict[tuple[int, int, int], dict] = field(default_factory=dict)
    time: str = "noon"
    weather: str = "clear"
    clearance: int = 6   # blocks of air cut around the set before it is placed
    anchor: str = "absolute"   # or "player": placed at your feet, framed relative to the plot

    def size(self) -> tuple[int, int, int]:
        width = height = depth = 0
        for y, layer in enumerate(self.layers):
            height = max(height, y + 1)
            for z, row in enumerate(layer):
                depth = max(depth, z + 1)
                width = max(width, len(row))
        for x, y, z in self.cells:
            width, height, depth = max(width, x + 1), max(height, y + 1), max(depth, z + 1)
        return width, height, depth

    def occupied(self) -> dict[tuple[int, int, int], str]:
        """Every filled cell, layers first and then `cells`, which wins on a clash.

        Two ways in on purpose. Walls and floors are grids and read best as aligned text; mounds,
        scatter and tree canopies are round and would be unreadable spelled out layer by layer.
        """
        out: dict[tuple[int, int, int], str] = {}
        for y, layer in enumerate(self.layers):
            for z, row in enumerate(layer):
                for x, char in enumerate(row):
                    spec = self.legend[char]
                    if spec is not None:
                        out[(x, y, z)] = spec
        out.update(self.cells)
        return out


def parse_state(spec: str) -> dict:
    """`recompile:steel_i_beam[axis=x]` into a palette entry."""
    if "[" not in spec:
        return {"Name": spec}
    name, _, rest = spec.partition("[")
    props = {}
    for pair in rest.rstrip("]").split(","):
        key, _, value = pair.partition("=")
        props[key.strip()] = value.strip()
    return {"Name": name, "Properties": props}


def build_structure(scene: Scene) -> dict:
    palette: list[dict] = []
    index_of: dict[str, int] = {}
    blocks: list[dict] = []

    # Sorted so the file is byte-stable across runs: an unordered dict would reshuffle `blocks` and
    # make every rebuild look like a change in the diff.
    for pos, spec in sorted(scene.occupied().items()):
        if spec not in index_of:
            index_of[spec] = len(palette)
            palette.append(parse_state(spec))
        entry = {"state": index_of[spec], "pos": list(pos)}
        extra = scene.block_entities.get(pos)
        if extra:
            entry["nbt"] = extra
        blocks.append(entry)

    width, height, depth = scene.size()
    return {
        "size": [width, height, depth],
        "entities": [],
        "blocks": blocks,
        "palette": palette,
        "DataVersion": DATA_VERSION,
    }


def validate(scene: Scene) -> list[str]:
    """Everything about a scene that fails silently in game.

    A painting with no wall behind it does not error, does not log, and does not appear - the summon
    succeeds and the entity removes itself on its first tick because it has nothing to hang on. That is
    the whole reason this function exists: the failure looks identical to a typo in a variant name.
    """
    problems: list[str] = []
    filled = set(scene.occupied())

    for painting in scene.paintings:
        # The wall is one block behind the painting, opposite the way it faces.
        back = {"south": (0, 0, -1), "north": (0, 0, 1),
                "west": (1, 0, 0), "east": (-1, 0, 0)}[painting.facing]
        for dx in range(painting.width):
            for dy in range(painting.height):
                cell = (painting.left + dx, painting.bottom + dy, painting.z)
                if (cell[0] + back[0], cell[1] + back[1], cell[2] + back[2]) not in filled:
                    problems.append(
                        f"{painting.variant} has no wall behind {cell}; it will vanish on spawn")
                if cell in filled:
                    problems.append(f"{painting.variant} overlaps a block at {cell}")

    seen: dict[tuple[int, int, int], str] = {}
    for painting in scene.paintings:
        for dx in range(painting.width):
            for dy in range(painting.height):
                cell = (painting.left + dx, painting.bottom + dy, painting.z)
                if cell in seen:
                    problems.append(f"{painting.variant} overlaps {seen[cell]} at {cell}")
                seen[cell] = painting.variant

    for pos in scene.block_entities:
        if pos not in filled:
            problems.append(f"block entity data at {pos} but no block there")

    return problems


def build_function(scene: Scene) -> str:
    """The commands that place a scene and put you at its camera.

    ANCHOR IS THE INTERESTING PART. A set that wants a clean backdrop is placed at fixed coordinates
    high in the air. A scene that is ABOUT the terrain cannot be: floated, it reads as an island, with
    a slab edge across the bottom of the frame and sky where the world should be. Those want to sit in
    real ground, and the tool has no way to know how high the ground is - so they anchor to wherever
    you are standing and frame relative to the plot instead. An A/B pair stays honest under that as
    long as you do not move between the two commands, which is the same discipline as not nudging a
    tripod.
    """
    relative = scene.anchor == "player"

    # Same arithmetic either way; only the prefix differs. In relative mode `origin` is an offset
    # from where you stand rather than a world position.
    def coord(base: int, offset: float) -> str:
        return f"~{base + offset:g}" if relative else f"{base + offset:g}"

    ox, oy, oz = scene.origin
    cx, cy, cz = scene.camera.pos
    lines = [
        "# generated by tools/build_showcase.py - do not edit",
        f"# scene: {scene.name}. See docs/showcase_spec.md.",
        "",
        # 26.1 RENAMED THE GAMERULES to snake_case: doDaylightCycle is advance_time and
        # doWeatherCycle is advance_weather. Read out of GameRules in the 26.1.2 jar after the first
        # run failed to load with "Incorrect argument for command at position 9: gamerule". CLAUDE.md
        # already records the same rename for doTileDrops -> block_drops; it is the whole family.
        "gamerule advance_time false",
        "gamerule advance_weather false",
        f"time set {scene.time}",
        f"weather {scene.weather}",
        "",
        "# Scoped by type and distance so re-running replaces this scene's paintings rather than",
        "# hanging a second set on top of the first.",
        "kill @e[type=minecraft:painting,distance=..64]",
        "",
    ]

    # CUT THE SPACE BEFORE PLACING IT. A scene dropped into the garbage world lands inside whatever
    # mound happens to be at its coordinates: the first run placed the whole museum inside terrain and
    # teleported the camera into a pile of rubbish, which looks exactly like the function having done
    # nothing. /place does not clear, and a set has to own its own room.
    width, height, depth = scene.size()
    m = scene.clearance
    pad = 2.0
    lo = (min(-m, cx - pad), min(0, cy - pad), min(-m, cz - pad))
    hi = (max(width + m, cx + pad), max(height + max(m, 8), cy + pad), max(depth + m, cz + pad))
    lo = tuple(int(v // 1) for v in lo)
    hi = tuple(int(-(-v // 1)) for v in hi)
    lines += [
        # Headroom is not the same dial as margin. A scene with clearance 0 still has to clear what
        # is ABOVE it, or an overhanging mound stays put and hangs over the set; it just must not cut
        # sideways into terrain that is part of the shot.
        #
        # THE CAMERA IS PART OF THE SCENE and the box is stretched to hold it. A camera standing
        # outside the cleared volume is standing in whatever was there, and the shot is a close-up of
        # the inside of a block - which looks like a broken scene rather than a mispositioned camera.
        f"fill {coord(ox, lo[0])} {coord(oy, lo[1])} {coord(oz, lo[2])} "
        f"{coord(ox, hi[0])} {coord(oy, hi[1])} {coord(oz, hi[2])} air",
        "",
        f"place template recompile:showcase/{scene.name} "
        f"{coord(ox, 0)} {coord(oy, 0)} {coord(oz, 0)}",
        "",
    ]
    for machine in scene.assemble:
        mx, my, mz = machine.at
        lines.append(f"# {machine.core}: components placed, then assembled by the game")
        for (dx, dy, dz), component in machine.cells.items():
            lines.append(f"setblock {coord(ox, mx + dx)} {coord(oy, my + dy)} "
                         f"{coord(oz, mz + dz)} {component}")
        lines.append(f"setblock {coord(ox, mx)} {coord(oy, my)} {coord(oz, mz)} "
                     f"{machine.core}[facing={machine.facing}]")
        # The nudge, and it must be two real state changes: see the Machine docstring.
        first = next(iter(machine.cells))
        nudge = (mx + first[0], my + first[1], mz + first[2])
        lines.append(f"setblock {coord(ox, nudge[0])} {coord(oy, nudge[1])} "
                     f"{coord(oz, nudge[2])} minecraft:air")
        lines.append(f"setblock {coord(ox, nudge[0])} {coord(oy, nudge[1])} "
                     f"{coord(oz, nudge[2])} {machine.cells[first]}")
        lines.append("")

    for painting in scene.paintings:
        ax, ay, az = painting.anchor()
        facing = FACING_ID[painting.facing]
        lines.append(
            f"summon minecraft:painting {coord(ox, ax)} {coord(oy, ay)} {coord(oz, az)} "
            f'{{facing:{facing}b, variant:"{painting.variant}"}}'
        )
    lines += ["",
              f"tp @s {coord(ox, cx)} {coord(oy, cy)} {coord(oz, cz)} "
              f"{scene.camera.yaw} {scene.camera.pitch}", ""]
    return "\n".join(lines)


# ---------------------------------------------------------------- the scenes

WALL = "recompile:corrugated_metal"
FLOOR = "recompile:pressed_junk_block"
PLINTH = "recompile:display_pedestal"

# The museum wall. Six recovered masterworks over pedestals holding the found objects, which is the
# most theme-legible frame this mod can produce and the one the gallery is missing
# (../mod-jam-2026/round_1_rewards_analysis.md).
#
# Two rows rather than one. Laid out end to end the six are 18 blocks of painting plus gaps, which
# forces the camera so far back that every piece is small; stacked, the whole wall sits in one frame.
#
# Wall is at z=0 facing south, so the camera stands at +z looking north (yaw 180).
MUSEUM_LEGEND = {
    ".": None,
    "#": FLOOR,
    "C": WALL,
    "P": PLINTH,
    "T": "recompile:scrap_torch",
}

_WALL_ROW = "CCCCCCCCCCCCC"
_FLOOR_ROW = "#############"
_EMPTY_ROW = "............."

# The floor has to reach the camera, not just the wall. At this width the camera stands about ten
# blocks back to fit all six paintings in frame, and a floor that stopped at the pedestals would put
# nine blocks of whatever the world happens to be in the bottom of every shot.
FLOOR_DEPTH = 16

MUSEUM = Scene(
    name="museum",
    origin=(0, 120, 0),
    legend=MUSEUM_LEGEND,
    layers=[
        # y=0: the floor, running from the wall out past where the camera stands.
        [_FLOOR_ROW] * FLOOR_DEPTH,
        # y=1: wall, a row of pedestals two blocks out, torches at the ends for light.
        [_WALL_ROW, _EMPTY_ROW, "..P..P..P..P.", "T...........T"],
        # y=2 upward: wall only. Ten courses so the top row of paintings has headroom.
        *[[_WALL_ROW] for _ in range(10)],
    ],
    block_entities={
        (2, 1, 2): {"id": PLINTH, "Items": [{"Slot": Byte(0), "id": "recompile:puzzle_cube",
                                             "count": 1}]},
        (5, 1, 2): {"id": PLINTH, "Items": [{"Slot": Byte(0), "id": "recompile:toy_car",
                                             "count": 1}]},
        (8, 1, 2): {"id": PLINTH, "Items": [{"Slot": Byte(0), "id": "recompile:gold_coin",
                                             "count": 1}]},
        (11, 1, 2): {"id": PLINTH, "Items": [{"Slot": Byte(0), "id": "recompile:present",
                                              "count": 1}]},
    },
    paintings=[
        # Bottom row, bottoms level at y=66. The two 4-tall pieces anchor the middle.
        Painting("recompile:pearl_earring", 3, 4, left=1, bottom=2, z=1),
        Painting("recompile:the_scream", 3, 4, left=5, bottom=2, z=1),
        Painting("recompile:la_grande_jatte", 3, 2, left=9, bottom=2, z=1),
        # Top row, one course of clear wall above the tallest piece below it.
        Painting("recompile:starry_night", 4, 3, left=1, bottom=7, z=1),
        Painting("recompile:mona_lisa", 2, 3, left=6, bottom=7, z=1),
        Painting("recompile:great_wave", 3, 2, left=9, bottom=7, z=1),
    ],
    # Centred on the wall, backed off far enough to hold all six, tilted up slightly so the top row
    # is not foreshortened.
    # Level with the middle of the wall so neither row is foreshortened, and centred on x.
    #
    # DISTANCE IS SET BY THE WALL'S HEIGHT, NOT ITS WIDTH. Minecraft's FOV option is the VERTICAL
    # angle (70 by default), so an 11-block wall needs 5.5/tan(35) = 7.9 blocks to fill the frame,
    # while its 13-block width needs only about 5 at any normal aspect ratio. The first shot was taken
    # from 13.5 and left the wall sitting in a third of the frame surrounded by sky.
    camera=Camera(pos=(6.5, 5.5, 10.5), yaw=180.0, pitch=0.0),
)


# ---------------------------------------------------------------- terrain helpers

def plane(width: int, depth: int, y: int, block: str) -> dict:
    return {(x, y, z): block for x in range(width) for z in range(depth)}


def mound(cx: int, cz: int, y: int, radius: int, block: str) -> dict:
    """A dome of garbage. Rounded rather than a cube because a cube reads as a building."""
    out = {}
    for dx in range(-radius, radius + 1):
        for dz in range(-radius, radius + 1):
            distance = (dx * dx + dz * dz) ** 0.5
            if distance > radius + 0.4:
                continue
            for dy in range(int(round(radius - distance)) + 1):
                out[(cx + dx, y + dy, cz + dz)] = block
    return out


def tree(x: int, y: int, z: int, height: int = 5) -> dict:
    """Oak, placed as logs and leaves. A sapling would not do: in this world nothing grows on its own
    and a structure cannot wait for one anyway."""
    out = {(x, y + i, z): "minecraft:oak_log" for i in range(height)}
    for dy in (height - 2, height - 1):
        spread = 2 if dy == height - 2 else 1
        for dx in range(-spread, spread + 1):
            for dz in range(-spread, spread + 1):
                if abs(dx) == spread and abs(dz) == spread:
                    continue
                out.setdefault((x + dx, y + dy, z + dz), "minecraft:oak_leaves[persistent=true]")
    out[(x, y + height, z)] = "minecraft:oak_leaves[persistent=true]"
    return out


def scatter(rng, positions, block: str, chance: float) -> dict:
    return {pos: block for pos in positions if rng.random() < chance}


# ---------------------------------------------------------------- the reclamation pair

# The mod's whole argument in two frames: one plot, shot before and after, from a camera that is
# identical to the pixel. That last part is the only reason this is worth automating - two hand-flown
# screenshots are never quite the same shot, and the comparison is exactly what a viewer is being asked
# to make.
#
# CLEARANCE IS 0 ON BOTH, and that is not an oversight. The museum cuts a six block hole to escape the
# terrain; this pair IS the terrain, and a fill would erase its own subject.
PLOT_W, PLOT_D = 21, 19
# y = -1 so the plot's ground layer REPLACES the block you are standing on rather than being laid on
# top of it, which would bury you in your own scene.
PLOT_ORIGIN = (0, -1, 0)
# EYE HEIGHT, NOT A DRONE. The first version looked down from six blocks up and the plot read as a
# floating island: a slab of coarse dirt across the bottom of the frame and sky above it. Standing on
# the ground and looking across puts the plot underfoot and the dump on the horizon behind it, which
# is the comparison the pair is for.
PLOT_CAMERA = Camera(pos=(10.5, 2.6, 26.0), yaw=180.0, pitch=4.0)


def _before_cells() -> dict:
    import random
    rng = random.Random(2026)
    cells = plane(PLOT_W, PLOT_D, 0, "minecraft:coarse_dirt")
    for cx, cz, r in ((5, 6, 3), (14, 5, 2), (9, 13, 3), (17, 14, 2)):
        cells.update(mound(cx, cz, 1, r, "recompile:garbage_block"))
    flat = [(x, 1, z) for x in range(PLOT_W) for z in range(PLOT_D) if (x, 1, z) not in cells]
    cells.update(scatter(rng, flat, "recompile:trash_bag", 0.03))
    cells.update(scatter(rng, [p for p in flat if p not in cells], "recompile:bulky_waste", 0.012))
    return cells


def _after_cells() -> dict:
    import random
    rng = random.Random(2026)
    cells = plane(PLOT_W, PLOT_D, 0, "minecraft:grass_block")

    # A coarse dirt fringe, because healed ground really does stop somewhere. Encroachment is a
    # mechanic in this mod and a plot healed edge to edge would be a picture of something the game
    # does not do.
    for x in range(PLOT_W):
        for z in range(PLOT_D):
            if x < 2 or x >= PLOT_W - 2 or z < 2 or z >= PLOT_D - 2:
                cells[(x, 0, z)] = "minecraft:coarse_dirt"

    green = [(x, 1, z) for x in range(3, PLOT_W - 3) for z in range(3, PLOT_D - 3)]
    cells.update(scatter(rng, green, "recompile:weedgrass", 0.22))
    cells.update(scatter(rng, [p for p in green if p not in cells], "recompile:fireweed", 0.07))

    # A worked plot: wet farmland under grown wheat.
    for x in range(4, 9):
        for z in range(4, 7):
            cells[(x, 0, z)] = "minecraft:farmland[moisture=7]"
            cells[(x, 1, z)] = "minecraft:wheat[age=7]"

    cells.update(tree(15, 1, 7))
    cells.update(tree(12, 1, 14, height=4))

    # One mound kept. Reclaiming a plot retires the garbage it was making, so a player who wants both
    # leaves one standing - and the picture should say that rather than pretend the dump is gone.
    cells.update(mound(17, 4, 1, 2, "recompile:garbage_block"))
    return cells


RECLAIM_BEFORE = Scene(
    name="reclaim_before",
    origin=PLOT_ORIGIN,
    legend={},
    layers=[],
    cells=_before_cells(),
    camera=PLOT_CAMERA,
    clearance=0,
    anchor="player",
)

RECLAIM_AFTER = Scene(
    name="reclaim_after",
    origin=PLOT_ORIGIN,
    legend={},
    layers=[],
    cells=_after_cells(),
    camera=PLOT_CAMERA,
    clearance=0,
    anchor="player",
)



# ---------------------------------------------------------------- the machine wall

# Every machine in one plane, facing the camera. For the technology audience, who will not read a
# description.
#
# THIS WAS A FLOOR FIRST AND THE FLOOR WAS WRONG. Laid out as a workshop the machines occlude each
# other, the far ones shrink with distance, and no camera catches the Separator without losing the
# bench behind it. A wall gives every machine the same distance and the same size, which is the whole
# reason the museum works, and here the subject is the machines rather than the room.
WALL_W, WALL_H = 13, 9
PANEL = "recompile:corrugated_metal"
BEAM, FRAME = "recompile:steel_i_beam", "recompile:machine_frame"

# Mounted on the wall face, read left to right, top row down: what you build, what you burn, what you
# power it with, what you store it in, and what you feed it.
#
# SPACED TWO APART, NOT THREE. The first pass used a 19-wide wall on a three-block grid and the
# machines read as scattered dots on a field of panel - a wall works by putting things next to each
# other, and gaps that large undo the reason for building one.
COLS = (1, 3, 5, 7, 9, 11)
ROWS = (7, 5, 3)

WALL_MACHINES = {
    (COLS[0], ROWS[0]): "recompile:recompile_workbench",
    (COLS[1], ROWS[0]): "recompile:scrap_crafting_table",
    (COLS[2], ROWS[0]): "recompile:cupola_furnace[facing=south,lit=true]",
    (COLS[3], ROWS[0]): "recompile:burn_barrel[facing=south,lit=true]",
    (COLS[4], ROWS[0]): "recompile:filing_cabinet[facing=south]",
    (COLS[5], ROWS[0]): "recompile:display_pedestal",

    (COLS[0], ROWS[1]): "recompile:burner_generator[facing=south]",
    (COLS[1], ROWS[1]): "recompile:solar_panel",
    (COLS[2], ROWS[1]): "recompile:hydroponics_bay",
    (COLS[3], ROWS[1]): "recompile:scrap_barrel",
    (COLS[4], ROWS[1]): "recompile:sorting_tarp",
    (COLS[5], ROWS[1]): "recompile:scrap_bin",

    (COLS[0], ROWS[2]): "recompile:mechanical_waste",
    (COLS[1], ROWS[2]): "recompile:garbage_block",
    (COLS[4], ROWS[2]): "recompile:trash_bag",
    (COLS[5], ROWS[2]): "recompile:compacted_bale",
}


def _wall_cells() -> dict:
    cells = {}
    for x in range(WALL_W):
        for y in range(1, WALL_H + 1):
            cells[(x, y, 0)] = PANEL            # the backing panel
        for z in range(0, 3):
            cells[(x, 0, z)] = PANEL            # a shallow floor lip so nothing floats
    for (x, y), block in WALL_MACHINES.items():
        cells[(x, y, 1)] = block
    cells[(0, 1, 1)] = "recompile:scrap_torch"
    cells[(WALL_W - 1, 1, 1)] = "recompile:scrap_torch"
    return cells


# The Separator's blueprint, transcribed as COMPONENTS only - the formed cells are the game's job.
# Offsets are the blueprint's own, valid at facing=north.
SEPARATOR_CELLS = {
    (1, 0, 0): FRAME, (2, 0, 0): FRAME,
    (0, 0, 1): FRAME, (1, 0, 1): FRAME, (2, 0, 1): FRAME,
    (0, 1, 0): FRAME, (0, 1, 1): FRAME,
    (1, 1, 0): BEAM, (1, 1, 1): BEAM, (2, 1, 0): BEAM, (2, 1, 1): BEAM,
}

MACHINE_WALL = Scene(
    name="machine_wall",
    origin=(0, -1, 0),
    legend={},
    layers=[],
    cells=_wall_cells(),
    # Centred at the bottom and standing proud of the wall, because it is the one machine that is
    # three blocks wide and the only multiblock here. Its own depth carries it toward the camera.
    assemble=[Machine(core="recompile:separator", at=(5, 1, 1), cells=SEPARATOR_CELLS)],
    # Distance set by the wall's height, which is the binding dimension at this aspect: 4.5/tan(35)
    # is about 6.4, so 8 back leaves a little margin without stranding it in the middle of the frame.
    camera=Camera(pos=(6.5, 5.4, 10.0), yaw=180.0, pitch=2.0),
    clearance=4,
    anchor="player",
)


SCENES = [MUSEUM, RECLAIM_BEFORE, RECLAIM_AFTER, MACHINE_WALL]


def main() -> None:
    failed = False
    for scene in SCENES:
        problems = validate(scene)
        if problems:
            failed = True
            print(f"{scene.name}: {len(problems)} problem(s)")
            for problem in problems:
                print(f"  {problem}")
            continue

        structure = build_structure(scene)
        nbt_path = OUT / "structure" / "showcase" / f"{scene.name}.nbt"
        write_nbt(nbt_path, structure)

        fn_path = OUT / "function" / "showcase" / f"{scene.name}.mcfunction"
        fn_path.parent.mkdir(parents=True, exist_ok=True)
        fn_path.write_text(build_function(scene), encoding="utf-8", newline="\n")

        width, height, depth = scene.size()
        print(f"{scene.name}: {width}x{height}x{depth}, {len(structure['blocks'])} blocks, "
              f"{len(structure['palette'])} palette entries, {len(scene.paintings)} paintings")
        print(f"  {nbt_path.relative_to(REPO)}")
        print(f"  {fn_path.relative_to(REPO)}")

    if failed:
        raise SystemExit("some scenes did not build")


if __name__ == "__main__":
    main()
