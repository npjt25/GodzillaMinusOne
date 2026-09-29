"""Builds the GeckoLib models and textures of the mod's own props:

  * the searchlight: a WWII-style drum searchlight on a yoke, on a turntable with a seat for its operator;
    bone "turret" turns with the operator, bone "lamp" tilts up and down (client/render/SearchlightRenderer),
  * the nuclear bomb: a stubby, finned bomb in olive drab, built nose-down (it falls nose first).

    python3 tools/make_props.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import detail_godzilla  # noqa: E402  (worley noise for the metal's wear)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
TEXELS = 4


def box(cx, cy, cz, sx, sy, sz, kind, rot=None, pivot=None):
    c = {"origin": [cx - sx / 2, cy - sy / 2, cz - sz / 2], "size": [sx, sy, sz], "kind": kind}
    if rot:
        c["rotation"] = list(rot)
        c["pivot"] = list(pivot if pivot else (cx, cy, cz))
    return c


def octagon(cx, cy, cz, d, length, kind, axis="z"):
    """A round-ish prism of diameter d along the given axis (a stepped circle: two crossed slabs and a square)."""
    shapes = ((d, d * 0.6), (d * 0.6, d), (d * 0.86, d * 0.86))
    if axis == "z":
        return [box(cx, cy, cz, a, b, length, kind) for a, b in shapes]
    return [box(cx, cy, cz, a, length, b, kind) for a, b in shapes]


def pack(bones, size):
    items = []
    for bone in bones.values():
        for c in bone["cubes"]:
            sx, sy, sz = (max(1, math.ceil(v)) for v in c["size"])
            items.append((sy + sz, 2 * (sx + sz), c))
    items.sort(key=lambda it: (-it[0], -it[1]))
    x = y = shelf = 0
    for hgt, wid, c in items:
        if x + wid > size:
            x, y, shelf = 0, y + shelf, 0
        if y + hgt > size:
            raise SystemExit("texture sheet full")
        c["uv"] = [x, y]
        x += wid
        shelf = max(shelf, hgt)


def paint(bones, size, palette, glow_kinds, seed):
    rng = np.random.default_rng(seed)
    n = size * TEXELS
    f1, f2 = detail_godzilla.worley(n, 9, rng)
    wear = 0.9 + 0.12 * np.clip(1 - f1 / 6, 0, 1) - 0.1 * np.clip(1 - (f2 - f1) / 1.5, 0, 1) ** 2
    img = np.zeros((n, n, 4))
    glow = np.zeros((n, n, 4))
    for bone in bones.values():
        for c in bone["cubes"]:
            u, v = c["uv"]
            sx, sy, sz = (max(1, math.ceil(q)) for q in c["size"])
            w, h = 2 * (sx + sz) * TEXELS, (sy + sz) * TEXELS
            region = np.empty((h, w, 3))
            region[:] = palette[c["kind"]]
            region *= wear[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, None]
            region *= (0.95 + 0.1 * rng.random((h, w)))[..., None]
            if c["kind"] == "band":
                region[:] = palette["band"]
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, :3] = np.clip(region, 0, 255)
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, 3] = 255
            if c["kind"] in glow_kinds:
                glow[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w] = list(glow_kinds[c["kind"]]) + [255]
    return img, glow


def write(name, bones, size, palette, glow_kinds=None, seed=1):
    pack(bones, size)
    img, glow = paint(bones, size, palette, glow_kinds or {}, seed)
    tex = os.path.join(ASSETS, "textures", "entity")
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(tex, name + ".png"))
    if glow_kinds:
        Image.fromarray(glow.astype(np.uint8), "RGBA").save(os.path.join(tex, name + "_glow.png"))
    out = []
    for bname, b in bones.items():
        entry = {"name": bname, "pivot": b["pivot"]}
        if b.get("parent"):
            entry["parent"] = b["parent"]
        cubes = []
        for c in b["cubes"]:
            cube = {"origin": [round(q, 3) for q in c["origin"]], "size": [round(q, 3) for q in c["size"]], "uv": c["uv"]}
            for k in ("rotation", "pivot"):
                if k in c:
                    cube[k] = [round(q, 3) for q in c[k]]
            cubes.append(cube)
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + name, "texture_width": size, "texture_height": size,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}
    with open(os.path.join(ASSETS, "geo", "entity", name + ".geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")
    print("wrote", name, sum(len(b["cubes"]) for b in bones.values()), "cubes")


def searchlight():
    """About 2 blocks across and 2.5 tall; the lamp looks along -z."""
    base = {"pivot": [0, 0, 0], "cubes": []}
    base["cubes"] += octagon(0, 1.5, 0, 30, 3, "dark", axis="y")
    for x, z in ((12, 12), (-12, 12), (12, -12), (-12, -12)):
        base["cubes"].append(box(x, 0.75, z, 4, 1.5, 4, "dark"))
    turret = {"parent": "base", "pivot": [0, 3, 0], "cubes": []}
    turret["cubes"] += octagon(0, 4, 0, 24, 2, "metal", axis="y")
    for x in (13.5, -13.5):                                   # the yoke
        turret["cubes"].append(box(x, 14, 0, 3, 20, 6, "metal"))
        turret["cubes"].append(box(x, 5.5, 0, 5, 3, 9, "metal"))
    turret["cubes"].append(box(0, 5.5, 0, 25, 2, 5, "metal"))
    # the operator's seat, on the right of the lamp
    turret["cubes"].append(box(-21, 9, 4, 8, 1.5, 8, "seat"))
    turret["cubes"].append(box(-21, 6.5, 4, 2, 4, 2, "dark"))
    turret["cubes"].append(box(-21, 13, 7.8, 8, 7, 1.2, "seat"))
    turret["cubes"].append(box(-17, 5, 4, 8, 1.5, 3, "dark"))
    lamp = {"parent": "turret", "pivot": [0, 20, 0], "cubes": []}
    lamp["cubes"] += octagon(0, 20, 1, 24, 22, "metal")       # the drum
    lamp["cubes"] += octagon(0, 20, -10.8, 25, 2, "dark")     # front rim
    lamp["cubes"] += octagon(0, 20, -11.2, 21, 1, "lens")     # the glass
    lamp["cubes"] += octagon(0, 20, 12.8, 18, 2, "dark")      # rear cap
    lamp["cubes"].append(box(0, 20, 14.5, 6, 6, 2, "dark"))   # the arc lamp housing
    lamp["cubes"].append(box(0, 33, 3, 6, 4, 8, "dark"))      # vent on top
    lamp["cubes"].append(box(0, 36, 3, 8, 1, 10, "metal"))
    for x in (12.5, -12.5):
        lamp["cubes"].append(box(x, 20, 0, 1.5, 4, 4, "dark"))   # trunnions
    lamp["cubes"].append(box(-8, 14, 12, 1.5, 1.5, 8, "dark"))    # handle
    bones = {"base": base, "turret": turret, "lamp": lamp}
    palette = {"dark": (46, 50, 46), "metal": (96, 104, 92), "seat": (84, 62, 40), "lens": (236, 232, 206)}
    write("searchlight", bones, 256, palette, glow_kinds={"lens": (255, 250, 220)}, seed=3)


def nuclear_bomb():
    """About 3 blocks long, built nose down (the nose at the bottom, the tail fins at the top)."""
    body = {"pivot": [0, 0, 0], "cubes": []}
    body["cubes"] += octagon(0, 22, 0, 14, 30, "drab", axis="y")         # the casing
    body["cubes"] += octagon(0, 5.5, 0, 12, 3, "drab", axis="y")         # nose, tapering
    body["cubes"] += octagon(0, 3, 0, 9, 2, "drab", axis="y")
    body["cubes"] += octagon(0, 1.2, 0, 5, 1.6, "dark", axis="y")
    body["cubes"] += octagon(0, 16, 0, 14.6, 2, "band", axis="y")        # yellow band
    body["cubes"] += octagon(0, 38, 0, 10, 4, "drab", axis="y")          # tapering to the tail
    tail = {"parent": "body", "pivot": [0, 40, 0], "cubes": []}
    tail["cubes"].append(box(0, 46, 0, 16, 10, 16, "dark"))              # box tail
    tail["cubes"].append(box(0, 46, 0, 14, 10.2, 14, "void"))            # hollow look
    for rot in (0, 90):
        tail["cubes"].append(box(0, 44, 0, 20, 12, 1, "drab", rot=(0, rot + 45, 0), pivot=(0, 44, 0)))
    bones = {"body": body, "tail": tail}
    palette = {"drab": (82, 88, 58), "dark": (44, 46, 38), "band": (196, 168, 60), "void": (16, 16, 14)}
    write("nuclear_bomb", bones, 256, palette, seed=5)


def main():
    searchlight()
    nuclear_bomb()


if __name__ == "__main__":
    main()
