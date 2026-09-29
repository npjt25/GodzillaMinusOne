"""Builds the Odo Island Monster: Godzilla Minus One's earlier, smaller dinosaur form (about 15 blocks tall),
in the same blocky style as the add-on's Godzilla. Writes its GeckoLib geometry, textures and animations.

    python3 tools/make_odo_model.py

Everything is generated here: a theropod skeleton (body leaning forward over two big digitigrade legs, a long
tail held out behind, small arms, a big-snouted head), dorsal plates in rows down the neck, back and tail, and a
skin texture made with tools/detail_godzilla.py's hide pattern.
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
    "chest": ("hips", (0, HIP_Y + 2, -9), (-10, 0, 0)),
    "neck": ("chest", (0, HIP_Y + 6, -25), (-28, 0, 0)),
    "neck2": ("neck", (0, HIP_Y + 7, -33), (-8, 0, 0)),
    "head": ("neck2", (0, HIP_Y + 8, -40), (38, 0, 0)),
    "jaw": ("head", (0, HIP_Y + 5, -43), (0, 0, 0)),
    "tail1": ("hips", (0, HIP_Y + 1, 8), (6, 0, 0)),
    "tail2": ("tail1", (0, HIP_Y + 1, 26), (4, 0, 0)),
    "tail3": ("tail2", (0, HIP_Y + 1, 44), (-2, 0, 0)),
    "tail4": ("tail3", (0, HIP_Y + 1, 61), (-4, 0, 0)),
    "tail5": ("tail4", (0, HIP_Y + 1, 76), (-4, 0, 0)),
    "arm_left": ("chest", (6.5, HIP_Y - 1, -20), (25, 0, -10)),
    "forearm_left": ("arm_left", (7.5, HIP_Y - 8, -20), (-55, 0, 0)),
    "arm_right": ("chest", (-6.5, HIP_Y - 1, -20), (25, 0, 10)),
    "forearm_right": ("arm_right", (-7.5, HIP_Y - 8, -20), (-55, 0, 0)),
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
    """A dorsal plate: a broad root with three jagged points fanning out of it, standing on (x, y, z)."""
    bone_cubes.append(box(x, y + height * 0.18, z, 1.4, height * 0.36, length, "plate", rot=(tilt, 0, lean), pivot=(x, y, z)))
    for dz, h, l, fan in ((-length * 0.3, 0.72, 0.34, 20), (0.0, 1.0, 0.38, 0), (length * 0.32, 0.58, 0.32, -24)):
        bone_cubes.append(box(x, y + height * h / 2, z + dz, 1.0, height * h, length * l, "plate",
                              rot=(tilt + fan, 0, lean), pivot=(x, y, z + dz)))


def build_cubes():
    Y = HIP_Y
    cubes = {name: [] for name in BONES}
    # --- torso
    cubes["hips"] += [box(0, Y + 1, 0, 19, 20, 20), box(0, Y - 9, 1, 14, 3, 15, "belly"),
                      box(0, Y + 11.5, 1, 13, 2, 16), box(0, Y + 1, 10.5, 16, 17, 3)]
    cubes["chest"] += [box(0, Y + 2.5, -17.5, 18, 20, 18), box(0, Y - 8, -17, 13, 3, 15, "belly"),
                       box(0, Y + 13, -16.5, 12, 2, 15), box(0, Y - 3, -25.5, 14, 12, 3)]
    cubes["neck"] += [box(0, Y + 5.5, -29.5, 12.5, 13, 11), box(0, Y - 1.5, -29.5, 8.5, 2, 9, "belly")]
    cubes["neck2"] += [box(0, Y + 7.5, -37, 10.5, 11, 8.5), box(0, Y + 1.8, -37, 7, 2, 7.5, "belly")]
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
        "thigh_left": [box(x + 1, Y - 8, 0, 12, 20, 15), box(x + 1, Y - 0.5, 0, 13, 8, 17), box(x + 1.5, Y - 14.5, -1, 10, 4, 12)],
        "shin_left": [box(x, Y - 23.5, 0, 7.5, 14, 8), box(x, Y - 17.5, -1, 8.5, 4, 9), box(x, Y - 23, 3.2, 5, 9, 2)],
        "ankle_left": [box(x, Y - 34, 0, 5.5, 9, 5.5)],
        "foot_left": [box(x, Y - 39.3, -3, 6.5, 2.5, 9.5)],
    }
    for i, dx in enumerate((-2.2, 0, 2.2)):                    # three toes with claws
        left["foot_left"].append(box(x + dx, Y - 39.2, -9, 1.8, 2, 4, rot=(0, (1 - i) * 12, 0), pivot=(x + dx, Y - 39.2, -7)))
        left["foot_left"].append(box(x + dx, Y - 39.6, -11.6, 1.2, 1.4, 2.2, "claw", rot=(18, (1 - i) * 12, 0), pivot=(x + dx, Y - 39.2, -7)))
    left["foot_left"].append(box(x, Y - 38.6, 3, 1.6, 1.6, 3, "claw"))          # dewclaw at the back
    for name, cs in left.items():
        cubes[name] += cs
        cubes[name.replace("left", "right")] += mirror(cs)
    # --- arms
    a = {"arm_left": [box(7.5, Y - 4.5, -20, 3.5, 8, 3.5)],
         "forearm_left": [box(7.5, Y - 11.5, -20, 2.8, 7, 2.8)]}
    for i, dz in enumerate((-1, 0.6)):
        a["forearm_left"].append(box(7.5, Y - 15.5, -20 + dz, 0.8, 2.2, 0.8, "claw", rot=(35, 0, 0), pivot=(7.5, Y - 14.5, -20 + dz)))
    for name, cs in a.items():
        cubes[name] += cs
        cubes[name.replace("left", "right")] += mirror(cs)
    # --- dorsal plates: a centre row, with smaller rows either side on the back
    rows = [  # bone, z positions, base y, heights
        ("neck", [-27, -31.5], Y + 11.5, [6.5, 7]),
        ("neck2", [-35, -39], Y + 12.5, [5.5, 4.2]),
        ("chest", [-10.5, -15, -19.5, -24], Y + 13.5, [13, 14, 12.5, 10]),
        ("hips", [-5.5, -1, 3.5, 8], Y + 11.5, [14, 15, 13.5, 11.5]),
        ("tail1", [11, 15.5, 20, 24.5], Y + 9, [10, 9, 8, 7]),
        ("tail2", [29, 33.5, 38, 42], Y + 7.5, [6.5, 6, 5.5, 5]),
        ("tail3", [47, 51, 55, 58.5], Y + 6, [4.5, 4, 3.6, 3.2]),
        ("tail4", [63, 67, 71], Y + 4.8, [2.8, 2.4, 2.1]),
        ("tail5", [79, 84, 89], Y + 3.6, [1.8, 1.5, 1.2]),
    ]
    for bone, zs, base, hs in rows:
        for z, height in zip(zs, hs):
            plate(cubes[bone], 0, base, z, height, max(2.5, height * 0.55))
            if bone in ("neck", "chest", "hips", "tail1", "tail2"):
                for x, lean in ((3.4, 16), (-3.4, -16)):
                    plate(cubes[bone], x, base - 1.2, z + 1.2, height * 0.66, max(2.0, height * 0.42), lean=lean)
    return cubes


# ---------------------------------------------------------------- texture
COLORS = {  # base RGB for each kind of cube
    "skin": (62, 64, 58), "belly": (82, 82, 74), "plate": (70, 70, 66), "mouth": (132, 46, 48),
    "tooth": (196, 188, 168), "claw": (44, 42, 40), "eye": (190, 120, 40), "dark": (18, 18, 18),
}
BURNT = {
    "skin": (26, 23, 21), "belly": (34, 30, 27), "plate": (44, 40, 36), "mouth": (52, 24, 20),
    "tooth": (92, 86, 78), "claw": (20, 18, 17), "eye": (30, 26, 22), "dark": (10, 10, 10),
}


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
                region *= d[..., None]
            if c["kind"] == "plate":
                # pale towards the tips, dark at the root (rows run from the top of the plate down)
                grad = np.linspace(1.75, 0.8, h)[:, None, None]
                region *= grad
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
