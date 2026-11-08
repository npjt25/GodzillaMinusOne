"""Builds Godzilla Minus Zero's look (client.render.GodzillaMinusZeroModel) from Minus One's (geo/entity/godzilla.geo.json
and its textures), as an evolved Minus One of this mod's own design: everything of Minus One's (every bone, so every
animation plays on him as on Minus One), plus

  - a second, staggered row of smaller plates along his lower back and tail, either side of the first and tilted out,
    on the same plate bones (so they light up with them in his charge),
  - overlapping armour bands round his upper arms and forearms, and layered caps on his shoulders,
  - a row of spines along either side of his tail,
  - every plate (his own and the new ones) drawn out into a sharp point, and the plate at the end of his tail into
    the longest and sharpest of all his tail's,
  - and a darker, heavier skin (the scorched skin he collapses in too).

The new parts' texture goes where Minus One's texture sheet has room (none of his own is moved), so the overlays
Minus One's renderer draws (eyes, mouth, burns, beam) fit Minus Zero unchanged; his plates' charge glow has his own
sheet with the new plates in it. None of the new parts reaches above Minus One's highest point (the points on the plates
that would are cut short to it), so Minus Zero, this model at exactly twice Minus One's scale, stands exactly twice
his height.

    python3 tools/make_minus_zero.py
"""
import copy
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
GEO_IN = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")
GEO_OUT = os.path.join(ASSETS, "geo", "entity", "godzilla_minus_zero.geo.json")
ANIM = os.path.join(ASSETS, "animations", "entity", "godzilla.animation.json")
TEX = os.path.join(ASSETS, "textures", "entity")
PX = 2  # texture pixels per texture unit (512 x 512 for a 256 x 256 sheet)

# plates of his tail and lower back that get a smaller plate either side
FLANKED = ["T12", "T15", "T16", "T17", "T18", "T19", "T20", "T21", "T22"]
TAIL = ["bone13", "bone14", "bone16", "bone17", "bone18"]


def cube(origin, size, uv=None, pivot=None, rotation=None, kind=None):
    c = {"origin": [round(v, 4) for v in origin], "size": list(size)}
    if pivot is not None:
        c["pivot"] = [round(v, 4) for v in pivot]
    if rotation is not None:
        c["rotation"] = [round(v, 4) for v in rotation]
    c["kind"] = kind
    return c


def mirrored(c):
    """The same cube on his other side (x -> -x)."""
    m = copy.deepcopy(c)
    ox, sx = c["origin"][0], c["size"][0]
    m["origin"][0] = round(-(ox + sx), 4)
    if "pivot" in c:
        m["pivot"][0] = round(-c["pivot"][0], 4)
    if "rotation" in c:
        m["rotation"] = [c["rotation"][0], -c["rotation"][1], -c["rotation"][2]]
    return m


def flank_plates(bone):
    """Smaller plates either side of a plate bone's tallest blades: 60% as tall, 2.5 units out, tilted out 25 degrees,
    set back half a plate (so the two rows are staggered)."""
    blades = sorted((c for c in bone["cubes"] if c["size"][0] <= 1 and c["size"][1] >= 7), key=lambda c: -c["size"][1])[:2]
    out = []
    for b in blades:
        h = max(4, int(round(b["size"][1] * 0.6)))
        d = max(1, int(round(b["size"][2])))
        ox, oy, oz = b["origin"]
        px, py, pz = b.get("pivot", [ox + 0.5, oy, oz + d / 2])
        rx = b.get("rotation", [0, 0, 0])[0]
        right = cube([ox - 2.5, oy, oz + 1.5], [1, h, d], pivot=[px - 2.0, oy, pz + 1.5], rotation=[rx, 0, 25], kind="plate")
        out += [right, mirrored(right)]
    return out


