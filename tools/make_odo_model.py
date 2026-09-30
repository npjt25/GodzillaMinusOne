"""Builds the Odo Island Monster: Godzilla Minus One's earlier, smaller dinosaur form (about 15 blocks tall),
in the same blocky style as the add-on's Godzilla. Writes its GeckoLib geometry, textures and animations.

    python3 tools/make_odo_model.py

Everything is generated here: a theropod skeleton (the body leaning forward over two massive digitigrade legs,
high shoulders, a long thick neck reaching forward to a level, long-snouted head, big clawed arms, a long tail
held out behind), a cluster of tall many-pointed plates over the shoulders with rows of smaller spikes down the
neck, skull and tail, rough bumps all over, and a brown hide of overlapping scale rows with pale edges (after a
screenshot of a Bedrock model of the Odo form that the mod's author sent as a reference).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import detail_godzilla  # noqa: E402  (the hide pattern)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
NAME = "odo_island_monster"
TEX_SIZE = 512          # UV units
TEXELS = 2              # texels per UV unit

# Model units are pixels; the renderer scales the model by 3.46 (OdoIslandMonsterRenderer) so that its 69 px
# stand 15 blocks tall. The whole model is written Z_SHIFT px further back than it is built here, so that the
# middle of its torso (not its hips) sits over the entity's position, at the centre of its hitbox.
Z_SHIFT = 9.0

# ---------------------------------------------------------------- skeleton
# name: (parent, pivot, rest rotation [x, y, z] in Bedrock degrees; negative x lifts the front (-z) end up)
HIP_Y = 36.0
BONES = {
    "root": (None, (0, 0, 0), (0, 0, 0)),
    "body": ("root", (0, HIP_Y, 0), (0, 0, 0)),
    "hips": ("body", (0, HIP_Y, 0), (-8, 0, 0)),
    "chest": ("hips", (0, HIP_Y + 2, -9), (-36, 0, 0)),     # the shoulders held high
    "neck": ("chest", (0, HIP_Y + 6, -25), (12, 0, 0)),     # the neck reaching forward...
    "neck2": ("neck", (0, HIP_Y + 7, -33), (8, 0, 0)),
    "head": ("neck2", (0, HIP_Y + 8, -40), (26, 0, 0)),     # ...to a level head
    "jaw": ("head", (0, HIP_Y + 5, -43), (0, 0, 0)),
    "tail1": ("hips", (0, HIP_Y + 1, 8), (6, 0, 0)),
    "tail2": ("tail1", (0, HIP_Y + 1, 26), (4, 0, 0)),
    "tail3": ("tail2", (0, HIP_Y + 1, 44), (-2, 0, 0)),
    "tail4": ("tail3", (0, HIP_Y + 1, 61), (-4, 0, 0)),
    "tail5": ("tail4", (0, HIP_Y + 1, 76), (-4, 0, 0)),
    "arm_left": ("chest", (7.5, HIP_Y - 1, -21), (60, 0, -12)),
    "forearm_left": ("arm_left", (8, HIP_Y - 13, -21), (-58, 0, 0)),
    "arm_right": ("chest", (-7.5, HIP_Y - 1, -21), (60, 0, 12)),
    "forearm_right": ("arm_right", (-8, HIP_Y - 13, -21), (-58, 0, 0)),
}
LEG = {  # per side: thigh from the hip, shin, metatarsus (the long "ankle" bone), foot
    "thigh": ((8.5, HIP_Y - 1, 0), (-28, 0, 0)),
    "shin": ((8.5, HIP_Y - 17, 0), (62, 0, 0)),
    "ankle": ((8.5, HIP_Y - 30, 0), (-44, 0, 0)),
    "foot": ((8.5, HIP_Y - 38, 0), (10, 0, 0)),
}
for side, sx in (("left", 1), ("right", -1)):
    parent = "hips"
    for part, (pivot, rot) in LEG.items():
        name = "%s_%s" % (part, side)
        BONES[name] = (parent, (pivot[0] * sx, pivot[1], pivot[2]), rot)
        parent = name


# ---------------------------------------------------------------- cubes
def box(cx, cy, cz, sx, sy, sz, kind="skin", rot=None, pivot=None, inflate=0.0):
    """A cube by its centre (Bedrock coordinates) and size."""
    c = {"origin": [cx - sx / 2, cy - sy / 2, cz - sz / 2], "size": [sx, sy, sz], "kind": kind}
    if rot:
        c["rotation"] = list(rot)
        c["pivot"] = list(pivot if pivot else (cx, cy, cz))
    if inflate:
        c["inflate"] = inflate
    return c


def mirror(cubes):
    out = []
    for c in cubes:
        m = json.loads(json.dumps(c))
        m["origin"][0] = -(c["origin"][0] + c["size"][0])
        if "pivot" in m:
            m["pivot"][0] = -m["pivot"][0]
            m["rotation"] = [m["rotation"][0], -m["rotation"][1], -m["rotation"][2]]
        out.append(m)
    return out


def plate(bone_cubes, x, y, z, height, length, tilt=-12.0, lean=0.0):
    """A dorsal plate: a broad root with jagged points fanning out of it (five on the big ones), on (x, y, z)."""
    bone_cubes.append(box(x, y + height * 0.18, z, 1.6, height * 0.36, length, "plate", rot=(tilt, 0, lean), pivot=(x, y, z)))
    if height > 11:
        points = ((-length * 0.42, 0.5, 0.26, 34), (-length * 0.22, 0.8, 0.3, 16), (0.0, 1.0, 0.34, 0),
                  (length * 0.22, 0.74, 0.3, -16), (length * 0.42, 0.46, 0.26, -32))
    else:
        points = ((-length * 0.3, 0.72, 0.34, 20), (0.0, 1.0, 0.38, 0), (length * 0.32, 0.58, 0.32, -24))
    for dz, h, l, fan in points:
        bone_cubes.append(box(x, y + height * h / 2, z + dz, 1.1, height * h, length * l, "plate",
                              rot=(tilt + fan, 0, lean), pivot=(x, y, z + dz)))


def spike(bone_cubes, x, y, z, height, tilt=-20.0, lean=0.0):
    """A small spike on the ridge of the neck, skull or tail."""
    bone_cubes.append(box(x, y + height / 2, z, 1.0, height, 1.2, "plate", rot=(tilt, 0, lean), pivot=(x, y, z)))


def bumps(bone_cubes, rng, density, kinds=("skin",)):
    """Rough little lumps over a bone's big cubes (not their undersides)."""
    base = [c for c in bone_cubes if c["kind"] in kinds and min(c["size"]) >= 3.0 and "rotation" not in c]
    out = []
    for c in base:
        o, sz = c["origin"], c["size"]
        for axis, sign in ((0, 1), (0, -1), (1, 1), (2, 1), (2, -1)):
            a, b = [k for k in range(3) if k != axis]
            n = int(round(sz[a] * sz[b] / 100.0 * density))
            for _ in range(n):
                w = float(rng.uniform(0.8, 2.0))
                h = float(rng.uniform(0.3, 0.9))
                if sz[a] < w + 0.6 or sz[b] < w + 0.6:
                    continue
                origin = [0.0, 0.0, 0.0]
                size = [0.0, 0.0, 0.0]
                origin[a] = o[a] + float(rng.uniform(0.3, sz[a] - w - 0.3))
                origin[b] = o[b] + float(rng.uniform(0.3, sz[b] - w - 0.3))
                size[a], size[b] = w, w * float(rng.uniform(0.7, 1.2))
                size[axis] = h + 0.3
                origin[axis] = o[axis] + sz[axis] - 0.3 if sign > 0 else o[axis] - h
                out.append({"origin": origin, "size": size, "kind": c["kind"]})
    bone_cubes.extend(out)


