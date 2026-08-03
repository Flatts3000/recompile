"""Cut a multiblock's whole-machine skin into the per-cell tiles the game needs.

WHY THIS EXISTS. A machine built from one repeating 16px panel reads as a grid of tiles rather than
as a machine, and the stronger the panel's art the worse it gets, because the eye counts the repeats.
Six identical tiles across a flank is a wall. No prompt fixes it - the failure IS the repetition.

So a machine's surface is authored as one image per face and cut here into 16px tiles, one per cell
position. Nothing repeats, the seams stop being findable, and the machine becomes a single designed
object. The Separator's 2x2 grinding bay already worked this way and was the proof of it; this is the
same idea over every face.

WHAT IT EMITS, for a machine of W x H x D cells:
  textures/block/<machine>_skin_<face>_<cell>.png   one 16px tile per visible cell face
  models/block/<machine>_<part>_<cell>.json         one model per cell, wired to its own tiles
  blockstates/<part>.json                           cell x facing -> model

The cell index matches Multiblock.cellIndex - a dense index over the machine's real cells, ordered
bottom to top then back to front then left to right, taken from the UNROTATED offset. Facing is a model
rotation, which turns the whole skin together, which is exactly why the index can ignore it.

SOURCE SHEETS are the promoted <machine>_skin_<face>.png in textures/block/ - the ordinary texgen
output for a `block_skin` surface, so the sheets go through generate/sheet/select/promote like any
other art and there is no second place to look. Sized (W*16 x H*16) for north/south, (D*16 x H*16)
for east/west, (W*16 x D*16) for up/down. A face with no sheet falls back to the machine's plain
panel texture, so a machine can be skinned one face at a time.

The sheet itself stays in the atlas after cutting. It costs a few hundred pixels and it keeps
`texgen validate` honest about the surface being shipped; deleting it would make the pipeline report
a hole that is not there.

Run: python tools/skin_machine.py separator
"""
import io
import json
import os
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, 'src/main/resources/assets/recompile')
SHEETS = os.path.join(ASSETS, 'textures/block')

# Must equal MultiblockSkinnedBlock.MAX_CELLS.
MAX_CELLS = 16

# A face's sheet is indexed by two of the three axes, and which two depends on the face. The third
# entry says whether the horizontal axis runs backwards.
#
# GET THIS WRONG AND EVERY SEAM ON THAT FACE BREAKS, which is subtler than it sounds. Minecraft draws
# a face's texture un-mirrored when seen from outside - vanilla's TNT proves it, since its lettering
# reads correctly on all four sides. So reversing the tile ORDER without also mirroring each tile's
# CONTENT butts column 2's right edge against column 1's left edge, and nothing lines up anywhere.
# It is easy to talk yourself into believing a mirrored layout is still continuous; it is not.
#
# Which way each face runs, derived from where east ends up when you stand outside and look at it:
#   north  seen from -z looking +z: east is on your LEFT,  so screen left-to-right is x DECREASING
#   south  seen from +z looking -z: east is on your RIGHT, so screen left-to-right is x increasing
#   west   seen from -x looking +x: south is on your RIGHT, so screen left-to-right is z increasing
#   east   seen from +x looking -x: south is on your LEFT,  so screen left-to-right is z DECREASING
FACE_AXES = {
    'north': ('x', 'y', True),
    'south': ('x', 'y', False),
    'west':  ('z', 'y', False),
    'east':  ('z', 'y', True),
    'up':    ('x', 'z', False),
    'down':  ('x', 'z', False),
}