def arm_armour(upper, fore, shoulder):
    """Overlapping bands round the upper arm and forearm, and two layered caps over the shoulder (one arm)."""
    ux, uy, uz = upper["origin"]
    usx, usy, usz = upper["size"]
    out = []
    for i in range(3):
        y = uy + usy - 2.6 - i * 3.4
        out.append(cube([ux - 0.5, y, uz - 0.5], [usx + 1, 2, usz + 1], pivot=[ux + usx / 2, y + 1, uz + usz / 2],
                        rotation=[0, 0, 8], kind="band"))
    fx, fy, fz = fore["origin"]
    fsx, fsy, fsz = fore["size"]
    for i in range(3):
        z = fz + 1.0 + i * 4.5
        out.append(cube([fx - 0.5, fy - 0.5, z], [fsx + 1, fsy + 1, 2], pivot=[fx + fsx / 2, fy + fsy / 2, z + 1],
                        rotation=[-10, 0, 0], kind="band"))
    sx, sy, sz = shoulder["origin"]
    ssx, ssy, ssz = shoulder["size"]
    top = sy + ssy
    out.append(cube([sx - 1.0, top - 0.6, sz - 0.5], [ssx + 1, 2, ssz + 1], pivot=[sx + ssx / 2, top, sz + ssz / 2],
                    rotation=[0, 0, 14], kind="cap"))
    out.append(cube([sx - 1.8, top - 2.4, sz], [ssx - 1, 2, ssz], pivot=[sx, top - 1.4, sz + ssz / 2],
                    rotation=[0, 0, 30], kind="cap"))
    return out


def tail_spines(bone):
    """A row of spines along either side of a tail segment, near its top, as it's tilted."""
    main = max(bone["cubes"], key=lambda c: c["size"][0] * c["size"][1] * c["size"][2])
    ox, oy, oz = main["origin"]
    sx, sy, sz = main["size"]
    pivot = main.get("pivot", [0, oy, oz])
    rot = main.get("rotation", [0, 0, 0])
    length = 4 if sx >= 10 else 3
    out = []
    for f in (0.3, 0.7):
        z = oz + sz * f
        right = cube([ox + sx - 0.5, oy + sy * 0.65, z], [length, 1, 1], pivot=pivot, rotation=rot, kind="spine")
        left = cube([ox - length + 0.5, oy + sy * 0.65, z], [length, 1, 1], pivot=pivot, rotation=rot, kind="spine")
        out += [right, left]
    return out


def is_plate_bone(name):
    return name == "T" or (name.startswith("T") and name[1:].isdigit())


def is_blade(c):
    return c["size"][0] <= 1 and c["size"][1] >= 7


def tip(blade, length, depth, kind="tip"):
    """A point drawn out of the top of a plate: narrower than the plate, along it (its pivot and tilt)."""
    ox, oy, oz = blade["origin"]
    sx, sy, sz = blade["size"]
    t = cube([ox, oy + sy - 0.2, oz + (sz - depth) / 2.0], [sx, length, depth], kind=kind)
    if "pivot" in blade:
        t["pivot"] = list(blade["pivot"])
    if "rotation" in blade:
        t["rotation"] = list(blade["rotation"])
    return t


def sharpen(bones, adds):
    """Every plate drawn out into a point (half its height again, half as deep); the end of his tail (T22) into the
    longest and finest: as long as the plate, and a needle beyond that."""
    out = {}
    for name, bone in bones.items():
        if not is_plate_bone(name):
            continue
        for b in [c for c in bone["cubes"] if is_blade(c)] + [c for c in adds.get(name, []) if c["kind"] == "plate"]:
            sy, sz = b["size"][1], b["size"][2]
            if name == "T22":
                first = tip(b, max(4, int(round(sy * 1.0))), 1)
                needle = tip(first, max(3, int(round(sy * 0.6))), 0.5)
                out.setdefault(name, []).extend([first, needle])
            else:
                out.setdefault(name, []).append(tip(b, max(2, int(round(sy * 0.5))), max(0.5, round(sz / 2.0, 1))))
    return out


def cap_heights(bones_list, tips, ceiling, tw, th):
    """Points that would stand above Minus One's highest point are cut short to it (or left off)."""
    pose = rp.animation_pose(ANIM, "animation.gojira.idle", 0.0)
    m = rp.bone_matrices(bones_list, pose)
    kept = {}
    cut = 0
    for name, cs in tips.items():
        for c in cs:
            while True:
                top = max((np.c_[corners, np.ones(4)] @ m[name].T)[:, 1].max() for _, corners, _ in rp.face_list(c, tw, th))
                if top <= ceiling - 0.05:
                    kept.setdefault(name, []).append(c)
                    break
                cut += 1
                if c["size"][1] <= 1:
                    break
                c["size"][1] -= 1
    return kept, cut