def build_cubes():
    Y = HIP_Y
    cubes = {name: [] for name in BONES}
    # --- torso
    cubes["hips"] += [box(0, Y + 1, 0, 19, 20, 20), box(0, Y - 9, 1, 14, 3, 15, "belly"),
                      box(0, Y + 11.5, 1, 13, 2, 16), box(0, Y + 1, 10.5, 16, 17, 3)]
    cubes["chest"] += [box(0, Y + 2.5, -17.5, 19, 21, 18), box(0, Y - 8.5, -17, 14, 3, 15, "belly"),
                       box(0, Y + 13.5, -16.5, 15, 3, 15), box(0, Y - 3, -25.5, 15, 13, 3),
                       box(8.5, Y + 6, -19, 4, 9, 9), box(-8.5, Y + 6, -19, 4, 9, 9)]     # shoulders
    cubes["neck"] += [box(0, Y + 5.5, -29.5, 13, 13.5, 11), box(0, Y - 1.5, -29.5, 9, 2, 9, "belly")]
    cubes["neck2"] += [box(0, Y + 7.5, -37, 11.5, 11.5, 9), box(0, Y + 1.8, -37, 7.5, 2, 7.5, "belly")]
    # --- head: skull, brows, snout, teeth, eyes
    h = Y + 8
    head = cubes["head"]
    head += [box(0, h, -45, 12.5, 10, 12),                     # skull
             box(0, h + 5.3, -44, 10, 1.6, 10),                # skull ridge
             box(4, h + 3.6, -49, 3.6, 2.4, 4.5),              # brows
             box(-4, h + 3.6, -49, 3.6, 2.4, 4.5),
             box(0, h - 0.5, -55, 9, 6.5, 10),                 # snout
             box(0, h + 3, -54.5, 6.5, 1.3, 9),                # snout ridge
             box(0, h - 1.5, -61.5, 7.5, 4.5, 4.5),            # snout tip
             box(0, h - 4.4, -53.5, 8.5, 1.4, 16, "mouth"),     # palate
             box(5.9, h - 1, -44, 1.4, 5, 7),                  # cheeks
             box(-5.9, h - 1, -44, 1.4, 5, 7),
             box(3.6, h + 1.8, -50.4, 1.3, 1.3, 0.6, "eye"),
             box(-3.6, h + 1.8, -50.4, 1.3, 1.3, 0.6, "eye"),
             box(0, h + 1.2, -38.8, 11, 8, 2.5),               # back of the skull, into the neck
             box(2.4, h + 0.4, -63.8, 1.1, 1.1, 0.5, "dark"),   # nostrils
             box(-2.4, h + 0.4, -63.8, 1.1, 1.1, 0.5, "dark")]
    for i in range(9):                                          # upper teeth, both sides
        z = -62.5 + i * 1.9
        length = [1.7, 1.4, 1.6, 1.2, 1.4, 1.1, 1.0, 0.9, 0.8][i]
        for x in (3.5, -3.5):
            head.append(box(x, h - 5.1 - length / 2, z, 0.8, length, 0.9, "tooth"))
    jaw = cubes["jaw"]
    j = Y + 5
    jaw += [box(0, j - 3.3, -52.5, 8, 3.2, 19), box(0, j - 5.2, -50.5, 6.5, 1.5, 14, "belly"),
            box(0, j - 1.5, -52.5, 7, 0.5, 17, "mouth")]       # tongue / floor of the mouth
    for i in range(9):
        z = -61 + i * 1.9
        length = [1.3, 1.5, 1.1, 1.3, 1.0, 1.1, 0.9, 0.8, 0.7][i]
        for x in (3.3, -3.3):
            jaw.append(box(x, j - 1.7 + length / 2, z, 0.8, length, 0.9, "tooth"))
    # --- tail: tapering segments with a spiky ridge
    tail = [("tail1", 15, 16.5, 19, 17), ("tail2", 12.5, 13, 19, 35), ("tail3", 9.5, 10, 18, 52.5),
            ("tail4", 7, 7.5, 16, 68.5), ("tail5", 4.8, 5.2, 18, 84)]
    for name, w, hgt, length, zc in tail:
        cubes[name] += [box(0, Y + 1, zc, w, hgt, length), box(0, Y + 1 - hgt / 2 - 0.6, zc, w * 0.7, 1.2, length * 0.9, "belly")]
    cubes["tail5"].append(box(0, Y + 1, 94.5, 2.5, 3, 4))
    # --- legs (left; mirrored below)
    x = 8.5
    left = {
        "thigh_left": [box(x + 1, Y - 8, 0, 14, 21, 17), box(x + 1, Y - 0.5, 0, 15, 8, 19), box(x + 1.5, Y - 14.5, -1, 11, 4, 13)],
        "shin_left": [box(x, Y - 23.5, 0, 9, 14, 9.5), box(x, Y - 17.5, -1, 10, 4, 10.5), box(x, Y - 23, 3.8, 6, 9, 2)],
        "ankle_left": [box(x, Y - 34, 0, 6.5, 9, 6.5)],
        "foot_left": [box(x, Y - 39.3, -3.5, 8, 2.5, 11)],
    }
    for i, dx in enumerate((-2.7, 0, 2.7)):                    # three thick toes with big pale claws
        left["foot_left"].append(box(x + dx, Y - 39.2, -10, 2.4, 2.4, 4.5, rot=(0, (1 - i) * 12, 0), pivot=(x + dx, Y - 39.2, -8)))
        left["foot_left"].append(box(x + dx, Y - 39.7, -13.2, 1.5, 1.8, 3.2, "claw", rot=(22, (1 - i) * 12, 0), pivot=(x + dx, Y - 39.2, -8)))
    left["foot_left"].append(box(x, Y - 38.4, 3.5, 1.8, 1.8, 3.5, "claw"))      # dewclaw at the back
    for name, cs in left.items():
        cubes[name] += cs
        cubes[name.replace("left", "right")] += mirror(cs)
    # --- arms
    a = {"arm_left": [box(8, Y - 7, -21, 5.5, 13, 5.5), box(8, Y - 1.5, -21, 7, 5, 7)],
         "forearm_left": [box(8, Y - 18.5, -21, 4.6, 11, 4.6), box(8, Y - 24.8, -21.3, 5.2, 3, 5.4)]}   # forearm, hand
    for i, dz in enumerate((-1.6, 0, 1.6)):                    # three long curved claws
        a["forearm_left"].append(box(8 + (i - 1) * 1.4, Y - 27.8, -22.8 + dz * 0.3, 1.1, 3.8, 1.1, "claw",
                                     rot=(40, 0, (i - 1) * 8), pivot=(8 + (i - 1) * 1.4, Y - 26.2, -22.6)))
    for name, cs in a.items():
        cubes[name] += cs
        cubes[name.replace("left", "right")] += mirror(cs)
    # --- dorsal plates: a centre row, with smaller rows either side on the back
    rows = [  # bone, z positions, base y, heights: the big cluster rises over the shoulders
        ("chest", [-9.5, -13.5, -17.5, -21.5, -25], Y + 14.5, [15, 21, 23, 19, 12]),
        ("hips", [-4.5, 0, 4.5, 8.5], Y + 11.5, [13, 11.5, 10, 8.5]),
        ("tail1", [11, 16, 21], Y + 9, [6.5, 5.5, 5]),
    ]
    for bone, zs, base, hs in rows:
        for i, (z, height) in enumerate(zip(zs, hs)):
            plate(cubes[bone], 0, base, z, height, max(2.2, height * 0.34))
            if bone == "chest" and i % 2 == 0 or bone == "hips" and i == 0:
                for x, lean in ((4.4, 20), (-4.4, -20)):
                    plate(cubes[bone], x, base - 1.5, z + 1.4, height * 0.6, max(2.0, height * 0.3), lean=lean)
    # ridges of small spikes down the neck, over the skull and snout, and along the tail
    ridges = [
        ("neck", [(-26, Y + 12.4, 4.5), (-29, Y + 12.4, 4.0), (-32, Y + 12.4, 3.6)]),
        ("neck2", [(-35, Y + 13.4, 3.4), (-38, Y + 13.4, 3.0)]),
        ("head", [(z, Y + 14.3 - (0 if z > -49 else 2.2 if z > -56 else 3.4), hgt) for z, hgt in
                  ((-41, 2.6), (-43.5, 2.4), (-46, 2.2), (-48.5, 2.0), (-51, 1.7), (-53.5, 1.5), (-56, 1.4), (-58.5, 1.2))]),
        ("tail1", [(25, Y + 9.5, 4.4)]),
        ("tail2", [(z, Y + 7.8, h) for z, h in ((28, 4.2), (31.5, 3.9), (35, 3.6), (38.5, 3.4), (42, 3.1))]),
        ("tail3", [(z, Y + 6.2, h) for z, h in ((46, 3.0), (49.5, 2.8), (53, 2.6), (56.5, 2.4), (60, 2.2))]),
        ("tail4", [(z, Y + 4.9, h) for z, h in ((63.5, 2.1), (67, 1.9), (70.5, 1.7), (74, 1.5))]),
        ("tail5", [(z, Y + 3.8, h) for z, h in ((78, 1.4), (81.5, 1.3), (85, 1.2), (88.5, 1.1), (92, 1.0))]),
    ]
    for bone, spikes in ridges:
        for z, y, height in spikes:
            spike(cubes[bone], 0, y, z, height)
            if bone in ("neck", "neck2", "tail1", "tail2", "tail3"):
                for x, lean in ((2.8, 20), (-2.8, -20)):
                    spike(cubes[bone], x, y - 1.0, z + 0.8, height * 0.65, lean=lean)
    rng = np.random.default_rng(2023)
    for bone, density in (("hips", 2.4), ("chest", 2.4), ("neck", 2.6), ("neck2", 2.6), ("head", 1.6), ("jaw", 1.4),
                          ("tail1", 2.2), ("tail2", 2.2), ("tail3", 2.0), ("tail4", 1.8), ("tail5", 1.5),
                          ("thigh_left", 2.4), ("shin_left", 2.2), ("arm_left", 2.0), ("forearm_left", 1.8)):
        before = len(cubes[bone])
        bumps(cubes[bone], rng, density)
        if bone.endswith("_left"):
            cubes[bone.replace("left", "right")] += mirror(cubes[bone][before:])
    return cubes