class Machine:
    """A machine's shape plus which formed block sits at each cell."""

    def __init__(self, name, width, height, depth, positions, cells, fallback, shapes=None,
                 bay=None):
        self.name = name
        self.w, self.h, self.d = width, height, depth
        self.positions = set(positions) | {(0, 0, 0)} | set(cells)
        self.cells = cells          # {(x, y, z): part-name} - only the cells this tool skins
        self.fallback = fallback    # texture id used where a face has no sheet
        # Parts whose geometry is not a full cube, as [from, to] boxes. A cell keeps its shape and
        # only its SKIN comes from the sheet - the chute's mouth is cut into its model, and a
        # generator that quietly replaced it with a cube would erase the machine's one opening.
        self.shapes = shapes or {}
        # {(x, y, z): quadrant} for a machine with an animated grinding bay.
        self.bay = bay or {}

    def skin_order(self):
        """Every position the machine occupies, in the SAME canonical order as Multiblock.skinOrder on
        the Java side: bottom to top, then back to front, then left to right.

        EVERY position, including the ones this tool emits nothing for. The core has its own model and
        so do the animated bay cells, but they still take up numbers, and leaving them out shifts every
        index above them by one. That mismatch shows up as the machine's skin scrambling - each cell
        wearing some other cell's tile - which reads as bad art rather than as an off-by-one, so
        nothing points back here."""
        return sorted(self.positions, key=lambda p: (p[1], p[2], p[0]))

    def index(self, x, y, z):
        return self.skin_order().index((x, y, z))

    def outward(self, x, y, z):
        """The faces of this cell that are on the machine's outside. Interior faces are never seen,
        so they keep the plain panel and cost no art."""
        faces = []
        if z == 0:
            faces.append('north')
        if z == self.d - 1:
            faces.append('south')
        if x == 0:
            faces.append('west')
        if x == self.w - 1:
            faces.append('east')
        if y == self.h - 1:
            faces.append('up')
        if y == 0:
            faces.append('down')
        return faces

    def sheet_size(self, face):
        hor, ver, _ = FACE_AXES[face]
        span = {'x': self.w, 'y': self.h, 'z': self.d}
        return span[hor] * 16, span[ver] * 16

    def tile_at(self, face, x, y, z):
        """Where in the face's sheet this cell's tile lives, in tile units."""
        hor, ver, flip = FACE_AXES[face]
        pos = {'x': x, 'y': y, 'z': z}
        span = {'x': self.w, 'y': self.h, 'z': self.d}
        u = pos[hor]
        if flip:
            u = span[hor] - 1 - u
        v = pos[ver]
        # Image rows run downward and the machine's y runs upward, so the top row of a side sheet is
        # the machine's top layer. Get this wrong and the skin is upside down but still "works".
        if ver == 'y':
            v = self.h - 1 - v
        return u, v


def load_sheet(machine, face):
    path = os.path.join(SHEETS, '%s_skin_%s.png' % (machine.name, face))
    if not os.path.exists(path):
        return None
    sheet = Image.open(path).convert('RGBA')
    want = machine.sheet_size(face)
    if sheet.size != want:
        raise SystemExit('%s is %s, expected %s for a %dx%dx%d machine'
                         % (path, sheet.size, want, machine.w, machine.h, machine.d))
    return sheet


# The Separator's grinding bay. Its top is the animated grinder and its rim is geometry, but its
# OUTWARD SIDES are machine flank like everything else, so they take skin tiles too. Leaving them on
# the plain panel put a repeating tile in the middle of an otherwise continuous face - the exact thing
# the skin exists to remove, just harder to spot because it is only two blocks of it.
BAY_RIM_BOTTOM, BAY_RIM_TOP, BAY_RIM_THICK, BAY_FLOOR_TOP = 13, 16, 2, 13

# quadrant -> the two edges of the 2x2 bay that are on its outside
BAY_OUTER = {0: ('west', 'north'), 1: ('east', 'north'), 2: ('west', 'south'), 3: ('east', 'south')}