def additions(bones):
    adds = {}
    for name in FLANKED:
        adds.setdefault(name, []).extend(flank_plates(bones[name]))

    def biggest(bone, pred=lambda c: True):
        return max((c for c in bone["cubes"] if pred(c)), key=lambda c: c["size"][0] * c["size"][1] * c["size"][2])

    upper = [c for c in bones["Right_arm"]["cubes"] if c["size"] == [6, 11, 6]][0]
    shoulder = [c for c in bones["Right_arm"]["cubes"] if "rotation" in c and c["rotation"][2] == 2.5][0]
    fore = biggest(bones["bone"])
    for c in arm_armour(upper, fore, shoulder):
        target = "bone" if c["kind"] == "band" and c["size"][2] == 2 else "Right_arm"
        adds.setdefault(target, []).append(c)
        adds.setdefault("bone4" if target == "bone" else "Left_arm", []).append(mirrored(c))
    for name in TAIL:
        adds.setdefault(name, []).extend(tail_spines(bones[name]))
    return adds


# ------------------------------------------------------------------------------------------------ texture sheet

def free_mask(geo, image):
    """Units of the 256 x 256 sheet no cube of Minus One's uses and nothing is painted on."""
    w, h = geo["description"]["texture_width"], geo["description"]["texture_height"]
    used = np.zeros((h, w), bool)
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            u, v = c["uv"]
            sx, sy, sz = (math.floor(k) for k in c["size"])
            used[v:v + sz + sy, u:u + 2 * (sx + sz)] = True
    painted = np.array(image)[::PX, ::PX, 3] > 0
    return ~used & ~painted


def pack(cubes, free):
    """Each new cube a place on the sheet for its box UV net (its integer size), where the sheet is free."""
    h, w = free.shape
    taken = ~free.copy()
    for c in sorted(cubes, key=lambda c: -(c["size"][1] + c["size"][2]) * (c["size"][0] + c["size"][2])):
        sx, sy, sz = (max(1, math.ceil(k)) for k in c["size"])   # (room for its net, whatever GeckoLib rounds it to)
        nw, nh = 2 * (sx + sz), sy + sz
        placed = False
        for v in range(0, h - nh + 1):
            for u in range(0, w - nw + 1):
                if not taken[v:v + nh, u:u + nw].any():
                    c["uv"] = [u, v]
                    taken[v:v + nh, u:u + nw] = True
                    placed = True
                    break
            if placed:
                break
        if not placed:
            raise SystemExit("no room on the texture sheet for a %s cube" % c["kind"])