# ---------------------------------------------------------------- texture
COLORS = {  # base RGB for each kind of cube: a dark brown hide, pale claws and plate tips
    "skin": (84, 64, 46), "belly": (104, 82, 60), "plate": (60, 46, 35), "mouth": (128, 44, 42),
    "tooth": (206, 196, 172), "claw": (190, 170, 138), "eye": (205, 150, 48), "dark": (16, 14, 12),
}
BURNT = {
    "skin": (26, 23, 21), "belly": (34, 30, 27), "plate": (44, 40, 36), "mouth": (52, 24, 20),
    "tooth": (92, 86, 78), "claw": (40, 36, 32), "eye": (30, 26, 22), "dark": (10, 10, 10),
}


def scale_rows(h, w, rng, row=5, cell=6):
    """Overlapping scales in staggered rows, like the reference's hide: each scale pale along its top edge
    and shadowed along its bottom, with its own shade."""
    yy, xx = np.mgrid[0:h, 0:w]
    r = yy // row
    shift = (r % 2) * (cell // 2)
    c = (xx + shift) // cell
    within = (yy % row) / (row - 1)
    tone = rng.random((h // row + 2, w // cell + 3))
    t = tone[r, np.minimum(c, tone.shape[1] - 1)]
    edge = np.where(within < 0.21, 1.34, np.where(within > 0.79, 0.72, 1.0))
    side = np.where((xx + shift) % cell == 0, 0.8, 1.0)
    return edge * side * (0.88 + 0.24 * t)


def pack(cubes):
    """Shelf-packs every cube's box UV into the sheet; sets each cube's "uv"."""
    items = []
    for bone, cs in cubes.items():
        for c in cs:
            sx, sy, sz = (max(1, math.ceil(v)) for v in c["size"])
            items.append((sy + sz, 2 * (sx + sz), c))
    items.sort(key=lambda it: (-it[0], -it[1]))
    x = y = shelf = 0
    for hgt, wid, c in items:
        if x + wid > TEX_SIZE:
            x, y, shelf = 0, y + shelf, 0
        if y + hgt > TEX_SIZE:
            raise SystemExit("texture sheet full")
        c["uv"] = [x, y]
        x += wid
        shelf = max(shelf, hgt)
    return y + shelf


def paint(cubes, palette, detail, rng, embers=None):
    img = np.zeros((TEX_SIZE * TEXELS, TEX_SIZE * TEXELS, 4))
    glow = np.zeros_like(img)
    for bone, cs in cubes.items():
        for c in cs:
            u, v = c["uv"]
            sx, sy, sz = c["size"]
            sxi, syi, szi = (max(1, math.ceil(q)) for q in c["size"])
            w, h = 2 * (sxi + szi) * TEXELS, (syi + szi) * TEXELS
            base = np.array(palette[c["kind"]], float)
            region = np.empty((h, w, 3))
            region[:] = base
            if c["kind"] in ("skin", "belly", "plate", "claw"):
                d = detail[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w]
                region *= (0.55 + 0.45 * d)[..., None]
            if c["kind"] in ("skin", "belly"):
                region *= scale_rows(h, w, rng, row=5 if c["kind"] == "skin" else 7)[..., None]
            if c["kind"] == "plate":
                # dark, with pale tan tips (rows run from the top of the plate down)
                frac = np.linspace(0.0, 1.0, h)
                grad = 0.9 + 1.9 * np.clip(1.0 - frac / 0.3, 0.0, 1.0) ** 1.5
                region *= grad[:, None, None]
            if c["kind"] == "claw":
                region *= np.linspace(1.15, 0.7, h)[:, None, None]
            if c["kind"] == "skin":
                # the side faces get a little lighter towards the belly
                side = np.zeros((h, w))
                side[szi * TEXELS:, :] = np.linspace(0, 1, h - szi * TEXELS)[:, None]
                region *= (0.9 + 0.2 * side)[..., None]
            region *= (0.94 + 0.12 * rng.random((h, w)))[..., None]
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, :3] = np.clip(region, 0, 255)
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, 3] = 255
            if embers is not None and c["kind"] in ("skin", "belly", "plate"):
                # glowing cracks in the burnt hide
                crack = embers[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w]
                g = glow[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w]
                g[..., 0] = 255 * crack
                g[..., 1] = 110 * crack
                g[..., 2] = 30 * crack
                g[..., 3] = 255 * (crack > 0.05)
            if c["kind"] == "eye" and embers is None:
                glow[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w] = [255, 150, 60, 255]
    return img, glow


def build_textures(cubes):
    rng = np.random.default_rng(1954)
    size = TEX_SIZE * TEXELS
    detail = detail_godzilla.hide_detail(size, rng, cell=7)
    f1, f2 = detail_godzilla.worley(size, 14, rng)
    embers = np.clip(1.0 - (f2 - f1) / 1.8, 0.0, 1.0) ** 3 * (rng.random((size, size)) > 0.35)
    tex = os.path.join(ASSETS, "textures", "entity")
    img, glow = paint(cubes, COLORS, detail, np.random.default_rng(7))
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(tex, NAME + ".png"))
    Image.fromarray(glow.astype(np.uint8), "RGBA").save(os.path.join(tex, NAME + "_glow.png"))
    img, glow = paint(cubes, BURNT, detail * 0.9 + 0.1, np.random.default_rng(7), embers)
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(tex, NAME + "_burnt.png"))
    Image.fromarray(glow.astype(np.uint8), "RGBA").save(os.path.join(tex, NAME + "_burnt_glow.png"))