def bay_rim(side, has_ns):
    """A rim wall on `side`, filling to the outside corner without overlapping its partner."""
    lo, hi = 0, 16
    if side in ('west', 'east'):
        lo, hi = (BAY_RIM_THICK, 16) if has_ns == 'north' else (0, 16 - BAY_RIM_THICK)
    if side == 'north':
        return [0, BAY_RIM_BOTTOM, 0], [16, BAY_RIM_TOP, BAY_RIM_THICK]
    if side == 'south':
        return [0, BAY_RIM_BOTTOM, 16 - BAY_RIM_THICK], [16, BAY_RIM_TOP, 16]
    if side == 'west':
        return [0, BAY_RIM_BOTTOM, lo], [BAY_RIM_THICK, BAY_RIM_TOP, hi]
    return [16 - BAY_RIM_THICK, BAY_RIM_BOTTOM, lo], [16, BAY_RIM_TOP, hi]


def emit_bay(machine, sheets, bay_cells):
    """Models for the four bay cells: animated grinder on top, skin on the outward sides."""
    made = 0
    for (x, y, z), quadrant in sorted(bay_cells.items()):
        index = machine.index(x, y, z)
        outward = machine.outward(x, y, z)
        textures = {'particle': 'recompile:block/' + machine.fallback}
        for face in ('down', 'up', 'north', 'south', 'west', 'east'):
            key = machine.fallback
            if face in outward and face not in ('up', 'down') and sheets.get(face) is not None:
                u, v = machine.tile_at(face, x, y, z)
                tile = sheets[face].crop((u * 16, v * 16, u * 16 + 16, v * 16 + 16))
                key = '%s_skin_%s_%d' % (machine.name, face, index)
                tile.save(os.path.join(ASSETS, 'textures/block', key + '.png'))
                made += 1
            textures[face] = 'recompile:block/' + key

        for running in (False, True):
            local = dict(textures)
            local['floor'] = 'recompile:block/%s_bay_%d%s' % (
                machine.name, quadrant, '_running' if running else '')
            elements = [{
                'from': [0, 0, 0], 'to': [16, BAY_FLOOR_TOP, 16],
                'faces': {
                    'down': {'texture': '#down', 'cullface': 'down'},
                    'up': {'texture': '#floor'},
                    'north': {'texture': '#north', 'cullface': 'north'},
                    'south': {'texture': '#south', 'cullface': 'south'},
                    'west': {'texture': '#west', 'cullface': 'west'},
                    'east': {'texture': '#east', 'cullface': 'east'},
                },
            }]
            sides = BAY_OUTER[quadrant]
            ns = 'north' if 'north' in sides else 'south'
            for side in sides:
                frm, to = bay_rim(side, ns)
                elements.append({
                    'from': frm, 'to': to,
                    # Cull only the face on the machine's outside; the inner wall of the well is
                    # visible and culling it punches a hole straight through.
                    'faces': {f: ({'texture': '#' + f, 'cullface': f} if f == side
                                  else {'texture': '#' + f})
                              for f in ('up', 'down', 'north', 'south', 'west', 'east')},
                })
            name = '%s_bay_%d%s' % (machine.name, quadrant, '_running' if running else '')
            io.open(os.path.join(ASSETS, 'models/block', name + '.json'),
                    'w', encoding='utf-8', newline='\n').write(
                json.dumps({'parent': 'minecraft:block/block', 'textures': local,
                            'elements': elements}, indent=2) + '\n')
    print('bay: 4 quadrants x idle/running, %d skin tiles' % made)