def darken(img):
    """His skin, darker and heavier: shadows deeper, a cold charcoal cast."""
    a = np.array(img).astype(float)
    rgb = a[..., :3] / 255.0
    rgb = np.power(rgb, 1.15) * np.array([0.85, 0.87, 0.90])
    a[..., :3] = np.clip(rgb * 255.0, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def paint(img, cubes, rng, scorched=False):
    """The new parts: plates dark with worn pale edges, armour bands with a ridge catching the light along the top,
    caps the same, spines dark with bone-pale tips."""
    a = np.array(img).astype(float)
    base = np.array([44.0, 46.0, 50.0]) if not scorched else np.array([30.0, 26.0, 24.0])
    for c in cubes:
        u, v = c["uv"]
        sx, sy, sz = (max(1, math.ceil(k)) for k in c["size"])
        x0, y0 = u * PX, v * PX
        w, h = 2 * (sx + sz) * PX, (sy + sz) * PX
        region = np.empty((h, w, 3))
        region[:] = base
        region *= (0.85 + 0.3 * rng.random((h, w)))[..., None]
        ys = np.arange(h)[:, None] / max(1, h - 1)
        if c["kind"] in ("plate", "tip"):
            # paler towards the edge it points up with, scratched
            region += (np.clip(1.0 - ys * 3.0, 0, 1) * 40.0)[..., None]
            region[rng.random((h, w)) < 0.05] *= 1.6
        elif c["kind"] in ("band", "cap"):
            region *= 0.8
            ridge = (np.abs(ys - (sz * PX) / max(1, h)) < 0.12)
            region[ridge[:, 0]] += 16.0
            region[:, ::max(2, w // 12)] *= 0.75   # the overlapping edges of the scutes
        elif c["kind"] == "spine":
            xs = np.arange(w)[None, :] / max(1, w - 1)
            tip = np.clip((xs - 0.7) * 3.0, 0, 1) * 0.7
            region = region * (1 - tip[..., None]) + np.array([112.0, 106.0, 96.0]) * tip[..., None] * (0.6 if scorched else 1.0)
        a[y0:y0 + h, x0:x0 + w, :3] = np.clip(region, 0, 255)
        a[y0:y0 + h, x0:x0 + w, 3] = 255
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def paint_glow(img, cubes):
    """His plates' charge glow: the new plates glow as Minus One's do, brightest at their tips."""
    a = np.array(img).astype(float)
    lit = a[a[..., 3] > 0][:, :3]
    colour = np.median(lit, axis=0) if len(lit) else np.array([120.0, 200.0, 255.0])
    for c in cubes:
        if c["kind"] not in ("plate", "tip"):
            continue
        u, v = c["uv"]
        sx, sy, sz = (max(1, math.ceil(k)) for k in c["size"])
        x0, y0 = u * PX, v * PX
        w, h = 2 * (sx + sz) * PX, (sy + sz) * PX
        ys = np.arange(h)[:, None] / max(1, h - 1)
        strength = np.clip(1.15 - ys, 0.35, 1.0)
        a[y0:y0 + h, x0:x0 + w, :3] = np.clip(colour * strength[..., None] * np.ones((1, w, 1)), 0, 255)
        a[y0:y0 + h, x0:x0 + w, 3] = 255
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# ------------------------------------------------------------------------------------------------ height

def highest(bones, tw, th):
    """His highest point (texture units above his feet) in his standing pose, the beam (bone B) left out."""
    pose = rp.animation_pose(ANIM, "animation.gojira.idle", 0.0)
    m = rp.bone_matrices(bones, pose)
    top = -1e9
    for b in bones:
        if b["name"] == "B":
            continue
        for c in b.get("cubes", []):
            for _, corners, _ in rp.face_list(c, tw, th):
                pts = np.c_[corners, np.ones(4)] @ m[b["name"]].T
                top = max(top, pts[:, 1].max())
    return top


def main():
    data = json.load(open(GEO_IN))
    geo = data["minecraft:geometry"][0]
    bones = {b["name"]: b for b in geo["bones"]}
    adds = additions(bones)
    tw, th = geo["description"]["texture_width"], geo["description"]["texture_height"]
    ceiling = highest(geo["bones"], tw, th)
    tips, cut = cap_heights(geo["bones"], sharpen(bones, adds), ceiling, tw, th)
    for name, cs in tips.items():
        adds.setdefault(name, []).extend(cs)
    new = [c for cs in adds.values() for c in cs]
    skin = Image.open(os.path.join(TEX, "godzilla.png")).convert("RGBA")
    pack(new, free_mask(geo, skin))

    out = copy.deepcopy(data)
    ogeo = out["minecraft:geometry"][0]
    ogeo["description"]["identifier"] = "geometry.godzilla_minus_zero"
    for b in ogeo["bones"]:
        for c in adds.get(b["name"], []):
            b["cubes"].append({k: v for k, v in c.items() if k != "kind"})
    with open(GEO_OUT, "w") as f:
        json.dump(out, f, indent=1)
        f.write("\n")

    rng = np.random.default_rng(23)
    paint(darken(skin), new, rng).save(os.path.join(TEX, "godzilla_minus_zero.png"))
    dead = Image.open(os.path.join(TEX, "godzilla_dead.png")).convert("RGBA")
    paint(darken(dead), new, rng, scorched=True).save(os.path.join(TEX, "godzilla_minus_zero_dead.png"))
    glow = Image.open(os.path.join(TEX, "godzilla_charged_glow.png")).convert("RGBA")
    paint_glow(glow, new).save(os.path.join(TEX, "godzilla_minus_zero_charged_glow.png"))

    before = ceiling
    after = highest(ogeo["bones"], tw, th)
    print("added %d cubes (%s); points cut short to stay under his top: %d steps" % (len(new), ", ".join(
        "%s %d" % (k, sum(1 for c in new if c["kind"] == k)) for k in ("plate", "band", "cap", "spine", "tip")), cut))
    print("highest point: Minus One %.3f, Minus Zero's model %.3f (texture units)" % (before, after))
    if after > before + 1e-6:
        raise SystemExit("the new parts reach above Minus One's highest point: Minus Zero would stand more than twice his height")


if __name__ == "__main__":
    main()
