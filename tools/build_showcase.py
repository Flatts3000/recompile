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
    block_entities: dict[tuple[int, int, int], dict] = field(default_factory=dict)
    time: str = "noon"
    weather: str = "clear"
    clearance: int = 6   # blocks of air cut around the set before it is placed

    def size(self) -> tuple[int, int, int]:
        depth = max(len(layer) for layer in self.layers)
        width = max(len(row) for layer in self.layers for row in layer)
        return width, len(self.layers), depth


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

    for y, layer in enumerate(scene.layers):
        for z, row in enumerate(layer):
            for x, char in enumerate(row):
                spec = scene.legend[char]
                if spec is None:
                    continue      # absent from `blocks`, so /place leaves whatever is there
                if spec not in index_of:
                    index_of[spec] = len(palette)
                    palette.append(parse_state(spec))
                entry = {"state": index_of[spec], "pos": [x, y, z]}
                extra = scene.block_entities.get((x, y, z))
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
    filled = {
        (x, y, z)
        for y, layer in enumerate(scene.layers)
        for z, row in enumerate(layer)
        for x, char in enumerate(row)
        if scene.legend[char] is not None
    }

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
    lines += [
        f"fill {ox - m} {oy} {oz - m} {ox + width + m} {oy + height + m} {oz + depth + m} air",
        "",
        f"place template recompile:showcase/{scene.name} {ox} {oy} {oz}",
        "",
    ]
    for painting in scene.paintings:
        ax, ay, az = painting.anchor()
        facing = FACING_ID[painting.facing]
        lines.append(
            f"summon minecraft:painting {ox + ax} {oy + ay} {oz + az} "
            f'{{facing:{facing}b, variant:"{painting.variant}"}}'
        )
    lines += ["",
              f"tp @s {ox + cx} {oy + cy} {oz + cz} {scene.camera.yaw} {scene.camera.pitch}", ""]
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
        (2, 1, 2): {"id": PLINTH, "Items": [{"slot": Byte(0), "id": "recompile:puzzle_cube",
                                             "count": 1}]},
        (5, 1, 2): {"id": PLINTH, "Items": [{"slot": Byte(0), "id": "recompile:toy_car",
                                             "count": 1}]},
        (8, 1, 2): {"id": PLINTH, "Items": [{"slot": Byte(0), "id": "recompile:gold_coin",
                                             "count": 1}]},
        (11, 1, 2): {"id": PLINTH, "Items": [{"slot": Byte(0), "id": "recompile:present",
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
    # Level with the middle of the wall so neither row is foreshortened, centred on x, and far enough
    # back that all six fit the frame.
    camera=Camera(pos=(6.5, 5.5, 14.5), yaw=180.0, pitch=0.0),
)

SCENES = [MUSEUM]


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