def emit(machine):
    sheets = {face: load_sheet(machine, face) for face in FACE_AXES}
    written = {'textures': 0, 'models': 0}
    by_part = {}

    for (x, y, z), part in sorted(machine.cells.items()):
        index = machine.index(x, y, z)
        by_part.setdefault(part, []).append(index)

        textures = {'particle': 'recompile:block/' + machine.fallback}
        faces = {}
        for face in ('down', 'up', 'north', 'south', 'west', 'east'):
            key = machine.fallback
            if face in machine.outward(x, y, z) and sheets.get(face) is not None:
                u, v = machine.tile_at(face, x, y, z)
                tile = sheets[face].crop((u * 16, v * 16, u * 16 + 16, v * 16 + 16))
                key = '%s_skin_%s_%d' % (machine.name, face, index)
                tile.save(os.path.join(ASSETS, 'textures/block', key + '.png'))
                written['textures'] += 1
            textures[face] = 'recompile:block/' + key
            faces[face] = {'texture': '#' + face, 'cullface': face}

        boxes = machine.shapes.get(part) or [[[0, 0, 0], [16, 16, 16]]]
        elements = []
        for frm, to in boxes:
            # Cull only where the element actually reaches the block edge. Culling a face that stops
            # short punches a hole straight through the world - the non-cube trap CLAUDE.md records -
            # and it looks like a rendering glitch with no obvious cause.
            reaches = {
                'west': frm[0] == 0, 'east': to[0] == 16,
                'down': frm[1] == 0, 'up': to[1] == 16,
                'north': frm[2] == 0, 'south': to[2] == 16,
            }
            elements.append({
                'from': list(frm),
                'to': list(to),
                'faces': {face: ({'texture': '#' + face, 'cullface': face} if reaches[face]
                                 else {'texture': '#' + face})
                          for face in faces},
            })
        model = {
            'parent': 'minecraft:block/block',
            'textures': textures,
            'elements': elements,
        }
        name = '%s_%d' % (part, index)
        io.open(os.path.join(ASSETS, 'models/block', name + '.json'),
                'w', encoding='utf-8', newline='\n').write(json.dumps(model, indent=2) + '\n')
        written['models'] += 1

    for part, indices in by_part.items():
        variants = {}
        # Every value the property can take, not just the ones this machine uses: a missing variant
        # renders as the purple missing model, and MAX_CELLS is a shared ceiling.
        for index in range(MAX_CELLS):
            model = '%s_%d' % (part, index if index in indices else indices[0])
            for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                entry = {'model': 'recompile:block/' + model}
                if rot:
                    entry['y'] = rot
                variants['cell=%d,facing=%s' % (index, facing)] = entry
        io.open(os.path.join(ASSETS, 'blockstates', part + '.json'),
                'w', encoding='utf-8', newline='\n').write(
            json.dumps({'variants': variants}, indent=2) + '\n')
        print('blockstate %-24s %d cells' % (part, len(indices)))

    if machine.bay:
        emit_bay(machine, sheets, machine.bay)
    print('%(textures)d tiles, %(models)d models' % written)


# The Separator: 3 wide x 2 tall x 2 deep. The core sits at (0,0,0) and keeps its own model, and the
# four bay cells keep theirs - the bay is already one image quartered, and it animates.
SEPARATOR = Machine(
    'separator', 3, 2, 2,
    # Every position the machine occupies. The core at the origin and the four bay cells keep their
    # own models, but they still take up indices.
    positions=[(x, y, z) for x in range(3) for y in range(2) for z in range(2)],
    cells={
        (1, 0, 0): 'separator_chute',
        (2, 0, 0): 'separator_housing',
        (0, 0, 1): 'separator_housing',
        (1, 0, 1): 'separator_housing',
        (2, 0, 1): 'separator_housing',
        (0, 1, 0): 'separator_housing',
        (0, 1, 1): 'separator_housing',
    },
    fallback='separator_housing',
    bay={(1, 1, 0): 0, (2, 1, 0): 1, (1, 1, 1): 2, (2, 1, 1): 3},
    shapes={
        # The chute: a body with the bottom front cut away, which is the mouth everything falls out
        # of. Matches the hand-authored model it replaces.
        'separator_chute': [
            [[0, 5, 0], [16, 16, 16]],
            [[0, 0, 4], [16, 5, 16]],
        ],
    },
)

MACHINES = {'separator': SEPARATOR}

if __name__ == '__main__':
    if len(sys.argv) != 2 or sys.argv[1] not in MACHINES:
        raise SystemExit('usage: skin_machine.py <%s>' % '|'.join(MACHINES))
    emit(MACHINES[sys.argv[1]])
