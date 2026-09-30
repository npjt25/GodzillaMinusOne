"""Builds the GeckoLib models and textures of the mod's own props:

  * the searchlight: a WWII-style drum searchlight on a yoke, on a turntable with a seat for its operator;
    bone "turret" turns with the operator, bone "lamp" tilts up and down (client/render/SearchlightRenderer),
  * the nuclear bomb: a stubby, finned bomb in olive drab, built nose-down (it falls nose first),
  * the Shinsei Maru: the film's small, run-down wooden minesweeper (a converted fishing boat), built at half size
    (the renderer doubles it): dark tarred hull, a weathered wheelhouse with the wheel up on its roof, a mast, cages
    on the foredeck, a davit and a rack of recovered naval mines astern, and the Type 93 13 mm machine gun on a
    platform at the stern pointing aft (bones "gun" turns, "gun_barrel" tilts; "mine1".."mine3" show the mines
    aboard; client/render/ShinseiMaruRenderer).

    python3 tools/make_props.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import noise  # noqa: E402  (worley noise for the metal's wear)

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


def paint(bones, size, palette, glow_kinds, seed, texels=TEXELS, planks=None):
    """Each cube's UV net in its kind's colour, worn; kinds in {@code planks} get plank seams: "h" across the
    texture's rows (strakes along a hull's side), "v" down its columns (deck boards along the top face)."""
    TEXELS = texels  # noqa: N806
    planks = planks or {}
    rng = np.random.default_rng(seed)
    n = size * TEXELS
    f1, f2 = noise.worley(n, 9, rng)
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
            direction = planks.get(c["kind"])
            if direction:
                step = 3 * TEXELS
                axis = 0 if direction == "h" else 1
                count = region.shape[axis]
                idx = np.arange(count)
                tint = 0.85 + 0.3 * rng.random(count // step + 2)[idx // step]   # each plank a little different
                seam = np.where(idx % step == 0, 0.55, 1.0)
                shade = tint * seam
                region *= shade[:, None, None] if axis == 0 else shade[None, :, None]
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, :3] = np.clip(region, 0, 255)
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, 3] = 255
            if c["kind"] in glow_kinds:
                glow[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w] = list(glow_kinds[c["kind"]]) + [255]
    return img, glow


def write(name, bones, size, palette, glow_kinds=None, seed=1, texels=TEXELS, planks=None, bounds=4):
    pack(bones, size)
    img, glow = paint(bones, size, palette, glow_kinds or {}, seed, texels, planks)
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
                        "visible_bounds_width": bounds, "visible_bounds_height": bounds, "visible_bounds_offset": [0, 1.5, 0]},
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
    lamp["cubes"] += octagon(0, 20, -12.0, 21, 0.5, "lens")   # the glass, just proud of the rim
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


# ---------------------------------------------------------------- the Shinsei Maru
# Designed in blocks at full size (x to starboard, y up, z aft: the bow is at -z), built at half size (8 model pixels
# to a block; the renderer draws it twice as big). ShinseiMaruEntity uses the same numbers for its seats, gun and stern.
HALF = 8.0
GUN_PIVOT = (0.0, 3.35, 5.1)     # where the machine gun turns
DRIVER_SEAT = (0.0, 4.45, 0.1)   # on the wheelhouse roof, behind the wheel
GUNNER_SEAT = (0.0, 2.35, 4.3)   # on the stern platform, behind the gun, facing aft


def hb(x0, y0, z0, x1, y1, z1, kind, rot=None, pivot=None):
    """A box between two corners given in (full-size) blocks."""
    c = [(x0 + x1) / 2 * HALF, (y0 + y1) / 2 * HALF, (z0 + z1) / 2 * HALF]
    size = [abs(x1 - x0) * HALF, abs(y1 - y0) * HALF, abs(z1 - z0) * HALF]
    return box(*c, *size, kind, rot=rot, pivot=[q * HALF for q in pivot] if pivot else None)


def shinsei_maru():
    """About 13.5 blocks long, 3.6 across; the wheelhouse roof is 4.5 blocks above the keel, the mast 9.5."""
    boat = {"pivot": [0, 0, 0], "cubes": []}
    c = boat["cubes"]
    # the hull: a rounded bottom in steps, the bow narrowing and rising, a flat transom
    c += [hb(-1.1, 0.0, -5.2, 1.1, 0.6, 5.4, "bottom"), hb(-1.55, 0.5, -5.6, 1.55, 1.2, 5.8, "bottom"),
          hb(-1.8, 1.1, -5.0, 1.8, 2.1, 5.9, "hull"), hb(-1.45, 0.7, -6.0, 1.45, 2.35, -5.0, "hull"),
          hb(-1.0, 1.0, -6.6, 1.0, 2.5, -6.0, "hull"), hb(-0.5, 1.4, -7.0, 0.5, 2.7, -6.6, "hull"),
          hb(-1.7, 1.0, 5.9, 1.7, 2.2, 6.3, "hull")]
    c += [hb(side * 1.8, 1.92, -5.0, side * 1.88, 2.06, 5.9, "rail") for side in (-1, 1)]        # rubbing strakes
    c += [hb(side * 1.4, 2.1, -6.0, side * 1.48, 2.3, -5.0, "rail") for side in (-1, 1)]
    # deck and bulwarks
    c.append(hb(-1.65, 2.1, -5.9, 1.65, 2.2, 5.8, "deck"))
    c += [hb(side * 1.66, 2.2, -5.0, side * 1.78, 2.6, 3.2, "hull") for side in (-1, 1)]
    c += [hb(side * 1.2, 2.3, -6.0, side * 1.45, 2.85, -5.0, "hull") for side in (-1, 1)]
    c.append(hb(-1.0, 2.5, -6.6, 1.0, 2.95, -6.0, "hull"))
    # the wheelhouse, its windows and roof
    c.append(hb(-1.25, 2.2, -1.2, 1.25, 4.3, 2.4, "cabin"))
    c += [hb(x - 0.28, 3.3, -1.26, x + 0.28, 3.9, -1.2, "window") for x in (-0.75, 0.0, 0.75)]
    c += [hb(side * 1.25, 3.3, z - 0.35, side * 1.31, 3.9, z + 0.35, "window") for side in (-1, 1) for z in (0.1, 1.5)]
    c.append(hb(-0.35, 2.2, 2.4, 0.35, 3.8, 2.46, "wood"))                                          # the door
    c.append(hb(-1.4, 4.3, -1.4, 1.4, 4.45, 2.6, "roof"))
    # the flying bridge: rails round the roof, the wheel on its stand
    for side in (-1, 1):
        c.append(hb(side * 1.32, 4.45, -1.35, side * 1.38, 5.2, 2.5, "rail"))
        c.append(hb(side * 1.32, 5.15, -1.35, side * 1.38, 5.25, 2.5, "rail"))
    c.append(hb(-1.38, 4.45, -1.4, 1.38, 5.25, -1.34, "rail"))
    c.append(hb(-0.12, 4.45, -0.85, 0.12, 5.05, -0.65, "wood"))
    c += octagon(0.0, 5.2 * HALF, -0.92 * HALF, 0.95 * HALF, 0.06 * HALF, "wood")                 # the wheel
    c.append(hb(0.75, 4.45, 1.9, 1.15, 6.0, 2.3, "metal"))                                          # the stack
    c.append(hb(0.7, 6.0, 1.85, 1.2, 6.15, 2.35, "rust"))
    # the mast, its yard and lamp
    c.append(hb(-0.1, 2.2, -2.5, 0.1, 9.2, -2.3, "wood"))
    c.append(hb(-1.05, 7.7, -2.46, 1.05, 7.82, -2.34, "wood"))
    c.append(hb(-0.18, 9.2, -2.58, 0.18, 9.5, -2.22, "lamp"))
    c.append(hb(-0.3, 9.5, -2.7, 0.3, 9.55, -2.1, "metal"))
    # cages and a coil of rope on the foredeck
    for x, y, z in ((-0.7, 2.2, -4.2), (0.6, 2.2, -4.0), (-0.05, 3.0, -4.1), (0.7, 2.2, -3.0), (-0.6, 2.2, -3.1)):
        c.append(hb(x - 0.45, y, z - 0.45, x + 0.45, y + 0.8, z + 0.45, "crate"))
    c += octagon(0.0, 2.35 * HALF, -5.3 * HALF, 0.7 * HALF, 0.3 * HALF, "rope", axis="y")
    # the stern platform and its rails
    c.append(hb(-1.6, 2.2, 3.4, 1.6, 2.35, 6.1, "deck"))
    for side in (-1, 1):
        c += [hb(side * 1.6, 2.35, z - 0.05, side * 1.68, 3.1, z + 0.05, "rail") for z in (3.5, 4.8, 6.05)]
        c.append(hb(side * 1.6, 3.0, 3.45, side * 1.68, 3.1, 6.1, "rail"))
    c.append(hb(-1.68, 3.0, 6.05, 1.68, 3.1, 6.15, "rail"))
    # the davit: a bent pipe over the port quarter, with a block hanging from it
    c.append(hb(-1.5, 2.35, 5.45, -1.34, 4.9, 5.61, "metal"))
    c.append(hb(-1.5, 4.8, 5.45, -1.34, 4.96, 6.4, "metal"))
    c.append(hb(-1.5, 4.3, 6.3, -1.34, 4.96, 6.46, "metal"))
    c.append(hb(-1.46, 3.5, 6.33, -1.38, 4.3, 6.41, "rope"))
    c.append(hb(-1.52, 3.3, 6.27, -1.32, 3.5, 6.47, "metal"))
    # the winch forward of the wheelhouse
    c.append(hb(-0.7, 2.2, -2.0, 0.7, 2.75, -1.5, "metal"))
    # the machine gun's pedestal and the gunner's seat
    gx, gy, gz = GUN_PIVOT
    c.append(hb(gx - 0.15, 2.35, gz - 0.15, gx + 0.15, gy - 0.05, gz + 0.15, "metal"))
    c.append(hb(-0.35, 2.35, 4.05, 0.35, 2.55, 4.5, "wood"))
    bones = {"boat": boat}
    # three recovered naval mines in a rack aft of the wheelhouse (shown as many as are aboard, up to three)
    for i, x in enumerate((-1.05, 0.0, 1.05)):
        mine = {"parent": "boat", "pivot": [x * HALF, 2.65 * HALF, 3.0 * HALF], "cubes": []}
        mine["cubes"] += octagon(x * HALF, 2.65 * HALF, 3.0 * HALF, 0.85 * HALF, 0.85 * HALF, "mine")
        for dx, dy, dz, sx, sy, sz in ((0, 0.5, 0, 0.1, 0.2, 0.1), (0.46, 0, 0, 0.2, 0.1, 0.1), (-0.46, 0, 0, 0.2, 0.1, 0.1),
                                       (0, 0, 0.46, 0.1, 0.1, 0.2), (0, 0, -0.46, 0.1, 0.1, 0.2)):
            mine["cubes"].append(box((x + dx) * HALF, (2.65 + dy) * HALF, (3.0 + dz) * HALF, sx * HALF, sy * HALF, sz * HALF, "rust"))
        bones["mine%d" % (i + 1)] = mine
    # the Type 93 13 mm machine gun: turns on its pedestal, the barrel tilts; it points aft
    gun = {"parent": "boat", "pivot": [gx * HALF, gy * HALF, gz * HALF], "cubes": []}
    gun["cubes"].append(hb(gx - 0.22, gy - 0.1, gz - 0.2, gx + 0.22, gy + 0.05, gz + 0.2, "metal"))    # the cradle
    barrel = {"parent": "gun", "pivot": [gx * HALF, gy * HALF, gz * HALF], "cubes": []}
    b = barrel["cubes"]
    b.append(hb(gx - 0.17, gy + 0.02, gz - 0.55, gx + 0.17, gy + 0.36, gz + 0.45, "gun"))              # receiver
    b += octagon(gx * HALF, (gy + 0.19) * HALF, (gz + 0.95) * HALF, 0.28 * HALF, 1.0 * HALF, "gun")    # cooling jacket
    b += octagon(gx * HALF, (gy + 0.19) * HALF, (gz + 1.75) * HALF, 0.13 * HALF, 0.6 * HALF, "gun")    # barrel
    b.append(hb(gx - 0.1, gy + 0.09, gz + 2.0, gx + 0.1, gy + 0.29, gz + 2.1, "gun"))                  # muzzle
    b.append(hb(gx - 0.2, gy + 0.36, gz - 0.25, gx + 0.2, gy + 0.62, gz + 0.1, "drab"))                # magazine
    b.append(hb(gx - 0.05, gy - 0.12, gz - 0.85, gx + 0.05, gy + 0.2, gz - 0.55, "metal"))             # spade grips
    b.append(hb(gx - 0.22, gy - 0.12, gz - 0.9, gx + 0.22, gy - 0.02, gz - 0.8, "metal"))
    bones["gun"] = gun
    bones["gun_barrel"] = barrel
    palette = {"bottom": (70, 36, 30), "hull": (48, 36, 30), "rail": (92, 68, 48), "deck": (112, 92, 68),
               "cabin": (106, 98, 86), "window": (28, 36, 44), "roof": (64, 62, 60), "wood": (96, 70, 46),
               "crate": (140, 112, 74), "rope": (150, 132, 96), "metal": (58, 60, 58), "rust": (112, 64, 38),
               "lamp": (214, 204, 168), "mine": (46, 48, 46), "gun": (40, 42, 40), "drab": (78, 84, 56)}
    write("shinsei_maru", bones, 512, palette, seed=7, texels=1, bounds=16,
          planks={"hull": "h", "bottom": "h", "cabin": "v", "deck": "v", "crate": "h"})


def main():
    searchlight()
    nuclear_bomb()
    shinsei_maru()


if __name__ == "__main__":
    main()