# ---------------------------------------------------------------- geometry file
def write_geometry(cubes):
    bones = []

    def shift(p):
        return [p[0], p[1], p[2] + Z_SHIFT]

    for name, (parent, pivot, rot) in BONES.items():
        b = {"name": name, "pivot": [round(p, 3) for p in shift(pivot)]}
        if parent:
            b["parent"] = parent
        if any(rot):
            b["rotation"] = list(rot)
        cs = []
        for c in cubes[name]:
            out = {"origin": [round(q, 3) for q in shift(c["origin"])], "size": [round(q, 3) for q in c["size"]], "uv": c["uv"]}
            for k in ("rotation", "pivot", "inflate"):
                if k in c:
                    v = shift(c[k]) if k == "pivot" else c[k]
                    out[k] = [round(q, 3) for q in v] if isinstance(v, list) else v
            cs.append(out)
        if cs:
            b["cubes"] = cs
        bones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_SIZE, "texture_height": TEX_SIZE,
                        "visible_bounds_width": 10, "visible_bounds_height": 8, "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}
    path = os.path.join(ASSETS, "geo", "entity", NAME + ".geo.json")
    with open(path, "w") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")
    return path


def main():
    cubes = build_cubes()
    used = pack(cubes)
    build_textures(cubes)
    path = write_geometry(cubes)
    import odo_animations
    odo_animations.write(os.path.join(ASSETS, "animations", "entity", NAME + ".animation.json"))
    print("wrote %s: %d bones, %d cubes, texture rows used %d/%d" % (
        os.path.relpath(path, ROOT), len(BONES), sum(len(c) for c in cubes.values()), used, TEX_SIZE))


if __name__ == "__main__":
    main()
